package uk.co.eaton.drive.address;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import uk.co.eaton.drive.routing.RouteParser;

/** Disk-only offline street, house number and place search from a regional OSM index. */
public final class AddressStore {
    public static final class Result {
        public final String label;
        public final RouteParser.Point point;
        public Result(String label, double lat, double lon) {
            this.label = label;
            this.point = new RouteParser.Point(lat,lon);
        }
    }
    private final Context app;
    public AddressStore(Context context) { app = context.getApplicationContext(); }
    private File database() { return app.getDatabasePath("eaton-addresses.db"); }
    private boolean valid(File file) {
        if (!file.isFile() || file.length() < 65536) return false;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
            if (db.getVersion() != 1) return false;
            try (Cursor c = db.rawQuery("SELECT name,lat,lon FROM locations LIMIT 1",null)) {
                return c.moveToFirst();
            }
        } catch (Exception ignored) { return false; }
    }
    public boolean installed() { return valid(database()); }
    public String importIndex(Uri source) throws IOException {
        File target = database();
        File dir = target.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) throw new IOException("Cannot create offline search directory");
        File temp = new File(dir,"addresses-importing.db");
        if (temp.exists() && !temp.delete()) throw new IOException("Cannot remove old temporary index");
        long bytes = 0;
        try {
            try (InputStream input = app.getContentResolver().openInputStream(source);
                 FileOutputStream output = new FileOutputStream(temp)) {
                if (input == null) throw new IOException("Cannot access selected address file");
                byte[] buffer = new byte[128*1024];
                int n;
                while ((n=input.read(buffer))!=-1) {
                    if (n==0) continue;
                    bytes += n;
                    if (bytes > 600L*1024*1024) throw new IOException("Address index is too large");
                    if (dir.getUsableSpace() < 65L*1024*1024)
                        throw new IOException("Insufficient storage to install address index");
                    output.write(buffer,0,n);
                }
                output.getFD().sync();
            }
            if (!valid(temp)) throw new IOException("File is not a valid Eaton Drive address .db index");
            // Replacing an already open DB is unsafe; lookups are short-lived and close immediately.
            if (target.exists() && !target.delete()) throw new IOException("Cannot replace existing search index");
            if (!temp.renameTo(target)) throw new IOException("Cannot activate address search index");
            return "Offline street and address search ready";
        } finally {
            if (temp.exists()) temp.delete();
        }
    }
    public List<Result> search(String text) { return search(text,null); }
    public List<Result> search(String text, RouteParser.Point near) {
        List<Result> results = new ArrayList<>();
        if (!installed()) return results;
        String match = AddressQuery.matchExpression(text);
        if (match.isEmpty()) return results;
        String sql = "SELECT name,house,place,lat,lon FROM locations WHERE locations MATCH ? ";
        String[] args;
        if (near != null) {
            // Sort matching results by approximate squared distance before limiting.
            // This is especially important for common street names like High Street.
            sql += "ORDER BY ((CAST(lat AS REAL)-?)*(CAST(lat AS REAL)-?)"
                + "+0.37*(CAST(lon AS REAL)-?)*(CAST(lon AS REAL)-?)) LIMIT 40";
            args = new String[]{match,Double.toString(near.lat),Double.toString(near.lat),
                Double.toString(near.lon),Double.toString(near.lon)};
        } else {
            sql += "LIMIT 40";
            args = new String[]{match};
        }
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database().getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
             Cursor c = db.rawQuery(sql,args)) {
            while (c.moveToNext()) {
                String road=c.getString(0),house=c.getString(1),place=c.getString(2);
                if (road == null || road.trim().isEmpty()) continue;
                String label = ((house==null||house.isEmpty())?"":house+" ") + road;
                if (place!=null&&!place.isEmpty()) label+=", "+place;
                try { results.add(new Result(label,c.getDouble(3),c.getDouble(4))); }
                catch (IllegalArgumentException ignored) { }
            }
        } catch (RuntimeException ignored) { }
        return results;
    }
}

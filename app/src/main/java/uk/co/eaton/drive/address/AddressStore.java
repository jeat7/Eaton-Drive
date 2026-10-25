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
    private volatile String lastSearchError = "";
    public AddressStore(Context context) { app = context.getApplicationContext(); }
    public String lastSearchError() { return lastSearchError; }
    /** The number of search-index entries; zero also indicates missing/corrupt data. */
    public long recordCount() {
        if (!installed()) return 0;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database().getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
             Cursor c = db.rawQuery("SELECT COUNT(*) FROM locations",null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } catch (RuntimeException exception) {
            lastSearchError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            return 0;
        }
    }
    private File database() { return app.getDatabasePath("eaton-addresses.db"); }
    private boolean valid(File file) {
        if (!file.isFile() || file.length() < 65536) return false;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
            if (db.getVersion() < 1 || db.getVersion() > 2) return false;
            try (Cursor c = db.rawQuery("SELECT name,lat,lon FROM locations LIMIT 1",null)) {
                return c.moveToFirst();
            }
        } catch (Exception ignored) { return false; }
    }
    public boolean installed() { return valid(database()); }
    public boolean hasDetailedAreaLabels() {
        if (!installed()) return false;
        try (SQLiteDatabase db=SQLiteDatabase.openDatabase(database().getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
            return db.getVersion()>=2;
        } catch (RuntimeException ignored) { return false; }
    }
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
            // Preserve the previous search index if activation fails, e.g. low storage.
            File backup = new File(dir,"addresses-backup.db");
            if (backup.exists() && !backup.delete())
                throw new IOException("Cannot clear previous address backup");
            if (target.exists() && !target.renameTo(backup))
                throw new IOException("Cannot back up current address index");
            if (!temp.renameTo(target)) {
                if (backup.exists() && !backup.renameTo(target))
                    throw new IOException("Cannot activate new index or restore previous index");
                throw new IOException("Cannot activate new address index; previous index restored");
            }
            if (backup.exists()) backup.delete();
            return "Offline street and address search ready";
        } finally {
            if (temp.exists()) temp.delete();
        }
    }
    public List<Result> search(String text) { return search(text,null); }
    public List<Result> search(String text, RouteParser.Point near) {
        List<Result> results = new ArrayList<>();
        lastSearchError = "";
        if (!installed()) {
            lastSearchError = "Offline address index is not installed";
            return results;
        }
        String match = AddressQuery.matchExpression(text);
        if (match.isEmpty()) return results;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database().getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
            // v2 includes locally computed neighbourhood and postcode district.
            boolean rich = db.getVersion() >= 2;
            String columns = rich ? "name,house,place,lat,lon,locality,district" : "name,house,place,lat,lon";
            // First use the indexed lookup, which is fast for 400,000+ records.
            try {
                String sql = "SELECT " + columns + " FROM locations WHERE locations MATCH ? ";
                String[] args;
                if (near != null) {
                    sql += "ORDER BY ((CAST(lat AS REAL)-?)*(CAST(lat AS REAL)-?)"
                        + "+0.37*(CAST(lon AS REAL)-?)*(CAST(lon AS REAL)-?)) LIMIT 40";
                    args = new String[]{match,Double.toString(near.lat),Double.toString(near.lat),
                        Double.toString(near.lon),Double.toString(near.lon)};
                } else {
                    sql += "LIMIT 40";
                    args = new String[]{match};
                }
                try (Cursor c = db.rawQuery(sql,args)) {
                    read(c,results);
                }
            } catch (RuntimeException ftsProblem) {
                lastSearchError = "Indexed search: " + ftsProblem.getClass().getSimpleName()
                    + ": " + ftsProblem.getMessage();
            }
            // Android device SQLite versions differ. Fall back to an exact street
            // substring search (not generic full-table postcode geocoding).
            if (results.isEmpty()) {
                String street = text == null ? "" : text.trim();
                if (street.length() > 1 && street.length() < 120) {
                    String sql = "SELECT " + columns + " FROM locations"
                        + " WHERE LOWER(name) LIKE ? ESCAPE '\\' LIMIT 40";
                    String safe = street.toLowerCase(Locale.UK)
                        .replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
                    try (Cursor c = db.rawQuery(sql,new String[]{"%"+safe+"%"})) {
                        read(c,results);
                        if (!results.isEmpty()) lastSearchError = "";
                    } catch (RuntimeException fallbackProblem) {
                        lastSearchError += " / Street fallback: "
                            + fallbackProblem.getClass().getSimpleName() + ": " + fallbackProblem.getMessage();
                    }
                }
            }
        } catch (RuntimeException openProblem) {
            lastSearchError = "Cannot open offline address index: " + openProblem.getMessage();
        }
        return results;
    }
    private static void read(Cursor c, List<Result> results) {
        while (c.moveToNext()) {
            String road = c.getString(0), house = c.getString(1), place = c.getString(2);
            if (road == null || road.trim().isEmpty()) continue;
            String locality = c.getColumnCount() > 5 ? c.getString(5) : "";
            String district = c.getColumnCount() > 6 ? c.getString(6) : "";
            String label = AddressLabel.format(road,house,place,locality,district);
            // The index has many nearby segments of the same street; display each
            // distinct location label once. FTS results are already distance-sorted.
            boolean duplicate = false;
            for (Result prior : results)
                if (prior.label.equalsIgnoreCase(label)) {duplicate = true; break;}
            if (duplicate) continue;
            try { results.add(new Result(label,c.getDouble(3),c.getDouble(4))); }
            catch (IllegalArgumentException ignored) { }
        }
    }
}

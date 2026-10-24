package uk.co.eaton.drive.postcode;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

import uk.co.eaton.drive.routing.RouteParser;

/**
 * Disk-backed lookup: ~1.7M records can be indexed without holding them in RAM.
 * Import runs on caller's worker thread. Input needs pre-calculated WGS84 lat/lon.
 */
public final class PostcodeStore {
    public interface Progress { void onProgress(int rows); }
    private final Context context;
    public PostcodeStore(Context context) { this.context = context.getApplicationContext(); }
    private File database() { return context.getDatabasePath("eaton-postcodes.db"); }
    public boolean installed() { return database().isFile(); }

    public RouteParser.Point lookup(String postcode) {
        if (!installed()) return null;
        String code = PostcodeCsv.normalise(postcode);
        if (!PostcodeCsv.plausible(code)) return null;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database().getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT lat,lon FROM postcodes WHERE code=?", new String[] { code })) {
            if (!cursor.moveToFirst()) return null;
            return new RouteParser.Point(cursor.getDouble(0), cursor.getDouble(1));
        } catch (Exception ex) { return null; }
    }

    public int importCsv(Uri source, Progress progress) throws Exception {
        File target = database();
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not create postcode directory");
        File temp = new File(parent, "eaton-postcodes-import.db");
        if (temp.exists() && !temp.delete()) throw new IOException("Previous import file is in use");
        SQLiteDatabase db = null;
        int count = 0;
        boolean success = false;
        try (InputStream raw = context.getContentResolver().openInputStream(source)) {
            if (raw == null) throw new IOException("Cannot read selected file");
            BufferedInputStream buffered = new BufferedInputStream(raw);
            buffered.mark(2);
            int a = buffered.read(), b = buffered.read();
            buffered.reset();
            InputStream content = (a == 0x1f && b == 0x8b) ? new GZIPInputStream(buffered) : buffered;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(content, StandardCharsets.UTF_8), 64 * 1024)) {
                String first = reader.readLine();
                if (first == null) throw new IOException("CSV file is empty");
                List<String> head = PostcodeCsv.fields(first);
                int pc = PostcodeCsv.index(head, "postcode", "pcd", "pcds");
                int lat = PostcodeCsv.index(head, "latitude", "lat");
                int lon = PostcodeCsv.index(head, "longitude", "lon", "lng");
                if (pc < 0 || lat < 0 || lon < 0)
                    throw new IOException("CSV must contain postcode, latitude and longitude headers; OS easting/northing-only CSV must be converted first");
                int required = Math.max(pc, Math.max(lat, lon));
                db = SQLiteDatabase.openOrCreateDatabase(temp, null);
                db.execSQL("CREATE TABLE postcodes(code TEXT PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL) WITHOUT ROWID");
                db.beginTransaction();
                SQLiteStatement insert = db.compileStatement("INSERT OR REPLACE INTO postcodes(code,lat,lon) VALUES(?,?,?)");
                try {
                    String line;
                    int scanned = 0;
                    while ((line = reader.readLine()) != null) {
                        if (++scanned > 2_750_000) throw new IOException("Excessive postcode records");
                        if (line.isEmpty()) continue;
                        List<String> items;
                        try { items = PostcodeCsv.fields(line); }
                        catch (IllegalArgumentException error) { continue; }
                        if (items.size() <= required) continue;
                        String code = PostcodeCsv.normalise(items.get(pc));
                        if (!PostcodeCsv.plausible(code)) continue;
                        try {
                            double latitude = Double.parseDouble(items.get(lat));
                            double longitude = Double.parseDouble(items.get(lon));
                            RouteParser.Point checked = new RouteParser.Point(latitude, longitude);
                            insert.clearBindings();
                            insert.bindString(1, code);
                            insert.bindDouble(2, checked.lat);
                            insert.bindDouble(3, checked.lon);
                            insert.executeInsert();
                        } catch (IllegalArgumentException badCoordinate) { continue; }
                        count++;
                        if (count % 25_000 == 0) {
                            db.setTransactionSuccessful();
                            db.endTransaction();
                            progress.onProgress(count);
                            db.beginTransaction();
                        }
                    }
                } finally {
                    insert.close();
                    db.setTransactionSuccessful();
                    db.endTransaction();
                }
                if (count < 100) throw new IOException("Insufficient valid postcode records (minimum 100)");
                db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)");
                db.close();
                db = null;
                File backup = new File(parent, "eaton-postcodes-backup.db");
                if (backup.exists()) backup.delete();
                if (target.exists() && !target.renameTo(backup)) throw new IOException("Could not back up old postcodes");
                if (!temp.renameTo(target)) {
                    if (backup.exists()) backup.renameTo(target);
                    throw new IOException("Could not install imported database");
                }
                backup.delete();
                success = true;
                return count;
            }
        } finally {
            if (db != null) db.close();
            if (!success) temp.delete();
        }
    }
}

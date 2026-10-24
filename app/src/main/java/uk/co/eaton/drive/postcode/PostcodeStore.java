package uk.co.eaton.drive.postcode;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import uk.co.eaton.drive.routing.RouteParser;

/**
 * Disk-backed exact postcode lookup. An indexed database is prepared on the CI
 * builder, packaged in the APK, and copied once. No CSV parsing on a 1GB unit.
 */
public final class PostcodeStore {
    public interface Progress { void onProgress(int rows); }
    private static final String ASSET = "gb-postcodes.db";
    private static final int EXPECTED_SCHEMA = 5;
    private final Context context;
    public PostcodeStore(Context context) { this.context = context.getApplicationContext(); }
    private File database() { return context.getDatabasePath("eaton-postcodes.db"); }

    /** Verify an existing DB, rather than treating an incomplete import as installed. */
    public boolean installed() { return valid(database()); }
    private boolean valid(File file) {
        if (!file.isFile() || file.length() < 2_000_000L) return false;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
             Cursor probe = db.rawQuery("SELECT code,lat,lon FROM postcodes LIMIT 1", null)) {
            return probe.moveToFirst() && probe.getString(0) != null;
        } catch (Exception e) { return false; }
    }

    public RouteParser.Point lookup(String postcode) {
        String code = PostcodeCsv.normalise(postcode);
        if (!PostcodeCsv.plausible(code) || !installed()) return null;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database().getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
             Cursor cursor = db.rawQuery("SELECT lat,lon FROM postcodes WHERE code=?", new String[] { code })) {
            if (cursor.moveToFirst())
                return new RouteParser.Point(cursor.getDouble(0), cursor.getDouble(1));
        } catch (Exception ignored) { }
        return null;
    }

    /** Run on an executor, never on the UI thread; happens only after initial app installation. */
    public String ensureBundled() throws IOException {
        File target = database();
        if (valid(target)) return "Offline postcode search ready";
        File folder = target.getParentFile();
        if (folder == null || (!folder.exists() && !folder.mkdirs()))
            throw new IOException("Cannot create database directory");
        File temp = new File(folder, "postcodes-installing.tmp");
        if (temp.exists() && !temp.delete()) throw new IOException("Cannot clear old temporary database");
        long total = 0;
        try {
            try (InputStream input = context.getAssets().open(ASSET);
                 OutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (n == 0) continue;
                    if (folder.getUsableSpace() < 32L*1024*1024)
                        throw new IOException("Storage almost full; need 32MB spare after database copy");
                    output.write(buffer, 0, n);
                    total += n;
                }
                output.flush();
            } catch (IOException failure) {
                throw new IOException("Could not unpack built-in postcode data: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage(), failure);
            }
            if (!valid(temp)) throw new IOException("Bundled postcode database failed validation");
            // Confirm schema before replacing a user-installed DB.
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(temp.getAbsolutePath(), null,SQLiteDatabase.OPEN_READONLY)) {
                if (db.getVersion() != EXPECTED_SCHEMA)
                    throw new IOException("Postcode database version mismatch");
            }
            if (target.exists() && !target.delete()) throw new IOException("Cannot replace invalid postcode database");
            if (!temp.renameTo(target)) throw new IOException("Cannot activate postcode database");
            return "Offline postcode search ready (" + (total / (1024*1024)) + " MB installed)";
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    /** Legacy CSV imports are deprecated: use the bundled indexed database instead. */
    @Deprecated public int importCsv(Uri unused, Progress ignored) throws IOException {
        throw new IOException("Update to the bundled postcode database; importing raw CSV is no longer necessary");
    }
}

package uk.co.eaton.drive.routing;

import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * One-time streaming road network installation from a prepared BRouter ZIP or
 * individually selected .rd5 tiles. No unrestricted filesystem permissions.
 */
public final class OfflineRoadImporter {
    public interface Progress { void onProgress(String detail); }
    private static final long MAX_SEGMENT = 150L * 1024 * 1024;
    private static final long MAX_TOTAL = 950L * 1024 * 1024;
    private static final int MAX_TILES = 30;
    private final Context context;
    private final File directory;
    public OfflineRoadImporter(Context context, File directory) {
        this.context = context.getApplicationContext();
        this.directory = directory;
    }
    public int importDocuments(List<Uri> sources, Progress progress) throws IOException {
        if (sources == null || sources.isEmpty()) throw new IOException("No files selected");
        if (sources.size() > MAX_TILES) throw new IOException("Too many files selected");
        int loaded = 0;
        long[] bytes = { 0 };
        for (Uri source : sources) {
            String name = documentName(source);
            try (InputStream sourceStream = context.getContentResolver().openInputStream(source)) {
                if (sourceStream == null) throw new IOException("Could not open " + name);
                if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(sourceStream))) {
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                            if (!entry.isDirectory()) {
                                String filename = new File(entry.getName().replace('\\','/')).getName();
                                if (RoadFileNames.isSegmentName(filename)) {
                                    if (++loaded > MAX_TILES) throw new IOException("Too many road tiles");
                                    importOne(filename, zip, bytes, progress, true);
                                }
                            }
                            zip.closeEntry();
                        }
                    }
                } else if (RoadFileNames.isSegmentName(name)) {
                    if (++loaded > MAX_TILES) throw new IOException("Too many road tiles");
                    importOne(name, sourceStream, bytes, progress, false);
                } else {
                    throw new IOException("Choose the BRouter routing ZIP or .rd5 road segment files; got " + name);
                }
            }
        }
        if (loaded == 0) throw new IOException("No BRouter .rd5 road files found in the selected ZIP");
        return loaded;
    }
    private void importOne(String filename, InputStream in, long[] total, Progress progress,
                           boolean fromZip) throws IOException {
        if (!RoadFileNames.isSegmentName(filename)) throw new IOException("Invalid routing filename");
        File output = new File(directory, filename.toUpperCase(Locale.ROOT));
        File part = new File(directory, filename.toUpperCase(Locale.ROOT) + ".incomplete");
        if (part.exists() && !part.delete()) throw new IOException("Cannot clear partial tile");
        progress.onProgress("Installing road tile " + filename);
        long count = 0;
        try {
            try (FileOutputStream fileOut = new FileOutputStream(part);
                 BufferedOutputStream out = new BufferedOutputStream(fileOut, 128 * 1024)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (read == 0) continue;
                    count += read;
                    total[0] += read;
                    if (count > MAX_SEGMENT || total[0] > MAX_TOTAL)
                        throw new IOException("Routing archive is too large");
                    if (directory.getUsableSpace() < 55L * 1024 * 1024)
                        throw new IOException("Insufficient space; keep at least 55MB free");
                    out.write(buffer, 0, read);
                }
                out.flush();
                fileOut.getFD().sync();
            }
            if (count < 16_384) throw new IOException("Routing segment is incomplete: " + filename);
            if (output.exists() && !output.delete()) throw new IOException("Cannot replace existing routing segment");
            if (!part.renameTo(output)) throw new IOException("Could not activate routing segment " + filename);
        } finally {
            if (part.exists()) part.delete();
        }
    }
    private String documentName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String s = cursor.getString(0);
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "unknown" : uri.getLastPathSegment();
    }
}

package uk.co.eaton.drive.routing;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import btools.router.RoutingContext;
import btools.router.RoutingEngine;
import btools.router.RoutingParamCollector;
import btools.router.OsmNodeNamed;
import btools.router.OsmTrack;
import btools.router.FormatGpx;

/**
 * BRouter 1.7.10 runs inside Eaton Drive, not as a second Android application.
 * Open-source MIT BRouter copyright notice included as an app asset.
 */
public final class EmbeddedRouter {
    public interface Callback { void completed(RouteParser.Route route, String error); }
    private static final String PROFILE = "car-vario.brf";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean busy;
    private boolean destroyed;

    public EmbeddedRouter(Context context) { this.context = context.getApplicationContext(); }

    public File roadDirectory() {
        File directory = context.getExternalFilesDir("routing-segments");
        if (directory == null) directory = new File(context.getFilesDir(), "routing-segments");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    public int segmentCount() {
        File[] files = roadDirectory().listFiles(file -> file.isFile()
            && RoadFileNames.isSegmentName(file.getName()) && file.length() > 16_384L);
        return files == null ? 0 : files.length;
    }

    private File profileFile() throws Exception {
        File file = new File(context.getFilesDir(), "routing-profiles/" + PROFILE);
        if (!file.isFile() || file.length() < 1024) {
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Cannot prepare driving profile");
            File part = new File(parent, PROFILE + ".tmp");
            try (InputStream input = context.getAssets().open("brouter/" + PROFILE);
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
                out.getFD().sync();
            }
            if (file.exists() && !file.delete()) throw new IllegalStateException("Cannot update driving profile");
            if (!part.renameTo(file)) throw new IllegalStateException("Could not activate driving profile");
        }
        return file;
    }

    public void request(RouteParser.Point start, RouteParser.Point finish, Callback callback) {
        if (destroyed) { callback.completed(null,"Navigation screen closed"); return; }
        if (busy) { callback.completed(null, "A route calculation is already underway"); return; }
        if (segmentCount() == 0) {
            callback.completed(null,"Road network not installed. Tap ROADS and select your UK routing ZIP.");
            return;
        }
        busy = true;
        executor.execute(() -> {
            RouteParser.Route answer = null;
            String error = null;
            try {
                File profile = profileFile();
                String positions = start.lon + "," + start.lat + "|" + finish.lon + "," + finish.lat;
                RoutingParamCollector helper = new RoutingParamCollector();
                List<OsmNodeNamed> waypoints = helper.getWayPointList(positions);
                RoutingContext rc = new RoutingContext();
                rc.localFunction = profile.getAbsolutePath();
                rc.turnInstructionMode = 3;  // OsmAnd GPX turn annotations for our voice parser
                rc.outputFormat = "gpx";
                rc.memoryclass = 48;         // Conservative for 1GB head unit
                RoutingEngine engine = new RoutingEngine(null, null, roadDirectory(), waypoints, rc);
                engine.quite = true;
                engine.doRun(45_000);
                if (engine.getErrorMessage() != null)
                    throw new IllegalStateException(engine.getErrorMessage());
                OsmTrack track = engine.getFoundTrack();
                if (track == null) throw new IllegalStateException("No drivable route found using installed road data");
                track.exportWaypoints = rc.exportWaypoints;
                track.exportCorrectedWaypoints = rc.exportCorrectedWaypoints;
                answer = RouteParser.parse(new FormatGpx(rc).format(track));
            } catch (Exception exception) {
                String detail = exception.getMessage();
                error = (detail == null || detail.isEmpty()) ? exception.getClass().getSimpleName() : detail;
            } catch (OutOfMemoryError errorOom) {
                error = "Not enough memory to calculate this route on this device";
            }
            RouteParser.Route result = answer;
            String message = error;
            ui.post(() -> {
                busy = false;
                if (!destroyed) callback.completed(result,message);
            });
        });
    }
    public void destroy() {
        destroyed = true;
        executor.shutdownNow();
    }
}

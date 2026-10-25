package uk.co.eaton.drive.routing;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * BRouter requires car-vario.brf and lookups.dat in the same directory.
     * Both are included as assets and installed on first route calculation.
     */
    private File profileFile() throws Exception {
        File directory = new File(context.getFilesDir(), "routing-profiles");
        if (!directory.exists() && !directory.mkdirs())
            throw new IllegalStateException("Cannot create internal routing configuration");
        File lookup = new File(directory, "lookups.dat");
        File profile = new File(directory, PROFILE);
        installAssetIfMissing("brouter/lookups.dat", lookup, 10_000);
        installAssetIfMissing("brouter/" + PROFILE, profile, 1024);
        return profile;
    }

    private void installAssetIfMissing(String asset, File target, long minSize) throws Exception {
        if (target.isFile() && target.length() >= minSize) return;
        File temporary = new File(target.getParentFile(), target.getName() + ".incoming");
        try {
            try (InputStream input = context.getAssets().open(asset);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > 0) output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (temporary.length() < minSize)
                throw new IllegalStateException("Invalid bundled routing file: " + asset);
            if (target.exists() && !target.delete())
                throw new IllegalStateException("Cannot update routing configuration: " + target.getName());
            if (!temporary.renameTo(target))
                throw new IllegalStateException("Cannot activate routing configuration: " + target.getName());
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    /** Compatible overload for automatic reroutes using default, unrestricted routing. */
    public void request(RouteParser.Point start, RouteParser.Point finish, Callback callback) {
        request(start,finish,new RouteOptions(false,false,0),callback);
    }

    public void request(RouteParser.Point start, RouteParser.Point finish,
                        RouteOptions options, Callback callback) {
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
                // Profile variables are parsed by BRouter and respected during routing.
                Map<String,String> settings = new HashMap<>();
                settings.put("avoid_motorways", options.avoidMotorways ? "1" : "0");
                settings.put("avoid_toll", options.avoidTolls ? "1" : "0");
                rc.keyValues = settings;
                rc.setAlternativeIdx(options.alternativeIndex);
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
                answer = RouteParser.parse(new FormatGpx(rc).format(track))
                    .withEstimatedSeconds(track.getTotalSeconds());
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

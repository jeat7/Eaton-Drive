package uk.co.eaton.drive;

import android.Manifest;
import android.os.SystemClock;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.widget.EditText;
import android.text.InputType;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.overlay.FixedPixelCircle;
import uk.co.eaton.drive.routing.BRouterClient;
import uk.co.eaton.drive.routing.RouteParser;
import uk.co.eaton.drive.routing.NavigationSession;
import uk.co.eaton.drive.routing.VoiceGuide;
import uk.co.eaton.drive.postcode.PostcodeStore;
import java.util.ArrayList;
import java.util.Locale;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.datastore.MultiMapDataStore;
import java.util.Arrays;
import java.util.Comparator;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import uk.co.eaton.drive.gps.GpsTracker;
import uk.co.eaton.drive.ui.DriveUi;

/** Foreground-only offline route guidance prototype with optional spoken turns. */
public final class MapActivity extends Activity {
    private static final int IMPORT_MAP = 43;
    private static final int LOCATION_REQUEST = 902;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private MapView mapView;
    private TextView status, placeholder, speed;
    private GpsTracker gps;
    private Location lastFix;
    private MultiMapDataStore mapFileReader;
    private volatile boolean copying;
    private BRouterClient routing;
    private PostcodeStore postcodes;
    private VoiceGuide voice;
    private NavigationSession navigation;
    private boolean routeCalculating;
    private boolean stopped;
    private boolean autoCentredOnGps;
    private boolean userMovedMap;
    private boolean preparingPostcodes;
    private RouteParser.Point destination;
    private Polyline routeOverlay;
    private FixedPixelCircle destinationOverlay;
    private boolean mapReady;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        AndroidGraphicFactory.createInstance(getApplication());
        buildScreen();
        gps = new GpsTracker(this, this::onGps);
        routing = new BRouterClient(this);
        postcodes = new PostcodeStore(this);
        voice = new VoiceGuide(this);
        loadInstalledMaps();
        prepareBuiltInPostcodes();
    }
    private void buildScreen() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(DriveUi.BG);
        setContentView(outer);
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) userMovedMap = true;
            return false;
        });
        mapView.setBuiltInZoomControls(true);
        mapView.getMapScaleBar().setVisible(true);
        outer.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        placeholder = DriveUi.text(this, "", 21, DriveUi.TEXT, true);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setBackgroundColor(DriveUi.BG);
        FrameLayout.LayoutParams placeholderLp = new FrameLayout.LayoutParams(-1, -1);
        placeholderLp.topMargin = DriveUi.dp(this, 90);
        placeholderLp.bottomMargin = DriveUi.dp(this, 63);
        outer.addView(placeholder, placeholderLp);

        LinearLayout toolbar = DriveUi.column(this);
        toolbar.setBackgroundColor(DriveUi.BG);
        DriveUi.pad(this, toolbar, 5, 2);
        LinearLayout row1 = DriveUi.row(this);
        LinearLayout row2 = DriveUi.row(this);
        toolbar.addView(row1, new LinearLayout.LayoutParams(-1, DriveUi.dp(this, 43)));
        toolbar.addView(row2, new LinearLayout.LayoutParams(-1, DriveUi.dp(this, 43)));
        addButton(row1, "‹ BACK", this::finish);
        TextView title = DriveUi.text(this, "EATON  /  NAVIGATION", 17, DriveUi.TEXT, true);
        title.setGravity(Gravity.CENTER);
        row1.addView(title, DriveUi.weighted(0, -1, 1f));
        addButton(row1, "GPS", this::centreOnGps);
        addButton(row1, "VOICE", this::toggleVoice);
        addButton(row2, "POSTCODE", this::enterPostcode);
        addButton(row2, "MAP DEST", this::selectMapCentre);
        addButton(row2, "START ROUTE", () -> calculateRoute(false));
        addButton(row2, "STOP", this::clearNavigation);
        addButton(row2, "IMPORT MAP", this::importMap);
        addButton(row2, "DATA", this::showDataStatus);
        outer.addView(toolbar, new FrameLayout.LayoutParams(-1, DriveUi.dp(this, 90), Gravity.TOP));

        LinearLayout footer = DriveUi.row(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(DriveUi.BG);
        DriveUi.pad(this, footer, 16, 5);
        status = DriveUi.text(this, "", 12, DriveUi.MUTED, false);
        footer.addView(status, DriveUi.weighted(0, -1, 1f));
        speed = DriveUi.text(this, "— MPH", 17, DriveUi.TEAL, true);
        footer.addView(speed);
        outer.addView(footer, new FrameLayout.LayoutParams(-1, DriveUi.dp(this, 63), Gravity.BOTTOM));
    }
    private void addButton(LinearLayout row, String label, Runnable action) {
        TextView button = DriveUi.text(this, label, 12, DriveUi.TEAL, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(DriveUi.bg(this, DriveUi.PANEL2, 10, true));
        button.setPadding(DriveUi.dp(this, 7), 0, DriveUi.dp(this, 7), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -1);
        lp.setMargins(DriveUi.dp(this, 3), DriveUi.dp(this, 3), DriveUi.dp(this, 3), DriveUi.dp(this, 3));
        row.addView(button, lp);
        button.setOnClickListener(v -> action.run());
    }
    private void setMessage(String message) {
        if (status != null) status.setText(message);
        if (placeholder != null) {
            placeholder.setVisibility(mapReady ? View.GONE : View.VISIBLE);
            placeholder.setText(message);
        }
    }
    private File destinationFile() {
        File dir = getExternalFilesDir("offline-maps");
        if (dir == null) dir = new File(getFilesDir(), "offline-maps");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "active.map");
    }
    private void importMap() {
        if (copying) return;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        startActivityForResult(picker, IMPORT_MAP);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null || copying) return;
        if (requestCode != IMPORT_MAP) return;
        copying = true;
        setMessage("Importing local offline map...");
        final android.net.Uri chosen = data.getData();
        io.execute(() -> {
            File target = new File(destinationFile().getParentFile(), selectedMapFilename(chosen));
            File temporary = new File(target.getParentFile(), "incoming.map");
            String error = null;
            try (InputStream input = getContentResolver().openInputStream(chosen);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("Unable to open selected map");
                byte[] buffer = new byte[128 * 1024];
                int n;
                long total = 0;
                while ((n = input.read(buffer)) != -1) {
                    if (n == 0) continue;
                    if (target.getParentFile().getUsableSpace() < 80L * 1024 * 1024)
                        throw new IOException("Not enough free storage");
                    output.write(buffer, 0, n);
                    total += n;
                }
                output.getFD().sync();
                if (total < 1024L) throw new IOException("The selected file is too small");
                MapFile validation = new MapFile(temporary);
                try {
                    if (validation.startPosition() == null) throw new IOException("Invalid Mapsforge .map file");
                } finally { validation.close(); }
                if (target.exists() && !target.delete()) throw new IOException("Cannot replace old map");
                if (!temporary.renameTo(target)) throw new IOException("Unable to complete import");
            } catch (Exception ex) {
                error = ex.getMessage() == null ? "Unknown error" : ex.getMessage();
            }
            if (error != null) temporary.delete();
            final String finalError = error;
            main.post(() -> {
                copying = false;
                if (finalError != null) {
                    setMessage("Map import failed: " + finalError);
                    new AlertDialog.Builder(this).setTitle("Import failed").setMessage(finalError).setPositiveButton("OK", null).show();
                } else recreate();
            });
        });
    }
    /** Install a pre-indexed lookup from the APK on first launch, never parse CSV on-device. */
    private void prepareBuiltInPostcodes() {
        if (postcodes.installed()) return;
        preparingPostcodes = true;
        status.setText("Preparing built-in offline postcodes (first launch only)...");
        io.execute(() -> {
            String result;
            try {
                result = postcodes.ensureBundled();
            } catch (Exception failure) {
                String reason = failure.getMessage();
                result = "Postcodes unavailable: " + (reason == null ? failure.getClass().getSimpleName() : reason);
            }
            String message = result;
            main.post(() -> {
                preparingPostcodes = false;
                if (navigation == null && !routeCalculating) status.setText(message);
            });
        });
    }
    private void showDataStatus() {
        String message = (postcodes.installed()
            ? "Offline Great Britain postcode search is ready."
            : (preparingPostcodes ? "Postcode data is being prepared on this device."
                : "The built-in postcode database is unavailable. Check storage and reinstall the current APK."))
            + "\n\nYour offline map files remain saved between launches."
            + "\nMap regions are rendered as needed near the screen position."
            + "\n\nPostcode data: Postcodes UK, September 2026. GB only. Offline map © OpenStreetMap contributors.";
        new AlertDialog.Builder(this).setTitle("Offline data")
            .setMessage(message).setPositiveButton("OK", null).show();
    }
    private void enterPostcode() {
        if (!postcodes.installed()) {
            new AlertDialog.Builder(this).setTitle("Offline postcodes")
                .setMessage(preparingPostcodes
                    ? "Preparing the built-in postcode database. This only happens after the first installation."
                    : "Postcode data is not ready. Check that the latest APK was installed and at least 100MB storage is free.")
                .setPositiveButton("OK", null).show();
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("e.g. B1 1AA");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(this).setTitle("Offline postcode destination")
            .setView(input)
            .setPositiveButton("Find", (d,w) -> {
                RouteParser.Point found = postcodes.lookup(input.getText().toString());
                if (found == null) {
                    status.setText("Postcode not found in the imported offline database");
                    return;
                }
                setDestination(found);
                if (mapReady) {
                    mapView.setCenter(new LatLong(found.lat,found.lon));
                    mapView.setZoomLevel((byte) 15);
                }
                status.setText("Postcode selected • Press START ROUTE");
            }).setNegativeButton("Cancel", null).show();
    }
    private void toggleVoice() {
        boolean on = voice.toggle();
        status.setText(on ? voice.status() : "Spoken guidance muted");
    }
    private String selectedMapFilename(Uri selected) {
        String supplied = null;
        try (Cursor cursor = getContentResolver().query(selected,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) supplied = cursor.getString(0);
        } catch (Exception ignored) { }
        if (supplied == null) supplied = "offline.map";
        String safe = supplied.toLowerCase(Locale.UK).replaceAll("[^a-z0-9._-]", "_");
        if (!safe.endsWith(".map") || safe.length() > 65 ||
            safe.equals("incoming.map") || safe.equals("active.map")) {
            safe = "imported-" + System.currentTimeMillis() + ".map";
        }
        return safe;
    }
    private void loadInstalledMaps() {
        File directory = destinationFile().getParentFile();
        File[] maps = directory.listFiles(file -> file.isFile()
            && file.getName().toLowerCase(Locale.UK).endsWith(".map")
            && file.length() > 1024L);
        if (maps == null || maps.length == 0) {
            setMessage("Import England, Wales or Scotland Mapsforge .map files");
            return;
        }
        Arrays.sort(maps, Comparator.comparing(File::getName));
        MultiMapDataStore combined = new MultiMapDataStore(MultiMapDataStore.DataPolicy.DEDUPLICATE);
        try {
            int count = 0;
            for (File file : maps) {
                try {
                    MapFile reader = new MapFile(file);
                    combined.addMapDataStore(reader, count == 0, count == 0);
                    count++;
                } catch (Exception failed) {
                    throw new IOException("Invalid " + file.getName() + ": " + failed.getMessage(), failed);
                }
            }
            TileCache cache = AndroidUtil.createTileCache(this, "eaton-map-cache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor());
            TileRendererLayer renderer = new TileRendererLayer(cache, combined,
                mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
            renderer.setXmlRenderTheme(MapsforgeThemes.MOTORIDER);
            mapView.getLayerManager().getLayers().add(renderer);
            mapFileReader = combined;
            mapView.setCenter(combined.startPosition());
            mapView.setZoomLevel(combined.startZoomLevel());
            restoreLastMapPosition();
            mapReady = true;
            setMessage(count + " offline map(s) loaded • © OpenStreetMap contributors");
        } catch (Exception ex) {
            combined.close();
            mapReady = false;
            setMessage("Could not load offline maps: " + ex.getMessage());
        }
    }
    /** Restore previous viewport while awaiting an accurate GPS fix. */
    private void restoreLastMapPosition() {
        try {
            String lat = getPreferences(0).getString("map_lat", null);
            String lon = getPreferences(0).getString("map_lon", null);
            if (lat != null && lon != null) {
                double latitude = Double.parseDouble(lat);
                double longitude = Double.parseDouble(lon);
                if (Double.isFinite(latitude) && Double.isFinite(longitude)
                    && latitude >= -90 && latitude <= 90
                    && longitude >= -180 && longitude <= 180) {
                    mapView.setCenter(new LatLong(latitude, longitude));
                    int zoom = getPreferences(0).getInt("map_zoom", 14);
                    mapView.setZoomLevel((byte)Math.max(3,Math.min(19,zoom)));
                }
            }
        } catch (RuntimeException ignored) { }
    }
    private void saveLastMapPosition() {
        if (!mapReady) return;
        LatLong position = mapView.getModel().mapViewPosition.getCenter();
        if (position == null) return;
        getPreferences(0).edit()
            .putString("map_lat",Double.toString(position.latitude))
            .putString("map_lon",Double.toString(position.longitude))
            .putInt("map_zoom",mapView.getZoomLevel())
            .apply();
    }
    private void centreOnGps() {
        if (!mapReady) { Toast.makeText(this, "Import a map first", Toast.LENGTH_SHORT).show(); return; }
        if (lastFix == null) { Toast.makeText(this, "Waiting for GPS", Toast.LENGTH_SHORT).show(); return; }
        mapView.setCenter(new LatLong(lastFix.getLatitude(), lastFix.getLongitude()));
        mapView.setZoomLevel((byte) 16);
    }
    private void selectMapCentre() {
        if (!mapReady) { Toast.makeText(this, "Import a map first", Toast.LENGTH_SHORT).show(); return; }
        LatLong target = mapView.getModel().mapViewPosition.getCenter();
        setDestination(new RouteParser.Point(target.latitude, target.longitude));
        status.setText("Map destination set • Press START ROUTE");
    }
    private void setDestination(RouteParser.Point point) {
        destination = point; stopped = false;
        navigation = null;
        removeRouteOverlay();
        if (destinationOverlay != null) mapView.getLayerManager().getLayers().remove(destinationOverlay);
        if (mapReady) {
            Paint fill = AndroidGraphicFactory.INSTANCE.createPaint();
            fill.setColor(DriveUi.TEAL); fill.setStyle(Style.FILL);
            destinationOverlay = new FixedPixelCircle(new LatLong(point.lat,point.lon),
                DriveUi.dp(this, 10), fill, null);
            mapView.getLayerManager().getLayers().add(destinationOverlay);
        }
    }
    private void calculateRoute(boolean automatic) {
        if (!mapReady) { Toast.makeText(this, "Import a map first", Toast.LENGTH_LONG).show(); return; }
        if (destination == null) { Toast.makeText(this, "Pan the map to the destination and press SET DEST", Toast.LENGTH_LONG).show(); return; }
        if (lastFix == null || !lastFix.hasAccuracy() || lastFix.getAccuracy() > 45f) {
            status.setText("Waiting for accurate GPS to calculate the route"); return;
        }
        if (routeCalculating) return;
        routeCalculating = true;
        stopped = false;
        RouteParser.Point requestedDestination = destination;
        RouteParser.Point start = new RouteParser.Point(lastFix.getLatitude(), lastFix.getLongitude());
        status.setText(automatic ? "Off route • recalculating offline..." : "Calculating offline driving route...");
        routing.request(start, destination, (route, error) -> {
            routeCalculating = false;
            if (stopped || requestedDestination != destination) return;
            if (error != null) {
                status.setText("Route unavailable: " + error);
                if (navigation != null) navigation.failedReroute();
                if (!automatic) new AlertDialog.Builder(this).setTitle("Offline route unavailable")
                    .setMessage(error + "\n\nBRouter and its local driving (.rd5) segments are required.")
                    .setPositiveButton("BRouter setup", (d,w) -> openBRouterSetup())
                    .setNegativeButton("Close", null).show();
                return;
            }
            removeRouteOverlay();
            Paint stroke = AndroidGraphicFactory.INSTANCE.createPaint();
            stroke.setColor(DriveUi.TEAL);
            stroke.setStyle(Style.STROKE);
            stroke.setStrokeWidth(DriveUi.dp(this, 6));
            routeOverlay = new Polyline(stroke, AndroidGraphicFactory.INSTANCE);
            ArrayList<LatLong> points = new ArrayList<>(route.points.size());
            for (RouteParser.Point point : route.points)
                points.add(new LatLong(point.lat, point.lon));
            routeOverlay.setPoints(points);
            mapView.getLayerManager().getLayers().add(routeOverlay);
            if (navigation == null) navigation = new NavigationSession(route);
            else navigation.reset(route);
            status.setText(String.format(Locale.UK, "Route: %.1f miles • %s • %s",
                route.miles, navigation.hasTurns() ? "Guidance active" : "No turn data", voice.status()));
            if (navigation.hasTurns()) voice.say(automatic ? "New route calculated" : "Navigation started");
            mapView.setCenter(new LatLong(start.lat, start.lon));
            mapView.setZoomLevel((byte) 16);
        });
    }
    private void openBRouterSetup() {
        try {
            Intent setup = new Intent();
            setup.setClassName("btools.routingapp", "btools.routingapp.BRouterActivity");
            startActivity(setup);
        } catch (Exception ex) {
            new AlertDialog.Builder(this).setTitle("BRouter not installed")
                .setMessage("Install the BRouter Android app from a trusted source, open it, choose motorcar routing and download local routing segments covering your region. Then come back to Eaton Drive.")
                .setPositiveButton("OK", null).show();
        }
    }
    private void removeRouteOverlay() {
        if (routeOverlay != null) {
            mapView.getLayerManager().getLayers().remove(routeOverlay);
            routeOverlay = null;
        }
    }
    private void clearNavigation() {
        stopped = true;
        navigation = null;
        if (voice != null) voice.say("Navigation stopped");
        destination = null;
        removeRouteOverlay();
        if (destinationOverlay != null) {
            mapView.getLayerManager().getLayers().remove(destinationOverlay);
            destinationOverlay = null;
        }
        status.setText(mapReady ? "Route cleared • Select destination using SET DEST" : "Import a map first");
    }
    private void onGps(Integer mph, String state, Location location) {
        speed.setText(mph == null ? "— MPH" : mph + " MPH");
        lastFix = location;
        if (location != null && mapReady && !autoCentredOnGps && !userMovedMap
            && location.hasAccuracy() && location.getAccuracy() <= 60f
            && navigation == null && destination == null) {
            mapView.setCenter(new LatLong(location.getLatitude(), location.getLongitude()));
            mapView.setZoomLevel((byte)15);
            autoCentredOnGps = true;
        }
        if (navigation == null || location == null || stopped || routeCalculating) return;
        NavigationSession.Update update = navigation.locate(
            new RouteParser.Point(location.getLatitude(), location.getLongitude()),
            location.hasAccuracy() ? location.getAccuracy() : 999f,
            SystemClock.elapsedRealtime());
        status.setText(update.display);
        if (update.speak != null) voice.say(update.speak);
        if (update.reroute) calculateRoute(true);
        if (update.arrived) {
            navigation = null;
            status.setText("Destination reached");
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (gps != null) {
            if (!gps.hasPermission()) requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, LOCATION_REQUEST);
            else gps.start();
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] result) {
        super.onRequestPermissionsResult(code, permissions, result);
        if (code == LOCATION_REQUEST && gps != null) gps.start();
    }
    @Override protected void onPause() {
        saveLastMapPosition();
        if (gps != null) gps.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        io.shutdown();
        if (routing != null) routing.destroy();
        if (voice != null) voice.shutdown();
        if (mapView != null) mapView.destroyAll();
        if (mapFileReader != null) try { mapFileReader.close(); } catch (Exception ignored) { }
        AndroidGraphicFactory.clearResourceMemoryCache();
        super.onDestroy();
    }
}

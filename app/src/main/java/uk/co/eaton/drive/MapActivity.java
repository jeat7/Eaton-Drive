package uk.co.eaton.drive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
import java.util.ArrayList;
import java.util.Locale;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import uk.co.eaton.drive.gps.GpsTracker;
import uk.co.eaton.drive.ui.DriveUi;

/** Offline local vector-map VIEWER; not turn-by-turn navigation yet. */
public final class MapActivity extends Activity {
    private static final int IMPORT_MAP = 43;
    private static final int LOCATION_REQUEST = 902;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private MapView mapView;
    private TextView status, placeholder, speed;
    private GpsTracker gps;
    private Location lastFix;
    private MapFile mapFileReader;
    private volatile boolean copying;
    private BRouterClient routing;
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
        File file = destinationFile();
        if (file.isFile() && file.length() > 0) loadMap(file);
        else setMessage("Import an offline Mapsforge .map file to begin.");
    }
    private void buildScreen() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(DriveUi.BG);
        setContentView(outer);
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(true);
        mapView.getMapScaleBar().setVisible(true);
        outer.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        placeholder = DriveUi.text(this, "", 21, DriveUi.TEXT, true);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setBackgroundColor(DriveUi.BG);
        FrameLayout.LayoutParams placeholderLp = new FrameLayout.LayoutParams(-1, -1);
        placeholderLp.topMargin = DriveUi.dp(this, 61);
        placeholderLp.bottomMargin = DriveUi.dp(this, 63);
        outer.addView(placeholder, placeholderLp);

        LinearLayout toolbar = DriveUi.row(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(DriveUi.BG);
        DriveUi.pad(this, toolbar, 12, 5);
        addButton(toolbar, "‹  BACK", this::finish);
        TextView title = DriveUi.text(this, "EATON  /  NAVIGATION", 17, DriveUi.TEXT, true);
        title.setGravity(Gravity.CENTER);
        toolbar.addView(title, DriveUi.weighted(0, -1, 1f));
        addButton(toolbar, "⌖ GPS", this::centreOnGps);
        addButton(toolbar, "SET DEST", this::selectMapCentre);
        addButton(toolbar, "ROUTE", this::calculateRoute);
        addButton(toolbar, "CLEAR", this::clearNavigation);
        addButton(toolbar, "MAP FILE", this::importMap);
        outer.addView(toolbar, new FrameLayout.LayoutParams(-1, DriveUi.dp(this, 61), Gravity.TOP));

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
        TextView button = DriveUi.text(this, label, 13, DriveUi.TEAL, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(DriveUi.bg(this, DriveUi.PANEL2, 10, true));
        button.setPadding(DriveUi.dp(this, 10), 0, DriveUi.dp(this, 10), 0);
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
        if (requestCode != IMPORT_MAP || resultCode != RESULT_OK || data == null || data.getData() == null || copying) return;
        copying = true;
        setMessage("Importing local offline map...");
        final android.net.Uri chosen = data.getData();
        io.execute(() -> {
            File target = destinationFile();
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
    private void loadMap(File file) {
        try {
            mapFileReader = new MapFile(file);
            TileCache cache = AndroidUtil.createTileCache(this, "eaton-map-cache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor());
            TileRendererLayer renderer = new TileRendererLayer(cache, mapFileReader,
                mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
            renderer.setXmlRenderTheme(MapsforgeThemes.MOTORIDER);
            mapView.getLayerManager().getLayers().add(renderer);
            mapView.setCenter(mapFileReader.startPosition());
            mapView.setZoomLevel(mapFileReader.startZoomLevel());
            mapReady = true;
            setMessage("Map ready • Select a destination using SET DEST • © OpenStreetMap contributors");
        } catch (Exception ex) {
            mapReady = false;
            setMessage("Could not load map: " + ex.getMessage());
        }
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
        destination = new RouteParser.Point(target.latitude, target.longitude);
        removeRouteOverlay();
        if (destinationOverlay != null) mapView.getLayerManager().getLayers().remove(destinationOverlay);
        Paint fill = AndroidGraphicFactory.INSTANCE.createPaint();
        fill.setColor(DriveUi.TEAL);
        fill.setStyle(Style.FILL);
        destinationOverlay = new FixedPixelCircle(target, DriveUi.dp(this, 10), fill, null);
        mapView.getLayerManager().getLayers().add(destinationOverlay);
        status.setText(String.format(Locale.UK, "Destination selected (%.4f, %.4f) • Press ROUTE", target.latitude, target.longitude));
    }
    private void calculateRoute() {
        if (!mapReady) { Toast.makeText(this, "Import a map first", Toast.LENGTH_LONG).show(); return; }
        if (destination == null) { Toast.makeText(this, "Pan the map to the destination and press SET DEST", Toast.LENGTH_LONG).show(); return; }
        if (lastFix == null) { Toast.makeText(this, "Waiting for a fresh GPS position", Toast.LENGTH_LONG).show(); return; }
        RouteParser.Point start = new RouteParser.Point(lastFix.getLatitude(), lastFix.getLongitude());
        status.setText("Calculating offline driving route • this can take up to 45 seconds...");
        routing.request(start, destination, (route, error) -> {
            if (error != null) {
                status.setText("Route unavailable: " + error);
                new AlertDialog.Builder(this).setTitle("Offline route unavailable")
                    .setMessage(error + "\n\nBRouter and its local driving (.rd5) segments are required in addition to the map file.")
                    .setPositiveButton("BRouter setup", (d, w) -> openBRouterSetup())
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
            status.setText(String.format(Locale.UK, "Route: %.1f miles • Preview only: no spoken instructions or live guidance", route.miles));
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
        if (gps != null) gps.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        io.shutdown();
        if (routing != null) routing.destroy();
        if (mapView != null) mapView.destroyAll();
        if (mapFileReader != null) try { mapFileReader.close(); } catch (Exception ignored) { }
        AndroidGraphicFactory.clearResourceMemoryCache();
        super.onDestroy();
    }
}

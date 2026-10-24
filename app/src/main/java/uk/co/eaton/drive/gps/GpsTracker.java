package uk.co.eaton.drive.gps;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import uk.co.eaton.drive.util.SpeedMath;

/** Foreground GPS, no internet or background location service. */
public final class GpsTracker implements LocationListener {
    public interface Callback { void onGps(Integer mph, String status, Location location); }
    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private Location latest;
    private boolean running;
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (!running) return;
            publish();
            handler.postDelayed(this, 1500);
        }
    };
    public GpsTracker(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        this.manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    public boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    public void start() {
        if (running) return;
        if (!hasPermission()) { callback.onGps(null, "GPS permission required", null); return; }
        if (manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            callback.onGps(null, "GPS disabled or unavailable", null); return;
        }
        try {
            running = true;
            latest = null;
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
            publish();
            handler.postDelayed(watchdog, 1500);
        } catch (SecurityException | IllegalArgumentException e) {
            running = false;
            callback.onGps(null, "GPS unavailable", null);
        }
    }
    public void stop() {
        running = false;
        handler.removeCallbacks(watchdog);
        if (manager != null) try { manager.removeUpdates(this); } catch (SecurityException ignored) { }
        latest = null;
    }
    private void publish() {
        if (latest == null) { callback.onGps(null, "Searching for GPS satellites", null); return; }
        if (!SpeedMath.isFresh(SystemClock.elapsedRealtime(), latest.getElapsedRealtimeNanos() / 1_000_000L)) {
            callback.onGps(null, "GPS signal lost", null); return;
        }
        Integer mph = latest.hasSpeed() ? SpeedMath.toMph(latest.getSpeed()) : null;
        callback.onGps(mph, mph == null ? "GPS fixed - speed unavailable" : "GPS connected", latest);
    }
    @Override public void onLocationChanged(Location location) {
        if (running && location != null) { latest = location; publish(); }
    }
    @Override public void onProviderEnabled(String provider) { if (running) publish(); }
    @Override public void onProviderDisabled(String provider) { callback.onGps(null, "GPS disabled", null); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
}

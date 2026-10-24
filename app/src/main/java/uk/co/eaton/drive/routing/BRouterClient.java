package uk.co.eaton.drive.routing;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import btools.routingapp.IBRouterService;

/**
 * BRouter runs in its own app process. Requires a separate BRouter installation,
 * car profile, and offline .rd5 routing segments, even if the map is available.
 */
public final class BRouterClient {
    public interface Callback { void completed(RouteParser.Route route, String error); }
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean bound;
    private boolean busy;
    private boolean destroyed;
    private ServiceConnection connection;

    public BRouterClient(Context context) { this.context = context.getApplicationContext(); }

    public void request(RouteParser.Point start, RouteParser.Point finish, Callback callback) {
        if (destroyed || busy) {
            callback.completed(null, "A route calculation is already running");
            return;
        }
        busy = true;
        Intent intent = new Intent();
        intent.setClassName("btools.routingapp", "btools.routingapp.BRouterService");
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                IBRouterService service = IBRouterService.Stub.asInterface(binder);
                executor.execute(() -> {
                    RouteParser.Route route = null;
                    String error = null;
                    try {
                        Bundle args = new Bundle();
                        args.putString("lonlats", Double.toString(start.lon) + "," + Double.toString(start.lat)
                            + "|" + Double.toString(finish.lon) + "," + Double.toString(finish.lat));
                        args.putString("v", "motorcar");
                        args.putString("fast", "1");
                        args.putString("trackFormat", "gpx");
                        args.putString("maxRunningTime", "45");
                        args.putString("acceptCompressedResult", "true");
                        args.putString("turnInstructionFormat", "osmand");
                        String response = service.getTrackFromParams(args);
                        route = RouteParser.parse(decodeResponse(response));
                    } catch (Exception problem) {
                        error = problem.getMessage();
                        if (error == null || error.trim().isEmpty()) error = "Route calculation failed";
                    }
                    final RouteParser.Route value = route;
                    final String message = error;
                    ui.post(() -> {
                        releaseConnection();
                        busy = false;
                        if (!destroyed) callback.completed(value, message);
                    });
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                ui.post(() -> {
                    if (!destroyed && busy) {
                        releaseConnection();
                        busy = false;
                        callback.completed(null, "Offline route service disconnected");
                    }
                });
            }
        };
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                busy = false;
                callback.completed(null, "Install and configure BRouter with local driving segments");
            }
        } catch (SecurityException | IllegalArgumentException failure) {
            busy = false;
            callback.completed(null, "BRouter service unavailable: " + failure.getMessage());
        }
    }

    /** BRouter Base64-encodes a z64 header followed by gzip; the encoded prefix is ejY0. */
    static String decodeResponse(String value) throws Exception {
        if (value == null) throw new IllegalArgumentException("No routing response");
        if (!value.startsWith("ejY0")) return value;
        byte[] bytes = Base64.decode(value, Base64.DEFAULT);
        if (bytes.length < 4 || bytes[0] != 'z' || bytes[1] != '6' || bytes[2] != '4')
            throw new IllegalArgumentException("Invalid compressed route");
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes, 3, bytes.length - 3));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                if (out.size() + count > 12_000_000)
                    throw new IllegalArgumentException("Route is too large");
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private void releaseConnection() {
        if (bound && connection != null) {
            try { context.unbindService(connection); } catch (IllegalArgumentException ignored) { }
        }
        bound = false;
        connection = null;
    }
    public void destroy() {
        destroyed = true;
        releaseConnection();
        executor.shutdownNow();
    }
}

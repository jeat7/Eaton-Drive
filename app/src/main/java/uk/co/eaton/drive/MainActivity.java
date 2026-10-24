package uk.co.eaton.drive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import uk.co.eaton.drive.gps.GpsTracker;
import uk.co.eaton.drive.ui.DriveUi;

/** Optional launcher; never modifies OEM radio, camera, MCU, or Android firmware. */
public final class MainActivity extends Activity {
    private static final int GPS_PERMISSION_REQUEST = 901;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GpsTracker gps;
    private TextView speedValue, speedStatus, clock;
    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            if (clock != null) clock.setText(new SimpleDateFormat("EEE d MMM  |  HH:mm", Locale.UK).format(new Date()));
            handler.postDelayed(this, 15000L);
        }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildHome();
        gps = new GpsTracker(this, this::onGps);
    }
    private void buildHome() {
        LinearLayout root = DriveUi.column(this);
        root.setBackgroundColor(DriveUi.BG);
        DriveUi.pad(this, root, 18, 11);
        setContentView(root);

        LinearLayout header = DriveUi.row(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(DriveUi.text(this, "◆  EATON  DRIVE", 19, DriveUi.TEXT, true),
            DriveUi.weighted(0, -2, 1f));
        clock = DriveUi.text(this, "", 15, DriveUi.MUTED, false);
        header.addView(clock);
        root.addView(header, new LinearLayout.LayoutParams(-1, DriveUi.dp(this, 47)));

        LinearLayout body = DriveUi.row(this);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bodyLp.topMargin = DriveUi.dp(this, 8);
        root.addView(body, bodyLp);

        LinearLayout speedPanel = DriveUi.column(this);
        speedPanel.setBackground(DriveUi.bg(this, DriveUi.PANEL, 20, true));
        speedPanel.setGravity(Gravity.CENTER);
        DriveUi.pad(this, speedPanel, 12, 10);
        TextView label = DriveUi.text(this, "LIVE GPS SPEED", 13, DriveUi.TEAL, true);
        label.setGravity(Gravity.CENTER);
        speedPanel.addView(label);
        speedValue = DriveUi.text(this, "—", 92, DriveUi.TEXT, true);
        speedValue.setGravity(Gravity.CENTER);
        speedValue.setIncludeFontPadding(false);
        speedPanel.addView(speedValue, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView unit = DriveUi.text(this, "MPH", 20, DriveUi.MUTED, true);
        unit.setGravity(Gravity.CENTER);
        speedPanel.addView(unit);
        speedStatus = DriveUi.text(this, "Searching for GPS satellites", 12, DriveUi.MUTED, false);
        speedStatus.setGravity(Gravity.CENTER);
        speedPanel.addView(speedStatus);
        LinearLayout.LayoutParams left = DriveUi.weighted(0, -1, .9f);
        left.rightMargin = DriveUi.dp(this, 14);
        body.addView(speedPanel, left);

        LinearLayout actions = DriveUi.column(this);
        body.addView(actions, DriveUi.weighted(0, -1, 1.2f));
        LinearLayout top = DriveUi.row(this);
        LinearLayout bottom = DriveUi.row(this);
        actions.addView(top, DriveUi.weighted(-1, 0, 1f));
        LinearLayout.LayoutParams bottomLp = DriveUi.weighted(-1, 0, 1f);
        bottomLp.topMargin = DriveUi.dp(this, 12);
        actions.addView(bottom, bottomLp);
        addTile(top, "⌖", "NAVIGATION", "Offline maps",
            () -> startActivity(new Intent(this, MapActivity.class)), null);
        addTile(top, "◉", "RADIO", "FM stations", () -> launchAssigned("radio"), () -> chooseApp("radio"));
        addTile(bottom, "♫", "MUSIC", "Your music", () -> launchAssigned("music"), () -> chooseApp("music"));
        addTile(bottom, "☎", "PHONE", "Bluetooth calls", () -> launchAssigned("phone"), () -> chooseApp("phone"));

        LinearLayout footer = DriveUi.row(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(DriveUi.text(this, "OFFLINE READY  •  DESIGNED FOR THE JOURNEY",
            11, DriveUi.MUTED, true), DriveUi.weighted(0, -1, 1f));
        TextView system = DriveUi.text(this, "ANDROID SETTINGS  ⚙", 13, DriveUi.TEAL, true);
        system.setPadding(DriveUi.dp(this, 14), 0, 0, 0);
        system.setOnClickListener(v -> openSystemMenu());
        footer.addView(system);
        root.addView(footer, new LinearLayout.LayoutParams(-1, DriveUi.dp(this, 48)));
        clockTicker.run();
    }
    private void addTile(LinearLayout parent, String icon, String name, String subtitle,
                         Runnable action, Runnable longPress) {
        LinearLayout tile = DriveUi.column(this);
        tile.setBackground(DriveUi.bg(this, DriveUi.PANEL2, 18, true));
        DriveUi.pad(this, tile, 20, 10);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.addView(DriveUi.text(this, icon, 32, DriveUi.TEAL, true));
        tile.addView(DriveUi.text(this, name, 19, DriveUi.TEXT, true));
        tile.addView(DriveUi.text(this, subtitle, 12, DriveUi.MUTED, false));
        tile.setContentDescription(name + " - " + subtitle);
        tile.setClickable(true);
        tile.setOnClickListener(v -> action.run());
        if (longPress != null) tile.setOnLongClickListener(v -> { longPress.run(); return true; });
        LinearLayout.LayoutParams lp = DriveUi.weighted(0, -1, 1f);
        if (parent.getChildCount() == 0) lp.rightMargin = DriveUi.dp(this, 12);
        parent.addView(tile, lp);
    }
    private void onGps(Integer mph, String state, Location location) {
        speedValue.setText(mph == null ? "—" : String.valueOf(mph));
        speedStatus.setText(state);
        speedStatus.setTextColor(mph == null ? DriveUi.MUTED : DriveUi.TEAL);
    }
    private void launchAssigned(String key) {
        String saved = getPreferences(0).getString("app_" + key, null);
        if (saved == null) { chooseApp(key); return; }
        ComponentName component = ComponentName.unflattenFromString(saved);
        if (component == null) { chooseApp(key); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (RuntimeException ex) {
            Toast.makeText(this, "App unavailable; please select another", Toast.LENGTH_LONG).show();
            chooseApp(key);
        }
    }
    private void chooseApp(String key) {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> candidates = pm.queryIntentActivities(query, 0);
        List<ResolveInfo> options = new ArrayList<>();
        for (ResolveInfo info : candidates)
            if (!getPackageName().equals(info.activityInfo.packageName)) options.add(info);
        Collections.sort(options, (a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        if (options.isEmpty()) {
            Toast.makeText(this, "No compatible apps found", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[options.size()];
        for (int i = 0; i < names.length; i++) names[i] = options.get(i).loadLabel(pm).toString();
        new AlertDialog.Builder(this).setTitle("Choose " + key + " app").setItems(names, (d, index) -> {
            ResolveInfo selected = options.get(index);
            String flattened = new ComponentName(selected.activityInfo.packageName, selected.activityInfo.name).flattenToString();
            getPreferences(0).edit().putString("app_" + key, flattened).apply();
            launchAssigned(key);
        }).setNegativeButton("Cancel", null).show();
    }
    private void openSystemMenu() {
        new AlertDialog.Builder(this).setTitle("Eaton Drive settings")
            .setItems(new String[] { "Android settings", "Change default home app",
                "Change radio app", "Change music app", "Change phone app" }, (d, item) -> {
                if (item == 0) startActivity(new Intent(Settings.ACTION_SETTINGS));
                if (item == 1) {
                    try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                    catch (Exception ex) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                }
                if (item == 2) chooseApp("radio");
                if (item == 3) chooseApp("music");
                if (item == 4) chooseApp("phone");
            }).setNegativeButton("Cancel", null).show();
    }
    @Override protected void onResume() {
        super.onResume();
        if (gps != null) {
            if (!gps.hasPermission()) requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, GPS_PERMISSION_REQUEST);
            else gps.start();
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == GPS_PERMISSION_REQUEST && gps != null) gps.start();
    }
    @Override protected void onPause() {
        if (gps != null) gps.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(clockTicker);
        super.onDestroy();
    }
}

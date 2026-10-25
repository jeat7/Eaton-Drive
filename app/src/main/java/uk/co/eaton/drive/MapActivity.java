package uk.co.eaton.drive;

import android.Manifest;
import android.os.SystemClock;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Build;
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
import uk.co.eaton.drive.routing.EmbeddedRouter;
import uk.co.eaton.drive.routing.RouteOptions;
import uk.co.eaton.drive.ui.CarMarkerView;
import uk.co.eaton.drive.ui.DestinationPinView;
import uk.co.eaton.drive.routing.OfflineRoadImporter;
import java.util.List;
import uk.co.eaton.drive.routing.RouteParser;
import uk.co.eaton.drive.routing.NavigationSession;
import uk.co.eaton.drive.routing.NavigationService;
import uk.co.eaton.drive.routing.RouteStorage;
import uk.co.eaton.drive.routing.TurnDisplay;
import uk.co.eaton.drive.postcode.PostcodeCsv;
import uk.co.eaton.drive.routing.VoiceGuide;
import uk.co.eaton.drive.postcode.PostcodeStore;
import uk.co.eaton.drive.address.AddressStore;
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
    private static final int IMPORT_ROADS = 45;
    private static final int IMPORT_ADDRESSES = 46;
    private static final int LOCATION_REQUEST = 902;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private MapView mapView;
    private TextView status, placeholder, speed, routeInfo, pinButton, nextTurn, destinationSearch;
    private CarMarkerView carMarker;
    private DestinationPinView centrePin;
    private GpsTracker gps;
    private Location lastFix;
    private MultiMapDataStore mapFileReader;
    private volatile boolean copying;
    private EmbeddedRouter routing;
    private PostcodeStore postcodes;
    private AddressStore addressStore;
    private NavigationService navigationService;
    private boolean navBound, navigationActive, screenVisible;
    private final NavigationService.Observer serviceObserver = this::onNavigationState;
    private final ServiceConnection navConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            navigationService=((NavigationService.LocalBinder)binder).instance();
            navigationService.observe(serviceObserver);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            navigationService=null;
            navigationActive=false;
        }
    };
    private NavigationSession navigation;
    private RouteParser.Route plannedRoute, primaryRoute, alternativeRoute;
    private boolean selectedAlternative;
    private boolean choosingPin;
    private boolean routeCalculating;
    private boolean stopped;
    private boolean autoCentredOnGps;
    private boolean userMovedMap;
    private boolean preparingPostcodes;
    private int carSizeDp = 72;
    private RouteParser.Point destination;
    private Polyline routeOverlay;
    private FixedPixelCircle destinationOverlay;
    private boolean mapReady;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        AndroidGraphicFactory.createInstance(getApplication());
        carSizeDp = Math.max(36, Math.min(120, getPreferences(0).getInt("car_marker_dp",72)));
        buildScreen();
        gps = new GpsTracker(this, this::onGps);
        routing = new EmbeddedRouter(this);
        postcodes = new PostcodeStore(this);
        addressStore = new AddressStore(this);
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
        addButton(row1, "MUTE / VOICE", this::toggleVoice);
        addButton(row1, "OPTIONS", this::showRouteOptions);
        addButton(row1, "SETUP", this::openSetup);
        destinationSearch = DriveUi.text(this, "⌕  WHERE TO?  •  POSTCODE, STREET OR ADDRESS", 17, DriveUi.TEXT, true);
        destinationSearch.setGravity(Gravity.CENTER_VERTICAL);
        destinationSearch.setBackground(DriveUi.bg(this,DriveUi.PANEL2,12,true));
        destinationSearch.setPadding(DriveUi.dp(this,15),0,DriveUi.dp(this,10),0);
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(0,-1,1f);
        searchLp.setMargins(DriveUi.dp(this,4),DriveUi.dp(this,3),DriveUi.dp(this,4),DriveUi.dp(this,3));
        row2.addView(destinationSearch,searchLp);
        destinationSearch.setOnClickListener(v->openDestinationSearch());
        pinButton = addButton(row2, "PIN", this::selectMapCentre);
        addButton(row2, "PLAN", () -> calculateRoute(false));
        addButton(row2, "ROUTES", this::chooseAlternative);
        addButton(row2, "GO", this::startGuidance);
        addButton(row2, "STOP", this::clearNavigation);
        outer.addView(toolbar, new FrameLayout.LayoutParams(-1, DriveUi.dp(this, 90), Gravity.TOP));
        // The pin's tip is exactly at the map centre. Only shown while selecting manually.
        centrePin = new DestinationPinView(this);
        FrameLayout.LayoutParams pinParams = new FrameLayout.LayoutParams(
            DriveUi.dp(this, 58), DriveUi.dp(this, 100), Gravity.CENTER);
        outer.addView(centrePin,pinParams);
        centrePin.setVisibility(View.GONE);
        // Route summary remains readable above roads; the car follows GPS during GO.
        routeInfo = DriveUi.text(this,"",15,DriveUi.TEXT,true);
        routeInfo.setGravity(Gravity.CENTER);
        routeInfo.setBackground(DriveUi.bg(this,DriveUi.PANEL,13,true));
        DriveUi.pad(this,routeInfo,12,9);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
            -2,DriveUi.dp(this,53),Gravity.TOP|Gravity.RIGHT);
        infoParams.topMargin = DriveUi.dp(this,95);
        infoParams.rightMargin = DriveUi.dp(this,13);
        outer.addView(routeInfo,infoParams);
        routeInfo.setVisibility(View.GONE);
        nextTurn = DriveUi.text(this, "Continue along route", 23, DriveUi.TEXT, true);
        nextTurn.setGravity(Gravity.CENTER_VERTICAL);
        nextTurn.setBackground(DriveUi.bg(this,DriveUi.PANEL,12,true));
        DriveUi.pad(this,nextTurn,14,10);
        int bannerWidth=Math.min(DriveUi.dp(this,550),
            (int)(getResources().getDisplayMetrics().widthPixels*0.52f));
        FrameLayout.LayoutParams turnParams=new FrameLayout.LayoutParams(
            bannerWidth,DriveUi.dp(this,78),Gravity.TOP|Gravity.LEFT);
        turnParams.topMargin=DriveUi.dp(this,94);
        turnParams.leftMargin=DriveUi.dp(this,12);
        outer.addView(nextTurn,turnParams);
        nextTurn.setVisibility(View.GONE);
        carMarker = new CarMarkerView(this);
        FrameLayout.LayoutParams carParams = new FrameLayout.LayoutParams(
            DriveUi.dp(this,carSizeDp),DriveUi.dp(this,carSizeDp),Gravity.CENTER);
        outer.addView(carMarker,carParams);

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
    private TextView addButton(LinearLayout row, String label, Runnable action) {
        TextView button = DriveUi.text(this, label, 12, DriveUi.TEAL, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(DriveUi.bg(this, DriveUi.PANEL2, 10, true));
        button.setPadding(DriveUi.dp(this, 7), 0, DriveUi.dp(this, 7), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -1);
        lp.setMargins(DriveUi.dp(this, 3), DriveUi.dp(this, 3), DriveUi.dp(this, 3), DriveUi.dp(this, 3));
        row.addView(button, lp);
        button.setOnClickListener(v -> action.run());
        return button;
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
        if (resultCode != RESULT_OK || data == null || copying) return;
        if (requestCode == IMPORT_ROADS) {
            importRoadDocuments(data);
            return;
        }
        if (requestCode == IMPORT_ADDRESSES) {
            if (data.getData()!=null) importAddressDatabase(data.getData());
            return;
        }
        if (requestCode != IMPORT_MAP || data.getData() == null) return;
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
            + "\n\nStreet and house-number index: "
            + (addressStore.installed()
                ? String.format(Locale.UK, "%,d searchable records installed • %s",
                    addressStore.recordCount(),
                    addressStore.hasDetailedAreaLabels() ? "enhanced neighbourhood/district labels ready."
                        : "older index — import the new West Midlands v2 .db for area labels.")
                : "NOT INSTALLED — choose SETUP > Import street/address search index.")
            + "\nPostcode data: Postcodes UK, September 2026. GB only. Offline map © OpenStreetMap contributors.";
        new AlertDialog.Builder(this).setTitle("Offline data")
            .setMessage(message).setPositiveButton("OK", null).show();
    }
    private void configureRoads() {
        int count = routing.segmentCount();
        String intro = count > 0
            ? count + " offline driving tile(s) installed. Your route engine is built into Eaton Drive."
            : "The offline routing engine is built into Eaton Drive. It needs local road tiles once.";
        new AlertDialog.Builder(this)
            .setTitle("Offline driving roads")
            .setMessage(intro + "\n\nSelect the BRouter UK routing ZIP you downloaded, or select multiple .rd5 files. Eaton Drive will store them automatically; no second app or account is needed."
                + "\n\nLeave at least 200MB free. You do not need to reimport the roads on every launch.")
            .setPositiveButton("Choose routing files", (d, w) -> pickRoads())
            .setNegativeButton("Close", null).show();
    }
    private void pickRoads() {
        if (copying) return;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(picker, IMPORT_ROADS);
    }
    private void importRoadDocuments(Intent data) {
        List<Uri> documents = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++)
                documents.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            documents.add(data.getData());
        }
        if (documents.isEmpty()) {
            status.setText("No routing files chosen");
            return;
        }
        copying = true;
        status.setText("Installing local driving roads...");
        io.execute(() -> {
            String result;
            try {
                int imported = new OfflineRoadImporter(this, routing.roadDirectory())
                    .importDocuments(documents, message -> main.post(() -> status.setText(message)));
                result = "Installed " + imported + " offline routing tiles. Driving-route calculation is ready for testing.";
            } catch (Exception ex) {
                String reason = ex.getMessage();
                result = "Road installation failed: " +
                    (reason == null ? ex.getClass().getSimpleName() : reason);
            }
            String outcome = result;
            main.post(() -> {
                copying = false;
                status.setText(outcome);
                new AlertDialog.Builder(this).setTitle("Road data").setMessage(outcome).setPositiveButton("OK",null).show();
            });
        });
    }
    private void openSetup() {
        String[] choices = {"Import map regions","Import driving roads",
            "Import street/address search index","Offline data status"};
        new AlertDialog.Builder(this).setTitle("Eaton Drive setup")
            .setItems(choices,(d,index) -> {
                if (index == 0) importMap();
                if (index == 1) configureRoads();
                if (index == 2) pickAddressDatabase();
                if (index == 3) showDataStatus();
            }).setNegativeButton("Close",null).show();
    }
    private RouteOptions preferredOptions(int alternate) {
        android.content.SharedPreferences prefs = getPreferences(0);
        return new RouteOptions(prefs.getBoolean("avoid_motorways",false),
            prefs.getBoolean("avoid_tolls",false),alternate);
    }
    private void showRouteOptions() {
        if(navigationActive){
            status.setText("Stop guidance before changing route options");return;
        }
        android.content.SharedPreferences prefs = getPreferences(0);
        final boolean[] toggles = {prefs.getBoolean("avoid_motorways",false),
            prefs.getBoolean("avoid_tolls",false)};
        new AlertDialog.Builder(this).setTitle("Route preferences")
            .setMultiChoiceItems(new String[] {"Avoid motorways","Avoid toll roads"},toggles,
                (d,idx,isChecked) -> toggles[idx]=isChecked)
            .setPositiveButton("Save", (d,w) -> {
                boolean changed = toggles[0] != prefs.getBoolean("avoid_motorways",false)
                    || toggles[1] != prefs.getBoolean("avoid_tolls",false);
                prefs.edit().putBoolean("avoid_motorways",toggles[0])
                    .putBoolean("avoid_tolls",toggles[1]).apply();
                if (changed && destination != null) {
                    plannedRoute = null; primaryRoute = null; alternativeRoute = null;
                    navigation = null; selectedAlternative = false;
                    removeRouteOverlay();
                    routeInfo.setVisibility(View.GONE);
                    status.setText("Preferences saved • Tap PLAN to recalculate");
                }
            }).setNeutralButton("CAR SIZE",(dialog,which)->chooseCarSize())
              .setNegativeButton("Cancel",null).show();
    }
    private void chooseCarSize() {
        // Physical dp dimensions, persisted separately from route restrictions.
        final int[] sizes = {40,56,72,88,104,120};
        String[] labels = {"Extra small — 40dp","Small — 56dp","Normal — 72dp",
            "Large — 88dp","Extra large — 104dp","Maximum — 120dp"};
        int checked = 2;
        int closest = Integer.MAX_VALUE;
        for (int i=0; i<sizes.length; i++) {
            int diff = Math.abs(sizes[i]-carSizeDp);
            if (diff<closest) { closest=diff; checked=i; }
        }
        new AlertDialog.Builder(this).setTitle("Car marker size")
            .setSingleChoiceItems(labels, checked, (dialog, index) -> {
                carSizeDp=sizes[index];
                getPreferences(0).edit().putInt("car_marker_dp",carSizeDp).apply();
                FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)carMarker.getLayoutParams();
                params.width=DriveUi.dp(this,carSizeDp);
                params.height=DriveUi.dp(this,carSizeDp);
                carMarker.setLayoutParams(params);
                carMarker.invalidate();
                status.setText("Car marker size set to "+labels[index].split(" — ")[0]);
                dialog.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    private void pickAddressDatabase() {
        if (copying) return;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        startActivityForResult(picker,IMPORT_ADDRESSES);
    }
    private void importAddressDatabase(Uri chosen) {
        if (copying) return;
        copying=true;
        status.setText("Installing offline address search index...");
        io.execute(() -> {
            String result;
            try { result=addressStore.importIndex(chosen); }
            catch (Exception e) {
                result="Address installation failed: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
            }
            final String message=result;
            main.post(() -> {
                copying=false;
                status.setText(message);
                new AlertDialog.Builder(this).setTitle("Offline address search")
                    .setMessage(message).setPositiveButton("OK",null).show();
            });
        });
    }
    private void searchAddress() {
        if (!addressStore.installed()) {
            new AlertDialog.Builder(this).setTitle("Offline street & house search")
                .setMessage("Download and extract a regional Eaton Drive address-index package. "
                    + "Then import its .db file once using SETUP > Import street/address search index. "
                    + "We currently have a West Midlands test pack. "
                    + "Only streets and house numbers mapped in OpenStreetMap are searchable.")
                .setPositiveButton("Import index",(d,w)->pickAddressDatabase())
                .setNegativeButton("Close",null).show();
            return;
        }
        EditText text = new EditText(this);
        text.setHint("e.g. 14 High Street, or Stoke");
        text.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Find offline address")
            .setView(text)
            .setPositiveButton("Search",(d,w) -> {
                String query=text.getText().toString();
                status.setText("Searching offline streets and addresses...");
                io.execute(() -> {
                    RouteParser.Point near = lastFix == null ? null
                        : new RouteParser.Point(lastFix.getLatitude(),lastFix.getLongitude());
                    List<AddressStore.Result> found=addressStore.search(query,near);
                    main.post(() -> {
                        if (isFinishing()||isDestroyed()) return;
                        if (found.isEmpty()) {
                            String problem=addressStore.lastSearchError();
                            String details=(problem!=null && !problem.isEmpty())
                                ? "Search database error: " + problem
                                    + "\n\nTry reinstalling the offline address index in SETUP."
                                : "No mapped results for this query. Try the street name only, "
                                    + "or verify the index under SETUP > Offline data status. "
                                    + "The West Midlands pack should contain Crayford Road.";
                            new AlertDialog.Builder(this).setTitle("Offline address search")
                                .setMessage(details)
                                .setPositiveButton("SETUP",(setupDialog,buttonIndex)->openSetup())
                                .setNegativeButton("Close",null).show();
                            status.setText(problem!=null && !problem.isEmpty()
                                ? "Address search error: " + problem : "No address matches"); return;
                        }
                        String[] labels=new String[found.size()];
                        for(int i=0;i<labels.length;i++)labels[i]=found.get(i).label;
                        new AlertDialog.Builder(this).setTitle("Select destination")
                            .setItems(labels,(a,index) -> {
                                AddressStore.Result selected=found.get(index);
                                setDestination(selected.point);
                                mapView.setCenter(new LatLong(selected.point.lat,selected.point.lon));
                                mapView.setZoomLevel((byte)16);
                                status.setText("Selected "+selected.label+" • tap PLAN");
                            }).setNegativeButton("Cancel",null).show();
                    });
                });
            }).setNegativeButton("Cancel",null).show();
    }
    /** Single large offline search action: exact GB postcodes or regional OSM addresses. */
    private void openDestinationSearch() {
        if(navigationActive){
            status.setText("Stop current guidance before choosing another destination");
            return;
        }
        EditText entry=new EditText(this);
        entry.setHint("Postcode, road, town or house number");
        entry.setSingleLine(true);
        entry.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        new AlertDialog.Builder(this).setTitle("Where to?")
            .setView(entry)
            .setPositiveButton("FIND",(dialog,which)->findDestination(entry.getText().toString().trim()))
            .setNeutralButton("MAP PIN",(dialog,which)->selectMapCentre())
            .setNegativeButton("Cancel",null).show();
    }
    private void findDestination(String query){
        if(query.isEmpty()){status.setText("Enter a destination");return;}
        if(PostcodeCsv.plausible(PostcodeCsv.normalise(query))){
            if(!postcodes.installed()){
                status.setText("Built-in postcode search is not ready yet");return;
            }
            io.execute(()->{
                RouteParser.Point found=postcodes.lookup(query);
                main.post(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(found==null){status.setText("Postcode not found in offline GB database");return;}
                    setDestination(found);
                    if(mapReady){
                        mapView.setCenter(new LatLong(found.lat,found.lon));
                        mapView.setZoomLevel((byte)15);
                    }
                    destinationSearch.setText("⌕  "+query.toUpperCase(Locale.UK));
                    status.setText("Postcode selected — tap PLAN");
                });
            });
            return;
        }
        if(!addressStore.installed()){
            new AlertDialog.Builder(this).setTitle("Street search needs offline data")
                .setMessage("Install a regional address index once via SETUP. Postcode search is already built in.")
                .setPositiveButton("SETUP",(d,w)->openSetup())
                .setNegativeButton("Close",null).show();
            return;
        }
        status.setText("Searching local streets and addresses...");
        RouteParser.Point near=lastFix==null?null:
            new RouteParser.Point(lastFix.getLatitude(),lastFix.getLongitude());
        io.execute(()->{
            List<AddressStore.Result> results=addressStore.search(query,near);
            main.post(()->{
                if(isFinishing()||isDestroyed())return;
                if(results.isEmpty()){
                    status.setText("No local street/address matches. Try a postcode or another search");
                    return;
                }
                String[] labels=new String[results.size()];
                for(int i=0;i<labels.length;i++)labels[i]=results.get(i).label;
                new AlertDialog.Builder(this).setTitle("Choose destination")
                    .setItems(labels,(dialog,index)->{
                        AddressStore.Result result=results.get(index);
                        setDestination(result.point);
                        if(mapReady){
                            mapView.setCenter(new LatLong(result.point.lat,result.point.lon));
                            mapView.setZoomLevel((byte)16);
                        }
                        destinationSearch.setText("⌕  "+result.label);
                        status.setText("Destination selected — tap PLAN");
                    }).setNegativeButton("Cancel",null).show();
            });
        });
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
        input.setHint("e.g. B1 1BB");
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
        android.content.SharedPreferences preferences=getSharedPreferences("navigation",MODE_PRIVATE);
        boolean enabled=!preferences.getBoolean("voice_enabled",true);
        preferences.edit().putBoolean("voice_enabled",enabled).apply();
        if(navigationService!=null)navigationService.setSpeechEnabled(enabled);
        status.setText(enabled?"Voice enabled (offline speech voice required)":"Spoken guidance muted");
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
            .putInt("map_zoom",mapView.getModel().mapViewPosition.getZoomLevel())
            .apply();
    }
    private void centreOnGps() {
        userMovedMap = false;
        if (!mapReady) { Toast.makeText(this, "Import a map first", Toast.LENGTH_SHORT).show(); return; }
        if (lastFix == null) { Toast.makeText(this, "Waiting for GPS", Toast.LENGTH_SHORT).show(); return; }
        mapView.setCenter(new LatLong(lastFix.getLatitude(), lastFix.getLongitude()));
        mapView.setZoomLevel((byte) 16);
    }
    private void selectMapCentre() {
        if (!mapReady) { Toast.makeText(this,"Import a map first",Toast.LENGTH_SHORT).show(); return; }
        if (!choosingPin) {
            choosingPin = true;
            userMovedMap = true;
            centrePin.setVisibility(View.VISIBLE);
            pinButton.setText("SET PIN");
            status.setText("Move the map until the pin tip touches your destination, then tap SET PIN");
            return;
        }
        LatLong centre = mapView.getModel().mapViewPosition.getCenter();
        setDestination(new RouteParser.Point(centre.latitude, centre.longitude));
        choosingPin = false;
        centrePin.setVisibility(View.GONE);
        pinButton.setText("PIN");
        status.setText("Destination selected • Tap PLAN");
    }
    private void setDestination(RouteParser.Point point) {
        if(navigationActive){
            status.setText("Stop navigation before selecting a new destination");
            return;
        }
        destination = point;
        stopped = false;
        plannedRoute = null; primaryRoute = null; alternativeRoute = null;
        selectedAlternative = false; navigation = null;
        removeRouteOverlay();
        routeInfo.setVisibility(View.GONE);
        if (destinationOverlay != null) mapView.getLayerManager().getLayers().remove(destinationOverlay);
        if (mapReady) {
            Paint fill = AndroidGraphicFactory.INSTANCE.createPaint();
            fill.setColor(DriveUi.TEAL); fill.setStyle(Style.FILL);
            destinationOverlay = new FixedPixelCircle(new LatLong(point.lat,point.lon),
                DriveUi.dp(this,11),fill,null);
            mapView.getLayerManager().getLayers().add(destinationOverlay);
        }
    }
    private void calculateRoute(boolean automatic) {
        if (!mapReady) { Toast.makeText(this,"Import a map first",Toast.LENGTH_LONG).show(); return; }
        if (routing.segmentCount() == 0) { configureRoads(); return; }
        if (destination == null) { status.setText("Enter a postcode or choose MAP PIN"); return; }
        if (lastFix == null || !lastFix.hasAccuracy() || lastFix.getAccuracy() > 45f) {
            status.setText("Waiting for accurate GPS: move outdoors and enable precise location"); return;
        }
        if (routeCalculating) return;
        routeCalculating = true;
        stopped = false;
        RouteParser.Point requestedDestination = destination;
        RouteParser.Point start = new RouteParser.Point(lastFix.getLatitude(),lastFix.getLongitude());
        int index = automatic && selectedAlternative ? 1 : 0;
        status.setText(automatic ? "Recalculating offline route..." : "Finding the recommended driving route...");
        routing.request(start,destination,preferredOptions(index),(route,error) -> {
            routeCalculating = false;
            if (stopped || requestedDestination != destination) return;
            if (error != null) {
                status.setText("Route unavailable: " + error);
                if (navigation != null) navigation.failedReroute();
                if (!automatic) new AlertDialog.Builder(this).setTitle("Offline route unavailable")
                    .setMessage(error + "\n\nCheck local routing tiles in SETUP.")
                    .setPositiveButton("Setup",(d,w) -> configureRoads())
                    .setNegativeButton("Close",null).show();
                return;
            }
            if (!automatic) {
                primaryRoute = route;
                alternativeRoute = null;
                selectedAlternative = false;
                navigation = null; // A route preview is not active guidance.
            } else {
                navigation = new NavigationSession(route);
            }
            plannedRoute = route;
            displayRoute(route);
            if (!automatic) status.setText("Route ready • Tap GO to begin, or ROUTES to compare");
        });
    }
    private void displayRoute(RouteParser.Route route) {
        removeRouteOverlay();
        Paint stroke = AndroidGraphicFactory.INSTANCE.createPaint();
        stroke.setColor(DriveUi.TEAL); stroke.setStyle(Style.STROKE);
        stroke.setStrokeWidth(DriveUi.dp(this,6));
        routeOverlay = new Polyline(stroke,AndroidGraphicFactory.INSTANCE);
        ArrayList<LatLong> coordinates = new ArrayList<>(route.points.size());
        for (RouteParser.Point point : route.points)
            coordinates.add(new LatLong(point.lat,point.lon));
        routeOverlay.setPoints(coordinates);
        mapView.getLayerManager().getLayers().add(routeOverlay);
        routeInfo.setText(routeSummary(route) + (navigationActive ? " • LIVE" : " • PREVIEW"));
        routeInfo.setVisibility(View.VISIBLE);
    }
    private String routeSummary(RouteParser.Route route) {
        int seconds = route.estimatedSeconds > 0
            ? route.estimatedSeconds
            : (int)Math.ceil(route.miles/35.0*3600.0);
        int minutes = Math.max(1,(int)Math.ceil(seconds/60.0));
        return String.format(Locale.UK,"%.1f miles • about %d min",route.miles,minutes);
    }
    private void chooseAlternative() {
        if (primaryRoute == null || destination == null) {
            status.setText("Calculate your main route first using PLAN"); return;
        }
        if (navigationActive) {
            status.setText("Stop active guidance before selecting another route"); return;
        }
        if (alternativeRoute != null) { showRouteComparison(); return; }
        if (routeCalculating) return;
        if (lastFix == null || !lastFix.hasAccuracy() || lastFix.getAccuracy() > 45) {
            status.setText("Alternative route needs an accurate GPS position"); return;
        }
        routeCalculating = true;
        RouteParser.Point requested = destination;
        status.setText("Looking for a second offline driving route...");
        routing.request(new RouteParser.Point(lastFix.getLatitude(),lastFix.getLongitude()),
            requested,preferredOptions(1),(route,error) -> {
                routeCalculating = false;
                if (stopped || destination != requested) return;
                if (error != null) {
                    status.setText("An alternative is unavailable: " + error);
                    return;
                }
                alternativeRoute = route;
                showRouteComparison();
            });
    }
    private void showRouteComparison() {
        if (primaryRoute == null || alternativeRoute == null) return;
        RouteParser.Route[] routes = {primaryRoute,alternativeRoute};
        int shortest = primaryRoute.miles <= alternativeRoute.miles ? 0 : 1;
        int quickest = primaryRoute.estimatedSeconds > 0 && alternativeRoute.estimatedSeconds > 0
            ? (primaryRoute.estimatedSeconds <= alternativeRoute.estimatedSeconds ? 0 : 1) : -1;
        String[] labels = {
            "Recommended • " + routeSummary(primaryRoute)
                + (shortest==0 ? " • shorter" : "") + (quickest==0 ? " • quicker" : ""),
            "Alternative • " + routeSummary(alternativeRoute)
                + (shortest==1 ? " • shorter" : "") + (quickest==1 ? " • quicker" : "")
        };
        new AlertDialog.Builder(this).setTitle("Choose your route")
            .setSingleChoiceItems(labels,selectedAlternative ? 1 : 0,(d,which) -> {
                selectedAlternative = which == 1;
                plannedRoute = routes[which];
                displayRoute(plannedRoute);
                status.setText("Selected " + (selectedAlternative?"alternative":"recommended")
                    + " • Tap GO to start");
                d.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    private void startGuidance() {
        if(plannedRoute==null){status.setText("Tap PLAN to calculate a route first");return;}
        if(navigationActive){status.setText("Navigation is already active");return;}
        if(!gps.hasPermission()){
            status.setText("Precise location permission required to start guidance");return;
        }
        if(lastFix==null||!lastFix.hasAccuracy()||lastFix.getAccuracy()>45f){
            status.setText("Wait for an accurate GPS position before starting");return;
        }
        try {
            RouteStorage.save(this,plannedRoute,
                preferredOptions(selectedAlternative?1:0));
            // Android requires a user-visible launch for location FGS.
            Intent go=NavigationService.startIntent(this);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(go);
            else startService(go);
            if(!navBound)connectNavigationService();
            userMovedMap=false;
            choosingPin=false;
            centrePin.setVisibility(View.GONE);
            pinButton.setText("PIN");
            status.setText("Starting offline guidance • notification offers STOP");
        }catch(Exception problem){
            status.setText("Could not start navigation: "+problem.getMessage());
        }
    }
    private void connectNavigationService(){
        if(navBound)return;
        navBound=bindService(new Intent(this,NavigationService.class),navConnection,Context.BIND_AUTO_CREATE);
    }
    private void onNavigationState(NavigationService.Snapshot state){
        boolean wasActive=navigationActive;
        navigationActive=state.active;
        if(state.active){
            if(state.route!=null&&state.route!=plannedRoute){
                plannedRoute=state.route;
                displayRoute(state.route);
            }
            nextTurn.setVisibility(View.VISIBLE);
            nextTurn.setText(state.lostGps?"GPS SIGNAL LOST — instructions paused":state.instruction);
            status.setText(state.message);
            if(state.route!=null)
                routeInfo.setText(String.format(Locale.UK,"%.1f mi left • ~%d min • LIVE",
                    state.remainingMiles,state.remainingMinutes));
            routeInfo.setVisibility(View.VISIBLE);
            if(state.location!=null){
                lastFix=state.location;
                int mph=state.location.hasSpeed()?
                    uk.co.eaton.drive.util.SpeedMath.toMph(state.location.getSpeed()):-1;
                speed.setText(mph>=0?mph+" MPH":"— MPH");
                followGps(state.location);
            }else{
                carMarker.update(0,false);
                speed.setText("— MPH");
            }
        }else if(wasActive){
            nextTurn.setVisibility(View.GONE);
            carMarker.update(0,false);
            routeInfo.setVisibility(View.GONE);
            plannedRoute=null;primaryRoute=null;alternativeRoute=null;
            removeRouteOverlay();
            status.setText(state.message);
            navigation=null;
            if(screenVisible&&gps!=null)gps.start();
        }
    }
    private void followGps(Location location) {
        if (location == null || !mapReady || !location.hasAccuracy()
            || location.getAccuracy() > 45f) {
            carMarker.update(0,false);
            return;
        }
        if (userMovedMap) {
            // An on-screen centred marker would point to the wrong place after pan.
            // Hide it until GPS recentres the map; never misrepresent vehicle location.
            carMarker.update(0,false);
            return;
        }
        mapView.setCenter(new LatLong(location.getLatitude(),location.getLongitude()));
        carMarker.update(location.hasBearing()?location.getBearing():0,true);
    }
    private void removeRouteOverlay() {
        if (routeOverlay != null) {
            mapView.getLayerManager().getLayers().remove(routeOverlay);
            routeOverlay = null;
        }
    }
    private void clearNavigation() {
        if(navigationActive||navigationService!=null&&navigationService.isActive()){
            NavigationService.stop(this);
        }
        navigationActive=false;
        stopped=true;
        navigation=null;
        plannedRoute=null;primaryRoute=null;alternativeRoute=null;
        routeInfo.setVisibility(View.GONE);
        nextTurn.setVisibility(View.GONE);
        carMarker.update(0,false);
        choosingPin=false;
        centrePin.setVisibility(View.GONE);
        pinButton.setText("PIN");
        destination=null;
        destinationSearch.setText("⌕  WHERE TO?  •  POSTCODE, STREET OR ADDRESS");
        removeRouteOverlay();
        if(destinationOverlay!=null){
            mapView.getLayerManager().getLayers().remove(destinationOverlay);
            destinationOverlay=null;
        }
        status.setText("Route cleared • choose your next destination");
    }
    private void onGps(Integer mph, String state, Location location) {
        if(navigationActive)return; // Service owns the GPS fix and guidance while GO is active.
        speed.setText(mph==null?"— MPH":mph+" MPH");
        lastFix=location;
        if(location!=null&&mapReady&&!autoCentredOnGps&&!userMovedMap
            &&location.hasAccuracy()&&location.getAccuracy()<=60f
            &&destination==null){
            mapView.setCenter(new LatLong(location.getLatitude(),location.getLongitude()));
            mapView.setZoomLevel((byte)15);
            autoCentredOnGps=true;
        }
    }
    @Override protected void onResume() {
        super.onResume();
        screenVisible=true;
        connectNavigationService();
        if(gps!=null&&!navigationActive){
            if(!gps.hasPermission())requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST);
            else gps.start();
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] result) {
        super.onRequestPermissionsResult(code, permissions, result);
        if (code == LOCATION_REQUEST && gps != null) gps.start();
    }
    @Override protected void onPause() {
        screenVisible=false;
        saveLastMapPosition();
        if(gps!=null)gps.stop();
        super.onPause();
    }
    @Override protected void onStop(){
        if(navBound){
            if(navigationService!=null)navigationService.removeObserver(serviceObserver);
            unbindService(navConnection);
            navBound=false;
            navigationService=null;
        }
        super.onStop();
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

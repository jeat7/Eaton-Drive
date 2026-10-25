package uk.co.eaton.drive;

import android.Manifest;
import android.app.role.RoleManager;
import android.os.Build;
import android.net.Uri;
import android.content.Context;
import android.app.ActivityManager;
import android.content.res.Resources;
import uk.co.eaton.drive.overlay.FloatingHomeService;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
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
import uk.co.eaton.drive.ui.SignatureIcon;

/**
 * Eaton Drive Signature: lightweight, premium, offline home screen.
 * No animation, blur, external fonts or internet; designed for 1024x600 / 1GB.
 * All OEM radio, camera and Bluetooth internals remain untouched.
 */
public final class MainActivity extends Activity {
    private static final int GPS_PERMISSION_REQUEST=901;
    private static final int REQUEST_HOME_ROLE=902;
    private boolean awaitingOverlayPermission;
    private static final int NAVY=0xff061323;
    private static final int PANEL_TOP=0xff123454;
    private static final int PANEL_BOTTOM=0xff071827;
    private static final int WHITE=0xfff2f9fc;
    private static final int MUTED=0xffa1b9ca;
    private static final int CYAN=0xff16d9d6;
    private static final int BLUE_BORDER=0xff286388;

    private final Handler handler=new Handler(Looper.getMainLooper());
    private GpsTracker gps;
    private boolean compactLayout;
    private TextView speedValue,speedStatus,clock,gpsChip;
    private final Runnable clockTicker=new Runnable(){
        @Override public void run(){
            if(clock!=null)clock.setText(
                new SimpleDateFormat("HH:mm",Locale.UK).format(new Date()));
            handler.postDelayed(this,15000L);
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            |WindowManager.LayoutParams.FLAG_FULLSCREEN);
        float density=getResources().getDisplayMetrics().density;
        float widthDp=getResources().getDisplayMetrics().widthPixels/density;
        float heightDp=getResources().getDisplayMetrics().heightPixels/density;
        compactLayout=widthDp<800f||heightDp<500f;
        buildHome();
        gps=new GpsTracker(this,this::onGps);
    }

    private GradientDrawable background(int top,int bottom,int radius,int edge,int strokeDp){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{top,bottom});
        d.setCornerRadius(DriveUi.dp(this,radius));
        if(strokeDp>0)d.setStroke(DriveUi.dp(this,strokeDp),edge);
        return d;
    }
    private void interactiveBackground(View target,boolean featured){
        GradientDrawable surface=featured
            ?background(0xff07374d,0xff031322,19,0xff1fd8ce,2)
            :background(PANEL_TOP,PANEL_BOTTOM,18,BLUE_BORDER,1);
        target.setBackground(new RippleDrawable(
            ColorStateList.valueOf(0x382ceadd),surface,null));
    }
    private TextView text(String label,int sp,int colour,boolean bold){
        TextView t=DriveUi.text(this,label,sp,colour,bold);
        t.setIncludeFontPadding(false);
        return t;
    }
    private SignatureIcon symbol(int type,int size,int color){
        SignatureIcon view=new SignatureIcon(this,type);
        view.setColour(color);
        view.setLayoutParams(new LinearLayout.LayoutParams(
            DriveUi.dp(this,size),DriveUi.dp(this,size)));
        return view;
    }

    private void buildHome(){
        LinearLayout root=DriveUi.column(this);
        root.setBackground(background(0xff04101e,0xff071a2a,0,0,0));
        DriveUi.pad(this,root,compactLayout?9:20,compactLayout?6:13);
        setContentView(root);

        LinearLayout header=DriveUi.row(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header,new LinearLayout.LayoutParams(-1,DriveUi.dp(this,compactLayout?53:71)));

        LinearLayout brand=DriveUi.column(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout word=DriveUi.row(this);
        word.setGravity(Gravity.CENTER_VERTICAL);
        TextView eaton=text("EATON",compactLayout?21:29,WHITE,true);
        eaton.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        eaton.setLetterSpacing(0.20f);
        word.addView(eaton);
        TextView drive=text("DRIVE",compactLayout?21:29,CYAN,true);
        drive.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        drive.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams driveLp=new LinearLayout.LayoutParams(-2,-2);
        driveLp.leftMargin=DriveUi.dp(this,compactLayout?7:13);
        word.addView(drive,driveLp);
        brand.addView(word);
        View underline=new View(this);
        underline.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff3794c0,0xff20d9d4,0x001dd5d3}));
        LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(
            -1,DriveUi.dp(this,1));
        ulp.topMargin=DriveUi.dp(this,compactLayout?6:9);
        brand.addView(underline,ulp);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1f));

        LinearLayout rightHeader=DriveUi.row(this);
        rightHeader.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(rightHeader);

        LinearLayout appDrawer=DriveUi.row(this);
        appDrawer.setGravity(Gravity.CENTER);
        appDrawer.setBackground(background(0xff10324a,0xff061d31,15,0xff21617b,1));
        appDrawer.addView(symbol(SignatureIcon.APPS,compactLayout?23:31,CYAN));
        appDrawer.setContentDescription("Open installed apps");
        appDrawer.setClickable(true);
        appDrawer.setOnClickListener(v->showApps());
        LinearLayout.LayoutParams drawerLp=new LinearLayout.LayoutParams(
            DriveUi.dp(this,compactLayout?40:52),DriveUi.dp(this,compactLayout?38:48));
        drawerLp.rightMargin=DriveUi.dp(this,compactLayout?10:27);
        rightHeader.addView(appDrawer,drawerLp);

        clock=text("",compactLayout?16:21,WHITE,true);
        clock.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        rightHeader.addView(clock);
        View rule=new View(this);rule.setBackgroundColor(0xff1c6279);
        LinearLayout.LayoutParams lineLp=new LinearLayout.LayoutParams(
            DriveUi.dp(this,1),DriveUi.dp(this,25));
        lineLp.leftMargin=DriveUi.dp(this,compactLayout?10:17);
        lineLp.rightMargin=DriveUi.dp(this,compactLayout?10:17);
        rightHeader.addView(rule,lineLp);
        gpsChip=text("● GPS",compactLayout?11:13,MUTED,true);
        gpsChip.setLetterSpacing(0.08f);
        rightHeader.addView(gpsChip);

        LinearLayout body=DriveUi.row(this);
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-1,0,1f);
        blp.topMargin=DriveUi.dp(this,compactLayout?5:9);
        root.addView(body,blp);

        // Instrument-cluster-like speed panel with transparent blue Celerio.
        LinearLayout speedPanel=DriveUi.column(this);
        speedPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        speedPanel.setBackground(background(0xff143c5c,0xff071b2d,21,0xff2f749f,2));
        DriveUi.pad(this,speedPanel,compactLayout?8:12,compactLayout?7:17);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,-1,0.85f);
        slp.rightMargin=DriveUi.dp(this,compactLayout?8:12);
        body.addView(speedPanel,slp);

        TextView label=text("LIVE GPS SPEED",compactLayout?11:14,0xffc3d7e6,true);
        label.setLetterSpacing(0.19f);
        label.setGravity(Gravity.CENTER);
        speedPanel.addView(label,new LinearLayout.LayoutParams(-1,DriveUi.dp(this,compactLayout?19:28)));

        speedValue=text("—",compactLayout?57:80,WHITE,true);
        speedValue.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        speedValue.setGravity(Gravity.CENTER);
        // Fixed readout height: speed digits and MPH never jump when GPS changes.
        speedValue.setSingleLine(true);
        speedPanel.addView(speedValue,new LinearLayout.LayoutParams(
            -1,DriveUi.dp(this,compactLayout?61:105)));
        TextView mph=text("MPH",compactLayout?17:23,0xffd3e4f0,true);
        mph.setGravity(Gravity.CENTER);
        mph.setLetterSpacing(0.10f);
        speedPanel.addView(mph,new LinearLayout.LayoutParams(-1,DriveUi.dp(this,compactLayout?21:29)));

        ImageView car=new ImageView(this);
        car.setImageResource(R.drawable.celerio_side);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        car.setAlpha(0.94f);
        car.setPadding(DriveUi.dp(this,compactLayout?3:13),
            DriveUi.dp(this,compactLayout?1:8),
            DriveUi.dp(this,compactLayout?3:13),
            DriveUi.dp(this,compactLayout?1:8));
        car.setContentDescription("Light-blue 2017 Suzuki Celerio side profile");
        // All remaining flexible height belongs to the car, rather than making
        // the speed digits compete with it. Image remains aspect-ratio-correct.
        speedPanel.addView(car,new LinearLayout.LayoutParams(-1,0,1f));

        View lowerLine=new View(this);lowerLine.setBackgroundColor(0xff1b4c68);
        speedPanel.addView(lowerLine,new LinearLayout.LayoutParams(-1,DriveUi.dp(this,1)));
        speedStatus=text("●  SEARCHING FOR GPS",compactLayout?10:12,MUTED,true);
        speedStatus.setGravity(Gravity.CENTER);
        speedStatus.setLetterSpacing(0.07f);
        speedPanel.addView(speedStatus,new LinearLayout.LayoutParams(
            -1,DriveUi.dp(this,compactLayout?26:42)));

        LinearLayout right=DriveUi.column(this);
        body.addView(right,new LinearLayout.LayoutParams(0,-1,1.18f));

        LinearLayout nav=DriveUi.row(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        interactiveBackground(nav,true);
        nav.setClickable(true);
        nav.setContentDescription("Navigation. Offline maps. Plan a journey");
        nav.setOnClickListener(v->startActivity(new Intent(this,MapActivity.class)));
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,0,0.48f);
        nlp.bottomMargin=DriveUi.dp(this,compactLayout?8:12);
        right.addView(nav,nlp);
        int navIconSize=compactLayout?63:98;
        LinearLayout.LayoutParams navIconLp=new LinearLayout.LayoutParams(
            DriveUi.dp(this,navIconSize),DriveUi.dp(this,navIconSize));
        navIconLp.leftMargin=DriveUi.dp(this,compactLayout?10:20);
        nav.addView(symbol(SignatureIcon.NAVIGATION,navIconSize,CYAN),navIconLp);
        LinearLayout navTexts=DriveUi.column(this);
        navTexts.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ntlp=new LinearLayout.LayoutParams(0,-1,1f);
        ntlp.leftMargin=DriveUi.dp(this,compactLayout?7:13);
        nav.addView(navTexts,ntlp);
        TextView navTitle=text("NAVIGATION",compactLayout?17:27,WHITE,true);
        navTitle.setLetterSpacing(0.10f);
        navTexts.addView(navTitle);
        TextView navSubtitle=text("Offline maps  •  Plan a journey",compactLayout?11:15,MUTED,false);
        LinearLayout.LayoutParams nsLp=new LinearLayout.LayoutParams(-2,-2);
        nsLp.topMargin=DriveUi.dp(this,compactLayout?6:12);
        navTexts.addView(navSubtitle,nsLp);
        LinearLayout.LayoutParams chevLp=new LinearLayout.LayoutParams(
            DriveUi.dp(this,compactLayout?17:29),DriveUi.dp(this,compactLayout?17:29));
        chevLp.rightMargin=DriveUi.dp(this,compactLayout?9:19);
        nav.addView(symbol(SignatureIcon.CHEVRON,compactLayout?17:29,0xffa7e9ee),chevLp);

        LinearLayout shortcuts=DriveUi.row(this);
        right.addView(shortcuts,new LinearLayout.LayoutParams(-1,0,0.52f));
        addShortcut(shortcuts,SignatureIcon.RADIO,"RADIO","radio",false);
        addShortcut(shortcuts,SignatureIcon.MUSIC,"MUSIC","music",false);
        addShortcut(shortcuts,SignatureIcon.PHONE,"PHONE","phone",true);

        LinearLayout footer=DriveUi.row(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(footer,new LinearLayout.LayoutParams(-1,DriveUi.dp(this,compactLayout?24:34)));
        TextView footerText=text("OFFLINE-FIRST   •   EATON SYSTEMS",compactLayout?9:11,MUTED,true);
        footerText.setLetterSpacing(0.09f);
        footer.addView(footerText,new LinearLayout.LayoutParams(0,-2,1f));
        TextView settings=text("SETTINGS  ⚙",compactLayout?10:12,CYAN,true);
        settings.setPadding(DriveUi.dp(this,15),DriveUi.dp(this,4),0,DriveUi.dp(this,4));
        settings.setOnClickListener(v->openSystemMenu());
        footer.addView(settings);
        handler.removeCallbacks(clockTicker);
        clockTicker.run();
    }

    private void addShortcut(LinearLayout parent,int icon,String name,String key,boolean last){
        LinearLayout tile=DriveUi.column(this);
        tile.setGravity(Gravity.CENTER);
        interactiveBackground(tile,false);
        tile.addView(symbol(icon,compactLayout?38:60,CYAN));
        TextView text=text(name,compactLayout?14:19,WHITE,true);
        text.setLetterSpacing(0.08f);text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(-1,-2);
        labelLp.topMargin=DriveUi.dp(this,compactLayout?7:15);
        tile.addView(text,labelLp);
        tile.setContentDescription(name+" — tap to open, long press to choose app");
        tile.setClickable(true);
        tile.setOnClickListener(v->launchAssigned(key));
        tile.setOnLongClickListener(v->{chooseApp(key);return true;});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f);
        if(!last)p.rightMargin=DriveUi.dp(this,compactLayout?7:11);
        parent.addView(tile,p);
    }
    private void onGps(Integer mph,String state,Location location){
        if(speedValue==null)return;
        speedValue.setText(mph==null?"—":String.valueOf(mph));
        String value=state==null?"GPS UNAVAILABLE":state.toUpperCase(Locale.UK);
        boolean valid=mph!=null && location!=null && location.hasAccuracy()
            && location.getAccuracy()<=60f;
        speedStatus.setText(valid?"●  GPS READY":"●  "+value);
        speedStatus.setTextColor(valid?0xff52e6ba:MUTED);
        if(gpsChip!=null)gpsChip.setTextColor(valid?0xff52e6ba:MUTED);
    }
    private void showApps(){
        PackageManager pm=getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found=pm.queryIntentActivities(query,0);
        List<ResolveInfo> available=new ArrayList<>();
        for(ResolveInfo info:found)
            if(info.activityInfo!=null&&!getPackageName().equals(info.activityInfo.packageName))
                available.add(info);
        Collections.sort(available,(a,b)->a.loadLabel(pm).toString()
            .compareToIgnoreCase(b.loadLabel(pm).toString()));
        if(available.isEmpty()){openSystemMenu();return;}
        String[] names=new String[available.size()];
        for(int i=0;i<names.length;i++)names[i]=available.get(i).loadLabel(pm).toString();
        new AlertDialog.Builder(this).setTitle("Installed apps")
            .setItems(names,(dialog,index)->{
                ResolveInfo selected=available.get(index);
                Intent open=new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER);
                open.setComponent(new ComponentName(selected.activityInfo.packageName,
                    selected.activityInfo.name));
                try{startActivity(open);}
                catch(Exception error){
                    Toast.makeText(this,"Couldn't open selected app",Toast.LENGTH_LONG).show();
                }
            }).setNeutralButton("Settings",(dialog,which)->openSystemMenu())
            .setNegativeButton("Close",null).show();
    }
    private void launchAssigned(String key) {
        String saved=getPreferences(0).getString("app_"+key,null);
        if(saved==null){chooseApp(key);return;}
        ComponentName component=ComponentName.unflattenFromString(saved);
        if(component==null){chooseApp(key);return;}
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean overlayStarted=false;
        if(uk.co.eaton.drive.overlay.OverlayRules.showForShortcut(key,
            FloatingHomeService.enabled(this),FloatingHomeService.permitted(this))){
            try {
                Intent overlay=FloatingHomeService.showIntent(this);
                if(Build.VERSION.SDK_INT>=26)startForegroundService(overlay);
                else startService(overlay);
                overlayStarted=true;
            }catch(RuntimeException error){
                Toast.makeText(this,"Floating Home not supported on this device",
                    Toast.LENGTH_SHORT).show();
            }
        }
        try{startActivity(intent);}
        catch(RuntimeException ex){
            if(overlayStarted)FloatingHomeService.hide(this);
            Toast.makeText(this,"App unavailable; please select another",
                Toast.LENGTH_LONG).show();
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
        String overlayStatus=FloatingHomeService.enabled(this)?"ON":"OFF";
        String launcherStatus=isDefaultHome()?"DEFAULT":"NOT SELECTED";
        new AlertDialog.Builder(this).setTitle("Eaton Drive • System settings")
            .setItems(new String[] {
                "Default home launcher: "+launcherStatus,
                "Floating Home for Radio/Music/Phone: "+overlayStatus,
                "Test floating Home shortcut",
                "Floating Home diagnostics",
                "Assign FM radio application",
                "Assign music application",
                "Assign Bluetooth phone application",
                "Head unit diagnostic details",
                "Open Android settings"
            },(d,item)->{
                switch(item){
                    case 0:requestDefaultHome();break;
                    case 1:toggleOverlay();break;
                    case 2:testOverlay();break;
                    case 3:showOverlayDiagnostics();break;
                    case 4:chooseApp("radio");break;
                    case 5:chooseApp("music");break;
                    case 6:chooseApp("phone");break;
                    case 7:showDeviceDetails();break;
                    case 8:
                        try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
                        catch(RuntimeException ex){Toast.makeText(this,
                            "Android Settings unavailable",Toast.LENGTH_LONG).show();}
                        break;
                    default:break;
                }
            }).setNegativeButton("Close",null).show();
    }
    private boolean isDefaultHome() {
        if(Build.VERSION.SDK_INT>=29){
            try{
                RoleManager rm=getSystemService(RoleManager.class);
                if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_HOME))
                    return rm.isRoleHeld(RoleManager.ROLE_HOME);
            }catch(RuntimeException ignored){}
        }
        Intent home=new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo match=getPackageManager().resolveActivity(home,
            PackageManager.MATCH_DEFAULT_ONLY);
        return match!=null&&match.activityInfo!=null
            &&getPackageName().equals(match.activityInfo.packageName);
    }
    private void requestDefaultHome() {
        if(isDefaultHome()) {
            new AlertDialog.Builder(this).setTitle("Eaton Drive is Home")
                .setMessage("Eaton Drive is already the selected Android launcher. "
                    +"Test ignition sleep/restart as the stereo may override Home.")
                .setPositiveButton("OK",null).show();return;
        }
        if(Build.VERSION.SDK_INT>=29){
            try{
                RoleManager rm=getSystemService(RoleManager.class);
                if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_HOME)){
                    startActivityForResult(
                        rm.createRequestRoleIntent(RoleManager.ROLE_HOME),
                        REQUEST_HOME_ROLE);
                    return;
                }
            }catch(RuntimeException ignored){}
        }
        try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}
        catch(RuntimeException ex){
            try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
            catch(RuntimeException ignored){
                Toast.makeText(this,"Manufacturer disabled Home settings",
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    private void toggleOverlay() {
        if(FloatingHomeService.enabled(this)){
            setOverlayEnabled(false);
            Toast.makeText(this,"Floating Home disabled",Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Enable floating Home?")
            .setMessage("Shows a small draggable Home button when you open Radio, Music OR Phone "
                +"from Eaton Drive. It requires Android's Draw over other apps permission. "
                +"It will NOT run automatically when the car starts. "
                +"On some head units the button may appear over a reverse-camera or "
                +"system warning screen. TEST WHILE PARKED; turn it off if anything "
                +"is obstructed. Phone apps and active calls may hide or reject overlays, "
                +"and the button must never cover Answer, End Call, Mute or other "
                +"essential controls. Long-press it or use the notification to hide it.")
            .setPositiveButton("CONTINUE",(dialog,which)->requestOverlayPermission())
            .setNegativeButton("CANCEL",null).show();
    }
    private void setOverlayEnabled(boolean enabled){
        getSharedPreferences(FloatingHomeService.PREFS,MODE_PRIVATE).edit()
            .putBoolean(FloatingHomeService.ENABLED,enabled).apply();
        if(!enabled)FloatingHomeService.hide(this);
    }
    private void requestOverlayPermission(){
        if(FloatingHomeService.permitted(this)){
            setOverlayEnabled(true);
            Toast.makeText(this,"Floating Home enabled for Radio, Music and Phone",
                Toast.LENGTH_LONG).show();
            return;
        }
        awaitingOverlayPermission=true;
        try{
            Intent manage=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            if(Build.VERSION.SDK_INT<30)
                manage.setData(Uri.parse("package:"+getPackageName()));
            startActivity(manage);
        }catch(RuntimeException ex){
            awaitingOverlayPermission=false;
            Toast.makeText(this,"This Android firmware has no overlay permission screen. "
                +"Use the normal Home key instead.",Toast.LENGTH_LONG).show();
        }
    }
    private void testOverlay(){
        if(!FloatingHomeService.enabled(this)||!FloatingHomeService.permitted(this)){
            new AlertDialog.Builder(this).setTitle("Floating Home not active")
                .setMessage("Enable floating Home in Eaton Drive settings first.")
                .setPositiveButton("ENABLE",(d,w)->toggleOverlay())
                .setNegativeButton("Cancel",null).show();
            return;
        }
        try{
            Intent show=FloatingHomeService.showIntent(this);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(show);
            else startService(show);
            Toast.makeText(this,
                "Tap floating house to return • drag to reposition • hold to hide",
                Toast.LENGTH_LONG).show();
        }catch(RuntimeException ex){
            Toast.makeText(this,"Floating Home was blocked by this head unit",
                Toast.LENGTH_LONG).show();
        }
    }
    private void showOverlayDiagnostics(){
        new AlertDialog.Builder(this)
            .setTitle("Floating Home diagnostics")
            .setMessage(FloatingHomeService.diagnostics(this)
                +"\n\nUse TEST to show the button on this screen. Open Radio, Music "
                +"or Phone from Eaton Drive and check it remains visible. "
                +"If the last service state is Start failed, the error above "
                +"may identify an Android or manufacturer restriction.")
            .setPositiveButton("TEST",(dialog,which)->testOverlay())
            .setNeutralButton("OVERLAY SETTINGS",(dialog,which)->{
                try{
                    Intent manage=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    if(Build.VERSION.SDK_INT<30)
                        manage.setData(Uri.parse("package:"+getPackageName()));
                    startActivity(manage);
                }catch(RuntimeException ignored){
                    Toast.makeText(this,"Overlay settings unavailable",Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Close",null).show();
    }
    private void showDeviceDetails(){
        android.util.DisplayMetrics display=getResources().getDisplayMetrics();
        ActivityManager activity=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        String ram="?";
        if(activity!=null){
            ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
            activity.getMemoryInfo(info);
            ram=String.format(Locale.UK,"%.1f GB",info.totalMem/1073741824d);
        }
        String info="Manufacturer: "+Build.MANUFACTURER
            +"\\nModel: "+Build.MODEL
            +"\\nAndroid: "+Build.VERSION.RELEASE+" (SDK "+Build.VERSION.SDK_INT+")"
            +"\\nBuild display: "+Build.DISPLAY
            +"\\nDisplay: "+display.widthPixels+" × "+display.heightPixels+" pixels"
            +"\\nDensity: "+Math.round(display.densityDpi)+" dpi"
            +"\\nApprox. total RAM: "+ram
            +"\\nDefault Home: "+(isDefaultHome()?"Eaton Drive":"Other / not selected")
            +"\\nFloating Home permission: "+(FloatingHomeService.permitted(this)?"Granted":"Not granted")
            +"\\nOverlay state: "+
                getSharedPreferences(FloatingHomeService.PREFS,MODE_PRIVATE)
                    .getString(FloatingHomeService.LAST_STATUS,"Not started")
            +"\\n\\nMCU version and chipset are not available through standard Android APIs. "
            +"For tomorrow's car check, photograph the stereo's Settings → About device "
            +"and MCU/system information.";
        // The string is formatted with ordinary newlines in the final alert.
        info=info.replace("\\n","\n");
        new AlertDialog.Builder(this)
            .setTitle("Head unit test details")
            .setMessage(info)
            .setPositiveButton("OPEN ANDROID SETTINGS",(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
                catch(RuntimeException ignored){}
            }).setNegativeButton("CLOSE",null).show();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_HOME_ROLE){
            Toast.makeText(this,isDefaultHome()
                ?"Eaton Drive is now the Home launcher"
                :"Home role not changed; check manufacturer Settings",
                Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onResume() {
        super.onResume();
        // Returning Home tears down the optional floating overlay, while an
        // active navigation service continues independently.
        FloatingHomeService.hide(this);
        if(awaitingOverlayPermission){
            awaitingOverlayPermission=false;
            boolean granted=FloatingHomeService.permitted(this);
            setOverlayEnabled(granted);
            Toast.makeText(this,granted
                ?"Floating Home enabled — test RADIO, MUSIC and PHONE while parked"
                :"Overlay permission not granted — Home key remains available",
                Toast.LENGTH_LONG).show();
        }
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

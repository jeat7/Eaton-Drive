package uk.co.eaton.drive.routing;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.os.Build;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.content.pm.PackageManager;
import android.Manifest;
import java.util.Locale;
import uk.co.eaton.drive.MainActivity;
import uk.co.eaton.drive.MapActivity;
import uk.co.eaton.drive.R;
import uk.co.eaton.drive.gps.GpsTracker;

/**
 * Explicit user-started, visible location foreground service. Owns active GPS
 * tracking and speech independently of MapActivity and OEM radio/music UI.
 * Does not auto-restart after a process death or ignition restart.
 */
public final class NavigationService extends Service {
    public static final String ACTION_START="uk.co.eaton.drive.nav.START";
    public static final String ACTION_STOP="uk.co.eaton.drive.nav.STOP";
    private static final String CHANNEL="eaton_drive_navigation";
    private static final int NOTIFICATION_ID=1701;

    public interface Observer { void onNavigationState(Snapshot snapshot); }
    public static final class Snapshot {
        public final boolean active, hasTurns, lostGps;
        public final String instruction, message;
        public final double remainingMiles;
        public final int remainingMinutes;
        public final Location location;
        public final RouteParser.Route route;
        Snapshot(boolean active,boolean hasTurns,boolean lostGps,String instruction,
                 String message,double miles,int minutes,Location location,RouteParser.Route route){
            this.active=active;this.hasTurns=hasTurns;this.lostGps=lostGps;
            this.instruction=instruction;this.message=message;
            this.remainingMiles=miles;this.remainingMinutes=minutes;
            this.location=location;this.route=route;
        }
    }
    public final class LocalBinder extends Binder {
        public NavigationService instance(){return NavigationService.this;}
    }
    private final IBinder binder=new LocalBinder();
    private Observer observer;
    private GpsTracker gps;
    private VoiceGuide voice;
    private EmbeddedRouter router;
    private NavigationSession session;
    private RouteParser.Route route;
    private RouteOptions options;
    private Location lastFix;
    private boolean active, rerouting;
    private boolean hasStartedForeground;
    private long lastNotification;
    private long lastProcessedFixNanos;
    private String instruction="Preparing navigation",message="Waiting for GPS";

    public static Intent startIntent(Context context){
        return new Intent(context,NavigationService.class).setAction(ACTION_START);
    }
    public static void stop(Context context) {
        context.startService(new Intent(context,NavigationService.class).setAction(ACTION_STOP));
    }
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26 && nm!=null){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Eaton Drive navigation",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Current turn and Stop Navigation controls");
            nm.createNotificationChannel(channel);
        }
        gps=new GpsTracker(this,this::onGps);
        // Allocate routing and speech only when GO actually starts navigation.
    }
    @Override public IBinder onBind(Intent intent){return binder;}
    public void observe(Observer target){
        observer=target;
        if(target!=null)target.onNavigationState(snapshot());
    }
    public void removeObserver(Observer target){if(observer==target)observer=null;}
    public boolean isActive(){return active;}
    public RouteParser.Route currentRoute(){return route;}
    public void setSpeechEnabled(boolean enabled){
        getSharedPreferences("navigation",MODE_PRIVATE).edit().putBoolean("voice_enabled",enabled).apply();
        if(voice!=null)voice.setEnabled(enabled);
        notifyObserver();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(ACTION_STOP.equals(intent.getAction())){
            finishNavigation(false);
            return START_NOT_STICKY;
        }
        if(!ACTION_START.equals(intent.getAction()))return START_NOT_STICKY;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            stopSelf();return START_NOT_STICKY;
        }
        try {
            // Always promote promptly while the Activity is visible, before reading route.
            if(!hasStartedForeground){
                startForeground(NOTIFICATION_ID,notification("Preparing offline navigation"));
                hasStartedForeground=true;
            }
            if(router==null)router=new EmbeddedRouter(this);
            if(voice==null)voice=new VoiceGuide(this);
            voice.setEnabled(getSharedPreferences("navigation",MODE_PRIVATE).getBoolean("voice_enabled",true));
            RouteStorage.Saved saved=RouteStorage.load(this);
            route=saved.route;options=saved.options;
            session=new NavigationSession(route);
            lastFix=null;lastProcessedFixNanos=0;
            active=true;rerouting=false;
            instruction=route.turns.isEmpty()?
                "Follow highlighted route — voice turns unavailable":"Continue along route";
            message="Navigation active";
            gps.start();
            if(voice!=null)voice.say("Navigation started");
            updateNotification(true);
            notifyObserver();
        } catch(Exception problem){
            instruction="Navigation couldn't start";
            message=problem.getMessage()==null?"Route unavailable":problem.getMessage();
            notifyObserver();
            finishNavigation(false);
        }
        return START_NOT_STICKY;
    }
    private void onGps(Integer mph,String state,Location fix){
        if(!active||session==null)return;
        if(fix==null||!fix.hasAccuracy()||fix.getAccuracy()>45f){
            lastFix=null;
            instruction="GPS signal unavailable";
            message=state+" — guidance paused until GPS returns";
            updateNotification(false);notifyObserver();
            return;
        }
        lastFix=fix;
        // GpsTracker also republishes its last fix on a watchdog timer. Only process
        // genuinely new GPS fixes for turn announcement and off-route thresholds.
        if(fix.getElapsedRealtimeNanos()==lastProcessedFixNanos){
            notifyObserver();
            return;
        }
        lastProcessedFixNanos=fix.getElapsedRealtimeNanos();
        if(rerouting){message="Recalculating offline route";notifyObserver();return;}
        NavigationSession.Update result=session.locate(
            new RouteParser.Point(fix.getLatitude(),fix.getLongitude()),
            fix.getAccuracy(),SystemClock.elapsedRealtime());
        instruction=TurnDisplay.nextInstruction(result,session.hasTurns());
        message=result.display;
        if(result.speak!=null&&voice!=null)voice.say(result.speak);
        if(result.arrived){finishNavigation(true);return;}
        if(result.reroute)reroute(fix);
        updateNotification(false);
        notifyObserver();
    }
    private void reroute(Location fix){
        if(route==null||route.points.isEmpty()||rerouting)return;
        rerouting=true;
        RouteParser.Point target=route.points.get(route.points.size()-1);
        RouteParser.Point from=new RouteParser.Point(fix.getLatitude(),fix.getLongitude());
        RouteOptions request=options;
        router.request(from,target,request,(newRoute,error)->{
            rerouting=false;
            if(!active)return;
            if(newRoute==null){
                session.failedReroute();
                message="Recalculation failed: "+(error==null?"unknown error":error);
                instruction="Follow the existing route if safe";
            }else{
                route=newRoute;
                session=new NavigationSession(newRoute);
                instruction="Route recalculated";
                message="Continue on updated offline route";
                if(voice!=null)voice.say("Route recalculated");
                try{RouteStorage.save(this,newRoute,options);}catch(Exception ignored){}
            }
            updateNotification(true);notifyObserver();
        });
    }
    private Snapshot snapshot(){
        boolean fixed=active&&lastFix!=null&&lastFix.hasAccuracy()
            && lastFix.getAccuracy()<=45f
            && SystemClock.elapsedRealtime()-lastFix.getElapsedRealtimeNanos()/1_000_000L<=6000;
        double distance=session==null?0:session.remainingMiles();
        int seconds=route==null?0:route.estimatedSeconds;
        int mins=route==null||route.miles<=0?0:
            (int)Math.ceil((seconds>0?seconds:route.miles/35.0*3600.0)*distance/route.miles/60.0);
        return new Snapshot(active,session!=null&&session.hasTurns(),!fixed,
            instruction,message,distance,Math.max(0,mins),fixed?new Location(lastFix):null,route);
    }
    private void notifyObserver(){
        if(observer!=null)observer.onNavigationState(snapshot());
    }
    private PendingIntent contentIntent(){
        Intent i=new Intent(this,MapActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this,10,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification(String summary){
        Intent stopIntent=new Intent(this,NavigationService.class).setAction(ACTION_STOP);
        PendingIntent stop=PendingIntent.getService(this,11,stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26 ?
            new Notification.Builder(this,CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_eaton_drive).setContentTitle("Eaton Drive • Navigation")
            .setContentText(summary).setContentIntent(contentIntent())
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"STOP",stop)
            .build();
    }
    private void updateNotification(boolean force){
        if(!active||!hasStartedForeground)return;
        long now=SystemClock.elapsedRealtime();
        if(!force&&now-lastNotification<6000)return;
        lastNotification=now;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager!=null)manager.notify(NOTIFICATION_ID,notification(instruction));
    }
    private void finishNavigation(boolean arrived){
        active=false;rerouting=false;
        if(!arrived)RouteStorage.clear(this);
        if(gps!=null)gps.stop();
        if(arrived) {
            instruction="Destination reached";
            message="Navigation finished";
        }else{
            instruction="Navigation stopped";
            message="Route guidance is off";
        }
        notifyObserver();
        if(hasStartedForeground){
            stopForeground(true);
            hasStartedForeground=false;
        }
        stopSelf();
    }
    @Override public void onDestroy(){
        active=false;
        if(gps!=null)gps.stop();
        if(voice!=null)voice.shutdown();
        if(router!=null)router.destroy();
        if(observer!=null)observer.onNavigationState(snapshot());
        observer=null;
        super.onDestroy();
    }
}

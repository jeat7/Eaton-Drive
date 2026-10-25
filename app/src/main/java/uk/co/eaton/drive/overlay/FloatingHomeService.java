package uk.co.eaton.drive.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import uk.co.eaton.drive.MainActivity;
import uk.co.eaton.drive.R;

/**
 * User-enabled, small Home shortcut over RADIO/MUSIC/PHONE. No auto-start, no
 * monitoring of other apps or screen content, and no additional GPS receiver.
 *
 * CAUTION: some vendor reversing-camera screens are ordinary Android windows.
 * They may show an overlay; test while stationary and disable this feature if
 * the shortcut interferes with any camera, call or vehicle safety control.
 */
public final class FloatingHomeService extends Service {
    public static final String ACTION_SHOW="uk.co.eaton.drive.overlay.SHOW";
    public static final String ACTION_HIDE="uk.co.eaton.drive.overlay.HIDE";
    public static final String PREFS="eaton_overlay_settings";
    public static final String ENABLED="enabled";
    public static final String LAST_STATUS="last_status";
    public static final String LAST_ERROR="last_error";
    public static final String LAST_EVENT="last_event";

    private static final String CHANNEL="eaton_floating_home";
    private static final int NOTIFICATION_ID=1702;
    private WindowManager manager;
    private WindowManager.LayoutParams params;
    private View bubble;
    private boolean foreground;
    private boolean intentionalStop;
    private void record(String status,String error){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString(LAST_STATUS,status)
            .putString(LAST_ERROR,error==null?"":error)
            .putLong(LAST_EVENT,System.currentTimeMillis()).apply();
    }
    public static String diagnostics(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,MODE_PRIVATE);
        String state=p.getString(LAST_STATUS,"Not started");
        String error=p.getString(LAST_ERROR,"");
        long at=p.getLong(LAST_EVENT,0L);
        return "Enabled: "+(enabled(context)?"YES":"NO")
            +"\nOverlay permission: "+(permitted(context)?"GRANTED":"NOT GRANTED")
            +"\nLast service state: "+state
            +(error.isEmpty()?"":"\nLast error: "+error)
            +(at<=0?"":"\nLast service event: "+
                new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.UK).format(
                    new java.util.Date(at)));
    }

    public static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS,MODE_PRIVATE)
            .getBoolean(ENABLED,false);
    }
    public static boolean permitted(Context context) {
        return Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(context);
    }
    public static Intent showIntent(Context context) {
        return new Intent(context,FloatingHomeService.class).setAction(ACTION_SHOW);
    }
    public static void hide(Context context) {
        context.stopService(new Intent(context,FloatingHomeService.class));
    }
    @Override public IBinder onBind(Intent intent){return null;}

    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(nm!=null){
                NotificationChannel c=new NotificationChannel(CHANNEL,"Eaton floating Home",
                    NotificationManager.IMPORTANCE_LOW);
                c.setDescription("Optional Home shortcut for Radio, Music and Phone");
                nm.createNotificationChannel(c);
            }
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||ACTION_HIDE.equals(intent.getAction())){
            intentionalStop=true;
            record("Hidden by user or system","");stopSelf();return START_NOT_STICKY;
        }
        if(!ACTION_SHOW.equals(intent.getAction())){
            intentionalStop=true;
            record("Rejected unknown action","");stopSelf();return START_NOT_STICKY;
        }
        if(!enabled(this)||!permitted(this)){
            intentionalStop=true;
            record("Not enabled or overlay permission missing","");
            stopSelf();return START_NOT_STICKY;
        }
        if(bubble!=null){
            record("Visible","");
            return START_NOT_STICKY;
        }
        try{
            startForeground(NOTIFICATION_ID,notification());
            foreground=true;
            displayBubble();
            record("Visible","");
        }catch(Exception ex){
            String details=ex.getClass().getSimpleName()
                +(ex.getMessage()==null?"":": "+ex.getMessage());
            record("Start failed",details);
            Toast.makeText(this,"Floating Home unavailable: "+ex.getClass().getSimpleName(),
                Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY; // Never resurrect over arbitrary screens after process restart.
    }

    private Notification notification(){
        Intent home=new Intent(this,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP
                |Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent toHome=PendingIntent.getActivity(this,210,home,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent close=new Intent(this,FloatingHomeService.class).setAction(ACTION_HIDE);
        PendingIntent hide=PendingIntent.getService(this,211,close,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_eaton_drive)
            .setContentTitle("Eaton Drive Home shortcut")
            .setContentText("Tap to return • long-press floating Home to hide")
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(toHome)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"HIDE",hide).build();
    }

    private int dp(float value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
    private void displayBubble(){
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(manager==null)throw new IllegalStateException("Window manager unavailable");
        params=new WindowManager.LayoutParams(
            dp(48),dp(48),
            Build.VERSION.SDK_INT>=26
                ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                :WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;
        Point bounds=new Point();
        manager.getDefaultDisplay().getSize(bounds);
        params.x=Math.max(0,bounds.x-dp(66));
        params.y=Math.max(0,bounds.y-dp(104));
        HomeBubble view=new HomeBubble(this);
        view.setContentDescription("Eaton Drive Home. Tap to return, drag to move, hold to hide.");
        view.setOnTouchListener(new DragListener(bounds));
        manager.addView(view,params);
        bubble=view;
    }

    private final class DragListener implements View.OnTouchListener {
        final Point bounds;
        float downX,downY;
        int originalX,originalY;
        long pressStart;
        boolean dragged;
        DragListener(Point bounds){this.bounds=bounds;}
        @Override public boolean onTouch(View view,MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=event.getRawX();downY=event.getRawY();
                    originalX=params.x;originalY=params.y;
                    pressStart=SystemClock.uptimeMillis();dragged=false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                    if(Math.abs(dx)>dp(8)||Math.abs(dy)>dp(8))dragged=true;
                    if(dragged){
                        params.x=Math.max(0,Math.min(bounds.x-dp(48),originalX+Math.round(dx)));
                        params.y=Math.max(0,Math.min(bounds.y-dp(48),originalY+Math.round(dy)));
                        try{manager.updateViewLayout(view,params);}
                        catch(IllegalArgumentException ignored){}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!dragged){
                        if(SystemClock.uptimeMillis()-pressStart>=650) {
                            stopSelf();
                        }else{
                            Intent home=new Intent(FloatingHomeService.this,MainActivity.class);
                            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                |Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(home);
                            stopSelf();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:return true;
                default:return false;
            }
        }
    }

    /** Original vector home symbol, rendered in a single tiny view. */
    private static final class HomeBubble extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        HomeBubble(Context context){
            super(context);
            setLayerType(View.LAYER_TYPE_NONE,null);
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float scale=Math.min(getWidth(),getHeight())/48f;
            c.save();c.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xf0092237);
            c.drawRoundRect(1,1,47,47,14,14,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);
            paint.setColor(0xff30ddda);c.drawRoundRect(1.75f,1.75f,46.25f,46.25f,13,13,paint);
            path.reset();
            path.moveTo(11,24);path.lineTo(24,13);path.lineTo(37,24);
            path.moveTo(15,23);path.lineTo(15,35);
            path.lineTo(21,35);path.lineTo(21,28);path.lineTo(27,28);
            path.lineTo(27,35);path.lineTo(33,35);path.lineTo(33,23);
            paint.setStrokeWidth(3);paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(0xff65eee7);
            c.drawPath(path,paint);c.restore();
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        // The Eaton launcher is landscape and many Android music apps are
        // portrait. Stopping the service on *every* orientation change made
        // the Home button disappear as soon as music opened.
        // Instead keep the opt-in bubble visible and clamp it within the new
        // display dimensions. A user can always hide it via the notification.
        if(manager==null||bubble==null||params==null)return;
        try{
            Point bounds=new Point();
            manager.getDefaultDisplay().getSize(bounds);
            params.x=Math.max(0,Math.min(params.x,Math.max(0,bounds.x-dp(48))));
            params.y=Math.max(0,Math.min(params.y,Math.max(0,bounds.y-dp(48))));
            manager.updateViewLayout(bubble,params);
            bubble.setOnTouchListener(new DragListener(bounds));
            record("Visible after display rotation","");
        }catch(RuntimeException error){
            record("Rotation update failed",error.getClass().getSimpleName()+
                (error.getMessage()==null?"":": "+error.getMessage()));
            stopSelf();
        }
    }
    @Override public void onDestroy(){
        if(manager!=null&&bubble!=null){
            try{manager.removeView(bubble);}catch(IllegalArgumentException ignored){}
            bubble=null;
        }
        if(foreground)stopForeground(true);
        if(!intentionalStop && "Visible".equals(
            getSharedPreferences(PREFS,MODE_PRIVATE).getString(LAST_STATUS,"")))
            record("Stopped by Android or launcher","");
        super.onDestroy();
    }
}

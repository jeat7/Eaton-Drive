package uk.co.eaton.drive.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small top-down blue Suzuki-style hatchback navigation marker (original artwork). */
public final class CarMarkerView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float bearing;
    private boolean accurate;
    public CarMarkerView(Context context) {
        super(context);
        setContentDescription("GPS vehicle position");
        setVisibility(GONE);
    }
    public void update(float bearingDegrees, boolean hasAccurateGps) {
        bearing = bearingDegrees;
        accurate = hasAccurateGps;
        setVisibility(accurate ? VISIBLE : GONE);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!accurate) return;
        canvas.save();
        float cx = getWidth()/2f, cy = getHeight()/2f;
        canvas.translate(cx,cy);
        canvas.rotate(bearing);
        float size = Math.min(getWidth(),getHeight()) / 64f;
        canvas.scale(size,size);
        // Ground shadow
        p.setColor(0x66001122);
        canvas.drawOval(-16, -20, 17, 29,p);
        // Four wheels
        p.setColor(0xFF182C3B);
        canvas.drawRoundRect(-19,-18,-11,-5,3,3,p);
        canvas.drawRoundRect(11,-18,19,-5,3,3,p);
        canvas.drawRoundRect(-19,10,-11,22,3,3,p);
        canvas.drawRoundRect(11,10,19,22,3,3,p);
        // Light blue hatchback body
        p.setColor(0xFF61CFFF);
        canvas.drawRoundRect(-14,-26,14,26,9,9,p);
        p.setColor(0xFFB5EEFF);
        canvas.drawRoundRect(-11,-15,11,-3,4,4,p);
        p.setColor(0xFF237FAE);
        canvas.drawRoundRect(-11,5,11,16,4,4,p);
        p.setColor(0xFFEBFCFF);
        canvas.drawRoundRect(-11,-23,-6,-19,1,1,p);
        canvas.drawRoundRect(6,-23,11,-19,1,1,p);
        p.setColor(0xFFE43959);
        canvas.drawRoundRect(-10,20,-6,24,1,1,p);
        canvas.drawRoundRect(6,20,10,24,1,1,p);
        canvas.restore();
    }
}

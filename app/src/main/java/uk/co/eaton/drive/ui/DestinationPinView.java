package uk.co.eaton.drive.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Fixed screen-centre pin; tip shows exactly which geographic coordinate is selected. */
public final class DestinationPinView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public DestinationPinView(Context context) {
        super(context);
        setContentDescription("Destination pin, positioned at map centre");
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx=getWidth()/2f;
        float ty=getHeight()/2f;
        // Tip touches the map's centre coordinate; pin head is above it.
        paint.setColor(0x55000000);
        canvas.drawCircle(cx,ty+1,8,paint);
        Path shape = new Path();
        shape.moveTo(cx,ty);
        shape.cubicTo(cx-5,ty-10,cx-16,ty-18,cx-16,ty-28);
        shape.cubicTo(cx-16,ty-50,cx+16,ty-50,cx+16,ty-28);
        shape.cubicTo(cx+16,ty-18,cx+5,ty-10,cx,ty);
        shape.close();
        paint.setColor(0xFF2ED3B7);
        canvas.drawPath(shape,paint);
        paint.setColor(0xFF07111F);
        canvas.drawCircle(cx,ty-29,6,paint);
    }
}

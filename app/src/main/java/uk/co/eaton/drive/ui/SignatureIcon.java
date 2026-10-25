package uk.co.eaton.drive.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Lightweight vector-like dashboard glyphs. No downloaded fonts or animations. */
public final class SignatureIcon extends View {
    public static final int APPS=0, NAVIGATION=1, RADIO=2, MUSIC=3, PHONE=4, CHEVRON=5, GPS=6;
    private final int type;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private int colour=0xff20d7d6;

    public SignatureIcon(Context context,int type) {
        super(context);
        this.type=type;
        setLayerType(View.LAYER_TYPE_NONE,null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setColour(int argb){colour=argb;invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        canvas.save();
        canvas.translate((w-Math.min(w,h))/2f,(h-Math.min(w,h))/2f);
        float scale=Math.min(w,h)/100f;
        canvas.scale(scale,scale);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(colour);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);
        if(type==NAVIGATION){
            path.reset();
            path.moveTo(10,57);path.lineTo(88,12);path.quadTo(93,9,92,16);
            path.lineTo(89,92);path.quadTo(89,96,85,92);
            path.lineTo(58,62);path.lineTo(10,65);path.quadTo(3,65,10,57);
            path.close();
            p.setStyle(Paint.Style.FILL);canvas.drawPath(path,p);
            p.setColor(0x403eece0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
            canvas.drawPath(path,p);
        }else if(type==RADIO){
            p.setStrokeWidth(6);
            canvas.drawRoundRect(8,29,92,83,11,11,p);
            canvas.drawLine(20,28,73,6,p);
            canvas.drawLine(18,48,80,48,p);
            canvas.drawCircle(31,65,11,p);
            canvas.drawCircle(72,65,7,p);
            p.setStrokeWidth(4);canvas.drawLine(69,36,78,36,p);
        }else if(type==MUSIC){
            p.setStrokeWidth(8);
            canvas.drawLine(43,18,80,8,p);
            canvas.drawLine(43,18,43,77,p);
            canvas.drawLine(80,8,80,63,p);
            p.setStyle(Paint.Style.FILL);
            canvas.drawOval(15,70,45,90,p);
            canvas.drawOval(52,57,82,77,p);
        }else if(type==PHONE){
            path.reset();
            path.moveTo(27,12);
            path.cubicTo(14,15,10,30,16,46);
            path.cubicTo(23,68,43,88,66,91);
            path.cubicTo(82,94,91,83,91,73);
            path.lineTo(68,58);
            path.lineTo(56,70);
            path.cubicTo(44,67,31,54,29,42);
            path.lineTo(41,32);
            path.lineTo(27,12);
            path.close();
            p.setStyle(Paint.Style.FILL);canvas.drawPath(path,p);
        }else if(type==CHEVRON){
            p.setStrokeWidth(7);
            path.reset();path.moveTo(35,23);path.lineTo(62,50);path.lineTo(35,77);
            canvas.drawPath(path,p);
        }else if(type==GPS){
            p.setStrokeWidth(7);
            canvas.drawCircle(50,50,21,p);
            canvas.drawLine(50,12,50,22,p);canvas.drawLine(50,78,50,88,p);
            canvas.drawLine(12,50,22,50,p);canvas.drawLine(78,50,88,50,p);
            p.setStyle(Paint.Style.FILL);canvas.drawCircle(50,50,6,p);
        }else if(type==APPS){
            p.setStyle(Paint.Style.FILL);
            for(int row=0;row<3;row++)for(int col=0;col<3;col++){
                int c=((row+col)%3==0)?0xffe6f7ff:colour;
                p.setColor(c);
                float x=15+col*27,y=15+row*27;
                canvas.drawRoundRect(x,y,x+17,y+17,4,4,p);
            }
        }
        canvas.restore();
    }
}
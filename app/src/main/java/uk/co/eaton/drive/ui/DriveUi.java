package uk.co.eaton.drive.ui;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;

public final class DriveUi {
    public static final int BG = Color.rgb(7, 17, 31);
    public static final int PANEL = Color.rgb(13, 26, 43);
    public static final int PANEL2 = Color.rgb(18, 36, 58);
    public static final int BORDER = Color.rgb(33, 56, 83);
    public static final int TEXT = Color.rgb(238, 246, 251);
    public static final int MUTED = Color.rgb(145, 168, 188);
    public static final int TEAL = Color.rgb(46, 211, 183);
    private DriveUi() { }
    public static int dp(Context c, float amount) {
        return (int) (amount * c.getResources().getDisplayMetrics().density + .5f);
    }
    public static GradientDrawable bg(Context c, int color, int radius, boolean bordered) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        if (bordered) d.setStroke(dp(c, 1), BORDER);
        return d;
    }
    public static TextView text(Context c, String content, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(content);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); return l;
    }
    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    public static void pad(Context c, View v, int horizontal, int vertical) {
        v.setPadding(dp(c, horizontal), dp(c, vertical), dp(c, horizontal), dp(c, vertical));
    }
    public static LinearLayout.LayoutParams weighted(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }
}

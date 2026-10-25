package uk.co.eaton.drive.routing;

import java.util.Locale;

/** Human-readable, display-only navigation banner text, with a safe no-hints state. */
public final class TurnDisplay {
    private TurnDisplay() {}
    public static String nextInstruction(NavigationSession.Update update, boolean hasTurns) {
        if (update==null) return "Waiting for GPS";
        if (update.arrived) return "Destination reached";
        if (!hasTurns) return "Follow the highlighted route — spoken turns unavailable";
        return update.display==null||update.display.isEmpty()?"Continue on route":update.display;
    }
    public static String remaining(double miles, int seconds, double totalMiles) {
        double safe=Math.max(0,miles);
        double time=seconds>0&&totalMiles>0? seconds*safe/totalMiles : safe/35.0*3600;
        int mins=Math.max(0,(int)Math.ceil(time/60d));
        return String.format(Locale.UK,"%.1f miles left  •  ~%d min",safe,mins);
    }
}

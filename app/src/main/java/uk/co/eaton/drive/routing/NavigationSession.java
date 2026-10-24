package uk.co.eaton.drive.routing;

import java.util.List;

/**
 * Lightweight GPS guidance state machine. No network. Reacts only to accurate fixes
 * near the route, announces known GPX turns and reroutes after confirmed deviation.
 */
public final class NavigationSession {
    public static final class Update {
        public final String display;
        public final String speak;
        public final boolean reroute;
        public final boolean arrived;
        Update(String display, String speak, boolean reroute, boolean arrived) {
            this.display = display; this.speak = speak; this.reroute = reroute; this.arrived = arrived;
        }
    }
    private RouteParser.Route route;
    private int progress;
    private int upcoming;
    private int announcedStage;
    private int offRouteFixes;
    private long lastRerouteMs = -120_000;
    private boolean reroutePending;
    private boolean finished;

    public NavigationSession(RouteParser.Route route) { reset(route); }
    public void reset(RouteParser.Route next) {
        route = next; progress = 0; upcoming = 0; announcedStage = 0;
        offRouteFixes = 0; reroutePending = false; finished = false;
    }
    public boolean hasTurns() { return !route.turns.isEmpty(); }

    public Update locate(RouteParser.Point gps, float accuracyM, long elapsedMs) {
        if (finished) return new Update("Arrived", null, false, true);
        if (gps == null || accuracyM <= 0 || accuracyM > 45)
            return new Update("Waiting for accurate GPS", null, false, false);
        List<RouteParser.Point> pts = route.points;
        double bestM = Double.POSITIVE_INFINITY;
        int best = progress;
        int from = Math.max(0, progress - 12);
        int to = Math.min(pts.size() - 1, progress + 450);
        for (int i = from; i <= to; i++) {
            double m = RouteParser.distanceMiles(gps, pts.get(i)) * 1609.344;
            if (m < bestM) { bestM = m; best = i; }
        }
        if (bestM > 85) {
            if (++offRouteFixes >= 3 && !reroutePending && elapsedMs - lastRerouteMs > 40_000) {
                reroutePending = true;
                lastRerouteMs = elapsedMs;
                return new Update("Off route • recalculating", "Recalculating route", true, false);
            }
            return new Update("Checking route position", null, false, false);
        }
        offRouteFixes = 0;
        progress = Math.max(progress, best);
        if (reroutePending) return new Update("Recalculating route", null, false, false);
        double destinationM = RouteParser.distanceMiles(gps, pts.get(pts.size()-1)) * 1609.344;
        if (destinationM < 35 && progress >= pts.size() - 25) {
            finished = true;
            return new Update("Destination reached", "You have arrived at your destination", false, true);
        }
        while (upcoming < route.turns.size() && route.turns.get(upcoming).routeIndex < progress - 2) {
            upcoming++;
            announcedStage = 0;
        }
        if (upcoming >= route.turns.size()) {
            return new Update(route.turns.isEmpty() ? "Route tracking • spoken turns unavailable" : "Continue to destination", null, false, false);
        }
        RouteParser.Turn turn = route.turns.get(upcoming);
        if (turn.routeIndex < progress) return new Update("Continue", null, false, false);
        double m = RouteParser.distanceMiles(gps, pts.get(progress)) * 1609.344;
        for (int i = progress; i < turn.routeIndex && i + 1 < pts.size(); i++)
            m += RouteParser.distanceMiles(pts.get(i), pts.get(i+1)) * 1609.344;
        String direction = RouteParser.describeTurn(turn.code);
        if (direction == null) return new Update("Continue", null, false, false);
        String distance = m >= 1000 ? Math.round(m / 100.0) / 10.0 + " km" : Math.round(m / 10) * 10 + " metres";
        String speech = null;
        int stage = m <= 35 ? 3 : m <= 160 ? 2 : m <= 550 ? 1 : 0;
        if (stage > announcedStage && stage > 0) {
            announcedStage = stage;
            speech = stage == 3 ? direction + " now" : "In " + distance + ", " + direction;
        }
        return new Update(direction + " in " + distance, speech, false, false);
    }
    public void failedReroute() { reroutePending = false; }
}

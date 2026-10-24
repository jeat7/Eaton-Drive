package uk.co.eaton.drive.routing;
import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationSessionTest {
    private static RouteParser.Route route() throws Exception {
        return RouteParser.parse("<gpx><trk><trkseg>"
            +"<trkpt lat='52.000' lon='-1.000'/>"
            +"<trkpt lat='52.001' lon='-1.000'/>"
            +"<trkpt lat='52.002' lon='-1.000'/>"
            +"</trkseg></trk><rte><rtept lat='52.002' lon='-1.000'><extensions>"
            +"<turn>TL</turn></extensions></rtept></rte></gpx>");
    }
    @Test public void parsesTurnHints() throws Exception {
        RouteParser.Route r = route();
        assertEquals(1,r.turns.size());
        assertEquals("Turn left",RouteParser.describeTurn(r.turns.get(0).code));
        assertEquals("At the roundabout take exit 2",RouteParser.describeTurn("RNLB-2"));
    }
    @Test public void routeDeviationRequiresThreeAccurateFixesAndCoolDown() throws Exception {
        NavigationSession n = new NavigationSession(route());
        RouteParser.Point far = new RouteParser.Point(52.03,-1.03);
        assertFalse(n.locate(far,7,60_000).reroute);
        assertFalse(n.locate(far,7,61_000).reroute);
        assertTrue(n.locate(far,7,62_000).reroute);
        n.failedReroute();
        assertFalse(n.locate(far,7,63_000).reroute);
        assertFalse(n.locate(far,100,106_000).reroute);
    }
    @Test public void doesNotRerouteOnSparseStraightRoad() throws Exception {
        RouteParser.Route sparse = RouteParser.parse("<gpx><trk><trkseg>"
            + "<trkpt lat='52.0' lon='-1.0'/>"
            + "<trkpt lat='52.01' lon='-1.0'/>"
            + "</trkseg></trk></gpx>");
        NavigationSession n = new NavigationSession(sparse);
        RouteParser.Point midway = new RouteParser.Point(52.005,-1);
        assertTrue(NavigationSession.distanceToSegmentM(midway, sparse.points.get(0), sparse.points.get(1)) < 1);
        assertFalse(n.locate(midway,6,100_000).reroute);
        assertFalse(n.locate(midway,6,101_000).reroute);
        assertFalse(n.locate(midway,6,102_000).reroute);
    }
    @Test public void makesNoClaimsWhenTurnsMissing() throws Exception {
        RouteParser.Route noTurns = RouteParser.parse("<gpx><trk><trkseg>"
        +"<trkpt lat='52.0' lon='-1.0'/><trkpt lat='52.001' lon='-1.0'/>"
        +"</trkseg></trk></gpx>");
        assertFalse(new NavigationSession(noTurns).hasTurns());
    }
}

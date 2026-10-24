package uk.co.eaton.drive.routing;

import org.junit.Test;
import static org.junit.Assert.*;

public class RouteParserTest {
    @Test public void parsesGpxAndComputesDrivingDistance() throws Exception {
        String xml = "<?xml version=\"1.0\"?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\">"
            + "<trk><trkseg><trkpt lat=\"52.48\" lon=\"-1.90\"/>"
            + "<trkpt lat=\"52.48\" lon=\"-1.89\"/></trkseg></trk></gpx>";
        RouteParser.Route result = RouteParser.parse(xml);
        assertEquals(2, result.points.size());
        assertTrue(result.miles > .3 && result.miles < .6);
    }
    @Test public void rejectsServiceErrorsAndUnsafeXml() throws Exception {
        try { RouteParser.parse("no brouter service config found for mode motorcar_fast"); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("motorcar_fast")); }
        try {
            RouteParser.parse("<!DOCTYPE gpx [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><gpx/>");
            fail("XXE must be rejected");
        } catch (Exception expected) { /* secure rejection */ }
    }
    @Test public void rejectsInvalidCoordinates() {
        try { new RouteParser.Point(91, -1); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}

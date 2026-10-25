package uk.co.eaton.drive.routing;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteOptionsTest {
    @Test public void alternativePreservesAvoidance() {
        RouteOptions options = new RouteOptions(true,true,0).alternative();
        assertEquals(1,options.alternativeIndex);
        assertTrue(options.avoidMotorways);
        assertTrue(options.avoidTolls);
        assertEquals(0,options.primary().alternativeIndex);
    }
    @Test public void rejectsUnsupportedIndexes() {
        try { new RouteOptions(false,false,2); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}

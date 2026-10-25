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
    @Test public void brouterProfileFlagsAreNumericNotBooleanWords() {
        RouteOptions enabled = new RouteOptions(true,true,0);
        assertEquals("1",enabled.motorwaysFlag());
        assertEquals("1",enabled.tollsFlag());
        RouteOptions disabled = new RouteOptions(false,false,0);
        assertEquals("0",disabled.motorwaysFlag());
        assertEquals("0",disabled.tollsFlag());
    }
    @Test public void rejectsUnsupportedIndexes() {
        try { new RouteOptions(false,false,2); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}

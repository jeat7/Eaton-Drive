package uk.co.eaton.drive.saved;
import org.junit.Test;
import static org.junit.Assert.*;
public class SavedPlacesTest {
    @Test public void coordinateGuardRejectsBadInputs(){
        assertTrue(SavedPlaces.validCoordinates(52.53,-1.89));
        assertFalse(SavedPlaces.validCoordinates(91,0));
        assertFalse(SavedPlaces.validCoordinates(0,181));
        assertFalse(SavedPlaces.validCoordinates(Double.NaN,0));
        assertFalse(SavedPlaces.validCoordinates(0,Double.POSITIVE_INFINITY));
    }
    @Test public void labelsAreSanitisedAndBounded(){
        assertEquals("Grandad Home",SavedPlaces.cleanName("  Grandad\nHome  "));
        assertEquals(60,SavedPlaces.cleanName("x".repeat(90)).length());
        try {SavedPlaces.cleanName("   ");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void exactCoordinatesSurviveStoragePrecision(){
        double lat=52.53123456789;
        assertEquals(lat,Double.longBitsToDouble(Double.doubleToLongBits(lat)),0);
    }
}

package uk.co.eaton.drive.util;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeedMathTest {
    @Test public void convertsMetresPerSecondToMilesPerHour() {
        assertEquals(0, SpeedMath.toMph(0f));
        assertEquals(22, SpeedMath.toMph(10f));
        assertEquals(60, SpeedMath.toMph(26.8224f));
    }
    @Test public void rejectsStaleGps() {
        assertTrue(SpeedMath.isFresh(20_000L, 18_000L));
        assertFalse(SpeedMath.isFresh(20_000L, 13_000L));
        assertFalse(SpeedMath.isFresh(5_000L, 6_000L));
    }
}

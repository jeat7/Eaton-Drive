package uk.co.eaton.drive.routing;
import org.junit.Test;
import static org.junit.Assert.*;
public class RoadFileNamesTest {
    @Test public void acceptsBrouterSegments() {
        assertTrue(RoadFileNames.isSegmentName("W5_N50.rd5"));
        assertTrue(RoadFileNames.isSegmentName("E0_N55.rd5"));
        assertFalse(RoadFileNames.isSegmentName("../unsafe.rd5"));
        assertFalse(RoadFileNames.isSegmentName("W5_N50.zip"));
        assertFalse(RoadFileNames.isSegmentName("W5_N50.rd5/../../foo"));
    }
}

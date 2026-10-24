package uk.co.eaton.drive.postcode;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public class PostcodeCsvTest {
    @Test public void normalisesBritishPostcodes() {
        assertEquals("B11AA",PostcodeCsv.normalise(" b1 1aa "));
        assertTrue(PostcodeCsv.plausible("B11AA"));
        assertFalse(PostcodeCsv.plausible("HELLO"));
    }
    @Test public void acceptsQuotedFields() {
        assertEquals(Arrays.asList("AB10 1AB","Some, Town","57.1","-2.1"),
            PostcodeCsv.fields("AB10 1AB,\"Some, Town\",57.1,-2.1"));
        assertEquals(1, PostcodeCsv.index(Arrays.asList("postcode","latitude","longitude"), "latitude"));
    }
}

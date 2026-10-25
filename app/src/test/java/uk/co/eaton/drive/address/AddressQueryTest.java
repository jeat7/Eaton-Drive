package uk.co.eaton.drive.address;
import org.junit.Test;
import static org.junit.Assert.*;
public class AddressQueryTest {
    @Test public void supportsStreetAndHouseNumber() {
        assertEquals("14* AND high* AND street*",AddressQuery.matchExpression("14 High Street"));
        assertEquals("king* AND s* AND road*",AddressQuery.matchExpression("King's Road"));
    }
    @Test public void rejectsFTSSyntaxInjection() {
        assertEquals("a* AND drop* AND table*",AddressQuery.matchExpression("a' DROP TABLE"));
        assertEquals("",AddressQuery.matchExpression("*'"));
    }
}

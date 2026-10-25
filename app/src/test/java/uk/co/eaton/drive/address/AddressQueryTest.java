package uk.co.eaton.drive.address;

import org.junit.Test;
import static org.junit.Assert.*;

public class AddressQueryTest {
    @Test public void exactHouseAndRoadAreMatchedInSeparateColumns() {
        assertEquals("house:218 AND name:kingstanding* AND name:road*",
            AddressQuery.matchExpression("218 Kingstanding Road"));
        assertEquals("218",AddressQuery.parse("218 Kingstanding Road").house);
        assertEquals("kingstanding road",AddressQuery.parse("218 Kingstanding Road").street);
        assertEquals("house:14 AND name:high* AND name:street*",
            AddressQuery.matchExpression("14 High Street"));
        assertEquals("house:218a AND name:kingstanding* AND name:road*",
            AddressQuery.matchExpression("218A Kingstanding Road"));
    }
    @Test public void ignoresOptionalLocalityInFormattedAddress() {
        assertEquals("house:218 AND name:kingstanding* AND name:road*",
            AddressQuery.matchExpression("218 Kingstanding Road, Perry Beeches, B44"));
        assertEquals("name:crayford* AND name:road*",
            AddressQuery.matchExpression("Crayford Road, Kingstanding, B44"));
    }
    @Test public void streetSearchDoesNotMatchOtherRoadsOnlyBecauseTheirLocalityMatches() {
        assertEquals("name:kingstanding* AND name:road*",
            AddressQuery.matchExpression("Kingstanding Road"));
        assertEquals("name:king* AND name:s* AND name:road*",
            AddressQuery.matchExpression("King's Road"));
    }
    @Test public void rejectsQuerySyntaxInjection() {
        assertEquals("name:a* AND name:drop* AND name:table*",
            AddressQuery.matchExpression("a' DROP TABLE"));
        assertEquals("",AddressQuery.matchExpression("*'"));
    }
}

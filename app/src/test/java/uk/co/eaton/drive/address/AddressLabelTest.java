package uk.co.eaton.drive.address;
import org.junit.Test;
import static org.junit.Assert.*;
public class AddressLabelTest {
    @Test public void includesNeighbourhoodAndDistrict() {
        assertEquals("Crayford Road, Kingstanding, B44",
            AddressLabel.format("Crayford Road","","Birmingham","Kingstanding","b44"));
        assertEquals("14 Crayford Road, Kingstanding, B44",
            AddressLabel.format("Crayford Road","14","Birmingham","Kingstanding","B44"));
    }
    @Test public void remainsCompatibleWithOldIndexes() {
        assertEquals("Crayford Road",
            AddressLabel.format("Crayford Road","","","",""));
        assertEquals("Main Street, Sutton Coldfield",
            AddressLabel.format("Main Street","","Sutton Coldfield","",""));
    }
}

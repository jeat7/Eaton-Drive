package uk.co.eaton.drive.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayRulesTest {
    @Test public void requiresOptInAndOverlayPermission(){
        assertFalse(OverlayRules.showForShortcut("radio",false,true));
        assertFalse(OverlayRules.showForShortcut("radio",true,false));
        assertTrue(OverlayRules.showForShortcut("radio",true,true));
        assertTrue(OverlayRules.showForShortcut("music",true,true));
    }
    @Test public void neverStartAutomaticallyForPhoneOrOtherApps(){
        assertFalse(OverlayRules.showForShortcut("phone",true,true));
        assertFalse(OverlayRules.showForShortcut("navigation",true,true));
        assertFalse(OverlayRules.showForShortcut(null,true,true));
    }
}

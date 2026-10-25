package uk.co.eaton.drive.routing;
import org.junit.Test;
import static org.junit.Assert.*;
public class TurnDisplayTest {
    @Test public void doesNotInventSpokenDirections(){
        NavigationSession.Update u=new NavigationSession.Update("Route tracking • spoken turns unavailable",null,false,false);
        assertTrue(TurnDisplay.nextInstruction(u,false).contains("unavailable"));
    }
    @Test public void showsRemainingDistanceAndTime(){
        assertTrue(TurnDisplay.remaining(5.0,3600,10.0).contains("30 min"));
        assertTrue(TurnDisplay.remaining(0.0,3600,10.0).contains("0 min"));
    }
}
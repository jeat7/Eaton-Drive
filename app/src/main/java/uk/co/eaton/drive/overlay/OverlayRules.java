package uk.co.eaton.drive.overlay;

/**
 * Restricts *automatic* launch of a user-enabled overlay to the chosen
 * media shortcuts. A manual diagnostic test may be started from Settings.
 */
public final class OverlayRules {
    private OverlayRules(){}
    public static boolean showForShortcut(String shortcut,boolean enabled,boolean permitted){
        return enabled&&permitted&&("radio".equals(shortcut)||"music".equals(shortcut));
    }
}

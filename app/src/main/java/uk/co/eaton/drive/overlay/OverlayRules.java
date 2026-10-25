package uk.co.eaton.drive.overlay;

/**
 * Restricts *automatic* launch of a user-enabled overlay to the chosen
 * radio, music and phone shortcuts. A manual diagnostic test is in Settings.
 */
public final class OverlayRules {
    private OverlayRules(){}
    public static boolean showForShortcut(String shortcut,boolean enabled,boolean permitted){
        return enabled&&permitted&&("radio".equals(shortcut)||"music".equals(shortcut)||"phone".equals(shortcut));
    }
}

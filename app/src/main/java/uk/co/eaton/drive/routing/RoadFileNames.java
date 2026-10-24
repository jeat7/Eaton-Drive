package uk.co.eaton.drive.routing;

import java.util.Locale;

/** Accept only BRouter's fixed-format 5-by-5 degree segment names. */
public final class RoadFileNames {
    private RoadFileNames() { }
    public static boolean isSegmentName(String value) {
        return value != null && value.toUpperCase(Locale.ROOT)
            .matches("[EW](?:0|[1-9][0-9]{0,2})_[NS](?:0|[1-9][0-9]{0,2})\\.RD5");
    }
}

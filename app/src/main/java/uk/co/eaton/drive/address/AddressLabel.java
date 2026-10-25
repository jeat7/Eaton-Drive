package uk.co.eaton.drive.address;

/** Presentation-only formatting for offline OSM place labels. */
public final class AddressLabel {
    private AddressLabel() {}
    public static String format(String road, String house, String cityOrPlace,
                                String locality, String postcodeDistrict) {
        String title = nonblank(road);
        String unit = nonblank(house);
        if (!unit.isEmpty()) title = unit + " " + title;
        String area = nonblank(locality);
        if (area.isEmpty()) area = nonblank(cityOrPlace);
        if (!area.isEmpty() && !area.equalsIgnoreCase(nonblank(road)))
            title += ", " + area;
        String postcode = nonblank(postcodeDistrict).toUpperCase(java.util.Locale.UK);
        if (!postcode.isEmpty()) title += ", " + postcode;
        return title;
    }
    private static String nonblank(String s) {return s==null?"":s.trim();}
}

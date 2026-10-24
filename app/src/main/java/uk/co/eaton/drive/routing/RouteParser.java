package uk.co.eaton.drive.routing;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** Strict, bounded GPX route-track parser. No network, DTD, or external entity resolution. */
public final class RouteParser {
    private RouteParser() { }
    private static final int MAX_XML = 12_000_000;
    private static final int MAX_POINTS = 75_000;

    public static final class Point {
        public final double lat;
        public final double lon;
        public Point(double lat, double lon) {
            if (!Double.isFinite(lat) || !Double.isFinite(lon) ||
                lat < -90 || lat > 90 || lon < -180 || lon > 180)
                throw new IllegalArgumentException("Invalid coordinate");
            this.lat = lat;
            this.lon = lon;
        }
    }
    public static final class Route {
        public final List<Point> points;
        public final double miles;
        private Route(List<Point> points, double miles) {
            this.points = Collections.unmodifiableList(points);
            this.miles = miles;
        }
    }
    public static Route parse(String source) throws Exception {
        if (source == null || source.isEmpty()) throw new IllegalArgumentException("No route response");
        if (source.length() > MAX_XML) throw new IllegalArgumentException("Route is too large for this device");
        String trimmed = source.trim();
        if (!trimmed.startsWith("<")) {
            String error = trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
            throw new IllegalArgumentException(error);
        }
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        final ArrayList<Point> points = new ArrayList<>();
        final ArrayList<Point> fallback = new ArrayList<>();
        final boolean[] gpx = { false };
        factory.newSAXParser().parse(new InputSource(new StringReader(trimmed)), new DefaultHandler() {
            @Override public void startElement(String uri, String local, String qualified, Attributes attrs) {
                String name = local == null || local.isEmpty() ? qualified : local;
                if ("gpx".equals(name)) gpx[0] = true;
                if (!"trkpt".equals(name) && !"rtept".equals(name)) return;
                ArrayList<Point> target = "trkpt".equals(name) ? points : fallback;
                if (target.size() >= MAX_POINTS) throw new IllegalArgumentException("Route has too many points");
                String lat = attrs.getValue("lat"), lon = attrs.getValue("lon");
                if (lat == null || lon == null) throw new IllegalArgumentException("Malformed GPX point");
                target.add(new Point(Double.parseDouble(lat), Double.parseDouble(lon)));
            }
        });
        if (!gpx[0]) throw new IllegalArgumentException("Response is not GPX");
        ArrayList<Point> track = points.size() >= 2 ? points : fallback;
        if (track.size() < 2) throw new IllegalArgumentException("Route contains fewer than two points");
        double total = 0;
        for (int i = 1; i < track.size(); i++)
            total += distanceMiles(track.get(i - 1), track.get(i));
        return new Route(new ArrayList<>(track), total);
    }
    public static double distanceMiles(Point a, Point b) {
        final double phiA = Math.toRadians(a.lat), phiB = Math.toRadians(b.lat);
        final double dPhi = phiB - phiA, dLambda = Math.toRadians(b.lon - a.lon);
        double h = Math.pow(Math.sin(dPhi / 2), 2) +
            Math.cos(phiA) * Math.cos(phiB) * Math.pow(Math.sin(dLambda / 2), 2);
        return 3958.7613 * 2 * Math.asin(Math.min(1d, Math.sqrt(h)));
    }
}

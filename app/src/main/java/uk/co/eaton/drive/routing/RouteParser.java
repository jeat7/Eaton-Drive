package uk.co.eaton.drive.routing;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** Streaming GPX parser with optional BRouter OsmAnd-format turn hints. */
public final class RouteParser {
    private RouteParser() { }
    private static final int MAX_XML = 12_000_000;
    private static final int MAX_POINTS = 75_000;

    public static final class Point {
        public final double lat, lon;
        public Point(double lat, double lon) {
            if (!Double.isFinite(lat) || !Double.isFinite(lon) ||
                lat < -90 || lat > 90 || lon < -180 || lon > 180)
                throw new IllegalArgumentException("Invalid coordinate");
            this.lat = lat; this.lon = lon;
        }
    }
    public static final class Turn {
        public final Point point;
        public final String code;
        public final int routeIndex;
        Turn(Point point, String code, int routeIndex) {
            this.point = point; this.code = code; this.routeIndex = routeIndex;
        }
    }
    private static final class Hint {
        final Point point; String code = "";
        Hint(Point point) { this.point = point; }
    }
    public static final class Route {
        public final List<Point> points;
        public final List<Turn> turns;
        public final double miles;
        Route(List<Point> points, List<Turn> turns, double miles) {
            this.points = Collections.unmodifiableList(points);
            this.turns = Collections.unmodifiableList(turns);
            this.miles = miles;
        }
    }
    public static Route parse(String source) throws Exception {
        if (source == null || source.isEmpty()) throw new IllegalArgumentException("No route response");
        if (source.length() > MAX_XML) throw new IllegalArgumentException("Route is too large for this device");
        String trimmed = source.trim();
        if (!trimmed.startsWith("<")) {
            throw new IllegalArgumentException(trimmed.length() > 160 ? trimmed.substring(0,160) : trimmed);
        }
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        ArrayList<Point> track = new ArrayList<>();
        ArrayList<Point> fallback = new ArrayList<>();
        ArrayList<Hint> rawTurns = new ArrayList<>();
        final boolean[] isGpx = { false };
        factory.newSAXParser().parse(new InputSource(new StringReader(trimmed)), new DefaultHandler() {
            Hint pending;
            boolean readingTurn;
            StringBuilder turnText = new StringBuilder();
            @Override public void startElement(String uri, String local, String qualified, Attributes attrs) {
                String name = local == null || local.isEmpty() ? qualified : local;
                if ("gpx".equals(name)) isGpx[0] = true;
                if ("trkpt".equals(name) || "rtept".equals(name)) {
                    if (track.size() + fallback.size() >= MAX_POINTS)
                        throw new IllegalArgumentException("Route contains too many points");
                    String lat = attrs.getValue("lat"), lon = attrs.getValue("lon");
                    if (lat == null || lon == null) throw new IllegalArgumentException("Malformed GPX coordinate");
                    Point p = new Point(Double.parseDouble(lat), Double.parseDouble(lon));
                    if ("trkpt".equals(name)) track.add(p);
                    else { fallback.add(p); pending = new Hint(p); }
                } else if ("turn".equals(name) && pending != null) {
                    turnText.setLength(0); readingTurn = true;
                }
            }
            @Override public void characters(char[] chars, int off, int length) {
                if (readingTurn && turnText.length() < 40) turnText.append(chars, off, Math.min(length, 40-turnText.length()));
            }
            @Override public void endElement(String uri, String local, String qualified) {
                String name = local == null || local.isEmpty() ? qualified : local;
                if ("turn".equals(name) && readingTurn && pending != null) {
                    pending.code = turnText.toString().trim();
                    readingTurn = false;
                } else if ("rtept".equals(name) && pending != null) {
                    if (describeTurn(pending.code) != null) rawTurns.add(pending);
                    pending = null; readingTurn = false;
                }
            }
        });
        if (!isGpx[0]) throw new IllegalArgumentException("Not a GPX route");
        ArrayList<Point> points = track.size() >= 2 ? track : fallback;
        if (points.size() < 2) throw new IllegalArgumentException("Route contains fewer than two points");
        double miles = 0;
        for (int i = 1; i < points.size(); i++) miles += distanceMiles(points.get(i-1), points.get(i));
        ArrayList<Turn> turns = new ArrayList<>();
        int previous = 0;
        for (Hint hint : rawTurns) {
            int best = previous;
            double min = Double.POSITIVE_INFINITY;
            // BRouter OsmAnd-format routepoints are in track order; bounded forward scan.
            for (int i = previous; i < points.size() && i <= previous + 5000; i++) {
                double delta = distanceMiles(points.get(i), hint.point);
                if (delta < min) { min = delta; best = i; }
                if (delta < .003) break;
            }
            if (min < .08) {
                turns.add(new Turn(hint.point, hint.code, best));
                previous = best;
            }
        }
        return new Route(new ArrayList<>(points), turns, miles);
    }
    public static String describeTurn(String code) {
        if (code == null || code.isEmpty()) return null;
        String[] detail = code.toUpperCase(java.util.Locale.UK).split("-",2);
        String direction = detail[0];
        if (direction.startsWith("RND") || direction.startsWith("RNL")) {
            if (detail.length == 2 && detail[1].matches("[0-9]{1,2}"))
                return "At the roundabout take exit " + Integer.parseInt(detail[1]);
            return "At the roundabout follow the signs";
        }
        switch(direction) {
            case "TL": return "Turn left";
            case "TR": return "Turn right";
            case "TSLL": return "Turn slightly left";
            case "TSLR": return "Turn slightly right";
            case "TSHL": return "Turn sharply left";
            case "TSHR": return "Turn sharply right";
            case "KL": return "Keep left";
            case "KR": return "Keep right";
            case "TU": case "TLU": case "TRU": return "Make a U-turn where permitted";
            default: return null;
        }
    }
    public static double distanceMiles(Point a, Point b) {
        final double phiA = Math.toRadians(a.lat), phiB = Math.toRadians(b.lat);
        final double dPhi = phiB - phiA, dLambda = Math.toRadians(b.lon - a.lon);
        double h = Math.pow(Math.sin(dPhi / 2), 2) +
            Math.cos(phiA) * Math.cos(phiB) * Math.pow(Math.sin(dLambda / 2), 2);
        return 3958.7613 * 2 * Math.asin(Math.min(1d, Math.sqrt(h)));
    }
}

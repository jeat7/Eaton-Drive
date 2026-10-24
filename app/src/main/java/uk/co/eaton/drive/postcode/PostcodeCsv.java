package uk.co.eaton.drive.postcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses pre-geocoded postcode CSV rows: postcode,latitude,longitude. */
public final class PostcodeCsv {
    private PostcodeCsv() {}
    public static String normalise(String postcode) {
        if (postcode == null) return "";
        return postcode.toUpperCase(Locale.UK).replaceAll("[^A-Z0-9]", "");
    }
    public static List<String> fields(String line) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"'); i++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                out.add(field.toString().trim()); field.setLength(0);
            } else field.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("Unclosed quote in postcode CSV");
        out.add(field.toString().trim());
        return out;
    }
    public static int index(List<String> header, String... names) {
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).replace("\uFEFF", "").trim().toLowerCase(Locale.UK);
            for (String n : names) if (h.equals(n)) return i;
        }
        return -1;
    }
    public static boolean plausible(String code) {
        return code.matches("[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}") || code.equals("GIR0AA");
    }
}

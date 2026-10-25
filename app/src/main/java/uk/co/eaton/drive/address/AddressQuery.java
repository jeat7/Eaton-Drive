package uk.co.eaton.drive.address;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** Safe tokenised FTS4 match; never execute raw user-supplied SQLite syntax. */
public final class AddressQuery {
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private AddressQuery() { }
    public static String matchExpression(String query) {
        if (query == null) return "";
        Matcher m = TOKEN.matcher(query.toLowerCase(Locale.UK));
        ArrayList<String> parts = new ArrayList<>();
        while (m.find() && parts.size()<7) {
            String word=m.group();
            if (word.length()>40) word=word.substring(0,40);
            parts.add(word+"*");
        }
        return String.join(" AND ",parts);
    }
}

package uk.co.eaton.drive.address;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build column-scoped FTS4 queries. An address like "218 Kingstanding Road"
 * must match house=218 AND name="Kingstanding Road"; it must not find
 * unrelated numbered roads in a place named Kingstanding.
 */
public final class AddressQuery {
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final Pattern LEADING_HOUSE = Pattern.compile(
        "^([0-9]{1,5}[A-Za-z]?(?:[-/][0-9]{1,5}[A-Za-z]?)?)\\s+(.+)$");
    private AddressQuery() { }

    public static final class Parts {
        public final String house;
        public final String street;
        Parts(String house, String street) {
            this.house=house;
            this.street=street;
        }
        public boolean hasHouse() {return !house.isEmpty();}
    }

    public static Parts parse(String raw) {
        if(raw==null)return new Parts("","");
        // The first comma-delimited field contains the street address. Later
        // locality/district labels are display context, not part of the road name.
        String input=raw.trim();
        int comma=input.indexOf(',');
        if(comma>=0)input=input.substring(0,comma).trim();
        Matcher match=LEADING_HOUSE.matcher(input);
        if(match.matches())
            return new Parts(match.group(1).toLowerCase(Locale.UK),
                match.group(2).trim().toLowerCase(Locale.UK));
        return new Parts("",input.toLowerCase(Locale.UK));
    }

    /** Safe FTS4 query: only the street-name column and optional house column. */
    public static String matchExpression(String query) {
        Parts parsed=parse(query);
        ArrayList<String> parts=new ArrayList<>();
        if(parsed.hasHouse()){
            String house=parsed.house;
            // Houses such as 218A are single FTS tokens; ranges/slashes are
            // validated through fallback SQL rather than broadened matches.
            if(house.matches("[a-z0-9]+"))
                parts.add("house:"+house);
        }
        Matcher matcher=TOKEN.matcher(parsed.street);
        while(matcher.find()&&parts.size()<9){
            String word=matcher.group();
            if(word.length()>40)word=word.substring(0,40);
            parts.add("name:"+word+"*");
        }
        return String.join(" AND ",parts);
    }

    /** Input for a secondary exact-house lookup if FTS cannot resolve it. */
    public static String streetText(String query) {return parse(query).street;}
}

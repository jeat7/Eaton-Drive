package uk.co.eaton.drive.saved;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small fully-offline saved destinations store: one Home plus up to 24 favourites.
 * Uses one SharedPreferences transaction per change; independent of map/road imports.
 */
public final class SavedPlaces {
    private static final String FILE="eaton_saved_destinations";
    public static final int MAX_FAVOURITES=24;
    public static final class Place {
        public final String name;
        public final double lat, lon;
        public Place(String name,double lat,double lon) {
            if(!validCoordinates(lat,lon))throw new IllegalArgumentException("Invalid coordinates");
            this.name=cleanName(name);
            this.lat=lat;
            this.lon=lon;
        }
    }
    private final SharedPreferences prefs;
    public SavedPlaces(Context context) {
        prefs=context.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE);
    }
    public static boolean validCoordinates(double lat,double lon) {
        return Double.isFinite(lat)&&Double.isFinite(lon)
            && lat>=-90&&lat<=90&&lon>=-180&&lon<=180;
    }
    public static String cleanName(String name) {
        String value=name==null?"":name.replace('\n',' ').replace('\r',' ').trim();
        if(value.length()>60)value=value.substring(0,60).trim();
        if(value.isEmpty())throw new IllegalArgumentException("Enter a name for this place");
        return value;
    }
    private static long bits(double v){return Double.doubleToLongBits(v);}
    private Place get(String prefix) {
        if(!prefs.getBoolean(prefix+"_exists",false))return null;
        try {
            return new Place(prefs.getString(prefix+"_name",""),
                Double.longBitsToDouble(prefs.getLong(prefix+"_lat",0)),
                Double.longBitsToDouble(prefs.getLong(prefix+"_lon",0)));
        } catch(RuntimeException error){return null;}
    }
    private void write(SharedPreferences.Editor e,String prefix,Place place){
        e.putBoolean(prefix+"_exists",true)
            .putString(prefix+"_name",place.name)
            .putLong(prefix+"_lat",bits(place.lat))
            .putLong(prefix+"_lon",bits(place.lon));
    }
    private void erase(SharedPreferences.Editor e,String prefix){
        e.remove(prefix+"_exists").remove(prefix+"_name")
            .remove(prefix+"_lat").remove(prefix+"_lon");
    }
    public Place home(){return get("home");}
    public boolean setHome(Place place){
        if(place==null)return false;
        SharedPreferences.Editor e=prefs.edit();
        write(e,"home",place);
        return e.commit();
    }
    public boolean clearHome(){
        SharedPreferences.Editor e=prefs.edit();
        erase(e,"home");return e.commit();
    }
    public List<Place> favourites(){
        int count=Math.max(0,Math.min(MAX_FAVOURITES,prefs.getInt("favourite_count",0)));
        List<Place> result=new ArrayList<>();
        for(int i=0;i<count;i++){
            Place p=get("favourite_"+i);
            if(p!=null)result.add(p);
        }
        return Collections.unmodifiableList(result);
    }
    public boolean addFavourite(Place place){
        if(place==null)return false;
        List<Place> old=favourites();
        if(old.size()>=MAX_FAVOURITES)return false;
        for(Place p:old)
            if(Math.abs(p.lat-place.lat)<0.000001&&Math.abs(p.lon-place.lon)<0.000001)
                return false;
        SharedPreferences.Editor e=prefs.edit();
        write(e,"favourite_"+old.size(),place);
        e.putInt("favourite_count",old.size()+1);
        return e.commit();
    }
    public boolean renameFavourite(int index,String name){
        List<Place> old=favourites();
        if(index<0||index>=old.size())return false;
        Place p=old.get(index);
        SharedPreferences.Editor e=prefs.edit();
        write(e,"favourite_"+index,new Place(name,p.lat,p.lon));
        return e.commit();
    }
    public boolean deleteFavourite(int index){
        List<Place> old=favourites();
        if(index<0||index>=old.size())return false;
        SharedPreferences.Editor e=prefs.edit();
        for(int i=index;i<old.size()-1;i++)
            write(e,"favourite_"+i,old.get(i+1));
        erase(e,"favourite_"+(old.size()-1));
        e.putInt("favourite_count",old.size()-1);
        return e.commit();
    }
}

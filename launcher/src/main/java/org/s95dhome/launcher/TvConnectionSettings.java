package org.s95dhome.launcher;
import android.content.*;
final class TvConnectionSettings {
    private final SharedPreferences prefs;
    TvConnectionSettings(Context c){prefs=c.getSharedPreferences("tv-connection",Context.MODE_PRIVATE);}
    String host(){return prefs.getString("host","");}
    String pin(){return prefs.getString("pin","");}
    boolean ready(){try{TvEndpoint.localAddress(host());return pin().matches("[A-F0-9]{64}");}catch(Exception e){return false;}}
    void save(String host,String pin){if(!pin.matches("[A-F0-9]{64}"))throw new IllegalArgumentException("Invalid certificate");prefs.edit().putString("host",TvEndpoint.localAddress(host)).putString("pin",pin).apply();}
    void clear(){prefs.edit().clear().apply();}
}

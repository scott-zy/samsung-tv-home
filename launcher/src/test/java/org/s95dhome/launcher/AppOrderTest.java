package org.s95dhome.launcher;
import java.util.*;
public class AppOrderTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static List<String> list(String... values){return Arrays.asList(values);}
    static Set<String> set(String... values){return new LinkedHashSet<>(list(values));}
    public static void main(String[] args)throws Exception{
        List<String> old=list("tv.bili","tv.missing","tv.music");
        check(AppOrder.parse(AppOrder.encode(old)).equals(old),"order survives saving/reload");
        check(AppOrder.parse("[]").isEmpty(),"empty choice must remain empty, never reset defaults");
        check(AppOrder.replace(list("tv.bili","tv.airplay","tv.music"),"tv.airplay","tv.custom").equals(list("tv.bili","tv.custom","tv.music")),"custom receiver preserves shortcut position");
        check(AppOrder.replace(list("tv.bili"),"tv.airplay","tv.custom").equals(list("tv.bili")),"hidden receiver is not added by migration");
        check(AppOrder.replace(list("tv.airplay","tv.custom"),"tv.airplay","tv.custom").equals(list("tv.custom")),"migration has no duplicate shortcuts");
        check(AppOrder.parse("[\"tv.bili\",\"tv.bili\"]").equals(list("tv.bili")),"deduplicate identities");
        check(AppOrder.select(old,set("tv.bili","tv.music","tv.airplay"),set("tv.music","tv.airplay")).equals(list("tv.missing","tv.music","tv.airplay")),"hide installed app, preserve absent app, append selected app");
        check(AppOrder.reorder(old,list("tv.music","tv.bili")).equals(list("tv.music","tv.missing","tv.bili")),"sort without losing unavailable saved app");
        check(AppOrder.select(old,set("tv.bili","tv.music"),set()).equals(list("tv.missing")),"hide all visible apps");
        boolean rejected=false;try{AppOrder.parse("[\"javascript:alert(1)\"]");}catch(Exception e){rejected=true;}check(rejected,"reject non-package launch identity");
        rejected=false;try{AppOrder.parse("broken");}catch(Exception e){rejected=true;}check(rejected,"corrupt storage reported");
        System.out.println("PASS: persistent order; explicit empty selection; duplicates; hide/add; absent/reinstalled identities; reorder; invalid identities and corrupt JSON");
    }
}

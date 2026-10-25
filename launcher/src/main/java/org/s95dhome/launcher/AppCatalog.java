package org.s95dhome.launcher;
import android.content.*;
import android.content.pm.*;
import java.util.*;

/** Launchable Android apps only; no shell commands or raw component names from WebView. */
final class AppCatalog {
    static final String AIRPLAY="io.github.jqssun.airplay";
    static final String TV_AIRPLAY="org.s95dhome.airplay";
    static final class Entry {
        final String id,title,art;
        final Intent launch;
        Entry(String id,String title,String art,Intent launch){this.id=id;this.title=title;this.art=art;this.launch=launch;}
    }
    private final Context context;
    final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    AppCatalog(Context context){this.context=context;}
    void scan(){
        PackageManager pm=context.getPackageManager();Map<String,Entry> found=new HashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            for(ResolveInfo result:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                ActivityInfo a=result.activityInfo;
                if(a==null||!a.enabled||!a.exported||!a.applicationInfo.enabled||found.containsKey(a.packageName))continue;
                if(a.packageName.equals(context.getPackageName())||a.packageName.equals("com.samsung.tv.dummylauncher"))continue;
                if(a.permission!=null&&context.checkSelfPermission(a.permission)!=PackageManager.PERMISSION_GRANTED)continue;
                String title=String.valueOf(result.loadLabel(pm));
                String art=a.packageName.equals("com.xiaodianshi.tv.yst")||title.matches("(?i).*(bilibili|哔哩|小电视).*" )?"bili":
                    a.packageName.startsWith("com.netease.cloudmusic")||title.contains("网易云")?"music":a.packageName.equals(AIRPLAY)||a.packageName.equals(TV_AIRPLAY)?"airplay":"generic";
                Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(new ComponentName(a.packageName,a.name));
                found.put(a.packageName,new Entry(a.packageName,art.equals("bili")?"Bilibili":title,art,launch));
            }
        }
        if(found.containsKey(TV_AIRPLAY))found.remove(AIRPLAY);
        List<Entry> sorted=new ArrayList<>(found.values());
        Collections.sort(sorted,(a,b)->{int c=a.title.compareToIgnoreCase(b.title);return c==0?a.id.compareTo(b.id):c;});
        entries.clear();for(Entry entry:sorted)entries.put(entry.id,entry);
    }
    List<String> initialOrder(){
        List<String> out=new ArrayList<>();
        for(String art:new String[]{"bili","music"})for(Entry entry:entries.values())if(entry.art.equals(art)){out.add(entry.id);break;}
        // User explicitly requested AirPlay: this shortcut appears once that APK is installed.
        out.add(entries.containsKey(TV_AIRPLAY)?TV_AIRPLAY:AIRPLAY);return out;
    }
}

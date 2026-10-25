package org.s95dhome.launcher;
import android.app.*;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.widget.*;
import java.util.*;

final class AppManagement {
    interface Listener {void saved();}
    private final Activity activity;
    private final SharedPreferences prefs;
    private final AppCatalog catalog;
    private final Listener listener;
    AppManagement(Activity activity,SharedPreferences prefs,AppCatalog catalog,Listener listener){this.activity=activity;this.prefs=prefs;this.catalog=catalog;this.listener=listener;}
    List<String> order(){
        if(!prefs.contains("appOrder"))prefs.edit().putString("appOrder",AppOrder.encode(catalog.initialOrder())).apply();
        try{
            List<String> order=AppOrder.parse(prefs.getString("appOrder","[]"));
            if(catalog.entries.containsKey(AppCatalog.TV_AIRPLAY)&&order.contains(AppCatalog.AIRPLAY)){
                List<String> migrated=AppOrder.replace(order,AppCatalog.AIRPLAY,AppCatalog.TV_AIRPLAY);
                prefs.edit().putString("appOrder",AppOrder.encode(migrated)).apply();return migrated;
            }
            return order;
        }
        catch(Exception e){return catalog.initialOrder();}
    }
    void save(List<String> order){prefs.edit().putString("appOrder",AppOrder.encode(order)).apply();listener.saved();}
    void show(){
        new AlertDialog.Builder(activity).setTitle("应用管理")
            .setItems(new String[]{"添加或隐藏应用","调整首页顺序"},(d,i)->{if(i==0)selection();else sorting();})
            .setNegativeButton("返回",null).show();
    }
    private void selection(){
        List<AppCatalog.Entry> entries=new ArrayList<>(catalog.entries.values());
        if(entries.isEmpty()){Toast.makeText(activity,"未找到可启动的本机应用",Toast.LENGTH_LONG).show();return;}
        List<String> old=order();String[] labels=new String[entries.size()];boolean[] checked=new boolean[entries.size()];
        for(int i=0;i<entries.size();i++){labels[i]=entries.get(i).title;checked[i]=old.contains(entries.get(i).id);}
        new AlertDialog.Builder(activity).setTitle("选择首页应用（取消勾选只隐藏）")
            .setMultiChoiceItems(labels,checked,(d,i,value)->checked[i]=value)
            .setPositiveButton("保存",(d,which)->{
                Set<String> installed=new HashSet<>(),selected=new LinkedHashSet<>();
                for(int i=0;i<entries.size();i++){installed.add(entries.get(i).id);if(checked[i])selected.add(entries.get(i).id);}
                save(AppOrder.select(old,installed,selected));
            }).setNegativeButton("取消",null).show();
    }
    private void sorting(){
        List<String> old=order(),visible=new ArrayList<>();
        Map<String,AppCatalog.Entry> snapshot=new LinkedHashMap<>(catalog.entries);
        for(String id:old)if(catalog.entries.containsKey(id))visible.add(id);
        if(visible.size()<2){Toast.makeText(activity,"至少显示两个应用才能排序",Toast.LENGTH_LONG).show();return;}
        final int[] moving={-1};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_single_choice,new ArrayList<String>());
        Runnable render=()->{adapter.clear();for(int i=0;i<visible.size();i++)adapter.add((moving[0]==i?"↔  ":"")+snapshot.get(visible.get(i)).title);};render.run();
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("排序：确认拿起，左右移动，确认放下")
            .setSingleChoiceItems(adapter,0,(d,which)->{moving[0]=moving[0]==which?-1:which;render.run();ListView list=((AlertDialog)d).getListView();list.setItemChecked(which,true);list.setSelection(which);})
            .setPositiveButton("保存排序",(d,which)->save(AppOrder.reorder(old,visible))).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->{
            ListView list=dialog.getListView();
            list.setOnKeyListener((v,key,event)->{
                if(moving[0]<0)return false;
                if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){
                    int next=moving[0]+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);
                    if(next>=0&&next<visible.size()){Collections.swap(visible,moving[0],next);moving[0]=next;render.run();list.setItemChecked(next,true);list.setSelection(next);}
                }
                return true;
            });
        });dialog.show();
    }
}

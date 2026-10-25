package org.s95dhome.launcher;
import java.util.*;
import org.json.JSONArray;

/** Persisted package identities; missing applications remain available for a later reinstall. */
final class AppOrder {
    static List<String> parse(String text) throws Exception {
        JSONArray data=new JSONArray(text);if(data.length()>256)throw new IllegalArgumentException("Too many apps");
        List<String> out=new ArrayList<>();
        for(int i=0;i<data.length();i++){
            String id=data.getString(i);
            if(!id.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("Invalid package");
            if(!out.contains(id))out.add(id);
        }
        return out;
    }
    static String encode(List<String> order){return new JSONArray(order).toString();}
    static List<String> replace(List<String> old,String from,String to){
        List<String> out=new ArrayList<>();
        for(String id:old){String resolved=id.equals(from)?to:id;if(!out.contains(resolved))out.add(resolved);}
        return out;
    }
    static List<String> select(List<String> old,Set<String> installed,Set<String> checked){
        List<String> out=new ArrayList<>();
        for(String id:old)if(!installed.contains(id)||checked.contains(id))if(!out.contains(id))out.add(id);
        for(String id:checked)if(installed.contains(id)&&!out.contains(id))out.add(id);
        return out;
    }
    static List<String> reorder(List<String> old,List<String> visible){
        List<String> out=new ArrayList<>(old);int at=0;
        Set<String> ids=new HashSet<>(visible);
        for(int i=0;i<out.size();i++)if(ids.contains(out.get(i)))out.set(i,visible.get(at++));
        if(at!=visible.size())throw new IllegalArgumentException("Order changed during edit");
        return out;
    }
}

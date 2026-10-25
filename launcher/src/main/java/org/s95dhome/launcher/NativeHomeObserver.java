package org.s95dhome.launcher;

import android.os.Handler;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Read-only callback used by Samsung's own service; no Vconf writes or polling. */
final class NativeHomeObserver {
    private static final String KEY="memory/appfw/current_fullscreen_appid";
    interface Listener { void status(String text); void homeShown(long receivedAt); }
    private final Handler handler;
    private final Listener listener;
    private Class<?> manager, callbackType;
    private Object callback;
    private volatile boolean closed;
    private String homeId, previous;
    NativeHomeObserver(Handler handler,Listener listener){this.handler=handler;this.listener=listener;}
    void start(){
        try {
            manager=Class.forName("android.tizen.TizenVconfManager");
            Method read=manager.getMethod("getString",String.class);
            homeId=(String)read.invoke(null,"db/appfw/home_appid");
            // Only match the verified native Samsung Home, never Android's launcher or a source.
            if(!"com.samsung.tv.csfs".equals(homeId))throw new IllegalStateException("未验证的原生首页标识");
            previous=(String)read.invoke(null,KEY);
            listener.status("原生全屏初始状态="+label(previous));
            callbackType=Class.forName("android.tizen.TizenVconfManager$VconfChangedCallback");
            callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,method,args)->{
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return args!=null&&args.length==1&&proxy==args[0];
                if(method.getName().equals("toString"))return "S95D native Home observer";
                if(!closed&&method.getName().equals("onChange")&&args!=null&&args.length==1){
                    Object node=args[0];
                    String key=(String)node.getClass().getMethod("getName").invoke(node);
                    if(KEY.equals(key)){
                        String value=(String)node.getClass().getMethod("getString").invoke(node);
                        long receivedAt=SystemClock.elapsedRealtime();
                        handler.post(()->changed(value,receivedAt));
                    }
                }
                return null;
            });
            // Firmware returns false even after adding a callback. Do not interpret it as failure.
            manager.getMethod("registerCallback",String.class,callbackType).invoke(null,KEY,callback);
            listener.status("原生全屏只读回调已登记，等待实际事件验证");
        }catch(Exception e){listener.status("原生全屏回调不可用："+e.getClass().getSimpleName());close();}
    }
    private void changed(String value,long receivedAt){
        if(closed)return;
        boolean home=homeId.equals(value)&&!homeId.equals(previous);
        listener.status("原生全屏变化："+label(previous)+" → "+label(value));
        previous=value;
        if(home)listener.homeShown(receivedAt);
    }
    private String label(String value){
        if(homeId!=null&&homeId.equals(value))return "三星首页";
        if("com.samsung.tv.android-launcher".equals(value))return "Android";
        if("org.tizen.tv-viewer".equals(value))return "电视/HDMI";
        return value==null||value.isEmpty()?"空":"其他原生应用";
    }
    void close(){
        closed=true;
        if(callback!=null)try{manager.getMethod("unregisterCallback",String.class,callbackType).invoke(null,KEY,callback);}catch(Exception ignored){}
        callback=null;
    }
}

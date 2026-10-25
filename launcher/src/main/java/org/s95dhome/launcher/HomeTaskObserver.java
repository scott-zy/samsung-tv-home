package org.s95dhome.launcher;

import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

/** Normal read-only registration on this firmware's task notification service. No task control calls. */
final class HomeTaskObserver {
    interface Listener {
        void event(int kind, String pkg, String activity, long receivedAt);
        void status(String text);
    }
    private static final String SERVICE = "android.tizen.ITizenActivityManager";
    private static final String CALLBACK = "android.tizen.ITaskStackChangedListener";
    private final Handler handler;
    private final Listener listener;
    private IBinder service;
    private boolean registered;
    private volatile boolean closed;
    private final Binder callback = new Binder() {
        { attachInterface(null, CALLBACK); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(CALLBACK); return true; }
            if (code < 1 || code > 8) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(CALLBACK);
            // Task-created may include URI/caller extras. Never read or log those.
            if (closed || (code != 1 && code != 2 && code != 8)) return true;
            String pkg = data.readString(), activity = data.readString();
            // Discard other apps' metadata immediately; retain only own/Samsung Home events.
            if (!"com.samsung.tv.dummylauncher".equals(pkg) && !"org.s95dhome.launcher".equals(pkg)) return true;
            long receivedAt = SystemClock.elapsedRealtime();
            handler.post(() -> { if (!closed) listener.event(code, pkg, activity, receivedAt); });
            return true;
        }
    };
    HomeTaskObserver(Handler handler, Listener listener) { this.handler = handler; this.listener = listener; }
    void start() {
        try {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            service = (IBinder)manager.getMethod("getService", String.class).invoke(null, "tizenactivity");
            if (service == null || !SERVICE.equals(service.getInterfaceDescriptor())) throw new IllegalStateException("任务通知接口不可用");
            transact(3); registered = true;
            listener.status("任务时序通知已订阅（仅自身与三星首页；订阅成功不代表立即返回成功）");
        } catch (Exception e) { service = null; listener.status("任务时序通知未能订阅：" + e.getClass().getSimpleName()); }
    }
    private void transact(int code) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE); data.writeStrongBinder(callback);
            if (!service.transact(code, data, reply, 0)) throw new IllegalStateException("任务通知操作未支持");
            reply.readException();
        } finally { reply.recycle(); data.recycle(); }
    }
    void close() {
        closed = true;
        if (registered && service != null) try { transact(4); } catch (Exception ignored) { }
        registered = false; service = null;
    }
}

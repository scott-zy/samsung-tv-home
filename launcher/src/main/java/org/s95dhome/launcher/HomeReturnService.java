package org.s95dhome.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Visible, user-switchable Home event service. No polling or system-setting writes. */
public class HomeReturnService extends Service {
    static final String STOP = "org.s95dhome.launcher.STOP_HOME_TRIAL";
    static final String FROM_HOME = "home_trial_event";
    static final int[] RETURN_DELAYS = {0, 80, 160, 300, 650};
    private static volatile boolean active;
    private static HomeReturnService instance;
    private final Handler handler = new Handler();
    private final HomeReturnRace race = new HomeReturnRace();
    private HomeTaskObserver taskObserver;
    private NativeHomeObserver nativeHomeObserver;
    private boolean registered;
    private long lastHome = -10000;
    private SharedPreferences prefs;

    static SharedPreferences store(Context c) { return c.getSharedPreferences("home-return", MODE_PRIVATE); }
    static void restoreCompatibleTiming(Context c) {
        SharedPreferences p = store(c);
        // Preserve every explicit speed, including experimental 0/80ms. Never silently replace it.
        if (!p.contains("returnDelay")) p.edit().putInt("returnDelay", 160).apply();
    }
    static int delay(Context c) {
        int selected = store(c).getInt("returnDelay", 160);
        for (int allowed : RETURN_DELAYS) if (selected == allowed) return selected;
        return 160;
    }
    static String delayLabel(Context c) { return delay(c) == 0 ? "立即返回" : "等待 " + delay(c) + "ms"; }
    static synchronized void log(Context c, String value) {
        SharedPreferences p = store(c);
        String text = p.getString("log", "") + new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date()) + " " + value + "\n";
        if (text.length() > 12000) text = text.substring(text.indexOf('\n', text.length() - 12000) + 1);
        p.edit().putString("log", text).apply();
    }
    static boolean enabled(Context c) {
        return store(c).getBoolean("enabled", true);
    }
    static boolean running() { return active; }
    static void userAction() {
        if (instance != null) {
            instance.race.cancel();
            log(instance, "主动操作：取消上一轮 Home 返回与去重");
        }
    }
    static void traceActivity(Context c, String text) {
        HomeReturnService current = instance;
        long now = SystemClock.elapsedRealtime();
        log(c, text + (current != null && current.race.observing(now)
            ? "；距 Home " + (now - current.race.eventTime()) + "ms" : "；Home 观察窗口外"));
    }
    static void ensureRunning(Context c) {
        restoreCompatibleTiming(c);
        if (!enabled(c) || active) return;
        Intent intent = new Intent(c, HomeReturnService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(intent); else c.startService(intent);
    }
    static void startTrial(Context c) {
        store(c).edit().putBoolean("enabled", true).putLong("pauseUntil", 0).apply();
        try {
            ensureRunning(c);
        } catch (RuntimeException e) { log(c, "启动服务失败：" + e); throw e; }
    }
    static void stopTrial(Context c) {
        store(c).edit().putBoolean("enabled", false).apply(); c.stopService(new Intent(c, HomeReturnService.class)); log(c, "用户关闭 Home 返回");
    }
    static String summary(Context c) {
        return "Home 返回：" + (enabled(c) ? "已开启，无五分钟限制" : "已关闭")
            + "\n事件服务：" + (active ? "正在运行" : "尚未运行 / 已停止")
            + "\n返回速度：" + delayLabel(c)
            + "\n悬浮窗权限实际状态：" + Settings.canDrawOverlays(c)
            + "\n本应用不绘制悬浮窗；通过系统正常启动接口请求返回。\n\n"
            + store(c).getString("log", "尚无记录");
    }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) return;
            String reason = intent.getStringExtra("reason");
            if (!"homekey".equals(reason)) {
                log(HomeReturnService.this, "关闭系统对话框事件；reason=" + (reason!=null&&reason.matches("[A-Za-z0-9_-]{1,40}")?reason:"other-or-empty"));
                return;
            }
            long now = SystemClock.elapsedRealtime();
            log(HomeReturnService.this, "收到 Home 事件；enabled=" + enabled(HomeReturnService.this)
                + "，暂停剩余=" + Math.max(0, prefs.getLong("pauseUntil", 0) - now) + "ms");
            if (!enabled(HomeReturnService.this)) { log(HomeReturnService.this,"忽略 Home：用户已关闭返回"); return; }
            if (now < prefs.getLong("pauseUntil", 0)) { log(HomeReturnService.this,"忽略 Home：三星首页暂停期间"); return; }
            if (race.duplicateHome(lastHome,now)) { log(HomeReturnService.this,"忽略 Home：同轮事件去重，间隔="+(now-lastHome)+"ms"); return; }
            beginReturn(now);
        }
    };
    private void beginReturn(long now) {
        lastHome=now;
        int wait=delay(this);race.begin(now,wait);
        if(wait==0)returnHome(now);else handler.postDelayed(()->returnHome(now),wait);
    }
    private void nativeHomeShown(long receivedAt) {
        long now=SystemClock.elapsedRealtime();
        if(!enabled(this)||now<prefs.getLong("pauseUntil",0)){log(this,"原生首页事件：返回已关闭或暂停，不接管");return;}
        if(now-receivedAt>6000){log(this,"原生首页事件已过期，忽略");return;}
        if(race.observing(now)){
            if(race.shouldCorrect("com.samsung.tv.dummylauncher","com.samsung.tv.dummylauncher.MainActivity",receivedAt,now)){
                log(this,"原生三星首页已出现：事件驱动纠正返回");returnHome(race.eventTime());
            }else log(this,"原生首页事件已合并或本轮纠正次数已满");
        }else{
            log(this,"原生三星首页已出现：启动返回（无需 Android Home 广播）");beginReturn(now);
        }
    }
    private void returnHome(long eventTime) {
        if (!race.active(eventTime) || !enabled(this) || SystemClock.elapsedRealtime() < prefs.getLong("pauseUntil", 0)) return;
        Intent home = new Intent(this, HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(FROM_HOME, eventTime);
        try {
            race.submitted(eventTime, SystemClock.elapsedRealtime());
            startActivity(home);
            log(this, "已提交返回请求；距事件 " + (SystemClock.elapsedRealtime() - eventTime) + "ms（此行不代表画面成功）");
        }
        catch (RuntimeException e) { race.cancel(); log(this, "系统拒绝返回：" + e); }
    }
    @Override public void onCreate() {
        super.onCreate(); restoreCompatibleTiming(this); prefs = store(this);
        // An elapsedRealtime deadline from an earlier boot must not suspend Home indefinitely.
        prefs.edit().putLong("pauseUntil", 0).apply();
        String channel = "home-key-return";
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(channel, "Home 返回", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, HomeActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, HomeReturnService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        startForeground(953, b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("S95D 极简首页 · Home 返回")
            .setContentText("按 Home 返回极简首页；可随时关闭").setOngoing(true).setShowWhen(false).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭 Home 返回", stop).build());
        try {
            registerReceiver(receiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)); registered = true; active = true;
            instance = this;
            log(this, "服务已启动，Home 事件接收器已注册；overlay=" + Settings.canDrawOverlays(this));
            taskObserver = new HomeTaskObserver(handler, new HomeTaskObserver.Listener() {
                public void status(String text) { log(HomeReturnService.this, text); }
                public void event(int kind, String pkg, String activity, long receivedAt) {
                    long now = SystemClock.elapsedRealtime();
                    String name = activity == null ? "unknown" : activity.substring(activity.lastIndexOf('.') + 1);
                    log(HomeReturnService.this, "任务通知 " + kind + " " + name + "；观察中="+race.observing(now)+"；距 Home " + (race.eventTime()<0?"无事件":(receivedAt - race.eventTime())+"ms"));
                    if (!race.observing(now)) return;
                    // Transition-start is too early: wait for Samsung's actual drawn event.
                    if (kind==2 && race.shouldCorrect(pkg, activity, receivedAt, now)) {
                        log(HomeReturnService.this, "观察到三星首页绘制：事件驱动纠正（最多两次，六秒内）");
                        returnHome(race.eventTime());
                    }
                }
            });
            taskObserver.start();
            nativeHomeObserver=new NativeHomeObserver(handler,new NativeHomeObserver.Listener(){
                public void status(String text){log(HomeReturnService.this,text);}
                public void homeShown(long receivedAt){nativeHomeShown(receivedAt);}
            });
            nativeHomeObserver.start();
        } catch (RuntimeException e) { log(this, "无法注册 Home 事件：" + e); stopSelf(); }
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) prefs.edit().putBoolean("enabled", false).apply();
        if (!enabled(this)) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null); if (registered) unregisterReceiver(receiver);
        if (taskObserver != null) taskObserver.close();
        if (nativeHomeObserver != null) nativeHomeObserver.close();
        race.cancel(); if (instance == this) instance = null;
        active = false; log(this, "Home 返回服务已停止"); stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

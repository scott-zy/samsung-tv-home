package org.s95dhome.launcher;
import org.s95dhome.privacy.DiagnosticSanitizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;


import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local Arn0-style UI with explicit native app/source actions and a visible Home return service. */
public class HomeActivity extends Activity {
    private static final int ROLE_REQUEST = 20, PICK_WALLPAPER = 21;
    private AppCatalog appCatalog;
    private AppManagement appManagement;
    private boolean appUiDirty=true, packageReceiverRegistered;
    private final java.util.Map<String,String> appIcons=new java.util.HashMap<>();
    private final BroadcastReceiver packageReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){if(!closing){reloadApps();pushUiState();}}
    };
    private final Handler handler = new Handler();
    private final List<String> history = new ArrayList<String>();
    private SharedPreferences prefs;
    private TvConnectionSettings tvConnection;
    private TvConnectionSetup tvSetup;
    private boolean setupPrompted;
    private static final int EXPORT_DIAGNOSTIC=22;
    private WebView homeWeb;
    private TvInputManager inputManager;
    private boolean inputCallbackRegistered;
    private final String[] portStates = {"unknown", "unknown", "unknown", "unknown"};
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputAdded(String id) { pushUiState(); }
        @Override public void onInputRemoved(String id) { pushUiState(); }
        @Override public void onInputStateChanged(String id, int state) { pushUiState(); }
        @Override public void onInputUpdated(String id) { pushUiState(); }
    };
    private String inputNote = "尚未检测";
    private final TvInputInfo[] ports = new TvInputInfo[4];
    private TvSourceSession sourceSession;
    private TvSourceSession.Snapshot sourceSnapshot;
    private long sourceSnapshotAt;
    private int sourceGeneration;
    private boolean sourceForeground;
    private byte[] pendingDiagnostic;
    private String sourceNote = "三星输入状态尚未读取";
    // Filled only from a comparison with the real device, never guessed from enum numbers.
    private static final String VERIFIED_ACTIVE_POWER = "1", VERIFIED_CONNECTED_POWER = "2";
    private volatile boolean closing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        tvConnection=new TvConnectionSettings(this);
        tvSetup=new TvConnectionSetup(this,tvConnection,()->{stopSourceStatus();startSourceStatus();pushUiState();});
        appCatalog=new AppCatalog(this);
        appManagement=new AppManagement(this,prefs,appCatalog,()->{appUiDirty=true;pushUiState();});
        reloadApps();
        IntentFilter packages=new IntentFilter();packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
        registerReceiver(packageReceiver,packages);packageReceiverRegistered=true;
        ResolveInfo original = resolveHome();
        if (!prefs.contains("originalHome") && original != null && !getPackageName().equals(original.activityInfo.packageName)) {
            prefs.edit().putString("originalHome", new ComponentName(original.activityInfo.packageName, original.activityInfo.name).flattenToString()).apply();
        }
        note("创建：" + intentDescription(getIntent()));
        showHome();
        try { HomeReturnService.ensureRunning(this); }
        catch (RuntimeException e) { note("Home 服务启动失败：" + e); message("Home 返回服务未能启动，请在首页设置中查看记录"); }
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void note(String value) {
        history.add(new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + " " + value);
        while (history.size() > 10) history.remove(0);
    }
    private String intentDescription(Intent intent) {
        return intent == null ? "无" : intent.getAction() + " " + String.valueOf(intent.getCategories());
    }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private void immersive() { getWindow().getDecorView().setSystemUiVisibility(5894); }
    private TextView label(String value, int sp, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); return v;
    }
    private GradientDrawable surface(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(14));
        if (stroke != 0) d.setStroke(dp(2), stroke); return d;
    }
    private Button tile(String title, View.OnClickListener action) {
        Button b = new Button(this); b.setText(title); b.setTextColor(Color.WHITE); b.setTextSize(17);
        b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setBackground(surface(0x55202b38, 0)); b.setOnClickListener(action);
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(surface(focused ? 0xbb344354 : 0x55202b38, focused ? 0xffe9f2ff : 0));
            b.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(100).start();
        });
        return b;
    }
    private LinearLayout.LayoutParams margin(int width, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height); p.setMargins(0, dp(6), dp(12), dp(6)); return p;
    }

    private void showHome() {
        if (homeWeb != null) return;
        immersive();
        homeWeb = new WebView(this);
        homeWeb.setBackgroundColor(Color.BLACK);
        WebSettings webSettings = homeWeb.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setBlockNetworkLoads(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        homeWeb.setOverScrollMode(View.OVER_SCROLL_NEVER);
        homeWeb.addJavascriptInterface(new HomeBridge(), "S95D");
        homeWeb.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public void onPageFinished(WebView view, String url) {
                if ("file:///android_asset/home/index.html".equals(url)) {
                    appUiDirty=true;pushUiState();applyWebWallpaper();
                    if(sourceForeground&&!setupPrompted&&!tvConnection.ready()){setupPrompted=true;tvSetup.startFirstUse();}
                }
            }
        });
        setContentView(homeWeb);
        homeWeb.loadUrl("file:///android_asset/home/index.html");
        homeWeb.requestFocus();
        inputManager = (TvInputManager)getSystemService(TV_INPUT_SERVICE);
        try { if (inputManager != null) { inputManager.registerCallback(inputCallback, handler); inputCallbackRegistered = true; } }
        catch (RuntimeException e) { note("输入状态回调不可用：" + e.getClass().getSimpleName()); }
    }
    private boolean trustedPage() { return homeWeb != null && "file:///android_asset/home/index.html".equals(homeWeb.getUrl()); }
    private void ui(String script) { if (trustedPage() && !closing) homeWeb.evaluateJavascript(script, null); }
    public final class HomeBridge {
        @JavascriptInterface public void launchApp(String key) { runOnUiThread(() -> { if (trustedPage()) launchSelectedApp(key); }); }
        @JavascriptInterface public void openHdmi(int port) { runOnUiThread(() -> { if (trustedPage() && port >= 1 && port <= 4) openInput(port-1); }); }
        @JavascriptInterface public void openSystemSettings() { runOnUiThread(() -> { if (trustedPage()) settings(); }); }
        @JavascriptInterface public void launcherSettings() { runOnUiThread(() -> { if (trustedPage()) settings(); }); }
        @JavascriptInterface public void manageApps() { runOnUiThread(() -> { if(trustedPage()){HomeReturnService.userAction();reloadApps();appManagement.show();} }); }
    }
    private void reloadApps() {
        try { appCatalog.scan();appIcons.clear();appUiDirty=true; }
        catch(RuntimeException e){note("应用列表读取失败："+e.getClass().getSimpleName());}
    }
    private Intent selectedApp(String key) {
        AppCatalog.Entry entry=appCatalog.entries.get(key);
        return entry!=null&&appManagement.order().contains(key)?new Intent(entry.launch):null;
    }
    private void launchSelectedApp(String key) {
        HomeReturnService.userAction();
        try {
            Intent launch = selectedApp(key);
            if (launch == null) { message("电视中未找到该应用，请先从应用市场安装"); return; }
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); note("启动应用：" + key);
        } catch (RuntimeException e) { note("应用启动失败：" + e); message("应用打开失败：" + e.getMessage()); }
    }
    private String appIcon(Intent app) {
        if (app == null) return "";
        try {
            android.graphics.drawable.Drawable drawable = getPackageManager().getActivityIcon(app);
            Bitmap icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            int width=drawable.getIntrinsicWidth(),height=drawable.getIntrinsicHeight();
            if(width<=0||height<=0){width=128;height=128;}
            float scale=Math.min(128f/width,128f/height);int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
            drawable.setBounds((128-w)/2,(128-h)/2,(128+w)/2,(128+h)/2); drawable.draw(new Canvas(icon));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            try { icon.compress(Bitmap.CompressFormat.PNG, 100, out); return "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP); }
            finally { icon.recycle(); }
        } catch (Exception e) { return ""; }
    }
    private void pushUiState() {
        if (homeWeb == null || closing) return;
        readInputs();
        try {
            org.json.JSONObject state = new org.json.JSONObject(); org.json.JSONArray apps = new org.json.JSONArray(), inputs = new org.json.JSONArray();
            if(appUiDirty)for (String key : appManagement.order()) {
                AppCatalog.Entry entry=appCatalog.entries.get(key);if(entry==null)continue;
                org.json.JSONObject item = new org.json.JSONObject();
                if(!appIcons.containsKey(key))appIcons.put(key,entry.art.equals("bili")||entry.art.equals("airplay")?"":appIcon(entry.launch));
                item.put("id",key);item.put("title",entry.title);item.put("art",entry.art);item.put("icon",appIcons.get(key));apps.put(item);
            }
            for (String status : portStates) inputs.put(status);
            org.json.JSONArray titles=new org.json.JSONArray();
            for(int i=0;i<4;i++)titles.put(sourceSnapshot==null?"HDMI "+(i+1):sourceSnapshot.titles[i]);
            state.put("inputTitles",titles);
            if(appUiDirty)state.put("apps",apps);
            state.put("inputs", inputs); state.put("homeEnabled", HomeReturnService.enabled(this)); state.put("homeRunning", HomeReturnService.running());
            ui("window.HomeUI&&HomeUI.update(" + state + ")");
            if(trustedPage())appUiDirty=false;
        } catch (Exception e) { note("界面状态更新失败：" + e); }
    }
    private void applyWebWallpaper() {
        int mode = prefs.getInt("webWallpaper", 0);
        String value = mode == 1 ? "night-sky.jpg" : "milky-way.jpg";
        File saved = new File(getFilesDir(), "wallpaper.jpg");
        if (mode == 2 && saved.isFile() && saved.length() < 6000000) {
            try (InputStream stream = new java.io.FileInputStream(saved); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int size; while ((size = stream.read(buffer)) != -1) out.write(buffer, 0, size);
                value = "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
            } catch (Exception e) { note("读取自选壁纸失败：" + e.getClass().getSimpleName()); }
        }
        ui("window.HomeUI&&HomeUI.wallpaper(" + org.json.JSONObject.quote(value) + ")");
    }
    private ResolveInfo resolveHome() {
        return getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
    }
    private boolean defaultHome() {
        ResolveInfo home = resolveHome(); return home != null && getPackageName().equals(home.activityInfo.packageName);
    }
    private boolean holdsHomeRole() {
        try {
            if (Build.VERSION.SDK_INT < 29) return false;
            RoleManager roles = getSystemService(RoleManager.class);
            return roles != null && roles.isRoleHeld(RoleManager.ROLE_HOME);
        } catch (Exception e) { note("HOME 角色读取失败：" + e); return false; }
    }
    private String homeStatusMessage() {
        if (defaultHome()) return "实际 HOME 已指向本应用，请测试遥控器 Home";
        if (holdsHomeRole()) return "HOME 角色已授予，但实际首页未切换；需继续适配三星路由";
        return "HOME 角色尚未授予，实际首页也未切换";
    }
    private void requestHome() {
        if (defaultHome() || holdsHomeRole()) { message(homeStatusMessage()); return; }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager roles = getSystemService(RoleManager.class);
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), ROLE_REQUEST);
                    note("已请求系统 HOME 选择窗口"); return;
                }
            }
        } catch (Exception e) { note("HOME 角色请求失败：" + e); }
        openHomeSettings();
    }
    private void openHomeSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
            if (i.resolveActivity(getPackageManager()) == null) i = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
            startActivityForResult(i, ROLE_REQUEST); note("打开系统默认应用设置");
        } catch (Exception e) { note("默认应用设置不可用：" + e); message("系统没有开放默认首页选择入口；请查看能力检测"); }
    }
    private void samsungHome() {
        try {
            HomeReturnService.store(this).edit().putLong("pauseUntil", android.os.SystemClock.elapsedRealtime() + 30000).apply();
            String original = prefs.getString("originalHome", "com.samsung.tv.dummylauncher/com.samsung.tv.dummylauncher.MainActivity");
            ComponentName component = ComponentName.unflattenFromString(original);
            if (component == null || getPackageName().equals(component.getPackageName())) { message("未保存可用的三星首页入口"); return; }
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { note("三星首页：" + e); message("无法打开三星首页：" + e.getMessage()); }
    }

    private void readInputs() {
        for (int i = 0; i < 4; i++) { ports[i] = null; portStates[i] = "unknown"; }
        try {
            TvInputManager manager = (TvInputManager)getSystemService(TV_INPUT_SERVICE);
            if (manager == null) throw new IllegalStateException("Android 未提供 TV_INPUT_SERVICE");
            List<TvInputInfo> list = manager.getTvInputList();
            StringBuilder report = new StringBuilder("TV inputs：" + list.size() + "\n");
            for (TvInputInfo input : list) {
                String title = String.valueOf(input.loadLabel(this));
                report.append(title).append(" | ").append(input.getId()).append(" | type=").append(input.getType()).append('\n');
                if (input.getType() != TvInputInfo.TYPE_HDMI || !input.isPassthroughInput()) continue;
                int port = -1;
                Matcher match = Pattern.compile("HDMI\\s*[-_:]?\\s*([1-4])(?:\\D|$)", Pattern.CASE_INSENSITIVE).matcher(title + " " + input.getId());
                if (match.find()) port = Integer.parseInt(match.group(1));
                if (port >= 1 && port <= 4) {
                    ports[port - 1] = input;
                    int state = manager.getInputState(input.getId());
                    portStates[port - 1] = state == TvInputManager.INPUT_STATE_CONNECTED ? "connected" : state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? "standby" : state == TvInputManager.INPUT_STATE_DISCONNECTED ? "disconnected" : "unknown";
                }
            }
            inputNote = report.toString();
        } catch (Exception e) { inputNote = "信号源查询失败：" + e; }
        if (sourceSnapshot != null && android.os.SystemClock.elapsedRealtime() - sourceSnapshotAt < 25000) {
            String[] states = sourceSnapshot.states(VERIFIED_ACTIVE_POWER, VERIFIED_CONNECTED_POWER);
            for(int i=0;i<4;i++) portStates[i]=states[i];
        }
    }
    private void startSourceStatus() {
        if (!sourceForeground || closing || sourceSession != null) return;
        if(!tvConnection.ready()){sourceNote="等待自动识别并确认本机电视连接";return;}
        final int generation=++sourceGeneration;
        try {
            URI endpoint = TvEndpoint.channel(tvConnection.host());
            sourceSession = new TvSourceSession(endpoint, PinnedTvTls.factory(tvConnection.pin()), new TvSourceSession.Listener() {
                public void launchResult(String result) { runOnUiThread(() -> {
                    if(closing||generation!=sourceGeneration)return;
                    note(result);message(result);
                }); }
                public void snapshot(TvSourceSession.Snapshot snapshot) { runOnUiThread(() -> {
                    if(closing||!sourceForeground||generation!=sourceGeneration)return;
                    sourceSnapshot=snapshot;sourceSnapshotAt=android.os.SystemClock.elapsedRealtime();
                    sourceNote=snapshot.summary();pushUiState();
                }); }
                public void unavailable(String reason) { runOnUiThread(() -> {
                    if(closing||generation!=sourceGeneration)return;
                    sourceNote=reason;sourceSnapshot=null;note(reason);pushUiState();
                    // No automatic reconnect or repeated pairing prompt after failure.
                }); }
            });
            sourceNote="正在读取三星输入状态";sourceSession.start();
        } catch(Exception e) {sourceNote="输入状态连接无法建立："+e.getClass().getSimpleName()+"："+DiagnosticSanitizer.clean(e.getMessage());sourceSession=null;}
    }
    private void stopSourceStatus() {
        ++sourceGeneration;
        if(sourceSession!=null)sourceSession.cancel();
        sourceSession=null;sourceSnapshot=null;
    }
    private void openInput(int index) {
        HomeReturnService.userAction();
        if (ports[index] == null) {
            String error=sourceSession==null?"信号源接口尚未就绪，请稍后重试":sourceSession.launchInput(index);
            if(error!=null){note(error);message(error);}
            else note("已提交三星原生 HDMI "+(index+1)+" 切换请求，等待电视确认");
            return;
        }
        try {
            Uri uri = TvContract.buildChannelUriForPassthroughInput(ports[index].getId());
            startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            note("请求 HDMI " + (index + 1));
        } catch (Exception e) { note("信号源启动：" + e); message("切换失败：" + e.getMessage()); }
    }

    private void settings() {
        HomeReturnService.userAction();
        String[] options = {"壁纸", "Home 返回：" + (HomeReturnService.enabled(this) ? "开启" : "关闭"), "画质快捷设置", "三星原首页（暂停返回 30 秒）", "状态与日志", "应用管理", "电视连接", "退出并关闭 Home 返回"};
        new AlertDialog.Builder(this).setTitle("首页设置").setItems(options, (dialog, index) -> {
            if (index == 0) chooseWallpaper();
            if (index == 1) homeReturnTest();
            if (index == 2) {
                try { startActivity(new Intent().setComponent(new ComponentName("com.android.tv.quicksettings", "com.android.tv.quicksettings.QuickSettings"))); }
                catch (RuntimeException error) { message("固件未开放此设置入口"); }
            }
            if (index == 3) samsungHome();
            if (index == 4) diagnostics();
            if (index == 5) { reloadApps();appManagement.show(); }
            if (index == 6) tvSetup.show();
            if (index == 7) { HomeReturnService.stopTrial(this); finish(); }
        }).setNegativeButton("返回", null).show();
    }
    private void homeReturnTest() {
        new AlertDialog.Builder(this).setTitle("Home 返回")
            .setMessage("开启后持续接收 Home 事件并返回本首页，不再五分钟到期。\n\n三星外层页面是否仍会短暂闪现，以及不同应用是否都能返回，需要实机确认。\n\n" + HomeReturnService.summary(this))
            .setPositiveButton("开启", (d, which) -> {
                try { HomeReturnService.startTrial(this); handler.postDelayed(this::pushUiState, 350); message("Home 返回已开启"); }
                catch (RuntimeException e) { message("Home 服务启动失败：" + e.getClass().getSimpleName()); }
            }).setNegativeButton("关闭", (d, which) -> { HomeReturnService.stopTrial(this); pushUiState(); })
            .setNeutralButton("返回速度", (d, which) -> returnSpeed()).show();
    }
    private void returnSpeed() {
        String[] labels = {"立即返回（时序实验）", "等待 80ms（时序实验）", "等待 160ms（已实测可用）", "等待 300ms（已实测可用）", "等待 650ms（已实测可用）"};
        int selected = 0;
        for (int i = 0; i < HomeReturnService.RETURN_DELAYS.length; i++) if (HomeReturnService.RETURN_DELAYS[i] == HomeReturnService.delay(this)) selected = i;
        new AlertDialog.Builder(this).setTitle("Home 返回速度 · 快档增加任务事件跟随")
            .setSingleChoiceItems(labels, selected, (d, which) -> {
                HomeReturnService.store(this).edit().putInt("returnDelay", HomeReturnService.RETURN_DELAYS[which]).apply();
                HomeReturnService.log(this, "速度设置：" + HomeReturnService.delayLabel(this));
                d.dismiss(); homeReturnTest();
            }).setNegativeButton("取消", (d, which) -> homeReturnTest()).show();
    }
    private void chooseWallpaper() {
        new AlertDialog.Builder(this).setTitle("壁纸").setItems(new String[]{"银河星空", "深蓝星夜", "从本机或 U 盘选择图片"}, (d, item) -> {
            if (item < 2) { prefs.edit().putInt("webWallpaper", item).apply(); applyWebWallpaper(); return; }
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), PICK_WALLPAPER);
            } catch (Exception e) { message("系统没有可用的图片选择器，可先使用内置壁纸"); note("图片选择：" + e); }
        }).show();
    }
    private void importWallpaper(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(stream, null, options); }
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new IllegalArgumentException("不是可读取的图片");
            int sample = 1;
            while (options.outWidth / sample > 1920 || options.outHeight / sample > 1080) sample *= 2;
            options.inJustDecodeBounds = false; options.inSampleSize = sample;
            Bitmap image;
            try (InputStream stream = getContentResolver().openInputStream(uri)) { image = BitmapFactory.decodeStream(stream, null, options); }
            if (image == null) throw new IllegalArgumentException("图片解码失败");
            File pending = new File(getFilesDir(), "wallpaper.pending");
            try (FileOutputStream output = new FileOutputStream(pending)) {
                if (!image.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw new IllegalStateException("图片保存失败");
            } finally { image.recycle(); }
            File saved = new File(getFilesDir(), "wallpaper.jpg");
            if (!pending.renameTo(saved)) throw new IllegalStateException("壁纸文件替换失败");
            prefs.edit().putInt("webWallpaper", 2).apply(); applyWebWallpaper();
        } catch (Exception e) { message("无法使用此图片：" + e.getMessage()); }
    }

    private void diagnostics() {
        readInputs(); StringBuilder b = new StringBuilder();
        b.append("HDMI 状态：\n").append(sourceNote).append("\nforeground=").append(sourceForeground).append("\n")
            .append(sourceSession==null?"当前无状态连接":sourceSession.diagnostics()).append("\n\n");
        b.append("Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append(" / ").append(Build.DEVICE);
        b.append("\n本应用是 Android 默认首页：").append(defaultHome());
        ResolveInfo current = resolveHome();
        b.append("\n当前 HOME：").append(current == null ? "未返回" : current.activityInfo.packageName + "/" + current.activityInfo.name);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager roles = getSystemService(RoleManager.class);
                b.append("\nHOME 角色可用：").append(roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME));
                b.append("\n持有 HOME 角色：").append(roles != null && roles.isRoleHeld(RoleManager.ROLE_HOME));
            }
        } catch (Exception e) { b.append("\n角色检测：").append(e); }
        b.append("\n\n").append(HomeReturnService.summary(this));
        b.append("\n\nHOME 候选：\n");
        for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) b.append(r.activityInfo.packageName).append('/').append(r.activityInfo.name).append('\n');
        b.append("\n").append(inputNote).append("\n").append(sourceNote).append("\n最近事件：\n");
        for (String event : history) b.append(event).append('\n');
        b.append("\n可见的 Samsung/输入相关导出界面（只读，未调用）：\n");
        try {
            for (PackageInfo p : getPackageManager().getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
                if (p.activities == null || !(p.packageName.contains("samsung") || p.packageName.contains("tv.input"))) continue;
                for (ActivityInfo a : p.activities) if (a.exported) b.append(p.packageName).append('/').append(a.name).append('\n');
            }
        } catch (Exception e) { b.append(e); }
        ScrollView scroll = new ScrollView(this); scroll.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView report = label(b.toString(), 16, Color.WHITE); scroll.addView(report);
        new AlertDialog.Builder(this).setTitle("状态与日志").setView(scroll)
            .setNeutralButton("导出脱敏诊断", (d,which) -> exportDiagnostics())
            .setNegativeButton("刷新 HDMI 状态", (d,which) -> {
                stopSourceStatus();pushUiState();startSourceStatus();
                note("用户手动刷新 HDMI 状态");message("正在刷新 HDMI 状态，结果将更新到首页卡片");
            }).setPositiveButton("返回首页", null).show();
    }
    private void exportDiagnostics(){
        try{
            String connection=sourceSession==null?"no session":sourceSession.diagnostics();
            pendingDiagnostic=HomeDiagnosticReport.create(Build.MODEL,Build.VERSION.SDK_INT,sourceNote,connection,HomeReturnService.summary(this));
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"tv-home-diagnostics.zip");
            startActivityForResult(save,EXPORT_DIAGNOSTIC);
        }catch(Exception e){
            pendingDiagnostic=null;
            TextView text=label(DiagnosticSanitizer.clean(sourceNote+"\n"+(sourceSession==null?"":sourceSession.diagnostics())+"\n"+HomeReturnService.summary(this)),14,Color.WHITE);
            text.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(text);
            new AlertDialog.Builder(this).setTitle("系统未提供文件导出界面").setView(scroll)
                .setPositiveButton("复制脱敏日志",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TV diagnostics",text.getText()));})
                .setNegativeButton("返回",null).show();
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==EXPORT_DIAGNOSTIC){
            byte[] payload=pendingDiagnostic;pendingDiagnostic=null;
            if(result==RESULT_OK&&payload!=null&&data!=null&&data.getData()!=null)try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new java.io.IOException("No output");out.write(payload);message("脱敏诊断已保存，请检查后自行分享");
            }catch(Exception e){message("诊断文件未能保存");}
            return;
        }
        if (request == ROLE_REQUEST) {
            note("首页选择返回：result=" + result + ", default=" + defaultHome());
            message(homeStatusMessage());
        }
        if (request == PICK_WALLPAPER && result == RESULT_OK && data != null && data.getData() != null) importWallpaper(data.getData());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); note("新入口：" + intentDescription(intent));
        HomeReturnService.traceActivity(this, "首页 onNewIntent");
        // The foreground service keeps this process warm. Reuse the existing tree on Home.
        if (homeWeb == null) showHome();
        immersive(); overridePendingTransition(0, 0); pushUiState();
    }
    @Override protected void onResume() {
        super.onResume(); immersive(); note("回到前台");
        reloadApps();
        HomeReturnService.traceActivity(this, "首页 onResume");
        long event = getIntent().getLongExtra(HomeReturnService.FROM_HOME, -1);
        if (event >= 0) { overridePendingTransition(0, 0); HomeReturnService.log(this, "首页收到 onResume（仍需观察电视画面）；距 Home 事件 " + (android.os.SystemClock.elapsedRealtime() - event) + "ms"); getIntent().removeExtra(HomeReturnService.FROM_HOME); }
        if (homeWeb != null) { homeWeb.onResume(); ui("window.HomeUI&&HomeUI.refresh()"); }
        try { HomeReturnService.ensureRunning(this); } catch (RuntimeException e) { note("Home 服务：" + e.getClass().getSimpleName()); }
        sourceForeground=true;startSourceStatus();
        handler.postDelayed(this::pushUiState, 350);
    }
    @Override protected void onPause() {
        tvSetup.cancel();
        sourceForeground=false;stopSourceStatus();
        super.onPause(); if (homeWeb != null) homeWeb.onPause(); note("离开前台");
        HomeReturnService.traceActivity(this, "首页 onPause");
        if (HomeReturnService.enabled(this)) HomeReturnService.log(this, "首页离开前台");
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        HomeReturnService.traceActivity(this, "Android 窗口焦点=" + focused + "（非 Tizen 最终可见性证明）");
    }
    @Override public void onBackPressed() { /* Home remains visible; explicit exit is in settings. */ }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_MENU) { settings(); return true; }
        return super.onKeyDown(key, event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) { if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) settings(); return true; }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onDestroy() {
        stopSourceStatus();
        closing = true; handler.removeCallbacksAndMessages(null);
        if(packageReceiverRegistered)unregisterReceiver(packageReceiver);
        if (inputCallbackRegistered && inputManager != null) inputManager.unregisterCallback(inputCallback);
        if (homeWeb != null) { homeWeb.removeJavascriptInterface("S95D"); homeWeb.destroy(); homeWeb = null; }
        super.onDestroy();
    }

}

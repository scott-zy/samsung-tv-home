package org.s95dhome.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the user's enabled Home listener after Android boot; never changes the native home. */
public final class HomeBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try { HomeReturnService.ensureRunning(context); }
        catch (RuntimeException error) { HomeReturnService.log(context, "开机恢复 Home 服务失败：" + error.getClass().getSimpleName()); }
    }
}

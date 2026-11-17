package com.shenwenjun.a7r2app;

import android.app.Activity;
import android.content.Intent;

/**
 * 相机（DAConnectionManagerService）与 App 之间的三件事：
 *  1. 告诉相机“现在前台是我”——不发这个，按键/预览可能失效
 *  2. 关闭自动关机——Wi-Fi、长曝光、定时任务必备
 *  3. 退出
 */
public final class SystemBridge {

    private static final String DA = "com.android.server.DAConnectionManagerService.";

    private SystemBridge() {
    }

    /** 通知相机当前 Activity */
    public static void notifyAppInfo(Activity activity) {
        try {
            Intent intent = new Intent(DA + "AppInfoReceive");
            intent.putExtra("package_name", activity.getPackageName());
            intent.putExtra("class_name", activity.getComponentName().getClassName());
            activity.sendBroadcast(intent);
            Logger.i("broadcast AppInfoReceive");
        } catch (Throwable t) {
            Logger.e("AppInfoReceive failed", t);
        }
    }

    /** enable=false 时禁止自动关机，退出前记得恢复 */
    public static void setAutoPowerOff(Activity activity, boolean enable) {
        try {
            Intent intent = new Intent();
            intent.setAction(DA + "apo");
            intent.putExtra("apo_info", enable ? "APO/NORMAL" : "APO/NO");
            activity.sendBroadcast(intent);
            Logger.i("broadcast apo " + (enable ? "APO/NORMAL" : "APO/NO"));
        } catch (Throwable t) {
            Logger.e("apo broadcast failed", t);
        }
    }
}

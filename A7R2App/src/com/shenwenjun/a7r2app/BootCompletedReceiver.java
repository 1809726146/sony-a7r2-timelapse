package com.shenwenjun.a7r2app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 相机开机 / 应用列表刷新时收到。
 * 想开机自启就在这里 startActivity(MainActivity)，先用日志确认它真的被调用。
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.init();
        Logger.i("receiver: " + (intent == null ? "?" : intent.getAction()));
    }
}

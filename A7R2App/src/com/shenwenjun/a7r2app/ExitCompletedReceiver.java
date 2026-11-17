package com.shenwenjun.a7r2app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 相机把 App 退出流程走完时会发这个广播。
 * 需要做清理（关机、释放资源、写统计）就在这里做。
 */
public class ExitCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.i("receiver: " + (intent == null ? "?" : intent.getAction()));
    }
}

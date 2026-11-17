package com.shenwenjun.a7r2app;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 相机上没有控制台，日志同时写 logcat 和 SD 卡文件。
 * 输出路径：/sdcard/A7R2APP/LOG.TXT
 */
public final class Logger {

    private static final String TAG = "A7R2App";
    private static final String DIR_NAME = "A7R2APP";
    private static final String FILE_NAME = "LOG.TXT";

    private static FileWriter writer;
    /** 内存里的最近日志（快照用，避免再去读被占用的 LOG.TXT） */
    private static final StringBuilder HISTORY = new StringBuilder();
    private static final int HISTORY_MAX = 512 * 1024;
    private static File logFile;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    private Logger() {
    }

    public static synchronized void init() {
        if (writer != null) {
            return;
        }
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            logFile = new File(dir, FILE_NAME);
            writer = new FileWriter(logFile, true);
        } catch (Throwable t) {
            Log.w(TAG, "cannot open log file: " + t);
        }
    }

    public static String getPath() {
        return logFile == null ? "(unavailable)" : logFile.getAbsolutePath();
    }

    /**
     * 把当前日志另存一份带时间戳的快照，方便随时取证。
     * 返回绝对路径；失败返回 null。
     */
    public static synchronized String snapshot() {
        String body;
        synchronized (HISTORY) {
            body = HISTORY.toString();
        }
        if (body.length() == 0) {
            return null;
        }
        String stamp = new SimpleDateFormat("MMdd-HHmmss").format(new Date());
        File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
        Logger.i("snapshot begin: dir=" + dir + " exists=" + dir.exists()
                + " canWrite=" + dir.canWrite() + " chars=" + body.length());

        // 依次尝试多个文件名，找出这台机器允许新建哪种文件
        String[] names = {"SNAP-" + stamp + ".TXT", "SNAP.TXT"};
        closeWriterQuietly();
        try {
            for (String name : names) {
                File out = new File(dir, name);
                try {
                    FileWriter w = new FileWriter(out, false);
                    try {
                        w.write(body);
                        w.flush();
                    } finally {
                        w.close();
                    }
                    openWriterQuietly();
                    String path = out.getAbsolutePath();
                    e("snapshot ok: " + name + " (" + body.length() + " chars) path=" + path);
                    return path;
                } catch (Throwable t) {
                    e("snapshot try " + name + " failed -> " + t);
                }
            }
        } finally {
            openWriterQuietly();
        }

        // 最后兜底：直接追加到 LOG.TXT 尾部（append 一定可用）
        try {
            if (writer != null) {
                writer.write("\n===== SNAPSHOT " + stamp + " =====\n");
                int from = Math.max(0, body.length() - 200 * 1024);
                writer.write(body.substring(from));
                writer.write("\n===== SNAPSHOT END =====\n");
                writer.flush();
                e("snapshot appended to LOG.TXT (fallback)");
                return logFile == null ? null : logFile.getAbsolutePath();
            }
        } catch (Throwable t) {
            e("snapshot fallback failed -> " + t);
        }
        return null;
    }
    public static synchronized String writeText(String fileName, String content) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File out = new File(dir, fileName);
            FileWriter w = new FileWriter(out, false);
            try {
                w.write(content);
                w.flush();
            } finally {
                w.close();
            }
            return out.getAbsolutePath();
        } catch (Throwable t) {
            Log.w(TAG, "cannot write " + fileName + ": " + t);
            e("writeText failed: " + fileName + " -> " + t, t);
            return null;
        }
    }

    /** 关闭日志写句柄（快照前用，避免 fuse 锁） */
    private static void closeWriterQuietly() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            writer.close();
        } catch (Throwable ignored) {
        }
        writer = null;
    }

    /** 重新打开日志写句柄（追加） */
    private static void openWriterQuietly() {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            logFile = new File(dir, FILE_NAME);
            writer = new FileWriter(logFile, true);
        } catch (Throwable t) {
            Log.w(TAG, "reopen log failed: " + t);
            writer = null;
        }
    }

    public static synchronized void i(String msg) {
        write('I', msg);
    }

    public static synchronized void e(String msg) {
        write('E', msg);
    }

    public static synchronized void e(String msg, Throwable t) {
        write('E', msg + " -> " + t);
    }

    private static void write(char level, String msg) {
        Log.println(level == 'E' ? Log.ERROR : Log.INFO, TAG, msg);
        String line = TIME.format(new Date()) + " " + level + " " + msg + "\n";
        synchronized (HISTORY) {
            HISTORY.append(line);
            if (HISTORY.length() > HISTORY_MAX) {
                int cut = HISTORY.indexOf("\n", HISTORY.length() - HISTORY_MAX / 2);
                HISTORY.delete(0, cut > 0 ? cut + 1 : HISTORY.length() / 2);
            }
        }
        if (writer != null) {
            try {
                writer.write(TIME.format(new Date()) + " " + level + " " + msg + "\n");
                writer.flush();
            } catch (IOException e) {
                // 忽略：写日志失败不能影响主流程
            }
        }
    }
}

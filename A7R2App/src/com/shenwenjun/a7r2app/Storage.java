package com.shenwenjun.a7r2app;

import android.os.Environment;
import android.os.StatFs;

/**
 * 存储卡剩余空间估算：按当前格式估算还能拍多少张。
 * 单张体积参考实测：RAW(压缩) 约 41MB，RAW(未压缩) 约 82MB，JPEG 约 5MB。
 */
public final class Storage {

    private static final long MB = 1024L * 1024L;

    private Storage() {
    }

    private static long cachedFree = -2;
    private static long cachedAt;

    /** 剩余可用字节；2 秒内复用上次结果（StatFs 是系统调用，不必每帧查） */
    @SuppressWarnings("deprecation")
    public static long freeBytes() {
        long now = System.currentTimeMillis();
        if (cachedFree != -2 && now - cachedAt < 2000) {
            return cachedFree;
        }
        try {
            StatFs fs = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            cachedFree = (long) fs.getAvailableBlocks() * (long) fs.getBlockSize();
        } catch (Throwable t) {
            cachedFree = -1;
        }
        cachedAt = now;
        return cachedFree;
    }

    /** 按格式估算单张字节数 */
    public static long bytesPerShot(String format, boolean rawCompressed) {
        if ("jpeg".equals(format)) {
            return 5 * MB;
        }
        if ("rawjpeg".equals(format)) {
            return (rawCompressed ? 41 : 82) * MB + 5 * MB;
        }
        return (rawCompressed ? 41 : 82) * MB;   // raw
    }

    /** 估算还能拍多少张；未知返回 -1 */
    public static int estimatedShots(String format, boolean rawCompressed) {
        long free = freeBytes();
        if (free < 0) {
            return -1;
        }
        long per = bytesPerShot(format, rawCompressed);
        if (per <= 0) {
            return -1;
        }
        long n = free / per;
        return (int) Math.min(n, 99999);
    }

    public static String freeText() {
        long free = freeBytes();
        if (free < 0) {
            return "?";
        }
        long gb = free / (1024L * 1024L * 1024L);
        if (gb >= 1) {
            return gb + "GB";
        }
        return (free / MB) + "MB";
    }
}

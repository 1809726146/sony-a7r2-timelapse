package com.shenwenjun.a7r2app;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 相机系统默认字体是"子集字体"，只包含索尼 OSD 用到的少量汉字，
 * 于是「偿 / 对 / 测 / 检 / 选择」这类字会显示成方框。
 *
 * 优先级：
 *   1) APK 内置的子集中文字体（res/raw/cjk.ttf，只含本 App 用到的字，约 100KB）
 *   2) 系统里可能存在的完整中文字体
 *   3) 系统默认字体（会出现方块）
 */
public final class Fonts {

    private static final String[] SYSTEM_CANDIDATES = {
            "/system/fonts/MYingHeiC-GB18030-SJ.ttf",
            "/system/fonts/DroidSansFallback.ttf",
            "/system/fonts/DroidSansFallbackFull.ttf",
            "/system/fonts/DroidSansChinese.ttf",
            "/system/fonts/NotoSansCJK-Regular.ttc",
            "/system/fonts/NotoSansCJK.ttc",
            "/system/fonts/NotoSansSC-Regular.otf",
    };

    private static Typeface cjk;
    private static boolean resolved;

    private Fonts() {
    }

    public static Typeface cjk(Context ctx) {
        if (resolved) {
            return cjk;
        }
        resolved = true;

        // 1) 系统里若有完整中文字体，直接用（这台机器有 MYingHeiC-GB18030，覆盖全部汉字）
        for (String path : SYSTEM_CANDIDATES) {
            try {
                if (!new File(path).exists()) {
                    continue;
                }
                Typeface t = Typeface.createFromFile(path);
                if (t != null) {
                    cjk = t;
                    Logger.i("font loaded: " + path);
                    return cjk;
                }
            } catch (Throwable e) {
                Logger.e("font load failed: " + path, e);
            }
        }

        // 2) 内置字体兜底
        try {
            cjk = fromBundled(ctx);
            if (cjk != null) {
                Logger.i("font: bundled cjk.ttf");
                return cjk;
            }
        } catch (Throwable t) {
            Logger.e("bundled font failed", t);
        }

        Logger.i("no full CJK font, using default typeface (may show boxes)");
        cjk = Typeface.DEFAULT;
        return cjk;
    }

    private static Typeface fromBundled(Context ctx) throws Exception {
        // 用 versionCode 命名，保证升级后一定重新解压（之前的写法会一直用旧字体）
        int ver = 0;
        try {
            ver = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Throwable ignored) {
        }
        File out = new File(ctx.getFilesDir(), "cjk-" + ver + ".ttf");
        if (!out.exists() || out.length() < 2048) {
            InputStream in = ctx.getResources().openRawResource(R.raw.cjk);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
            fos.close();
            in.close();
            // 顺手清掉旧版本留下的字体文件
            File[] olds = ctx.getFilesDir().listFiles();
            if (olds != null) {
                for (File f : olds) {
                    if (f.getName().startsWith("cjk") && !f.getName().equals(out.getName())) {
                        f.delete();
                    }
                }
            }
        }
        Logger.i("bundled font v" + ver + " size=" + out.length());
        return Typeface.createFromFile(out);
    }

    /** 启动时把 /system/fonts 里的字体名列进日志，便于排查 */
    public static void logSystemFonts() {
        try {
            File dir = new File("/system/fonts");
            String[] names = dir.list();
            if (names == null) {
                Logger.i("system fonts: <无法列出>");
                return;
            }
            StringBuilder sb = new StringBuilder("system fonts(" + names.length + "): ");
            for (String n : names) {
                sb.append(n).append(" ");
            }
            Logger.i(sb.toString());
        } catch (Throwable t) {
            Logger.e("list fonts failed", t);
        }
    }
}

package com.shenwenjun.a7r2app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 间隔预设：4 个槽位，保存「间隔秒数 + 张数」。
 * 存在 App 私有 SharedPreferences 里，随应用卸载消失。
 */
public final class Presets {

    public static final int SLOTS = 4;

    /** 常用使用场景（转盘循环选择，避免在相机上打字） */
    public static final String[] SCENES = {
            "未设置", "日转夜", "流云", "星空", "车流",
            "日出日落", "潮汐", "建筑", "人流", "花卉"
    };
    private static final String PREFS = "a7r2_presets";

    private final SharedPreferences sp;

    public Presets(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 返回 {intervalSec, shots}；空槽返回 null */
    public int[] get(int slot) {
        String s = sp.getString(key(slot), null);
        if (s == null) {
            return null;
        }
        int comma = s.indexOf(',');
        if (comma <= 0) {
            return null;
        }
        try {
            int sec = Integer.parseInt(s.substring(0, comma).trim());
            int shots = Integer.parseInt(s.substring(comma + 1).trim());
            if (sec < IntervalShooter.INTERVAL_MIN || shots <= 0) {
                return null;
            }
            return new int[]{sec, shots};
        } catch (Throwable t) {
            return null;
        }
    }

    public void save(int slot, int intervalSec, int shots) {
        sp.edit().putString(key(slot), intervalSec + "," + shots).commit();
    }

    public void clear(int slot) {
        sp.edit().remove(key(slot)).commit();
    }

    public String scene(int slot) {
        String v = sp.getString("scene" + slot, null);
        return v == null ? SCENES[0] : v;
    }

    public void setScene(int slot, String scene) {
        sp.edit().putString("scene" + slot, scene).commit();
    }

    public String summary(int slot) {
        int[] v = get(slot);
        if (v == null) {
            return "空";
        }
        String sc = scene(slot);
        if (!SCENES[0].equals(sc)) {
            return formatInterval(v[0]) + " × " + v[1] + " · " + sc;
        }
        return formatInterval(v[0]) + " × " + v[1];
    }

    public boolean isEmpty(int slot) {
        return get(slot) == null;
    }

    public static String formatInterval(int sec) {
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        StringBuilder sb = new StringBuilder(8);
        if (h > 0) {
            sb.append(h).append(':');
        }
        if (m < 10) {
            sb.append('0');
        }
        sb.append(m).append(':');
        if (s < 10) {
            sb.append('0');
        }
        sb.append(s);
        return sb.toString();
    }

    private static String key(int slot) {
        return "slot" + slot;
    }
}

package com.shenwenjun.a7r2app;

import android.os.Handler;
import android.os.SystemClock;

/**
 * 间隔拍摄 / 延时。
 *
 * 计时用“绝对时间基准”：第 n 格的目标时刻 = 开始时刻 + n × 间隔，
 * 而不是“拍完再等 5 秒”——后者拍 250 张会累积漂移，后期视频会抖。
 *
 * 上一张还没写完卡时（onStored 未回调且未超时）跳过这一格，
 * 等价于索尼的“拍摄间隔优先”，但我们不压缩曝光，所以不会欠曝。
 */
public class IntervalShooter implements CameraController.ShotListener {

    /** UI 只需要知道“参数或状态变了”，自己去读 getter */
    public interface Ui {
        void onIntervalChanged();

        /** 某张照片已写进卡 */
        void onShotStored(String file, int index);

        /** 相机回传的这张照片的 JPEG，用于整张回放 */
    }

    public static final int INTERVAL_MIN = 5;
    public static final int INTERVAL_MAX = 24 * 3600 + 59 * 60 + 59;   // 24:59:59
    public static final int SHOTS_MIN = 1;
    public static final int SHOTS_MAX = 9999;
    private int shots = 250;            // 默认 250 张

    private static final long BUSY_TIMEOUT_MS = 15000L;
    private static final long START_DELAY_MS = 3000L;

    private final Handler handler = new Handler();
    private final CameraController camera;
    private final Ui ui;

    private int intervalSec = 5;      // 默认 5 秒（RAW 每张约 3.5 秒，1~2 秒会跳格）

    private boolean running;
    private boolean done;
    private int slot;                 // 已触发的格子数
    private int saved;
    private int skipped;
    private boolean busy;
    private long busySince;
    private long startAt;
    private String lastFile = "-";

    private final Runnable tick = new Runnable() {
        public void run() {
            onTick();
        }
    };

    private final Runnable refresh = new Runnable() {
        public void run() {
            notifyUi();
            if (running) {
                handler.postDelayed(this, 250L);
            }
        }
    };

    public IntervalShooter(CameraController camera, Ui ui) {
        this.camera = camera;
        this.ui = ui;
    }

    // ---------- 参数 ----------

    // ---------- 时 / 分 / 秒 ----------

    public int intervalHours() {
        return intervalSec / 3600;
    }

    public int intervalMinutes() {
        return (intervalSec % 3600) / 60;
    }

    public int intervalSeconds() {
        return intervalSec % 60;
    }

    /** delta 直接作用在「时」上（每格 1 小时） */
    public void adjustHours(int delta) {
        int total = intervalSec + delta * 3600;
        intervalSec = clampInterval(total);
        notifyUi();
    }

    /** 每格 1 分钟 */
    public void adjustMinutes(int delta) {
        int total = intervalSec + delta * 60;
        intervalSec = clampInterval(total);
        notifyUi();
    }

    /** 每格 1 秒 */
    public void adjustSeconds(int delta) {
        int total = intervalSec + delta;
        intervalSec = clampInterval(total);
        notifyUi();
    }

    private int clampInterval(int total) {
        if (total < INTERVAL_MIN) {
            total = INTERVAL_MIN;
        }
        if (total > INTERVAL_MAX) {
            total = INTERVAL_MAX;
        }
        return total;
    }

    /** 有小时显示 H:MM:SS，否则 M:SS（HUD 宽度有限） */
    public String intervalText() {
        int h = intervalHours();
        int m = intervalMinutes();
        int sec = intervalSeconds();
        StringBuilder sb = new StringBuilder(8);
        if (h > 0) {
            sb.append(h).append(':');
        }
        if (m < 10) {
            sb.append('0');
        }
        sb.append(m).append(':');
        if (sec < 10) {
            sb.append('0');
        }
        sb.append(sec);
        return sb.toString();
    }

    /** 直接设定总秒数（预设载入用） */
    public void setIntervalSec(int sec) {
        intervalSec = clampInterval(sec);
        notifyUi();
    }

    /** 当前张数在预设表里的下标 */
    public int countIndex() {
        return 0;
    }

    /** 按张数设定（预设载入用），范围 1..9999 */
    public void setShots(int shots) {
        this.shots = clampShots(shots);
        notifyUi();
    }

    private int clampShots(int v) {
        if (v < SHOTS_MIN) {
            return SHOTS_MIN;
        }
        if (v > SHOTS_MAX) {
            return SHOTS_MAX;
        }
        return v;
    }

    public int intervalSec() {
        return intervalSec;
    }

    public int totalShots() {
        return shots;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isDone() {
        return done;
    }

    public int slotCount() {
        return slot;
    }

    public int savedCount() {
        return saved;
    }

    public int skippedCount() {
        return skipped;
    }

    public String lastFile() {
        return lastFile;
    }

    public long nextInMs() {
        if (!running) {
            return 0;
        }
        long due = startAt + (long) slot * intervalSec() * 1000L;
        long left = due - SystemClock.elapsedRealtime();
        return left < 0 ? 0 : left;
    }

    public void intervalUp() {
        if (!running && intervalSec < INTERVAL_MAX) {
            intervalSec++;
            notifyUi();
        }
    }

    public void intervalDown() {
        if (!running && intervalSec > INTERVAL_MIN) {
            intervalSec--;
            notifyUi();
        }
    }

    public void shotsUp() {
        if (!running) {
            shots = clampShots(shots + 1);
            notifyUi();
        }
    }

    public void shotsDown() {
        if (!running) {
            shots = clampShots(shots - 1);
            notifyUi();
        }
    }

    // ---------- 运行 ----------

    public void start() {
        if (running) {
            return;
        }
        running = true;
        done = false;
        slot = 0;
        saved = 0;
        skipped = 0;
        busy = false;
        lastFile = "-";
        startAt = SystemClock.elapsedRealtime() + START_DELAY_MS;
        Logger.i("interval START interval=" + intervalSec() + "s count=" + totalShots()
                + " delay=" + (START_DELAY_MS / 1000) + "s");
        scheduleNext();
        handler.post(refresh);
        notifyUi();
    }

    public void stop(String reason) {
        if (!running) {
            return;
        }
        running = false;
        busy = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(refresh);
        long elapsed = (SystemClock.elapsedRealtime() - (startAt - START_DELAY_MS)) / 1000L;
        Logger.i("interval STOP (" + reason + ") slots=" + slot + "/" + totalShots()
                + " saved=" + saved + " skipped=" + skipped + " elapsed=" + elapsed + "s");
        notifyUi();
    }

    public void finishSequences() {
        if (!running) {
            return;
        }
        stop("done");
        done = true;
        notifyUi();
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        long due = startAt + (long) slot * intervalSec() * 1000L;
        long delay = due - SystemClock.elapsedRealtime();
        if (delay < 0) {
            delay = 0;
        }
        handler.postDelayed(tick, delay);
    }

    private void onTick() {
        if (!running) {
            return;
        }
        long now = SystemClock.elapsedRealtime();

        if (slot >= totalShots()) {
            finishSequences();
            return;
        }

        if (busy && (now - busySince) < BUSY_TIMEOUT_MS) {
            skipped++;
            Logger.i("interval skip slot " + (slot + 1) + "/" + totalShots() + " (camera busy)");
        } else {
            busy = false;
            if (camera.shoot()) {
                busy = true;
                busySince = now;
            } else {
                Logger.e("interval shoot failed slot " + (slot + 1));
            }
            Logger.i("interval slot " + (slot + 1) + "/" + totalShots()
                    + " t+" + ((now - startAt) / 1000L) + "s");
        }

        slot++;
        scheduleNext();
        notifyUi();
    }

    // ---------- CameraController.ShotListener ----------

    public void onShutter(int status) {
        if (status == 2) {
            Logger.e("shutter error");
            busy = false;
        } else {
            Logger.i("shutter status " + status);
        }
    }

    public void onStored(String dir, String file) {
        busy = false;
        saved++;
        lastFile = file;
        Logger.i("saved " + dir + "/" + file + " #" + saved);
        notifyUi();
        final String f = file;
        final int n = saved;
        handler.post(new Runnable() {
            public void run() {
                if (ui != null) {
                    ui.onShotStored(f, n);
                }
            }
        });
    }


    private void notifyUi() {
        if (ui == null) {
            return;
        }
        // 相机回调可能来自非 UI 线程，统一 post 到主线程再刷新界面
        handler.post(new Runnable() {
            public void run() {
                ui.onIntervalChanged();
            }
        });
    }
}

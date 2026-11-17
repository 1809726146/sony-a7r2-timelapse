package com.shenwenjun.a7r2app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.sony.scalar.sysutil.ScalarInput;

/**
 * v0.9
 *  - 预览 HUD：实时显示 光圈 / 快门 / ISO / 色温 / 模式 / 格式
 *  - 转盘直调：前拨轮改光圈、后拨轮改快门、控制轮改 ISO
 *  - FN 设置面板：模式 / ISO / 色温 / 格式 / 光圈 / 快门 / 静音
 *  - 间隔拍摄：上下键间隔、左右键张数、ENTER 开始停止
 */
public class MainActivity extends Activity implements SurfaceHolder.Callback, IntervalShooter.Ui {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private HudView hud;
    private MenuView settingsPanel;
    private View dimView;

    /** 夜间调暗档位（覆盖在取景画面上的黑色遮罩 alpha） */
    private static final int[] DIM_ALPHA = {0, 90, 150, 200};
    private int dimStep;

    private CameraController camera;
    private CameraSettings settings;
    private IntervalShooter interval;

    private boolean settingsMode;
    /** 三级菜单状态：0=选项卡 1=菜单组 2=菜单项目 */
    private int menuLevel = 0;
    private int menuTab = 1;
    private int menuGroup = 0;
    private int menuItem = 0;
    /** 上次调张数的时间，用于识别快速旋转 */
    private long lastShotsDial;
    private boolean settingsLoaded;
    /** 菜单当前停留项，变化时写一行日志，便于事后回放测试 */
    private String lastMenuKey = "";
    private boolean previewStarted;
    private int settingsRow;
    private int shotCount;
    private String lastKey = "-";


    private final Handler ui = new Handler();
    private final Runnable refreshTick = new Runnable() {
        public void run() {
            if (camera != null && camera.isOpen() && !camera.isCapturing()) {
                settings.refresh();
                render();
            }
            ui.postDelayed(this, 1000L);
        }
    };

    /** 面板/转盘的改动攒 120ms 再一次性写相机，转起来才跟手 */
    private final Runnable flushRunnable = new Runnable() {
        public void run() {
            if (settings != null) {
                settings.flush();
                render();
            }
        }
    };

    private void scheduleFlush() {
        ui.removeCallbacks(flushRunnable);
        ui.postDelayed(flushRunnable, 300L);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.init();
        Logger.i("=== app started === v5.0");
        Logger.i("model=" + Build.MODEL + " release=" + Build.VERSION.RELEASE
                + " sdk=" + Build.VERSION.SDK_INT + " log=" + Logger.getPath());

        setContentView(R.layout.main);
        hud = (HudView) findViewById(R.id.hud);
        settingsPanel = (MenuView) findViewById(R.id.settingsPanel);
        dimView = findViewById(R.id.dim);
        surfaceView = (SurfaceView) findViewById(R.id.surface);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(this);

        camera = new CameraController();
        settings = new CameraSettings(camera);
        interval = new IntervalShooter(camera, this);
        settings.setInterval(interval);
        settings.setPresets(new Presets(this));
        camera.setShotListener(interval);
        camera.setOnOpened(new Runnable() {
            public void run() {
                if (settings != null) {
                    settings.applyIfNeeded();
                }
            }
        });

        initFonts();
        render();
    }
    private void initFonts() {
        Fonts.logSystemFonts();
        Ui.init(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.i("onResume");
        SystemBridge.notifyAppInfo(this);
        SystemBridge.setAutoPowerOff(this, false);
        camera.open();
        if (camera.isOpen() && !settingsLoaded) {
            settings.load();
            settingsLoaded = true;
        }
        if (previewStarted) {
            camera.attach(surfaceHolder);
        }
        ui.removeCallbacks(refreshTick);
        ui.post(refreshTick);
        render();
    }

    @Override
    protected void onPause() {
        Logger.i("onPause");
        ui.removeCallbacks(refreshTick);
        ui.removeCallbacks(runTick);
        ui.removeCallbacks(flushRunnable);
        if (settings != null && settings.isDirty()) {
            settings.flush();
        }
        resetDim();
        if (interval.isRunning()) {
            interval.stop("app paused");
        }
        if (settingsMode) {
            settingsMode = false;
        }
        camera.close();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        SystemBridge.setAutoPowerOff(this, true);
        Logger.i("=== app stopped ===");
        super.onDestroy();
    }

    // ---------- SurfaceHolder.Callback ----------

    public void surfaceCreated(SurfaceHolder holder) {
        previewStarted = true;
        camera.attach(holder);
        render();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Logger.i("surfaceChanged " + width + "x" + height);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        previewStarted = false;
    }

    // ---------- IntervalShooter.Ui ----------

    public void onIntervalChanged() {
        // 拍摄结束（完成或手动停止）后把屏幕亮度恢复
        if (!interval.isRunning()) {
            resetDim();
            ui.removeCallbacks(runTick);
        } else {
            ui.removeCallbacks(runTick);
            ui.post(runTick);
        }
        render();
    }

    /** 恢复原始亮度（遮罩清零） */
    // ---------- 三级菜单（选项卡 → 组 → 项目）----------

    private boolean handleSettingsKey(int scan) {
        switch (scan) {
            // 上下：选择项目
            case ScalarInput.ISV_KEY_UP:
                menuMove(-1);
                return true;
            case ScalarInput.ISV_KEY_DOWN:
                menuMove(1);
                return true;

            // 左右：选择选项卡（只做选择，不改值）
            case ScalarInput.ISV_KEY_LEFT:
                if (menuTab == 3 && settings.presetPage() == 1) {
                    settings.closeFile();
                    menuItem = 0;
                    render();
                    return true;
                }
                switchTab(-1);
                return true;
            case ScalarInput.ISV_KEY_RIGHT:
                switchTab(1);
                return true;

            // 三个转盘：修改当前项目的参数
            case ScalarInput.ISV_DIAL_1_COUNTERCW:
            case ScalarInput.ISV_DIAL_2_COUNTERCW:
            case ScalarInput.ISV_DIAL_3_COUNTERCW:
                changeValue(-1);
                return true;
            case ScalarInput.ISV_DIAL_1_CLOCKWISE:
            case ScalarInput.ISV_DIAL_2_CLOCKWISE:
            case ScalarInput.ISV_DIAL_3_CLOCKWISE:
                changeValue(1);
                return true;

            case ScalarInput.ISV_KEY_CUSTOM2:
                cyclePreset();
                return true;

            // 确认键：执行当前项目（预设保存/载入、日志快照）；普通参数转盘已即时生效
            case ScalarInput.ISV_KEY_ENTER:
                confirmCurrentItem();
                return true;
            default:
                return true;
        }
    }

    /** 确认键：对"有动作"的项目执行，普通参数只提示 */
    private void confirmCurrentItem() {
        int item = settings.visibleItemAt(menuTab, menuItem);
        // 预设文件列表：进入文件
        if (item >= CameraSettings.ITEM_FILE_1 && item <= CameraSettings.ITEM_FILE_4) {
            settings.openFile(settings.fileSlot(item));
            menuItem = 0;
            Logger.i("preset open file #" + settings.fileSlot(item));
            render();
            return;
        }
        if (settings.confirmItem(item)) {
            Toast.makeText(this, settings.itemName(item) + " 已执行", Toast.LENGTH_SHORT).show();
            render();
            return;
        }
        Toast.makeText(this, settings.itemName(item) + "：转盘调整", Toast.LENGTH_SHORT).show();
    }

    /** 左右选择选项卡（列表常驻，只做选择） */
    private void switchTab(int dir) {
        int n = settings.tabCount();
        menuTab = (menuTab + dir + n) % n;
        menuGroup = 0;
        menuItem = 0;
        menuLevel = MenuView.LEVEL_ITEM;
        render();
    }
    /** 上下选择项目 */
    private void menuMove(int delta) {
        int n = settings.visibleItemCount(menuTab);
        if (n > 0) {
            menuItem = (menuItem + delta + n) % n;
        }
        render();
    }





    private void changeValue(int delta) {
        int item = settings.visibleItemAt(menuTab, menuItem);
        // 张数：快速转盘时步进 10
        if (item == CameraSettings.ITEM_SHOTS || item == CameraSettings.ITEM_PSET_SHOTS) {
            long now = System.currentTimeMillis();
            boolean fast = (now - lastShotsDial) < 160;
            lastShotsDial = now;
            int step = fast ? 10 : 1;
            if (interval != null) {
                int target = interval.totalShots() + delta * step;
                interval.setShots(target);
                scheduleFlush();
                render();
                return;
            }
        }
        if (delta > 0) {
            settings.next(item);
        } else {
            settings.prev(item);
        }
        scheduleFlush();
        render();
    }
    /** C2：循环切换到下一个非空预设 */
    private void cyclePreset() {
        // 从当前槽位的下一个开始，最多尝试 SLOTS 个槽位（每个只试一次）
        int start = settings.presetSlot();
        for (int i = 0; i < Presets.SLOTS; i++) {
            int slot = (start + i) % Presets.SLOTS + 1;
            if (settings.loadPreset(slot)) {
                Toast.makeText(this, "预设 " + slot + "：" + settings.presetSummary(slot),
                        Toast.LENGTH_SHORT).show();
                render();
                return;
            }
        }
        Logger.i("preset cycle: no saved preset");
        Toast.makeText(this, "没有已保存的预设", Toast.LENGTH_SHORT).show();
    }
    private void resetDim() {
        if (dimStep == 0) {
            return;
        }
        dimStep = 0;
        if (dimView != null) {
            dimView.setBackgroundColor(0x00000000);
        }
        Logger.i("dim reset (shooting stopped)");
    }

    /** 写卡完成：只记日志，不做任何回放（检视以相机系统为准） */
    public void onShotStored(String file, int index) {
        Logger.i("stored #" + index + " " + file);
    }

    /** 拍摄中按上键：循环调暗取景画面（0 / 90 / 150 / 200 alpha） */
    private void cycleDim() {
        dimStep = (dimStep + 1) % DIM_ALPHA.length;
        if (dimView != null) {
            dimView.setBackgroundColor(DIM_ALPHA[dimStep] << 24);
        }
        Logger.i("dim " + dimStep + " alpha=" + DIM_ALPHA[dimStep]);
    }

    // ---------- 按键 ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int scan = event.getScanCode();
        lastKey = KeyMap.name(scan);
        Logger.i("keyDown " + lastKey);
        boolean running = interval.isRunning();

        // FN：设置面板开关
        if (scan == ScalarInput.ISV_KEY_FN) {
            settingsMode = !settingsMode;
            if (settingsMode) {
                menuLevel = 0;
            }
            settingsRow = 0;
            Logger.i("settings mode " + (settingsMode ? "on" : "off"));
            render();
            return true;
        }
        // 物理曝光补偿拨盘：事件编码不稳定（拍完重开相机会从 code 1 变成 code 3），
        // 无法可靠映射成 ±1/3 EV，按方案 A 直接忽略。曝光补偿改用后拨轮 / FN 面板。
        if (scan == ScalarInput.ISV_KEY_EV_COMPENSATION) {
            return true;
        }
        if (settingsMode) {
            return handleSettingsKey(scan);
        }

        switch (scan) {
            // ---- 预览界面：前拨轮=光圈、后拨轮=快门（受档位限制）；ISO 只在面板调 ----
            case ScalarInput.ISV_DIAL_1_CLOCKWISE:
                stepPreview(1, true);
                return true;
            case ScalarInput.ISV_DIAL_1_COUNTERCW:
                stepPreview(-1, true);
                return true;
            case ScalarInput.ISV_DIAL_2_CLOCKWISE:
                stepPreview(1, false);
                return true;
            case ScalarInput.ISV_DIAL_2_COUNTERCW:
                stepPreview(-1, false);
                return true;
            // 后拨轮：调曝光补偿（曝光补偿拨盘本身只发无方向的 EV_COMP，只能用它）
            case ScalarInput.ISV_DIAL_3_CLOCKWISE:
                settings.adjustEv(1);
                render();
                scheduleFlush();
                return true;
            case ScalarInput.ISV_DIAL_3_COUNTERCW:
                settings.adjustEv(-1);
                render();
                scheduleFlush();
                return true;

            case ScalarInput.ISV_KEY_S1_1:
                camera.focus();
                return true;

            case ScalarInput.ISV_KEY_S2:
                if (running) {
                    interval.stop("shutter");
                } else if (camera.shoot()) {
                    shotCount++;
                }
                render();
                return true;

            // C2：一键切换间隔预设
            case ScalarInput.ISV_KEY_CUSTOM2:
                cyclePreset();
                return true;

            case ScalarInput.ISV_KEY_ENTER:
                if (running) {
                    interval.stop("enter");
                } else {
                    settings.flush();
                    interval.start();
                }
                return true;

            case ScalarInput.ISV_KEY_UP:
                if (interval.isRunning()) {
                    cycleDim();
                } else {
                    interval.intervalUp();
                    render();
                }
                return true;

            case ScalarInput.ISV_KEY_DOWN:
                interval.intervalDown();
                render();
                return true;

            case ScalarInput.ISV_KEY_RIGHT:
                interval.shotsUp();
                render();
                return true;

            case ScalarInput.ISV_KEY_LEFT:
                interval.shotsDown();
                render();
                return true;



            // C1：保存一份日志快照到 SD 卡，方便事后排查
            case ScalarInput.ISV_KEY_CUSTOM1:
                String saved = Logger.snapshot();
                if (saved != null) {
                    Logger.i("log snapshot -> " + saved);
                    String name = saved.substring(saved.lastIndexOf('/') + 1);
                    Toast.makeText(this, "日志已保存: " + name, Toast.LENGTH_LONG).show();
                } else {
                    Logger.e("log snapshot failed");
                    Toast.makeText(this, "日志保存失败", Toast.LENGTH_SHORT).show();
                }
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int scan = event.getScanCode();

        switch (scan) {
            case ScalarInput.ISV_KEY_S1_1:
                camera.cancelFocus();
                return true;

            case ScalarInput.ISV_KEY_S2:
                Logger.i("shutter released");
                return true;

            case ScalarInput.ISV_KEY_DELETE:
            case ScalarInput.ISV_KEY_SK2:
                if (settingsMode) {
                    settingsMode = false;
                    render();
                } else if (interval.isRunning()) {
                    interval.stop("delete");
                    render();
                } else {
                    Logger.i("exit requested by " + KeyMap.name(scan));
                    finish();
                }
                return true;

            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    /** 设置面板：上下选项、转盘改值、左右键（范围行切端点）、ENTER 退出 */

    /** 左右键：ISO范围行切换编辑下限/上限；其他行改值 */

    /** 预览界面拨轮：前拨轮=光圈、后拨轮=快门，不做档位拦截（相机不接受就自然无效果） */
    private void stepPreview(int dir, boolean aperture) {
        if (aperture) {
            settings.noteApertureStep(dir);
            Logger.i("preview aperture step " + dir + " (mode " + settings.modeText() + ")");
        } else {
            settings.noteShutterStep(dir);
            Logger.i("preview shutter step " + dir + " (mode " + settings.modeText() + ")");
        }
        scheduleFlush();
    }

    // ---------- 刷新/渲染 ----------

    private void delayedRefresh() {
        ui.postDelayed(new Runnable() {
            public void run() {
                if (camera.isOpen()) {
                    settings.refresh();
                    render();
                }
            }
        }, 250L);
    }

    private void render() {
        if (hud == null) {
            return;
        }
        if (settingsMode) {
            hud.setVisibility(View.GONE);
            settingsPanel.setVisibility(View.VISIBLE);
            int tabCount = settings.tabCount();
            if (menuTab >= tabCount) {
                menuTab = Math.max(0, tabCount - 1);
            }
            String[] tabs = new String[tabCount];
            for (int i = 0; i < tabCount; i++) {
                tabs[i] = settings.tabName(i);
            }
            int groupCount = settings.groupCount(menuTab);
            if (menuGroup >= groupCount) {
                menuGroup = Math.max(0, groupCount - 1);
            }
            String[] groups = new String[groupCount];
            for (int i = 0; i < groupCount; i++) {
                groups[i] = settings.groupName(menuTab, i);
            }
            int itemCount = settings.visibleItemCount(menuTab);
            if (menuItem >= itemCount) {
                menuItem = Math.max(0, itemCount - 1);
            }
            String[] labels = new String[itemCount];
            String[] values = new String[itemCount];
            for (int i = 0; i < itemCount; i++) {
                int item = settings.visibleItemAt(menuTab, i);
                labels[i] = CameraSettings.itemName(item);
                values[i] = settings.label(item);
            }
            settingsPanel.setState(menuTab, menuGroup, menuItem, menuLevel, false,
                    tabs, groups, labels, values);
            String mk = menuTab + "|" + menuItem + "|" + labels[menuItem] + "|" + values[menuItem];
            if (!mk.equals(lastMenuKey)) {
                lastMenuKey = mk;
                Logger.i("menu " + tabs[menuTab] + " / " + labels[menuItem] + " = " + values[menuItem]);
            }
            return;
        }

        settingsPanel.setVisibility(View.GONE);
        hud.setVisibility(View.VISIBLE);

        boolean running = interval.isRunning();
        int done = interval.slotCount();
        int total = interval.totalShots();

        // 左上：待机显示卡还能拍多少张，拍摄中显示本组还剩多少张
        int remain;
        if (running) {
            remain = Math.max(0, total - done);
        } else {
            int card = Storage.estimatedShots(settings.formatValue(), settings.isRawCompressed());
            remain = card > 0 ? card : total;
        }

        String fmt = settings.formatText();
        String kelvin = settings.kelvinText();
        String meterName = settings.label(CameraSettings.ITEM_METER);
        if (meterName == null) {
            meterName = "";
        }
        meterName = meterName.replace("测光", "");
        String topRight = running
                ? fmt + " \u00b7 " + kelvin
                : fmt + " \u00b7 " + kelvin + (meterName.length() > 0 ? " \u00b7 " + meterName + "测光" : "");

        int mv = settings.meterValue();
        if (mv > 9) {
            mv = 9;
        }
        if (mv < -9) {
            mv = -9;
        }
        // 拍摄中不画标尺，把曝光补偿拼到右上角，保证任何画面都能看到补偿值
        if (running && mv != 0) {
            topRight += " \u00b7 EV " + String.format("%+.1f", mv * settings.evStep());
        }

        HudView.S st = new HudView.S();
        st.running = running;
        st.finished = interval.isDone();
        st.remain = String.valueOf(remain);
        st.topRight = topRight;
        // 预览里已经单独画了 ISO 标签，这里只取值（否则会变成「ISO ISO 4000」）
        String iso = settings.isoLabel();
        if (iso != null && iso.startsWith("ISO ")) {
            iso = iso.substring(4);
        }
        st.iso = iso;
        st.shutter = toNativeShutter(settings.shutterShort());
        st.aperture = toNativeAperture(settings.apertureText());
        st.meter = mv;
        st.evStep = settings.evStep();
        st.interval = interval.intervalText();
        st.shots = String.valueOf(total);
        st.done = String.valueOf(done);
        st.doneCount = done;
        st.total = Math.max(1, total);
        if (running) {
            // 倒计时只显示整秒（向上取整，0 时正好触发）
            long ms = interval.nextInMs();
            st.big = ms <= 0 ? "0" : String.valueOf((ms + 999) / 1000);
            st.saved = "已存 " + interval.savedCount();
            st.hint = "快门 暂停    上键 调光    FN 退出";
        } else {
            st.saved = "";
            st.hint = "FN 菜单    中央 开始";
            if (st.finished) {
                st.summary = "已完成 " + done + " / " + total + "    已存 " + interval.savedCount()
                        + "    跳过 " + interval.skippedCount();
            }
        }
        hud.set(st);
    }

    /** f/4.0 -> F4.0（原生样式） */
    private static String toNativeAperture(String a) {
        if (a == null) {
            return "";
        }
        if (a.startsWith("f/")) {
            return "F" + a.substring(2);
        }
        return a;
    }

    /** 60 -> 1/60；2.5s 保持 */
    private static String toNativeShutter(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        if (s.endsWith("s") || s.indexOf('/') >= 0) {
            return s;
        }
        return "1/" + s;
    }

    /** 拍摄中的倒计时刷新（250ms 一次，整帧重画一次 HUD 很便宜） */
    private final Runnable runTick = new Runnable() {
        public void run() {
            if (interval != null && interval.isRunning()) {
                render();
                ui.postDelayed(this, 250L);
            }
        }
    };

    /** 20:50 / 2:46:39 */
    private static String formatDuration(int secs) {
        if (secs < 0) {
            secs = 0;
        }
        int d = secs / 86400;
        if (d > 0) {
            int hh = (secs % 86400) / 3600;
            int mm = (secs % 3600) / 60;
            int ss = secs % 60;
            return d + "天" + (hh < 10 ? "0" : "") + hh + ":" + (mm < 10 ? "0" : "") + mm
                    + ":" + (ss < 10 ? "0" : "") + ss;
        }
        int h = secs / 3600;
        int m = (secs % 3600) / 60;
        int s = secs % 60;
        String ss = s < 10 ? "0" + s : String.valueOf(s);
        if (h > 0) {
            String mm = m < 10 ? "0" + m : String.valueOf(m);
            return h + ":" + mm + ":" + ss;
        }
        return m + ":" + ss;
    }

}

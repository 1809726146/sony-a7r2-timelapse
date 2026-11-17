package com.shenwenjun.a7r2app;

import java.util.ArrayList;

/**
 * App 内的相机设置 + HUD 读数。
 *
 * 数值型参数一律"步进"而不是枚举：
 *   ISO      每次 ±100（AUTO ↔ 100 … 102400）
 *   色温     每次 ±100K（AUTO ↔ 2500 … 9900）
 *   光圈/快门 用 CameraEx 的 increment/decrement，读回显示
 *
 * ISO 不是 AUTO 时，设置面板里不显示"ISO 范围"这一项；
 * 切到 AUTO 后才出现，并且左右键在"下限/上限"之间切换，转盘改值。
 */
public class CameraSettings {

    public static final int ITEM_MODE = 0;
    public static final int ITEM_ISO = 1;
    public static final int ITEM_WB = 3;
    public static final int ITEM_FORMAT = 4;
    public static final int ITEM_APERTURE = 5;
    public static final int ITEM_SHUTTER = 6;
    public static final int ITEM_SILENT = 7;
    public static final int ITEM_EV = 9;
    public static final int ITEM_IS = 10;
    public static final int ITEM_FOCUS = 11;
    public static final int ITEM_RAW = 12;
    public static final int ITEM_METER = 13;
    public static final int ITEM_INT_H = 14;
    public static final int ITEM_INT_M = 15;
    public static final int ITEM_INT_S = 16;
    public static final int ITEM_PRESET_LOAD = 17;
    public static final int ITEM_PRESET_SAVE = 18;
    public static final int ITEM_SHOTS = 19;
    public static final int ITEM_PRESET_SLOT = 21;
    public static final int ITEM_PSET_H = 22;
    public static final int ITEM_PSET_M = 23;
    public static final int ITEM_PSET_S = 24;
    public static final int ITEM_PSET_SHOTS = 25;
    public static final int ITEM_PSET_SCENE = 26;
    public static final int ITEM_ISO_MIN = 27;
    public static final int ITEM_ISO_MAX = 28;
    public static final int ITEM_FILE_1 = 31;
    public static final int ITEM_FILE_2 = 32;
    public static final int ITEM_FILE_3 = 33;
    public static final int ITEM_FILE_4 = 34;

    /** AUTO 时多一行"ISO范围" */
    // ---------- 三级菜单树（参照 α6700：选项卡 → 组 → 项目）----------

    public static final String[] TAB_NAMES = {"拍摄", "设置", "延时设置", "预设"};

    private static final String[][] GROUP_NAMES = {
            {"拍摄参数"},
            {"相机设置"},
            {"延时参数"},
            {"预设管理"}
    };

    /** 预设二级页里的项目（0=文件列表，1=文件参数） */
    private static final int[] PRESET_FILE_ITEMS = {
            ITEM_PSET_H, ITEM_PSET_M, ITEM_PSET_S, ITEM_PSET_SHOTS,
            ITEM_PSET_SCENE, ITEM_PRESET_SAVE, ITEM_PRESET_LOAD
    };

    private int presetPage;      // 0=文件列表 1=文件内参数

    public int presetPage() {
        return presetPage;
    }

    public void setPresetPage(int page) {
        presetPage = page;
    }

    /** ISO 是否处于 AUTO */
    public boolean isIsoAuto() {
        return isoValue == 0;
    }

    /** 拍摄页项目：ISO 为 AUTO 时在 ISO 之后插入上下限两项 */
    private static final int[] SHOT_AUTO_EXTRA = {ITEM_ISO_MIN, ITEM_ISO_MAX};

    public int shotItemCount() {
        return 7 + (isIsoAuto() ? 2 : 0);
    }

    public int shotItemAt(int i) {
        if (isIsoAuto()) {
            if (i == 2) {
                return ITEM_ISO_MIN;
            }
            if (i == 3) {
                return ITEM_ISO_MAX;
            }
            if (i > 3) {
                i -= 2;
            }
        }
        return itemAt(0, 0, i);
    }

    /** 当前选项卡的可见项目数（预设页按层级变化） */
    public int visibleItemCount(int tab) {
        if (tab == 3 && presetPage == 1) {
            return PRESET_FILE_ITEMS.length;
        }
        if (tab == 3) {
            return 4;
        }
        if (tab == 0) {
            return shotItemCount();
        }
        return itemCount(tab, 0);
    }

    /** 当前选项卡第 i 个可见项目 */
    public int visibleItemAt(int tab, int i) {
        if (tab == 3 && presetPage == 1) {
            if (i < 0 || i >= PRESET_FILE_ITEMS.length) {
                return PRESET_FILE_ITEMS[0];
            }
            return PRESET_FILE_ITEMS[i];
        }
        if (tab == 3) {
            int[] files = {ITEM_FILE_1, ITEM_FILE_2, ITEM_FILE_3, ITEM_FILE_4};
            return files[Math.max(0, Math.min(i, files.length - 1))];
        }
        if (tab == 0) {
            return shotItemAt(i);
        }
        return itemAt(tab, 0, i);
    }

    private static final int[][][] TREE = {
            {{ITEM_MODE, ITEM_ISO, ITEM_SHUTTER, ITEM_APERTURE,
              ITEM_FORMAT, ITEM_RAW, ITEM_WB}},                                  // 拍摄（AUTO 时动态加两项）
            {{ITEM_FOCUS, ITEM_METER, ITEM_SILENT, ITEM_IS}},                    // 设置
            {{ITEM_INT_H, ITEM_INT_M, ITEM_INT_S, ITEM_SHOTS}},                  // 延时设置
            {{ITEM_FILE_1, ITEM_FILE_2, ITEM_FILE_3, ITEM_FILE_4}}               // 预设（文件列表）
    };
    public int tabCount() {
        return TAB_NAMES.length;
    }

    public String tabName(int t) {
        return TAB_NAMES[Math.max(0, Math.min(t, TAB_NAMES.length - 1))];
    }

    public int groupCount(int t) {
        if (t < 0 || t >= TREE.length) {
            return 0;
        }
        return TREE[t].length;
    }

    public String groupName(int t, int g) {
        if (t < 0 || t >= GROUP_NAMES.length || g < 0 || g >= GROUP_NAMES[t].length) {
            return "";
        }
        return (g + 1) + ". " + GROUP_NAMES[t][g];
    }

    public int itemCount(int t, int g) {
        if (t < 0 || t >= TREE.length || g < 0 || g >= TREE[t].length) {
            return 0;
        }
        return TREE[t][g].length;
    }

    public int itemAt(int t, int g, int i) {
        if (t < 0 || t >= TREE.length || g < 0 || g >= TREE[t].length) {
            return ITEM_MODE;
        }
        int[] arr = TREE[t][g];
        if (i < 0 || i >= arr.length) {
            return arr[0];
        }
        return arr[i];
    }

    private static final String[] IS_VALUES = {"off", "onetime", "keep"};
    private static final String[] IS_LABELS = {"关", "仅拍摄", "常开"};
    private static final String[] FOCUS_LABELS = {"MF", "AF-S", "AF-C", "AF-A", "DMF"};
    /** 对焦方式 -> focus-mode 参数值 */
    private static final String[] FOCUS_MODES = {"manual", "auto", "auto", "auto", "dmf"};
    /** 对焦方式 -> auto-focus-mode 参数值（MF/DMF 不需要） */
    private static final String[] AF_VALUES = {null, "af-s", "af-c", "af-a", null};
    private static final String[] RAW_VALUES = {"compress", "uncompress"};
    private static final String[] RAW_LABELS = {"压缩", "未压缩"};
    private static final String[] METER_VALUES = {"multi", "center-weighted", "center-spot"};
    private static final String[] METER_LABELS = {"多重", "中央重点", "点测"};

    /** 拍完后检视浮层显示的秒数 */

    private static final String[] MODE_VALUES = {
            "program-auto", "aperture-priority", "shutter-speed", "manual-exposure"};
    private static final String[] MODE_LABELS = {"P", "A", "S", "M"};

    private static final String[] FORMAT_VALUES = {"jpeg", "raw", "rawjpeg"};
    private static final String[] FORMAT_LABELS = {"JPEG", "RAW", "RAW+JPEG"};

    private static final int ISO_MIN_VALUE = 100;
    private static final int ISO_MAX_VALUE = 102400;
    private static final int ISO_STEP = 100;

    private static final int KELVIN_MIN = 2500;
    private static final int KELVIN_MAX = 9900;
    private static final int KELVIN_STEP = 100;

    /** 相机真实支持的 ISO 档位（从 iso-sensitivity-values 解析） */
    private int[] isoList = {0, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600, 51200, 102400};
    /** AUTO ISO 上下限的可选值（iso-auto-max-min-values） */
    private int[] rangeList = {100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600, 51200, 102400};

    private final CameraController camera;
    private IntervalShooter interval;
    private Presets presets;
    private int presetSlot = 1;
    private String presetScene;

    private int modeIndex;
    private int formatIndex;

    /** 0 = AUTO */
    private int isoValue;
    /** 0 = AUTO */
    private int kelvin;

    private int isoMin = 100;
    private int isoMax = 1600;
    /** ISO范围行里，左右键切换编辑哪一端 */
    private boolean rangeMaxSelected;

    private boolean silent;
    private int isIndex = 1;       // 防抖：默认"仅拍摄"
    private int focusIndex = 1;    // 默认 AF-S
    private int rawIndex;          // 默认压缩
    private int meterIndex;        // 默认多重
    private boolean customized;

    /** 需要攒着一起写（防抖），避免每拨一格就做一次 setParameters */
    private boolean dirtyApply;
    private boolean dirtySteps;
    private boolean pendingAperture;
    private boolean pendingShutter;

    /** 用户手动调过的光圈(f×100)/快门(分数)，重开相机后要还原 */
    private int desiredAperture = -1;
    private int desiredShutterN = -1;
    private int desiredShutterD = -1;

    // HUD 用的实时读数（从相机回读）
    private String apertureText = "f/-";
    private String shutterText = "-";
    private String isoText = "-";
    private String kelvinText = "-";
    private String modeText = "-";
    private String formatText = "-";
    private String shutterShort = "-";

    private int evCur;
    /** 要写入相机的曝光补偿（1/3 EV 步） */
    private int evTarget;
    private boolean evDirty;
    /** 只有 ISO/范围/色温/格式需要回读校验，模式和曝光补偿不需要 */
    private boolean verifyNeeded;
    private int lastMeterValue = Integer.MIN_VALUE;
    /** 预览标尺是 ±3EV，补偿范围也收到 ±9 格（3EV / (1/3EV)） */
    private static final int EV_LIMIT = -9;
    private int evMin = -15;
    private int evMax = 15;
    private float evStep = 0.33333f;

    public CameraSettings(CameraController camera) {
        this.camera = camera;
    }

    public void setInterval(IntervalShooter interval) {
        this.interval = interval;
    }

    public void setPresets(Presets presets) {
        this.presets = presets;
    }

    /** 当前选中的预设槽位（1..4） */
    public int presetSlot() {
        return presetSlot;
    }

    /** 供外部（C2 键）调用：载入某槽位 */
    public boolean loadPreset(int slot) {
        if (presets == null || interval == null) {
            return false;
        }
        if (presets.isEmpty(slot)) {
            return false;
        }
        int[] v = presets.get(slot);
        if (v == null) {
            return false;
        }
        presetSlot = slot;
        interval.setIntervalSec(v[0]);
        interval.setShots(v[1]);
        presetScene = presets.scene(slot);
        Logger.i("preset load #" + slot + " -> " + Presets.formatInterval(v[0]) + " x" + v[1]);
        return true;
    }

    /** 保存当前间隔+张数到槽位 */
    public boolean savePreset(int slot) {
        if (presets == null || interval == null) {
            return false;
        }
        presets.save(slot, interval.intervalSec(), interval.totalShots());
        presets.setScene(slot, presetScene == null ? Presets.SCENES[0] : presetScene);
        Logger.i("preset save #" + slot + " <- " + interval.intervalText() + " x" + interval.totalShots());
        return true;
    }

    /** 中央键确认时可能触发的动作（预设保存/载入等） */
    public boolean confirmItem(int item) {
        if (item == ITEM_PRESET_SAVE) {
            return savePreset(presetSlot);
        }
        if (item == ITEM_PRESET_LOAD) {
            return loadPreset(presetSlot);
        }
        return false;
    }

    /** 预设槽位的摘要文字 */
    public String presetSummary(int slot) {
        return presets == null ? "?" : presets.summary(slot);
    }

    public boolean isCustomized() {
        return customized;
    }

    /** 面板里当前可见多少行 */

    /** 第 row 行对应哪个设置项 */

    // ---------- 读 ----------

    public void load() {
        isoList = parseIntList(camera.getParameter("iso-sensitivity-values"), isoList);
        rangeList = parseIntList(camera.getParameter("iso-auto-max-min-values"), rangeList);
        Logger.i("iso list=" + isoList.length + " items, range list=" + rangeList.length + " items");

        modeIndex = indexOf(MODE_VALUES, camera.getParameter("scene-mode"), 0);

        isoValue = readIntDefault("iso-sensitivity", 0);
        if (indexOfValue(isoList, isoValue) < 0) {
            isoValue = 0;
        }
        isoMin = readIntDefault("iso-auto-min", rangeList[0]);
        isoMax = readIntDefault("iso-auto-max", rangeList[rangeList.length - 1]);
        if (indexOfValue(rangeList, isoMin) < 0) {
            isoMin = rangeList[0];
        }
        if (indexOfValue(rangeList, isoMax) < 0) {
            isoMax = rangeList[rangeList.length - 1];
        }
        if (isoMax < isoMin) {
            isoMax = isoMin;
        }

        String wb = camera.getParameter("whitebalance");
        if (wb != null && wb.startsWith("color-temp")) {
            kelvin = clamp(readIntDefault("color-temperture-white-balance", 4800), KELVIN_MIN, KELVIN_MAX);
        } else {
            kelvin = 0;
        }

        formatIndex = indexOf(FORMAT_VALUES, camera.getParameter("storage-fmt"), 1);
        silent = "true".equals(camera.getParameter("silent-shutter-mode"));

        // 对焦：先看 focus-mode（manual / dmf / auto），再回退到 auto-focus-mode
        String focusMode = camera.getParameter("focus-mode");
        String af = null;
        String[] extras = camera.readExtras();
        if (extras != null) {
            isIndex = indexOf(IS_VALUES, extras[0], 1);
            af = extras[1];
            rawIndex = indexOf(RAW_VALUES, extras[2], 0);
            meterIndex = indexOf(METER_VALUES, extras[3], 0);
        }
        if ("manual".equals(focusMode)) {
            focusIndex = 0;
        } else if ("dmf".equals(focusMode)) {
            focusIndex = 4;
        } else if ("af-c".equals(af)) {
            focusIndex = 2;
        } else if ("af-a".equals(af)) {
            focusIndex = 3;
        } else {
            focusIndex = 1;
        }
        Logger.i("focus: mode=" + focusMode + " af=" + af + " -> " + FOCUS_LABELS[focusIndex]);
        refresh();
        evTarget = evCur;
        // 启动时以相机为准：清掉定制标记，避免把上一轮的值反写回相机
        customized = false;
        dirtyApply = false;
        evDirty = false;
        Logger.i("settings loaded: " + summary());
        Logger.i("camera snapshot: scene=" + camera.getParameter("scene-mode")
                + " iso=" + camera.getParameter("iso-sensitivity")
                + " range=" + camera.getParameter("iso-auto-min") + "-" + camera.getParameter("iso-auto-max")
                + " wb=" + camera.getParameter("whitebalance")
                + " kelvin=" + camera.getParameter("color-temperture-white-balance")
                + " fmt=" + camera.getParameter("storage-fmt")
                + " silent=" + camera.getParameter("silent-shutter-mode"));
    }

    /** 回读当前光圈/快门/ISO/色温/模式/格式，用于 HUD */
    public void refresh() {
        CameraController.Snapshot snap = camera.readSnapshot(new String[]{
                "aperture", "shutter-speed-nume", "shutter-speed-deno",
                "iso-sensitivity", "color-temperture-white-balance",
                "scene-mode", "storage-fmt", "whitebalance", "silent-shutter-mode",
                "iso-auto-min", "iso-auto-max"});
        if (snap == null) {
            return;
        }
        String[] v = snap.values;
        apertureText = formatAperture(v[0]);
        shutterText = formatShutter(v[1], v[2]);
        shutterShort = shortShutter(v[1], v[2]);
        modeText = shortMode(v[5]);
        formatText = shortFormat(v[6]);
        kelvinText = shortKelvin(v[7], v[4]);
        silent = "true".equals(v[8]);
        evCur = snap.ev;
        evMin = snap.evMin;
        evMax = snap.evMax;
        evStep = snap.evStep;
        // 这里不覆盖 evTarget，否则每秒一次刷新会打断面板里的编辑
        int min = readIntDefault("iso-auto-min", isoMin);
        int max = readIntDefault("iso-auto-max", isoMax);
        isoText = formatIso(v[3], min, max, camera.getAutoIso());
        logMeterChange();

        if (pendingAperture) {
            pendingAperture = false;
            try {
                int a = Integer.parseInt(trim(v[0], ""));
                if (a > 0) {
                    desiredAperture = a;
                    Logger.i("aperture now " + formatAperture(v[0]));
                }
            } catch (Throwable ignored) {
            }
        }
        if (pendingShutter) {
            pendingShutter = false;
            try {
                int n = Integer.parseInt(trim(v[1], ""));
                int d = Integer.parseInt(trim(v[2], ""));
                if (n > 0 && d > 0) {
                    desiredShutterN = n;
                    desiredShutterD = d;
                    Logger.i("shutter now " + formatShutter(v[1], v[2]));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------- 改 ----------

    public void next(int item) {
        change(item, 1);
    }

    public void prev(int item) {
        change(item, -1);
    }


    private void change(int item, int delta) {
        switch (item) {
            case ITEM_MODE:
                modeIndex = wrap(modeIndex + delta, MODE_VALUES.length);
                customized = true;
                dirtyApply = true;
                verifyNeeded = false;
                break;
            case ITEM_EV:
                adjustEv(delta);
                break;
            case ITEM_ISO:
                stepIso(delta);
                customized = true;
                dirtyApply = true;
                verifyNeeded = true;
                break;
            case ITEM_ISO_MIN: {
                int i = clampIndex(indexOfValue(rangeList, isoMin) + delta, rangeList.length);
                isoMin = rangeList[i];
                if (isoMin > isoMax) {
                    isoMax = isoMin;
                }
                customized = true;
                dirtyApply = true;
                break;
            }
            case ITEM_ISO_MAX: {
                int i = clampIndex(indexOfValue(rangeList, isoMax) + delta, rangeList.length);
                isoMax = rangeList[i];
                if (isoMax < isoMin) {
                    isoMin = isoMax;
                }
                customized = true;
                dirtyApply = true;
                break;
            }
            case ITEM_FILE_1:
            case ITEM_FILE_2:
            case ITEM_FILE_3:
            case ITEM_FILE_4:
                // 文件列表层不直接改值
                break;
            case ITEM_PSET_H:
                if (interval != null) {
                    interval.adjustHours(delta);
                }
                break;
            case ITEM_PSET_M:
                if (interval != null) {
                    interval.adjustMinutes(delta);
                }
                break;
            case ITEM_PSET_S:
                if (interval != null) {
                    interval.adjustSeconds(delta);
                }
                break;
            case ITEM_PSET_SHOTS:
                if (interval != null) {
                    if (delta > 0) {
                        interval.shotsUp();
                    } else {
                        interval.shotsDown();
                    }
                }
                break;
            case ITEM_PSET_SCENE:
                if (presets != null) {
                    String cur = presets.scene(presetSlot);
                    int idx = 0;
                    for (int i = 0; i < Presets.SCENES.length; i++) {
                        if (Presets.SCENES[i].equals(cur)) {
                            idx = i;
                        }
                    }
                    idx = (idx + delta + Presets.SCENES.length) % Presets.SCENES.length;
                    presets.setScene(presetSlot, Presets.SCENES[idx]);
                    presetScene = Presets.SCENES[idx];
                }
                break;
            case ITEM_WB:
                stepKelvin(delta);
                customized = true;
                dirtyApply = true;
                break;
            case ITEM_FORMAT:
                formatIndex = wrap(formatIndex + delta, FORMAT_VALUES.length);
                customized = true;
                dirtyApply = true;
                break;
            case ITEM_APERTURE:
                noteApertureStep(delta);
                break;
            case ITEM_SHUTTER:
                noteShutterStep(delta);
                break;
            case ITEM_IS:
                isIndex = wrap(isIndex + delta, IS_VALUES.length);
                customized = true;
                dirtyApply = true;
                verifyNeeded = true;
                break;
            case ITEM_FOCUS:
                focusIndex = wrap(focusIndex + delta, FOCUS_LABELS.length);
                customized = true;
                dirtyApply = true;
                verifyNeeded = false;
                break;
            case ITEM_RAW:
                rawIndex = wrap(rawIndex + delta, RAW_VALUES.length);
                customized = true;
                dirtyApply = true;
                verifyNeeded = true;
                break;
            case ITEM_METER:
                meterIndex = wrap(meterIndex + delta, METER_VALUES.length);
                customized = true;
                dirtyApply = true;
                verifyNeeded = true;
                break;
            case ITEM_SHOTS:
                if (interval != null) {
                    if (delta > 0) {
                        interval.shotsUp();
                    } else {
                        interval.shotsDown();
                    }
                }
                break;
            case ITEM_PRESET_LOAD:
                if (delta != 0) {
                    presetSlot = wrapSlot(presetSlot - 1 + delta) + 1;
                    loadPreset(presetSlot);
                }
                break;
            case ITEM_PRESET_SAVE:
                if (delta != 0) {
                    presetSlot = wrapSlot(presetSlot - 1 + delta) + 1;
                    savePreset(presetSlot);
                }
                break;
            case ITEM_INT_H:
                if (interval != null) {
                    interval.adjustHours(delta);
                }
                break;
            case ITEM_INT_M:
                if (interval != null) {
                    interval.adjustMinutes(delta);
                }
                break;
            case ITEM_INT_S:
                if (interval != null) {
                    interval.adjustSeconds(delta);
                }
                break;
            case ITEM_SILENT:
                silent = !silent;
                camera.applyParameters(new String[]{"silent-shutter-mode"},
                        new String[]{silent ? "true" : "false"});
                Logger.i("silent shutter -> " + silent);
                break;
            default:
                break;
        }
    }

    // ---------- 防抖：攒够了再写相机 ----------

    public boolean isDirty() {
        return dirtyApply || dirtySteps;
    }

    /** 把攒下的改动写进相机，并回读一次 */
    public void flush() {
        if (dirtyApply) {
            dirtyApply = false;
            apply();
        }
        if (dirtySteps) {
            dirtySteps = false;
            refresh();
        }
    }

    /** 预览界面转拨轮：只发步进命令，回读留给防抖刷新 */
    public void noteApertureStep(int dir) {
        camera.stepAperture(dir);
        pendingAperture = true;
        dirtySteps = true;
    }

    public void noteShutterStep(int dir) {
        camera.stepShutter(dir);
        pendingShutter = true;
        dirtySteps = true;
    }

    public boolean canStepAperture() {
        return "A".equals(modeText) || "M".equals(modeText);
    }

    public boolean canStepShutter() {
        return "S".equals(modeText) || "M".equals(modeText);
    }

    /** AUTO → 下一个支持档 … ；已是最低档再往下回 AUTO。走相机真实档位，避免写进不支持的值 */
    private void stepIso(int delta) {
        int idx = indexOfValue(isoList, isoValue);
        if (idx < 0) {
            idx = 0;
        }
        int next = idx + delta;
        if (next < 0) {
            next = 0;
        }
        if (next >= isoList.length) {
            next = isoList.length - 1;
        }
        isoValue = isoList[next];
    }

    /** AUTO → 2500K → 2600K … ；2500K 再往下回 AUTO */
    private void stepKelvin(int delta) {
        if (kelvin == 0 && delta > 0) {
            kelvin = KELVIN_MIN;
        } else if (kelvin == KELVIN_MIN && delta < 0) {
            kelvin = 0;
        } else if (kelvin > 0) {
            kelvin = clamp(kelvin + delta * KELVIN_STEP, KELVIN_MIN, KELVIN_MAX);
        }
    }

    public boolean apply() {
        String wb = kelvin == 0 ? "auto" : "color-temp";
        boolean writeEv = evDirty;
        evDirty = false;
        CameraController.Settings req = new CameraController.Settings();
        req.iso = isoValue;
        req.isoMin = isoMin;
        req.isoMax = isoMax;
        req.kelvin = kelvin;
        req.ev = evTarget;
        req.format = FORMAT_VALUES[formatIndex];
        req.wb = wb;
        req.sceneMode = MODE_VALUES[modeIndex];
        req.antiHandBlur = IS_VALUES[isIndex];
        req.autoFocusMode = AF_VALUES[focusIndex];
        req.focusMode = FOCUS_MODES[focusIndex];
        req.rawRecMode = RAW_VALUES[rawIndex];
        req.metering = METER_VALUES[meterIndex];
        req.silent = silent;
        req.writeEv = writeEv;
        req.verify = verifyNeeded;
        boolean ok = camera.applySettings(req);
        syncFromCamera();
        Logger.i("settings apply " + (ok ? "ok" : "FAILED") + ": " + summary());
        return ok;
    }

    /** 用相机回读值校正本地状态（相机可能夹紧或忽略某些值） */
    private void syncFromCamera() {
        CameraController.Snapshot snap = camera.readSnapshot(new String[]{
                "iso-sensitivity", "iso-auto-min", "iso-auto-max", "storage-fmt",
                "whitebalance", "color-temperture-white-balance"});
        if (snap != null) {
            int isoNow = parseInt(trim(snap.values[0], ""), isoValue);
            if (indexOfValue(isoList, isoNow) >= 0) {
                isoValue = isoNow;
            }
            int mn = parseInt(trim(snap.values[1], ""), isoMin);
            int mx = parseInt(trim(snap.values[2], ""), isoMax);
            if (indexOfValue(rangeList, mn) >= 0) {
                isoMin = mn;
            }
            if (indexOfValue(rangeList, mx) >= 0) {
                isoMax = mx;
            }
            String wbNow = snap.values[4];
            if (wbNow != null) {
                if (wbNow.startsWith("color-temp")) {
                    int k = parseInt(trim(snap.values[5], ""), kelvin);
                    if (k > 0) {
                        kelvin = k;
                    }
                } else {
                    kelvin = 0;
                }
            }
            int fi = indexOf(FORMAT_VALUES, snap.values[3], -1);
            if (fi >= 0) {
                formatIndex = fi;
            }
            evCur = snap.ev;
            evMin = snap.evMin;
            evMax = snap.evMax;
            evStep = snap.evStep;
        }
        if (evMin == 0 && evMax == 0) {   // M 档相机可能报 0..0
            evMin = -15;
            evMax = 15;
        }
        // 注意：这里不回写 evTarget。回写会把手感变成"跳来跳去"——
        // 转盘连续转时，apply 还没写完就被下一次回读覆盖，差值就丢了。
    }

    /** HUD 标尺值变化时记一条，方便确认 M 档是否在动 */
    private void logMeterChange() {
        int mv = meterValue();
        if (mv != lastMeterValue) {
            lastMeterValue = mv;
            Logger.i("meter " + mv + " (mode=" + modeText + ", evCur=" + evCur
                    + ", level=" + camera.getExposureLevel() + ")");
        }
    }

    public void applyIfNeeded() {
        if (customized) {
            // 用户改过参数后，每次相机重开都重新写一遍，保证设置"锁住"
            // （之前会先比对，比对不准则跳过；实测相机会自行改写部分值，导致退出 FN 后设置丢失）
            dirtyApply = false;
            apply();
            restoreAperture();
            restoreShutter();
        }
        refresh();
    }

    /** 相机当前值是否已经等于我们要的（一次 getParameters 读完） */
    private boolean cameraMatches() {
        String[] v = camera.readKeys(new String[]{
                "iso-sensitivity", "iso-auto-min", "iso-auto-max", "storage-fmt",
                "whitebalance", "color-temperture-white-balance", "scene-mode"});
        if (v == null) {
            return false;
        }
        if (!strEq(v[0], String.valueOf(isoValue))
                || !strEq(v[1], String.valueOf(isoMin))
                || !strEq(v[2], String.valueOf(isoMax))
                || !strEq(v[3], FORMAT_VALUES[formatIndex])
                || !strEq(v[6], MODE_VALUES[modeIndex])) {
            return false;
        }
        String wb = trim(v[4], "");
        if (kelvin == 0) {
            return "auto".equals(wb);
        }
        return "color-temp".equals(wb) && strEq(v[5], String.valueOf(kelvin));
    }

    private boolean strEq(String a, String b) {
        return a != null && a.trim().equals(b);
    }

    // ---------- 光圈/快门步进 & 还原 ----------

    private void stepAperture(int dir) {
        noteApertureStep(dir);
    }

    private void stepShutter(int dir) {
        noteShutterStep(dir);
    }

    /** 重开相机后把光圈走回用户要的值（只在 A/M 档） */
    private void restoreAperture() {
        if (desiredAperture <= 0) {
            return;
        }
        String mode = camera.getParameter("scene-mode");
        if (!"aperture-priority".equals(mode) && !"manual-exposure".equals(mode)) {
            return;
        }
        int steps = 0;
        int dir = 0;
        for (int i = 0; i < 40; i++) {
            int cur = readIntDefault("aperture", -1);
            if (cur <= 0 || cur == desiredAperture) {
                break;
            }
            if (dir == 0) {
                dir = cur < desiredAperture ? 1 : -1;
            }
            camera.stepAperture(dir);
            int after = readIntDefault("aperture", -1);
            if (after <= 0 || after == cur) {
                break;
            }
            steps++;
            if (Math.abs(after - desiredAperture) > Math.abs(cur - desiredAperture)) {
                dir = -dir;
            }
        }
        if (steps > 0) {
            Logger.i("aperture restored " + formatAperture(camera.getParameter("aperture"))
                    + " target=" + desiredAperture + " steps=" + steps);
        }
    }

    /** 重开相机后把快门走回用户要的值（只在 S/M 档） */
    private void restoreShutter() {
        if (desiredShutterN <= 0 || desiredShutterD <= 0) {
            return;
        }
        String mode = camera.getParameter("scene-mode");
        if (!"shutter-speed".equals(mode) && !"manual-exposure".equals(mode)) {
            return;
        }
        double target = desiredShutterN / (double) desiredShutterD;
        int steps = 0;
        int dir = 0;
        for (int i = 0; i < 40; i++) {
            double cur = readShutterSeconds();
            if (cur <= 0 || Math.abs(cur - target) < 1e-6) {
                break;
            }
            if (dir == 0) {
                dir = cur > target ? 1 : -1;
            }
            camera.stepShutter(dir);
            double after = readShutterSeconds();
            if (after <= 0 || after == cur) {
                break;
            }
            steps++;
            if (Math.abs(after - target) > Math.abs(cur - target)) {
                dir = -dir;
            }
        }
        if (steps > 0) {
            Logger.i("shutter restored " + formatShutter(camera.getParameter("shutter-speed-nume"),
                    camera.getParameter("shutter-speed-deno")) + " target=" + target + " steps=" + steps);
        }
    }

    private double readShutterSeconds() {
        int n = readIntDefault("shutter-speed-nume", -1);
        int d = readIntDefault("shutter-speed-deno", -1);
        if (n <= 0 || d <= 0) {
            return -1;
        }
        return n / (double) d;
    }

    private int parseInt(String v, int fallback) {
        try {
            return Integer.parseInt(v);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private int readIntDefault(String key, int fallback) {
        try {
            return Integer.parseInt(trim(camera.getParameter(key), ""));
        } catch (Throwable t) {
            return fallback;
        }
    }

    // ---------- 显示 ----------

    public String label(int item) {
        switch (item) {
            case ITEM_MODE:
                return modeText.length() > 0 && !"-".equals(modeText) ? modeText : MODE_LABELS[modeIndex];
            case ITEM_ISO:
                if (isoValue == 0) {
                    int auto = camera.getAutoIso();
                    return "AUTO " + (auto > 0 ? auto + " " : "") + "(" + isoMin + "-" + isoMax + ")";
                }
                return Integer.toString(isoValue);
            case ITEM_ISO_MIN:
                return Integer.toString(isoMin);
            case ITEM_ISO_MAX:
                return Integer.toString(isoMax);
            case ITEM_FILE_1:
            case ITEM_FILE_2:
            case ITEM_FILE_3:
            case ITEM_FILE_4:
                return presetSummary(fileSlot(item));
            case ITEM_WB:
                return kelvin == 0 ? "AUTO" : kelvin + "K";
            case ITEM_FORMAT:
                return FORMAT_LABELS[formatIndex];
            case ITEM_APERTURE:
                return apertureText;
            case ITEM_SHUTTER:
                return shutterText;
            case ITEM_SILENT:
                return silent ? "开" : "关";
            case ITEM_IS:
                return IS_LABELS[isIndex];
            case ITEM_FOCUS:
                return FOCUS_LABELS[focusIndex];
            case ITEM_RAW:
                return RAW_LABELS[rawIndex];
            case ITEM_METER:
                return METER_LABELS[meterIndex];
            case ITEM_INT_H:
                return interval == null ? "0 h" : interval.intervalHours() + " h";
            case ITEM_INT_M:
                return interval == null ? "0 m" : interval.intervalMinutes() + " m";
            case ITEM_INT_S:
                return interval == null ? "0 s" : interval.intervalSeconds() + " s";
            case ITEM_SHOTS:
                return interval == null ? "?" : (interval.totalShots() + " 张");
            case ITEM_PRESET_SLOT:
                return "槽 " + presetSlot + " / " + Presets.SLOTS;
            case ITEM_PSET_H:
                return interval == null ? "-" : interval.intervalHours() + " h";
            case ITEM_PSET_M:
                return interval == null ? "-" : interval.intervalMinutes() + " m";
            case ITEM_PSET_S:
                return interval == null ? "-" : interval.intervalSeconds() + " s";
            case ITEM_PSET_SHOTS:
                return interval == null ? "-" : (interval.totalShots() + " 张");
            case ITEM_PSET_SCENE:
                return presets == null ? "-" : presets.scene(presetSlot);
            case ITEM_PRESET_LOAD:
                return presetSlot + "  " + presetSummary(presetSlot);
            case ITEM_PRESET_SAVE:
                return presetSlot + "  " + presetSummary(presetSlot);
            case ITEM_EV:
                return formatEv(evTarget * evStep);
            default:
                return "?";
        }
    }

    public static String itemName(int item) {
        switch (item) {
            case ITEM_MODE:
                return "模式";
            case ITEM_ISO:
                return "ISO";
            case ITEM_ISO_MIN:
                return "ISO自动下限";
            case ITEM_ISO_MAX:
                return "ISO自动上限";
            case ITEM_FILE_1:
                return "预设文件 1";
            case ITEM_FILE_2:
                return "预设文件 2";
            case ITEM_FILE_3:
                return "预设文件 3";
            case ITEM_FILE_4:
                return "预设文件 4";
            case ITEM_WB:
                return "白平衡";
            case ITEM_FORMAT:
                return "照片格式";
            case ITEM_APERTURE:
                return "光圈";
            case ITEM_SHUTTER:
                return "快门";
            case ITEM_SILENT:
                return "静音";
            case ITEM_IS:
                return "防抖";
            case ITEM_FOCUS:
                return "对焦";
            case ITEM_RAW:
                return "压缩";
            case ITEM_METER:
                return "测光";
            case ITEM_INT_H:
                return "间隔 时";
            case ITEM_INT_M:
                return "间隔 分";
            case ITEM_INT_S:
                return "间隔 秒";
            case ITEM_SHOTS:
                return "拍摄张数";
            case ITEM_PRESET_SLOT:
                return "预设槽位";
            case ITEM_PSET_H:
                return "间隔 时";
            case ITEM_PSET_M:
                return "间隔 分";
            case ITEM_PSET_S:
                return "间隔 秒";
            case ITEM_PSET_SHOTS:
                return "拍摄张数";
            case ITEM_PSET_SCENE:
                return "使用场景";
            case ITEM_PRESET_LOAD:
                return "载入该预设";
            case ITEM_PRESET_SAVE:
                return "保存当前参数";
            case ITEM_EV:
                return "曝光补偿";
            default:
                return "?";
        }
    }

    public String apertureText() {
        return apertureText;
    }

    public String shutterText() {
        return shutterText;
    }

    public String isoText() {
        return isoText;
    }

    public String kelvinText() {
        return kelvinText;
    }

    public String modeText() {
        return modeText;
    }

    public String formatText() {
        return formatText;
    }

    /** 右上角：AUTO ISO 6400 / ISO 1250 */
    public String isoLabel() {
        if (isoValue == 0) {
            int auto = camera.getAutoIso();
            return auto > 0 ? "AUTO ISO " + auto : "AUTO ISO --";
        }
        return "ISO " + isoValue;
    }

    /** 底部：500 表示 1/500，2.5s 表示 2.5 秒 */
    public String shutterShort() {
        return shutterShort;
    }

    public int evCur() {
        return evCur;
    }

    /** 当前曝光补偿（1/3 EV 步） */
    public int evSteps() {
        return evTarget;
    }

    /** 直接设定曝光补偿（1/3 EV 步），用于拨盘"绝对位置"映射 */
    public void setEv(int steps) {
        int v = clamp(steps, evMin(), evMax());
        if (v != steps) {
            Logger.i("setEv clamped " + steps + " -> " + v + " (range " + evMin() + ".." + evMax() + ")");
        }
        if (v != evTarget) {
            evTarget = v;
            evDirty = true;
            customized = true;
            dirtyApply = true;
        }
    }

    /** 调曝光补偿（1/3 EV 步） */
    public void adjustEv(int delta) {
        verifyNeeded = false;
        evTarget = clamp(evTarget + delta, evMin(), evMax());
        evDirty = true;
        customized = true;
        dirtyApply = true;
    }

    /**
     * 标尺显示曝光补偿值。
     * 注意：ProperExposureLevelCallback 在 A7R II 上实测从不回调（level 恒 0），
     * 所以 M 档也只能显示我们用后拨轮/面板设的曝光补偿。
     */
    public int meterValue() {
        // 用本地目标值：转盘一拨指针立刻走，不等相机写完（写相机要 ~110ms）
        return evTarget;
    }


    private String formatEv(float ev) {
        if (Math.abs(ev) < 0.05f) {
            return "0.0";
        }
        return String.format("%+.1f", ev);
    }

    /** 相机有时把范围报成 0..0 或 -15..0，这里做兜底，保证正负都调得动 */
    /** 曝光补偿下限：相机给的是 ±15 格，这里收到 ±3EV（±9 格），与预览标尺一致 */
    public int evMin() {
        int v = evMin >= 0 ? -15 : evMin;
        return Math.max(v, EV_LIMIT);
    }

    public int evMax() {
        int v = evMax <= 0 ? 15 : evMax;
        return Math.min(v, -EV_LIMIT);
    }

    public float evStep() {
        return evStep;
    }

    /** 当前格式参数值：jpeg / raw / rawjpeg */
    public String formatValue() {
        return FORMAT_VALUES[formatIndex];
    }

    /** RAW 是否为压缩格式 */
    public boolean isRawCompressed() {
        return rawIndex == 0;
    }

    /** 是否 MF（放大对焦只在 MF 下有意义） */
    public boolean isManualFocus() {
        return focusIndex == 0;
    }

    public String summary() {
        return "mode=" + MODE_LABELS[modeIndex]
                + " iso=" + (isoValue == 0 ? "AUTO " + isoMin + "-" + isoMax : String.valueOf(isoValue))
                + " wb=" + (kelvin == 0 ? "AUTO" : kelvin + "K")
                + " fmt=" + FORMAT_LABELS[formatIndex]
                + " silent=" + silent;
    }

    // ---------- 格式化 ----------

    private String formatAperture(String raw) {
        try {
            int v = Integer.parseInt(trim(raw, "0"));
            if (v <= 0) {
                return "F/-";
            }
            return String.format("F%.1f", v / 100.0);
        } catch (Throwable t) {
            return "F/-";
        }
    }

    private String formatShutter(String nume, String deno) {
        try {
            double n = Double.parseDouble(trim(nume, "0"));
            double d = Double.parseDouble(trim(deno, "1"));
            if (n <= 0 || d <= 0) {
                return "-";
            }
            double s = n / d;
            if (s >= 1.0) {
                return String.format("%.1fs", s);
            }
            return "1/" + Math.round(1.0 / s);
        } catch (Throwable t) {
            return "-";
        }
    }

    /** AUTO 时优先显示相机实时算出的 ISO（AutoISOSensitivityListener），拿不到才显示范围 */
    private String shortShutter(String nume, String deno) {
        try {
            double n = Double.parseDouble(trim(nume, "0"));
            double d = Double.parseDouble(trim(deno, "1"));
            if (n <= 0 || d <= 0) {
                return "-";
            }
            double s = n / d;
            if (s >= 1.0) {
                return String.format("%.1fs", s);
            }
            return Long.toString(Math.round(1.0 / s));
        } catch (Throwable t) {
            return "-";
        }
    }

    private String formatIso(String raw, int min, int max, int autoIso) {
        try {
            int v = Integer.parseInt(trim(raw, "0"));
            if (v == 0) {
                // AUTO：显示「当前值 + 自动上下限」
                if (autoIso > 0) {
                    return "AUTO " + autoIso + " (" + min + "-" + max + ")";
                }
                return "AUTO (" + min + "-" + max + ")";
            }
            return Integer.toString(v);
        } catch (Throwable t) {
            return "-";
        }
    }

    private String shortMode(String raw) {
        if (raw == null) {
            return "-";
        }
        int i = indexOf(MODE_VALUES, raw, -1);
        return i >= 0 ? MODE_LABELS[i] : raw;
    }

    private String shortFormat(String raw) {
        if (raw == null) {
            return "-";
        }
        int i = indexOf(FORMAT_VALUES, raw, -1);
        return i >= 0 ? FORMAT_LABELS[i] : raw;
    }

    private String shortKelvin(String wb, String kelvinRaw) {
        if (wb == null) {
            return "-";
        }
        if (!wb.startsWith("color-temp")) {
            return "WB " + wb;
        }
        return trim(kelvinRaw, "?") + "K";
    }

    // ---------- 工具 ----------

    /** "0,50,64,80,…" → int[]（升序） */
    private int[] parseIntList(String csv, int[] fallback) {
        if (csv == null || csv.length() == 0) {
            return fallback;
        }
        String[] parts = csv.split(",");
        java.util.ArrayList<Integer> out = new java.util.ArrayList<Integer>();
        for (int i = 0; i < parts.length; i++) {
            try {
                out.add(Integer.valueOf(Integer.parseInt(parts[i].trim())));
            } catch (Throwable ignored) {
            }
        }
        if (out.isEmpty()) {
            return fallback;
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i).intValue();
        }
        java.util.Arrays.sort(arr);
        return arr;
    }

    private int indexOfValue(int[] values, int v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == v) {
                return i;
            }
        }
        return -1;
    }

    private int clampIndex(int i, int n) {
        if (i < 0) {
            return 0;
        }
        return i >= n ? n - 1 : i;
    }

    /** 预设文件项 → 槽位号（1..4） */
    public int fileSlot(int item) {
        return item - ITEM_FILE_1 + 1;
    }

    /** 打开某个预设文件（进入文件内参数页） */
    public void openFile(int slot) {
        presetSlot = slot;
        presetPage = 1;
        int[] v = presets == null ? null : presets.get(slot);
        if (v != null) {
            interval.setIntervalSec(v[0]);
            interval.setShots(v[1]);
        }
    }

    /** 返回文件列表 */
    public void closeFile() {
        presetPage = 0;
    }

    private int wrapSlot(int i) {
        int n = Presets.SLOTS;
        int r = i % n;
        return r < 0 ? r + n : r;
    }

    private int clamp(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        return v > hi ? hi : v;
    }

    private String trim(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String t = value.trim();
        return t.length() == 0 ? fallback : t;
    }

    private int wrap(int v, int n) {
        if (v < 0) {
            return n - 1;
        }
        return v >= n ? 0 : v;
    }

    private int indexOf(String[] values, String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value.trim())) {
                return i;
            }
        }
        return fallback;
    }
}

package com.shenwenjun.a7r2app;

import android.hardware.Camera;
import android.os.Handler;
import android.os.SystemClock;
import android.view.SurfaceHolder;

import com.sony.scalar.hardware.CameraEx;

/**
 * 索尼相机预览/对焦/拍照的最小封装。
 *
 * 字段故意用 Object 存 CameraEx：模拟器上没有 com.sony.scalar.*，
 * 这样类加载本身不会失败，异常只在调用时抛出并被捕获。
 *
 * 【重要】A7R II 的 HAL 在 takePicture() 之后会拒绝下一次 takePicture()，
 * 重启预览、完整预览周期、JPEG 回调都无效（v0.5 实测），只有
 * release() + 重新 open() 才能复位。所以这里在每张写卡完成后立刻重开相机，
 * 保证下一格到点时相机是“新鲜”的、预览已经在跑。
 */
public class CameraController {

    /** 快门结果与“文件已写卡”的回调 */
    public interface ShotListener {
        void onShutter(int status);

        void onStored(String dir, String file);
    }

    private Object cameraEx;
    private Camera normalCamera;
    private SurfaceHolder holder;
    private String lastError = "";

    private ShotListener shotListener;
    private Object shutterListenerRef;
    private Object storeListenerRef;
    private Object autoIsoRef;
    private Object properRef;
    /** M 档下相机给出的曝光偏差（1/3 EV 步） */
    private volatile int exposureLevel;

    /** AUTO 档下相机自己算出来的 ISO（AutoISOSensitivityListener 回调） */
    private volatile int autoIso = -1;

    public int getAutoIso() {
        return autoIso;
    }

    private final Handler uiHandler = new Handler();
    private boolean previewActive;
    private boolean capturing;
    private boolean fresh;
    private long shootStartMs;

    private final Runnable resumeRunnable = new Runnable() {
        public void run() {
            resumePreview();
        }
    };

    private Runnable onOpened;

    /** 相机（重新）打开后回调，用来把用户设置重新写回去 */
    public void setOnOpened(Runnable r) {
        onOpened = r;
    }

    public String getParameter(String key) {
        if (!isOpen()) {
            return null;
        }
        try {
            return normalCamera.getParameters().get(key);
        } catch (Throwable t) {
            Logger.e("getParameter(" + key + ") failed", t);
            return null;
        }
    }

    /** 批量写参数：读 → set → setParameters，失败只记日志不抛 */
    public boolean applyParameters(String[] keys, String[] values) {
        if (!isOpen()) {
            Logger.e("applyParameters ignored: camera not open");
            return false;
        }
        try {
            Camera.Parameters p = normalCamera.getParameters();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.length; i++) {
                p.set(keys[i], values[i]);
                if (i > 0) {
                    sb.append(" ");
                }
                sb.append(keys[i]).append("=").append(values[i]);
            }
            normalCamera.setParameters(p);
            Logger.i("setParameters ok: " + sb);
            return true;
        } catch (Throwable t) {
            Logger.e("setParameters failed", t);
            return false;
        }
    }

    /** 写卡完成后在主线程重开相机，慢一点但要等下一格，不占快门时间 */
    private final Runnable reopenRunnable = new Runnable() {
        public void run() {
            // 已经重开过、或正拍着，就不要再来一次（否则会把拍摄打断）
            if (capturing || fresh) {
                return;
            }
            reopenCamera("after-store");
        }
    };

    public void setShotListener(ShotListener listener) {
        shotListener = listener;
        if (isOpen()) {
            registerListeners();
        }
    }

    public boolean open() {
        if (cameraEx != null) {
            return true;
        }
        try {
            cameraEx = openWithOptions();
            normalCamera = ((CameraEx) cameraEx).getNormalCamera();
            fresh = true;
            Logger.i("CameraEx.open() ok, camera=" + normalCamera);
            registerListeners();
            if (onOpened != null) {
                // 放到队列里执行：先让调用方完成 attach/startPreview，
                // 再重写参数，避免 applySettings 阻塞 UI 线程造成短暂黑屏。
                uiHandler.post(new Runnable() {
                    public void run() {
                        try {
                            onOpened.run();
                        } catch (Throwable t) {
                            Logger.e("onOpened hook failed", t);
                        }
                    }
                });
            }
            return normalCamera != null;
        } catch (Throwable t) {
            cameraEx = null;
            normalCamera = null;
            fresh = false;
            lastError = t.toString();
            Logger.e("CameraEx.open() failed", t);
            return false;
        }
    }

    /**
     * 带 OpenOptions 打开：预览随 open 立即启动、继承上一次设置、静态拍摄模式。
     * 失败就退回默认打开（老固件可能不认这些选项）。
     */
    private Object openWithOptions() {
        try {
            CameraEx.OpenOptions opts = new CameraEx.OpenOptions();
            opts.setPreview(true);
            opts.setRecordingMode(CameraEx.RECORDING_MODE_STILL);
            Object cam = CameraEx.open(0, opts);
            if (cam != null) {
                Logger.i("CameraEx.open(OpenOptions preview/inherit/still) ok");
                return cam;
            }
        } catch (Throwable t) {
            Logger.e("open with options failed, fallback to default", t);
        }
        Logger.i("CameraEx.open(default)");
        return CameraEx.open(0, (CameraEx.OpenOptions) null);
    }




    /** 注册“快门/写卡完成”回调；老机型不支持时只记一条日志 */
    private void registerListeners() {
        final CameraEx cam = (CameraEx) cameraEx;
        try {
            CameraEx.ShutterListener shutter = new CameraEx.ShutterListener() {
                public void onShutter(int status, CameraEx camera) {
                    if (status == 2) {
                        capturing = false;
                    }
                    if (shotListener != null) {
                        shotListener.onShutter(status);
                    }
                }
            };
            shutterListenerRef = shutter;
            cam.setShutterListener(shutter);
        } catch (Throwable t) {
            Logger.e("setShutterListener failed", t);
        }

        try {
            CameraEx.StoreImageCompleteListener store = new CameraEx.StoreImageCompleteListener() {
                public void onDone(int type, CameraEx.StoreImageInfo info, CameraEx camera) {
                    capturing = false;
                    previewActive = false;
                    fresh = false;
                    if (shootStartMs > 0) {
                        Logger.i("shot stored in " + (SystemClock.elapsedRealtime() - shootStartMs)
                                + "ms (capture+write)");
                        shootStartMs = 0;
                    }
                    // 尽快把相机重开，等下一格到点时它已经是可拍状态
                    uiHandler.post(reopenRunnable);
                    if (shotListener == null) {
                        return;
                    }
                    String dir = info == null ? "?" : info.DirectoryName;
                    String file = info == null ? "?" : info.FileName;
                    shotListener.onStored(dir, file);
                }
            };
            storeListenerRef = store;
            cam.setStoreImageCompleteListener(store);
        } catch (Throwable t) {
            Logger.e("setStoreImageCompleteListener failed", t);
        }

        // AUTO 档：相机实时算出的 ISO
        try {
            CameraEx.AutoISOSensitivityListener auto = new CameraEx.AutoISOSensitivityListener() {
                public void onChanged(int iso, CameraEx camera) {
                    autoIso = iso;
                }
            };
            autoIsoRef = auto;
            cam.setAutoISOSensitivityListener(auto);
            Logger.i("auto ISO listener registered");
        } catch (Throwable t) {
            Logger.e("setAutoISOSensitivityListener failed", t);
        }


        // M 档：相机实时给出的曝光偏差（用于底部标尺）
        try {
            CameraEx.ProperExposureLevelCallback proper = new CameraEx.ProperExposureLevelCallback() {
                public void onControlLevelNotify(int level, CameraEx camera) {
                    exposureLevel = level;
                }
            };
            properRef = proper;
            cam.setProperExposureLevelCallback(proper);
            Logger.i("proper exposure level listener registered");
        } catch (Throwable t) {
            Logger.e("setProperExposureLevelCallback failed", t);
        }
    }

    public int getExposureLevel() {
        return exposureLevel;
    }

    /** 正在拍摄/写卡时不要去读参数，免得干扰快门时序 */
    public boolean isCapturing() {
        return capturing;
    }

    public boolean isOpen() {
        return cameraEx != null && normalCamera != null;
    }

    public String getLastError() {
        return lastError;
    }

    /** Surface 就绪后开始送预览 */
    public void attach(SurfaceHolder h) {
        holder = h;
        if (!isOpen()) {
            return;
        }
        try {
            normalCamera.setPreviewDisplay(h);
            normalCamera.startPreview();
            previewActive = true;
            Logger.i("preview started");
        } catch (Throwable t) {
            lastError = t.toString();
            Logger.e("startPreview failed", t);
        }
    }

    public void detach() {
        if (normalCamera == null) {
            return;
        }
        try {
            normalCamera.stopPreview();
            previewActive = false;
            Logger.i("preview stopped");
        } catch (Throwable t) {
            Logger.e("stopPreview failed", t);
        }
    }

    /** 幂等：已经在预览、或在拍摄中，都直接返回 */
    public void resumePreview() {
        if (!isOpen() || capturing || previewActive) {
            return;
        }
        try {
            normalCamera.startPreview();
            previewActive = true;
            Logger.i("preview resumed");
        } catch (Throwable t) {
            Logger.e("resumePreview failed", t);
        }
    }

    public void close() {
        detach();
        try {
            if (cameraEx != null) {
                ((CameraEx) cameraEx).release();
                Logger.i("CameraEx.release()");
            }
        } catch (Throwable t) {
            Logger.e("release failed", t);
        }
        cameraEx = null;
        normalCamera = null;
        previewActive = false;
        capturing = false;
        fresh = false;
    }

    /** 快门半按 */
    public void focus() {
        if (!isOpen()) {
            return;
        }
        try {
            normalCamera.autoFocus(null);
            Logger.i("autoFocus()");
        } catch (Throwable t) {
            Logger.e("autoFocus failed", t);
        }
    }

    public void cancelFocus() {
        if (!isOpen()) {
            return;
        }
        try {
            normalCamera.cancelAutoFocus();
        } catch (Throwable t) {
            Logger.e("cancelAutoFocus failed", t);
        }
    }

    /**
     * 快门全按：照片由相机按现有设置写卡。
     * 不是“新鲜”状态时先重开相机（单张连拍、异常恢复都靠这个）。
     */
    public boolean shoot() {
        if (!isOpen()) {
            Logger.e("shoot ignored: camera not open");
            return false;
        }
        if (!fresh) {
            reopenCamera("before-shoot");
        }
        if (!isOpen()) {
            return false;
        }
        resumePreview();
        try {
            shootStartMs = SystemClock.elapsedRealtime();
            normalCamera.takePicture(null, null, null);
            capturing = true;
            previewActive = false;
            fresh = false;
            Logger.i("takePicture()");
            return true;
        } catch (Throwable t) {
            lastError = t.toString();
            capturing = false;
            previewActive = false;
            fresh = false;
            Logger.e("takePicture failed", t);
            return false;
        }
    }

    /**
     * 用索尼的 ParametersModifier 写设置（**关键**）。
     *
     * 之前直接用 Parameters.set("iso-sensitivity","200") 相机不认——HAL 只处理
     * modifier 里走校验的那条路。ISOSensitivity / ISOAutoMin / ISOAutoMax /
     * ColorTemperature 都必须从 modifier 写；scene-mode 和 whitebalance 没有对应
     * setter，只能继续用通用 set()。
     *
     * 写完立刻回读，如果没生效就 tryIgnoreInhibit() 再试一次。
     */
    /** 一次 apply 要写的全部设置 */
    public static class Settings {
        public int iso;
        public int isoMin;
        public int isoMax;
        public int kelvin;
        public int ev;
        public String format;
        public String wb;
        public String sceneMode;
        /** off / onetime / keep */
        public String antiHandBlur;
        /** af-s / af-c / af-a */
        public String autoFocusMode;
        /** manual / auto */
        public String focusMode;
        /** compress / uncompress */
        public String rawRecMode;
        /** multi / center-weighted / center-spot */
        public String metering;
        public boolean silent;
        public boolean writeEv;
        public boolean verify;
    }

    public boolean applySettings(Settings s) {
        if (!isOpen()) {
            Logger.e("applySettings ignored: camera not open");
            return false;
        }
        long t0 = android.os.SystemClock.elapsedRealtime();
        boolean ok = writeSettings(s);
        Logger.i("applySettings " + (ok ? "OK" : "FAILED")
                + " iso=" + s.iso + " range=" + s.isoMin + "-" + s.isoMax
                + " kelvin=" + s.kelvin + " fmt=" + s.format
                + " ev=" + (s.writeEv ? String.valueOf(s.ev) : "(skip)")
                + " is=" + s.antiHandBlur + " af=" + s.autoFocusMode
                + " raw=" + s.rawRecMode + " meter=" + s.metering
                + " took " + (android.os.SystemClock.elapsedRealtime() - t0) + "ms");
        return ok;
    }

    private boolean writeSettings(Settings s) {
        try {
            Camera.Parameters p = normalCamera.getParameters();
            CameraEx cam = (CameraEx) cameraEx;
            CameraEx.ParametersModifier m = cam.createParametersModifier(p);
            if (m != null) {
                m.setISOSensitivity(s.iso);
                m.setISOAutoMin(s.isoMin);
                m.setISOAutoMax(s.isoMax);
                if (s.kelvin > 0) {
                    m.setColorTemperatureForWhiteBalance(s.kelvin);
                }
                if (s.format != null) {
                    m.setPictureStorageFormat(s.format);
                }
                try {
                    if (m.isSupportedSilentShutterMode()) {
                        m.setSilentShutterMode(s.silent);
                    }
                } catch (Throwable ignored) {
                }
                if (s.antiHandBlur != null) {
                    try {
                        m.setAntiHandBlurMode(s.antiHandBlur);
                    } catch (Throwable t) {
                        Logger.e("setAntiHandBlurMode failed", t);
                    }
                }
                if (s.autoFocusMode != null) {
                    try {
                        m.setAutoFocusMode(s.autoFocusMode);
                    } catch (Throwable t) {
                        Logger.e("setAutoFocusMode failed", t);
                    }
                }
                if (s.rawRecMode != null) {
                    try {
                        m.setRawRecMode(s.rawRecMode);
                    } catch (Throwable t) {
                        Logger.e("setRawRecMode failed", t);
                    }
                }
                if (s.metering != null) {
                    try {
                        m.setMeteringMode(s.metering);
                    } catch (Throwable t) {
                        Logger.e("setMeteringMode failed", t);
                    }
                }
            } else {
                Logger.e("createParametersModifier returned null");
            }
            if (s.sceneMode != null) {
                p.set("scene-mode", s.sceneMode);
            }
            if (s.wb != null) {
                p.set("whitebalance", s.wb);
            }
            if (s.focusMode != null) {
                p.set("focus-mode", s.focusMode);
            }
            if (s.writeEv) {
                p.set("exposure-compensation", Integer.toString(s.ev));
            }
            normalCamera.setParameters(p);
        } catch (Throwable t) {
            Logger.e("writeSettings failed", t);
            return false;
        }

        if (!s.verify) {
            return true;
        }
        try {
            Camera.Parameters p = normalCamera.getParameters();
            boolean isoOk = parseIntSafe(p.get("iso-sensitivity"), -1) == s.iso;
            boolean rangeOk = parseIntSafe(p.get("iso-auto-min"), -1) == s.isoMin
                    && parseIntSafe(p.get("iso-auto-max"), -1) == s.isoMax;
            boolean fmtOk = s.format == null || s.format.equals(p.get("storage-fmt"));
            boolean evOk = !s.writeEv || p.getExposureCompensation() == s.ev;
            boolean rawOk = s.rawRecMode == null || s.rawRecMode.equals(p.get("raw-rec-mode"));
            boolean meterOk = s.metering == null || s.metering.equals(p.get("metering-mode"));
            if (isoOk && rangeOk && fmtOk && evOk && rawOk && meterOk) {
                return true;
            }
            Logger.i("writeSettings mismatch iso=" + isoOk + " range=" + rangeOk
                    + " fmt=" + fmtOk + " ev=" + evOk + " raw=" + rawOk + " meter=" + meterOk);
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 读防抖/对焦/RAW/测光（这些只有 modifier 能给） */
    public String[] readExtras() {
        if (!isOpen()) {
            return null;
        }
        try {
            Camera.Parameters p = normalCamera.getParameters();
            CameraEx.ParametersModifier m = ((CameraEx) cameraEx).createParametersModifier(p);
            if (m == null) {
                return null;
            }
            return new String[]{
                    safe(m.getAntiHandBlurMode()),
                    safe(m.getAutoFocusMode()),
                    safe(m.getRawRecMode()),
                    safe(m.getMeteringMode())};
        } catch (Throwable t) {
            Logger.e("readExtras failed", t);
            return null;
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
    private int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 一次 getParameters 同时拿到参数键和曝光补偿，供 HUD 每秒刷新用 */
    public final class Snapshot {
        public final String[] values;
        public final int ev;
        public final int evMin;
        public final int evMax;
        public final float evStep;

        Snapshot(String[] values, int ev, int evMin, int evMax, float evStep) {
            this.values = values;
            this.ev = ev;
            this.evMin = evMin;
            this.evMax = evMax;
            this.evStep = evStep;
        }
    }

    public Snapshot readSnapshot(String[] keys) {
        if (!isOpen()) {
            return null;
        }
        try {
            Camera.Parameters p = normalCamera.getParameters();
            String[] out = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                out[i] = p.get(keys[i]);
            }
            return new Snapshot(out, p.getExposureCompensation(),
                    p.getMinExposureCompensation(), p.getMaxExposureCompensation(),
                    p.getExposureCompensationStep());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 曝光补偿：{当前, 最小, 最大}，单位是 1/3 EV 的步数 */
    public int[] readExposureComp() {
        if (!isOpen()) {
            return null;
        }
        try {
            Camera.Parameters p = normalCamera.getParameters();
            return new int[]{p.getExposureCompensation(),
                    p.getMinExposureCompensation(), p.getMaxExposureCompensation()};
        } catch (Throwable t) {
            return null;
        }
    }

    public float exposureStep() {
        try {
            return normalCamera.getParameters().getExposureCompensationStep();
        } catch (Throwable t) {
            return 0.33333f;
        }
    }

    /** 一次 getParameters 读多个键，给 HUD 用 */
    public String[] readKeys(String[] keys) {
        if (!isOpen()) {
            return null;
        }
        try {
            Camera.Parameters p = normalCamera.getParameters();
            String[] out = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                out[i] = p.get(keys[i]);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 光圈步进（A/M 档有效）。没有合法值列表，只能一档一档走 */
    public boolean stepAperture(int dir) {
        if (!isOpen()) {
            return false;
        }
        try {
            if (dir > 0) {
                ((CameraEx) cameraEx).incrementAperture();
            } else {
                ((CameraEx) cameraEx).decrementAperture();
            }
            Logger.i("aperture step " + dir);
            return true;
        } catch (Throwable t) {
            Logger.e("aperture step failed", t);
            return false;
        }
    }

    /** 快门步进（S/M 档有效） */
    public boolean stepShutter(int dir) {
        if (!isOpen()) {
            return false;
        }
        try {
            if (dir > 0) {
                ((CameraEx) cameraEx).incrementShutterSpeed();
            } else {
                ((CameraEx) cameraEx).decrementShutterSpeed();
            }
            Logger.i("shutter step " + dir);
            return true;
        } catch (Throwable t) {
            Logger.e("shutter step failed", t);
            return false;
        }
    }

    public void cancelShoot() {
        if (cameraEx == null) {
            return;
        }
        try {
            ((CameraEx) cameraEx).cancelTakePicture();
            Logger.i("cancelTakePicture()");
        } catch (Throwable t) {
            Logger.e("cancelTakePicture failed", t);
        }
    }

    /**
     * 把相机真实参数表导出成文本：current / supported / initial + 常用标准字段。
     * 索尼的 P/A/S/M、ISO、色温、RAW/JPG 都藏在私有的 sony:* 键里，
     * 先读出来，才能确定 setParameters 该写什么。
     */
   private Camera.Parameters readParameters() {
        try {
            return normalCamera == null ? null : normalCamera.getParameters();
        } catch (Throwable t) {
            Logger.e("getParameters failed", t);
            return null;
        }
    }

    private void appendSection(StringBuilder sb, String title, Camera.Parameters p) {
        sb.append("== ").append(title).append(" ==\n");
        if (p == null) {
            sb.append("(null)\n\n");
            return;
        }
        String flat;
        try {
            flat = p.flatten();
        } catch (Throwable t) {
            sb.append("(flatten failed: ").append(t).append(")\n\n");
            return;
        }
        String[] parts = flat.split(";");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() > 0) {
                sb.append(parts[i]).append("\n");
            }
        }
        sb.append("\n");
    }

    private void appendStandard(StringBuilder sb) {
        sb.append("== standard getters ==\n");
        Camera.Parameters p = readParameters();
        if (p == null) {
            sb.append("(null)\n");
            return;
        }
        put(sb, "whitebalance", p.getWhiteBalance());
        put(sb, "supported-whitebalance", join(p.getSupportedWhiteBalance()));
        put(sb, "scene-mode", p.getSceneMode());
        put(sb, "supported-scene-modes", join(p.getSupportedSceneModes()));
        put(sb, "focus-mode", p.getFocusMode());
        put(sb, "supported-focus-modes", join(p.getSupportedFocusModes()));
        put(sb, "flash-mode", p.getFlashMode());
        put(sb, "supported-flash-modes", join(p.getSupportedFlashModes()));
        put(sb, "color-effect", p.getColorEffect());
        put(sb, "supported-color-effects", join(p.getSupportedColorEffects()));
        put(sb, "picture-format", Integer.toString(p.getPictureFormat()));
        put(sb, "supported-picture-formats", ints(p.getSupportedPictureFormats()));
        put(sb, "jpeg-quality", Integer.toString(p.getJpegQuality()));
        put(sb, "picture-size", p.getPictureSize() == null ? "?"
                : p.getPictureSize().width + "x" + p.getPictureSize().height);
        put(sb, "preview-size", p.getPreviewSize() == null ? "?"
                : p.getPreviewSize().width + "x" + p.getPreviewSize().height);
        put(sb, "exposure-compensation", Integer.toString(p.getExposureCompensation()));
        put(sb, "exposure-compensation-step", Float.toString(p.getExposureCompensationStep()));
        put(sb, "min-max-exposure-compensation",
                p.getMinExposureCompensation() + ".." + p.getMaxExposureCompensation());
    }

    private void put(StringBuilder sb, String label, String value) {
        sb.append(label).append(" = ").append(value == null ? "(null)" : value).append("\n");
    }

    private String join(java.util.List<String> values) {
        if (values == null) {
            return "(null)";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                b.append(",");
            }
            b.append(values.get(i));
        }
        return b.toString();
    }

    private String ints(java.util.List<Integer> values) {
        if (values == null) {
            return "(null)";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                b.append(",");
            }
            b.append(values.get(i));
        }
        return b.toString();
    }

    /** release + open + 重新挂预览：A7R II 上唯一能复位快门状态的办法 */
    private void reopenCamera(String reason) {
        SurfaceHolder h = holder;
        long t0 = SystemClock.elapsedRealtime();
        // 实测 CameraEx.reopen() 在这台机器上必定超时（2.5s 无回调），
        // 只会让重开变慢，所以直接走 release + open。
        close();
        holder = h;
        boolean ok = open();
        if (ok && h != null) {
            attach(h);
        }
        Logger.i("camera reopened (" + reason + ") ok=" + ok
                + " " + (SystemClock.elapsedRealtime() - t0) + "ms");
    }
}

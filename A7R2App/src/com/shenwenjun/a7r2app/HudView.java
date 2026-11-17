package com.shenwenjun.a7r2app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * 预览界面 HUD（v4.1，对应 Pixso 01 待机 / 02 拍摄中 / 10 汇总）。
 *
 * v4.1 相对 v4.0：整体字号放大约 45%，因为机身 3 寸屏上 9px 根本看不清。
 * 位置仍是设计稿像素（640×480 1:1），只是字阶变了：
 *   顶部 11/34px，参数标签 12px + 数值 30px，信息行 12/17px，提示 11px。
 */
public class HudView extends View {

    /** 一帧需要的全部内容，由 MainActivity 每次 render() 填好后交给 draw */
    public static final class S {
        public boolean running;
        public boolean finished;
        public String remain = "";
        public String topRight = "";
        public String iso = "";
        public String shutter = "";
        public String aperture = "";
        public int meter;
        public float evStep = 1f / 3f;
        public String interval = "";
        public String shots = "";
        public String done = "";
        public String big = "";
        public String saved = "";
        public int doneCount;
        public int total;
        public String hint = "";
        public String summary = "";
    }

    private final S s = new S();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public HudView(Context context, AttributeSet attrs) {
        super(context, attrs);
        try {
            paint.setSubpixelText(true);
        } catch (Throwable ignored) {
        }
    }

    public void set(S st) {
        s.running = st.running;
        s.finished = st.finished;
        s.remain = st.remain;
        s.topRight = st.topRight;
        s.iso = st.iso;
        s.shutter = st.shutter;
        s.aperture = st.aperture;
        s.meter = st.meter;
        s.evStep = st.evStep;
        s.interval = st.interval;
        s.shots = st.shots;
        s.done = st.done;
        s.big = st.big;
        s.saved = st.saved;
        s.doneCount = st.doneCount;
        s.total = st.total;
        s.hint = st.hint;
        s.summary = st.summary;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        Ui.init(getContext());
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (!s.running) {
            drawTop(c, 596f);
            drawCorners(c);
            drawBottomBlock(c, 322f, 332f, 346f);
            drawScale(c, 400f, 414f);
            drawIdleInfo(c);
            Ui.text(c, s.hint, 20f, 458f, 14f, Ui.cjk(), Ui.MID);
            if (s.finished && s.summary.length() > 0) {
                Ui.textCenter(c, s.summary, w / 2f, 186f, 17f, Ui.cjk(), Ui.alpha(Ui.INK, 0.92f));
            }
            return;
        }
        drawTop(c, 540f);
        // RUN 指示
        Ui.rect(c, 572f, 22f, 7f, 7f, Ui.BRASS);
        Ui.text(c, "RUN", 588f, 17f, 13f, Ui.num(), Ui.BRASS);
        // 中间倒计时（只要整秒，不显示小数；不再写「下一张」）
        float bw = Ui.width(s.big, 96f, Ui.num());
        float bx = w / 2f - bw / 2f;
        Ui.text(c, s.big, bx, 172f, 96f, Ui.num(), Ui.INK);
        Ui.text(c, "秒", bx + bw + 14f, 242f, 22f, Ui.cjk(), Ui.alpha(Ui.INK, 0.88f));
        // 进度线
        Ui.rect(c, 0f, 326f, w, 1f, Ui.alpha(0xFFFFFF, 0.14f));
        float ratio = s.total > 0 ? Math.min(1f, s.doneCount / (float) s.total) : 0f;
        Ui.rect(c, 0f, 326f, 600f * ratio, 2f, Ui.BRASS);
        Ui.text(c, s.doneCount + " / " + s.total, 20f, 332f, 13f, Ui.num(), Ui.alpha(Ui.INK, 0.88f));
        Ui.textRight(c, s.saved, 620f, 332f, 13f, Ui.cjk(), Ui.alpha(Ui.INK, 0.88f));
        drawBottomBlock(c, 348f, 354f, 370f);
        Ui.text(c, s.hint, 20f, 458f, 14f, Ui.cjk(), Ui.MID);
    }

    /** 左上「剩余 N 张」+ 右上参数串（v4.9：右上从 13px/次文字 提到 17px/主文字） */
    private void drawTop(Canvas c, float right) {
        Ui.text(c, "剩余", 20f, 2f, 13f, Ui.cjk(), Ui.MID);
        Ui.numberWithUnit(c, s.remain, "张", 20f, 20f, 34f, 15f, Ui.INK, Ui.MID);
        Ui.textRight(c, s.topRight, right, 18f, 17f, Ui.cjk(), Ui.alpha(Ui.INK, 0.92f));
    }

    /** 取景四角刻线，代替整块边框 */
    /**
     * 取景框四角的「刻线」（不是四个小方框）。
     * 每角两笔 L 形，臂长 20px，围出 20,88 – 620,296 这块取景区域。
     */
    private void drawCorners(Canvas c) {
        int col = Ui.alpha(Ui.INK, 0.72f);
        float l = 20f;
        float r = 620f;
        float t = 88f;
        float b = 296f;
        float a = 20f;
        // 左上
        Ui.rect(c, l, t, a, 1f, col);
        Ui.rect(c, l, t, 1f, a, col);
        // 右上
        Ui.rect(c, r - a, t, a, 1f, col);
        Ui.rect(c, r - 1f, t, 1f, a, col);
        // 左下
        Ui.rect(c, l, b - 1f, a, 1f, col);
        Ui.rect(c, l, b - a, 1f, a, col);
        // 右下
        Ui.rect(c, r - a, b - 1f, a, 1f, col);
        Ui.rect(c, r - 1f, b - a, 1f, a, col);
    }

    /** 参数三列：标签 14px(75%) + 数值 32px */
    private void drawBottomBlock(Canvas c, float ruleY, float labelY, float valueY) {
        Ui.rect(c, 20f, ruleY, 600f, 1f, Ui.alpha(0xFFFFFF, 0.16f));
        Ui.rect(c, 216f, labelY - 6f, 1f, 66f, Ui.alpha(0xFFFFFF, 0.14f));
        Ui.rect(c, 416f, labelY - 6f, 1f, 66f, Ui.alpha(0xFFFFFF, 0.14f));
        int labelColor = Ui.alpha(Ui.INK, 0.75f);
        Ui.text(c, "ISO", 20f, labelY, 14f, Ui.cjk(), labelColor);
        Ui.text(c, "快门", 240f, labelY, 14f, Ui.cjk(), labelColor);
        Ui.text(c, "光圈", 440f, labelY, 14f, Ui.cjk(), labelColor);
        Ui.textFit(c, s.iso, 20f, valueY, 32f, 20f, 192f, Ui.num(), Ui.INK);
        Ui.textFit(c, s.shutter, 240f, valueY, 32f, 20f, 168f, Ui.num(), Ui.INK);
        Ui.textFit(c, s.aperture, 440f, valueY, 32f, 20f, 172f, Ui.num(), Ui.INK);
    }

    /** 曝光标尺：A 档位徽标 + 细刻度 + 黄铜指针 */
    private void drawScale(Canvas c, float badgeY, float baseY) {
        Ui.text(c, "A", 20f, badgeY, 14f, Ui.num(), Ui.BRASS);
        // 曝光标尺：±3EV、每 1/3EV 一格（19 个刻度）。指针按「格数 × 步长」定位，
        // 所以每一格都恰好压在刻度线上（原来用 span/2/9 换算，1/3EV 根本对不齐）。
        final float x0 = 44f;
        final float step = 15f;
        final int slots = 18;                    // 18 格 = ±3EV
        final float span = step * slots;         // 270
        final float center = x0 + span / 2f;     // 179
        Ui.rect(c, x0, baseY, span, 1f, Ui.alpha(0xFFFFFF, 0.45f));
        for (int i = 0; i <= slots; i++) {
            boolean major = (i % 3 == 0);
            Ui.rect(c, x0 + i * step, major ? baseY - 11f : baseY - 7f, 1f,
                    major ? 11f : 7f, Ui.alpha(0xFFFFFF, major ? 0.82f : 0.50f));
        }
        // 数字：每 1EV 一个，中间 0、两侧 1..3（跟机身 OSD 一样的绝对值写法）
        for (int i = 0; i <= slots; i += 3) {
            int k = i / 3 - 3;
            String label = (k == 0) ? "0" : String.valueOf(Math.abs(k));
            int col = (k == 0) ? Ui.alpha(Ui.INK, 0.92f) : Ui.alpha(Ui.INK, 0.62f);
            Ui.textCenter(c, label, x0 + i * step, baseY - 30f, 13f, Ui.num(), col);
        }
        Ui.text(c, "-", x0 + 1f, baseY + 7f, 13f, Ui.num(), Ui.alpha(Ui.INK, 0.55f));
        Ui.textRight(c, "+", x0 + span, baseY + 7f, 13f, Ui.num(), Ui.alpha(Ui.INK, 0.55f));
        float px = center + s.meter * step;
        if (px < x0) {
            px = x0;
        }
        if (px > x0 + span) {
            px = x0 + span;
        }
        Ui.rect(c, px - 1f, baseY - 13f, 2f, 17f, Ui.BRASS);
        // 补偿读数：紧贴标尺右侧（太长会向右长，不压刻度）
        float ev = s.meter * s.evStep;
        String evText;
        if (Math.abs(ev) < 0.05f) {
            evText = "0.0";
        } else {
            evText = String.format("%+.1f", ev);
        }
        int evColor = (s.meter == 0) ? Ui.alpha(Ui.INK, 0.72f) : Ui.BRASS;
        Ui.text(c, evText, x0 + span + 16f, baseY - 16f, 22f, Ui.num(), evColor);
    }

    /** 待机信息行：间隔 / 张数 / 已拍（v4.9：标签 14px/75%，值 19px） */
    private void drawIdleInfo(Canvas c) {
        float y = 444f;
        int labelColor = Ui.alpha(Ui.INK, 0.75f);
        float x = 322f;
        x += Ui.text(c, "间隔", x, y + 5f, 14f, Ui.cjk(), labelColor) + 7f;
        x += Ui.text(c, s.interval, x, y, 19f, Ui.num(), Ui.INK) + 16f;
        x += Ui.text(c, "张数", x, y + 5f, 14f, Ui.cjk(), labelColor) + 7f;
        x += Ui.text(c, s.shots, x, y, 19f, Ui.num(), Ui.INK) + 16f;
        x += Ui.text(c, "已拍", x, y + 5f, 14f, Ui.cjk(), labelColor) + 7f;
        Ui.text(c, s.done, x, y, 19f, Ui.num(), Ui.alpha(Ui.INK, 0.92f));
    }

    /** 给外部算宽度用（预览里调间隔时判断换行） */
    float measureShots(String t) {
        return Ui.width(t, 17f, Ui.num());
    }

    @SuppressWarnings("unused")
    private void unusedPath() {
        path.reset();
    }
}

package com.shenwenjun.a7r2app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * v4.0 视觉令牌（对应 Pixso 设计稿 00 设计规范 / 11 可读性规范）。
 *
 * 重要：这里所有尺寸都是「设计稿像素」。机内屏幕是 640×480 px，
 * 设计稿也是 640×480 px，所以绘制时直接用原始数值、不做 density 换算，
 * 这样机上效果和设计稿逐像素对齐（density 1.5 只影响 XML 的 dp/sp）。
 */
final class Ui {

    static final int BG = 0xFF0A0A0A;
    static final int INK = 0xFFF0EFEA;   // 暖白 主文字
    // v4.9：机身屏偏暗、对比低，次/弱文字整体提亮（原来 9A9A93 / 5C5C56 在机上发灰看不清）
    static final int MID = 0xFFC6C6BE;   // 次文字
    static final int DIM = 0xFF93938B;   // 弱文字
    static final int BRASS = 0xFFC49A6C; // 唯一强调色

    static final float SCREEN_W = 640f;
    static final float SCREEN_H = 480f;

    private static Typeface CJK;
    private static Typeface NUM_LIGHT;
    private static Typeface NUM_REGULAR;

    private Ui() {
    }

    static void init(Context c) {
        if (CJK != null) {
            return;
        }
        Typeface base = Fonts.cjk(c);
        CJK = Typeface.create(base, Typeface.NORMAL);
        // 条数/参数用西文细体，和中文形成字重差
        NUM_LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
        NUM_REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
        if (NUM_LIGHT == null) {
            NUM_LIGHT = NUM_REGULAR;
        }
    }

    static Typeface cjk() {
        return CJK;
    }

    static Typeface num() {
        return NUM_LIGHT;
    }

    static Typeface numStrong() {
        return NUM_REGULAR;
    }

    static int alpha(int color, float a) {
        if (a < 0f) {
            a = 0f;
        }
        if (a > 1f) {
            a = 1f;
        }
        return (((int) (255 * a)) << 24) | (color & 0xFFFFFF);
    }

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    static void rect(Canvas c, float x, float y, float w, float h, int color) {
        P.reset();
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawRect(x, y, x + w, y + h, P);
    }

    static void frame(Canvas c, float x, float y, float w, float h, int color, float t) {
        P.reset();
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(t);
        P.setColor(color);
        c.drawRect(x + t / 2f, y + t / 2f, x + w - t / 2f, y + h - t / 2f, P);
    }

    /** 绘制并返回宽度；x/y 是文字左上角，内部换算成基线 */
    static float text(Canvas c, String s, float x, float yTop, float size, Typeface tf, int color) {
        if (s == null || s.length() == 0) {
            return 0f;
        }
        P.reset();
        P.setAntiAlias(true);
        P.setTypeface(tf);
        P.setTextSize(size);
        P.setColor(color);
        c.drawText(s, x, yTop + size * 0.78f, P);
        return P.measureText(s);
    }

    static float width(String s, float size, Typeface tf) {
        if (s == null || s.length() == 0) {
            return 0f;
        }
        P.reset();
        P.setAntiAlias(true);
        P.setTypeface(tf);
        P.setTextSize(size);
        return P.measureText(s);
    }

    static float textRight(Canvas c, String s, float right, float yTop, float size, Typeface tf, int color) {
        return text(c, s, right - width(s, size, tf), yTop, size, tf, color);
    }

    static float textCenter(Canvas c, String s, float cx, float yTop, float size, Typeface tf, int color) {
        return text(c, s, cx - width(s, size, tf) / 2f, yTop, size, tf, color);
    }

    /** 按最大宽度自动缩字号（参数值可能很长：AUTO ISO 12800 / 1/8000） */
    static float textFit(Canvas c, String s, float x, float yTop, float maxSize, float minSize,
                         float maxW, Typeface tf, int color) {
        if (s == null || s.length() == 0) {
            return 0f;
        }
        float size = maxSize;
        float w = width(s, size, tf);
        while (w > maxW && size > minSize) {
            size -= 1f;
            w = width(s, size, tf);
        }
        return text(c, s, x, yTop, size, tf, color);
    }

    /** 数字后面跟小单位（如「320 张」），返回整体右边界 */
    static float numberWithUnit(Canvas c, String num, String unit, float x, float yTop,
                                float numSize, float unitSize, int numColor, int unitColor) {
        float w = text(c, num, x, yTop, numSize, num(), numColor);
        text(c, unit, x + w + 7f, yTop + numSize - unitSize - 1f, unitSize, cjk(), unitColor);
        return x + w + 7f + width(unit, unitSize, cjk());
    }
}

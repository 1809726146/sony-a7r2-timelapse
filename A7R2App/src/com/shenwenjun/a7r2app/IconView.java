package com.shenwenjun.a7r2app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * 小图标：上下箭头 / 左右箭头 / 播放三角 / 快门环 / 时钟。
 * 全部用 Canvas 自绘，避免依赖图片资源。
 */
public class IconView extends View {

    public static final int GLYPH_UPDOWN = 0;
    public static final int GLYPH_LEFTRIGHT = 1;
    public static final int GLYPH_PLAY = 2;
    public static final int GLYPH_SHUTTER = 3;
    public static final int GLYPH_CLOCK = 4;

    private int glyph = GLYPH_UPDOWN;
    private int color = 0xFFFFFFFF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public IconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGlyph(int glyph, int color) {
        this.glyph = glyph;
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        float s = Math.min(w, h) / 18f;   // 基准单位
        float stroke = Math.max(1.4f, 1.6f * s);

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(stroke);

        switch (glyph) {
            case GLYPH_UPDOWN:
                triangle(cx, cy - 4.2f * s, 5.2f * s, 4.6f * s, true);
                canvas.drawPath(path, paint);
                triangle(cx, cy + 4.2f * s, 5.2f * s, 4.6f * s, false);
                canvas.drawPath(path, paint);
                break;
            case GLYPH_LEFTRIGHT:
                triangle(cx - 4.0f * s, cy, 5.0f * s, 4.4f * s, false, true);
                canvas.drawPath(path, paint);
                triangle(cx + 4.0f * s, cy, 5.0f * s, 4.4f * s, false, false);
                canvas.drawPath(path, paint);
                break;
            case GLYPH_PLAY:
                path.reset();
                path.moveTo(cx - 4.5f * s, cy - 6.2f * s);
                path.lineTo(cx + 7.0f * s, cy);
                path.lineTo(cx - 4.5f * s, cy + 6.2f * s);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case GLYPH_SHUTTER:
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(cx, cy, 6.6f * s, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, 2.6f * s, paint);
                break;
            case GLYPH_CLOCK:
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(cx, cy, 6.4f * s, paint);
                canvas.drawLine(cx, cy, cx, cy - 3.8f * s, paint);
                canvas.drawLine(cx, cy, cx + 3.2f * s, cy + 1.8f * s, paint);
                break;
            default:
                break;
        }
    }

    /** 以 (x,y) 为中心的水平箭头/竖箭头三角 */
    private void triangle(float x, float y, float halfW, float halfH, boolean up) {
        path.reset();
        path.moveTo(x - halfW / 2f, up ? y + halfH / 2f : y - halfH / 2f);
        path.lineTo(x + halfW / 2f, up ? y + halfH / 2f : y - halfH / 2f);
        path.lineTo(x, up ? y - halfH / 2f : y + halfH / 2f);
        path.close();
    }

    private void triangle(float x, float y, float halfW, float halfH, boolean up, boolean left) {
        path.reset();
        path.moveTo(left ? x + halfW / 2f : x - halfW / 2f, y - halfH / 2f);
        path.lineTo(left ? x + halfW / 2f : x - halfW / 2f, y + halfH / 2f);
        path.lineTo(left ? x - halfW / 2f : x + halfW / 2f, y);
        path.close();
    }

    private void drawPath(Canvas canvas) {
        canvas.drawPath(path, paint);
    }
}

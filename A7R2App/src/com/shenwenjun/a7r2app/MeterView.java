package com.shenwenjun.a7r2app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 曝光标尺：模仿索尼取景界面底部那条刻度 + 指针。
 * 输入是 Android 的曝光补偿步数（-15..15，每步 1/3 EV）。
 */
public class MeterView extends View {

    private final Paint paint = new Paint();
    private int cur;
    private int min = -15;
    private int max = 15;
    private float step = 0.33333f;

    public MeterView(Context context) {
        super(context);
        init();
    }

    public MeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
    }

    public void setRange(int cur, int min, int max, float step) {
        if (cur == this.cur && min == this.min && max == this.max && step == this.step) {
            return;   // 值没变就不重绘
        }
        this.cur = cur;
        this.min = min;
        this.max = max;
        this.step = step;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float left = 4f;
        float right = w - 4f;
        float textSize = h * 0.40f;
        float midY = h * 0.80f;
        float tickH = h * 0.16f;

        int n = max - min;
        if (n <= 0) {
            return;
        }

        // 基线
        paint.setStrokeWidth(1f);
        paint.setColor(0xCCFFFFFF);
        canvas.drawLine(left, midY, right, midY, paint);

        // 刻度：每 1/3 EV 一小格，每 1 EV 一格长线
        for (int i = 0; i <= n; i++) {
            float x = left + (right - left) * i / (float) n;
            int v = min + i;
            boolean major = (v % 3 == 0);
            paint.setStrokeWidth(major ? 2f : 1f);
            paint.setColor(major ? 0xFFFFFFFF : 0x99FFFFFF);
            float len = major ? tickH : tickH * 0.6f;
            canvas.drawLine(x, midY - len, x, midY + len, paint);
        }

        // 指针
        float x = left + (right - left) * (cur - min) / (float) n;
        paint.setStrokeWidth(3f);
        paint.setColor(Color.WHITE);
        canvas.drawLine(x, midY - tickH * 1.5f, x, midY + tickH * 1.5f, paint);

        // 数值（在刻度上方，跟着指针走）
        String text = formatEv(cur * step);
        paint.setTextSize(textSize);
        float tw = paint.measureText(text);
        float tx = x - tw / 2f;
        if (tx < left) {
            tx = left;
        }
        if (tx + tw > right) {
            tx = right - tw;
        }
        canvas.drawText(text, tx, textSize, paint);
    }

    private String formatEv(float ev) {
        if (Math.abs(ev) < 0.05f) {
            return "0.0";
        }
        return String.format("%+.1f", ev);
    }
}

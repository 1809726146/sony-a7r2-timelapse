package com.shenwenjun.a7r2app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 拍摄进度条：按"已拍/总张数"填充，右端一条白色游标。 */
public class MiniBarView extends View {

    private int done;
    private int total = 1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public MiniBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setProgress(int done, int total) {
        int t = Math.max(1, total);
        if (done == this.done && t == this.total) {
            return;
        }
        this.done = done;
        this.total = t;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float r = h / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66FFFFFF);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, paint);

        float ratio = done / (float) total;
        if (ratio > 1f) {
            ratio = 1f;
        }
        float fill = w * ratio;
        if (done > 0 && fill < h) {
            fill = h;   // 至少留一个圆点，别看不见
        }
        if (fill > 0) {
            paint.setColor(0xFFFFB300);
            rect.set(0, 0, fill, h);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setColor(0xFFFFFFFF);
            canvas.drawRect(fill - 1.5f, 0, fill + 1.5f, h, paint);
        }
    }
}

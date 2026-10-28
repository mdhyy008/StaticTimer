package cn.mdhyy.staticclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** 四角细角标，像老式仪器面板的框线。 */
public class CornerFrameView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = 0x66FFB27A;

    public CornerFrameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
    }

    public void setFrameColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float stroke = dp(1.2f);
        paint.setStrokeWidth(stroke);
        paint.setColor(color);

        float arm = dp(22);
        float inset = stroke / 2f;
        float right = getWidth() - inset;
        float bottom = getHeight() - inset;

        // 左上
        canvas.drawLine(inset, inset, inset + arm, inset, paint);
        canvas.drawLine(inset, inset, inset, inset + arm, paint);
        // 右上
        canvas.drawLine(right - arm, inset, right, inset, paint);
        canvas.drawLine(right, inset, right, inset + arm, paint);
        // 左下
        canvas.drawLine(inset, bottom - arm, inset, bottom, paint);
        canvas.drawLine(inset, bottom, inset + arm, bottom, paint);
        // 右下
        canvas.drawLine(right - arm, bottom, right, bottom, paint);
        canvas.drawLine(right, bottom - arm, right, bottom, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

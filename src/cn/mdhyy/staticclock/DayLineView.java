package cn.mdhyy.staticclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 贴在屏幕左边的竖向日线：从顶端 0 点到底端 24 点，
 * 每小时一根短刻度、每 6 小时一根长刻度，当前时刻是一道横杠。
 * 不写任何数字，含义交给左上角那行 DAY 说明。
 */
public class DayLineView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;
    private int fg = 0xFFFFFFFF;

    public DayLineView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setFraction(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - fraction) < 0.0001f) {
            return;
        }
        fraction = clamped;
        invalidate();
    }

    public void setTextColor(int color) {
        fg = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float height = getHeight();
        if (height <= 0f) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float x = density;  // 竖线距屏幕边缘 1dp

        paint.setStrokeWidth(1f * density);
        paint.setColor(withAlpha(fg, 0x30));
        canvas.drawLine(x, 0f, x, height, paint);

        for (int hour = 0; hour <= 24; hour++) {
            float y = height * hour / 24f;
            boolean major = hour % 6 == 0;
            paint.setStrokeWidth((major ? 1.2f : 1f) * density);
            paint.setColor(withAlpha(fg, major ? 0x88 : 0x3A));
            float length = (major ? 6f : 3f) * density;
            canvas.drawLine(x, y, x + length, y, paint);
        }

        float markerY = height * fraction;
        paint.setStrokeWidth(2f * density);
        paint.setColor(fg);
        canvas.drawLine(x, markerY, x + 14f * density, markerY, paint);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}

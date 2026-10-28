package cn.mdhyy.staticclock;

import android.graphics.Typeface;
import android.view.Gravity;

/**
 * 预置主题：配色 + 字体 + 字距 + 位置 + 日期顺序 + 字号比例，整体成一套。
 * 用户仍可单独改颜色和字号，版式跟主题走。
 */
public final class Theme {

    public final String key;
    public final String name;
    public final int bg;
    public final int fg;
    public final String family;
    public final int style;
    public final float letterSpacing;
    /** 整块内容停在屏幕的什么位置。 */
    public final int gravity;
    /** 文本自身的水平对齐。 */
    public final int align;
    /** 日期放在时间上方（否则下方）。 */
    public final boolean dateAbove;
    /** 主题自带的装饰：空串表示没有，其余见 MainActivity 的 applyChrome。 */
    public final String decor;
    /** 装饰用的第二颜色，0 表示跟正文同色。 */
    public final int accent;

    private Theme(String key, String name, int bg, int fg, String family, int style,
                  float letterSpacing, int gravity, int align, boolean dateAbove,
                  String decor, int accent) {
        this.key = key;
        this.name = name;
        this.bg = bg;
        this.fg = fg;
        this.family = family;
        this.style = style;
        this.letterSpacing = letterSpacing;
        this.gravity = gravity;
        this.align = align;
        this.dateAbove = dateAbove;
        this.decor = decor;
        this.accent = accent;
    }

    public Typeface typeface() {
        return Typeface.create(family, style);
    }

    public Typeface dateTypeface() {
        return Typeface.create(family, Typeface.NORMAL);
    }

    public static final Theme[] ALL = {
            new Theme("ink", "墨",
                    0xFF000000, 0xFFFFFFFF,
                    "sans-serif-thin", Typeface.NORMAL, 0.02f,
                    Gravity.CENTER, Gravity.CENTER, false, "ink", 0),
            new Theme("amber", "暖夜",
                    0xFF0A0806, 0xFFFFB27A,
                    "monospace", Typeface.NORMAL, 0.06f,
                    Gravity.CENTER, Gravity.CENTER, false, "amber", 0),
            new Theme("paper", "纸",
                    0xFFF4F1E8, 0xFF1C1B18,
                    "serif-monospace", Typeface.NORMAL, 0f,
                    Gravity.CENTER, Gravity.CENTER, false, "paper", 0),
            new Theme("terminal", "终端",
                    0xFF050805, 0xFF57F287,
                    "monospace", Typeface.BOLD, 0f,
                    Gravity.CENTER, Gravity.CENTER, true, "terminal", 0),
            new Theme("focus", "黑体",
                    0xFF101010, 0xFFFFFFFF,
                    "sans-serif-condensed", Typeface.BOLD, -0.01f,
                    Gravity.CENTER, Gravity.CENTER, false, "focus", 0),
            new Theme("moss", "苔",
                    0xFF0B1210, 0xFF7FF0C8,
                    "sans-serif-smallcaps", Typeface.NORMAL, 0.10f,
                    Gravity.CENTER, Gravity.CENTER, false, "moss", 0),
            new Theme("neon", "霓虹",
                    0xFF08000F, 0xFFFF3FA4,
                    "sans-serif-black", Typeface.NORMAL, 0.08f,
                    Gravity.CENTER, Gravity.CENTER, false, "neon", 0xFF22D3EE),
            new Theme("eink", "墨水屏",
                    0xFFE7E7E3, 0xFF3A3C38,
                    "serif", Typeface.NORMAL, 0.01f,
                    Gravity.CENTER, Gravity.CENTER, false, "eink", 0xFF6B6D64),
            new Theme("calendar", "日历页",
                    0xFFFAF7F2, 0xFF1A1A1A,
                    "sans-serif-medium", Typeface.NORMAL, 0.0f,
                    Gravity.CENTER, Gravity.CENTER, false, "calendar", 0xFFC8342B),
    };

    public static Theme get(String key) {
        for (Theme theme : ALL) {
            if (theme.key.equals(key)) {
                return theme;
            }
        }
        return ALL[0];
    }
}

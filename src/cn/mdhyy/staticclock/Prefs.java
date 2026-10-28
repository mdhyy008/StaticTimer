package cn.mdhyy.staticclock;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 全部可配置项集中在这里。
 */
public final class Prefs {

    public static final String DEF_FORMAT = "HH:mm:ss";
    public static final int DEF_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEF_BG_COLOR = 0xFF000000;
    public static final int DEF_TEXT_SIZE = 72;
    public static final String DEF_THEME = "ink";
    public static final String DEF_TIME_ZONE = "";
    public static final boolean DEF_SHOW_DATE = true;
    public static final boolean DEF_CHIME = true;
    public static final boolean DEF_KEEP_AWAKE = true;
    public static final boolean DEF_BURN_IN = true;
    public static final boolean DEF_SCREEN_REST = true;
    public static final boolean DEF_NIGHT_DIM = true;
    public static final int DEF_NIGHT_START = 22 * 60;
    public static final int DEF_NIGHT_END = 7 * 60;
    public static final int DEF_NIGHT_BRIGHTNESS = 12;
    public static final int DEF_BRIGHTNESS = -1; // -1 表示跟随系统


    public static final int MIN_TEXT_SIZE = 16;
    public static final int MAX_TEXT_SIZE = 300;
    public static final int MIN_NIGHT_BRIGHTNESS = 3;
    public static final int MAX_NIGHT_BRIGHTNESS = 60;

    private static final String NAME = "settings";

    private static final String K_THEME = "theme";
    private static final String K_TIME_ZONE = "time_zone";
    private static final String K_FORMAT = "format";
    private static final String K_TEXT_COLOR = "text_color";
    private static final String K_BG_COLOR = "bg_color";
    private static final String K_TEXT_SIZE = "text_size";
    private static final String K_SHOW_DATE = "show_date";
    private static final String K_CHIME = "chime";
    private static final String K_KEEP_AWAKE = "keep_awake";
    private static final String K_BURN_IN = "burn_in";
    private static final String K_SCREEN_REST = "screen_rest";
    private static final String K_BRIGHTNESS = "brightness";
    private static final String K_NIGHT_DIM = "night_dim";
    private static final String K_NIGHT_START = "night_start";
    private static final String K_NIGHT_END = "night_end";
    private static final String K_NIGHT_BRIGHTNESS = "night_brightness";
    private static final String K_HINT_SHOWN = "hint_shown";

    private static Prefs sInstance;

    private final SharedPreferences sp;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context context) {
        if (sInstance == null) {
            sInstance = new Prefs(context);
        }
        return sInstance;
    }

    public String theme() {
        String v = sp.getString(K_THEME, DEF_THEME);
        return (v == null) ? DEF_THEME : v;
    }

    public Prefs setTheme(String v) {
        sp.edit().putString(K_THEME, v).apply();
        return this;
    }

    /** 空字符串表示跟随系统。 */
    public String timeZone() {
        String v = sp.getString(K_TIME_ZONE, DEF_TIME_ZONE);
        return (v == null) ? DEF_TIME_ZONE : v;
    }

    public Prefs setTimeZone(String v) {
        sp.edit().putString(K_TIME_ZONE, v == null ? DEF_TIME_ZONE : v).apply();
        return this;
    }

    public String format() {
        String v = sp.getString(K_FORMAT, DEF_FORMAT);
        return (v == null || v.trim().isEmpty()) ? DEF_FORMAT : v;
    }

    public Prefs setFormat(String v) {
        sp.edit().putString(K_FORMAT, v).apply();
        return this;
    }

    public int textColor() {
        return sp.getInt(K_TEXT_COLOR, DEF_TEXT_COLOR);
    }

    public Prefs setTextColor(int v) {
        sp.edit().putInt(K_TEXT_COLOR, v).apply();
        return this;
    }

    public int bgColor() {
        return sp.getInt(K_BG_COLOR, DEF_BG_COLOR);
    }

    public Prefs setBgColor(int v) {
        sp.edit().putInt(K_BG_COLOR, v).apply();
        return this;
    }

    public int textSize() {
        return clamp(sp.getInt(K_TEXT_SIZE, DEF_TEXT_SIZE), MIN_TEXT_SIZE, MAX_TEXT_SIZE);
    }

    public Prefs setTextSize(int v) {
        sp.edit().putInt(K_TEXT_SIZE, clamp(v, MIN_TEXT_SIZE, MAX_TEXT_SIZE)).apply();
        return this;
    }

    public boolean showDate() {
        return sp.getBoolean(K_SHOW_DATE, DEF_SHOW_DATE);
    }

    public Prefs setShowDate(boolean v) {
        sp.edit().putBoolean(K_SHOW_DATE, v).apply();
        return this;
    }

    public boolean chime() {
        return sp.getBoolean(K_CHIME, DEF_CHIME);
    }

    public Prefs setChime(boolean v) {
        sp.edit().putBoolean(K_CHIME, v).apply();
        return this;
    }

    public boolean keepAwake() {
        return sp.getBoolean(K_KEEP_AWAKE, DEF_KEEP_AWAKE);
    }

    public Prefs setKeepAwake(boolean v) {
        sp.edit().putBoolean(K_KEEP_AWAKE, v).apply();
        return this;
    }

    public boolean screenRest() {
        return sp.getBoolean(K_SCREEN_REST, DEF_SCREEN_REST);
    }

    public Prefs setScreenRest(boolean v) {
        sp.edit().putBoolean(K_SCREEN_REST, v).apply();
        return this;
    }

    public boolean burnIn() {
        return sp.getBoolean(K_BURN_IN, DEF_BURN_IN);
    }

    public Prefs setBurnIn(boolean v) {
        sp.edit().putBoolean(K_BURN_IN, v).apply();
        return this;
    }

    /** -1 跟随系统，否则 1..100（百分比）。 */
    public int brightness() {
        return sp.getInt(K_BRIGHTNESS, DEF_BRIGHTNESS);
    }

    public Prefs setBrightness(int v) {
        sp.edit().putInt(K_BRIGHTNESS, v).apply();
        return this;
    }

    public boolean nightDim() {
        return sp.getBoolean(K_NIGHT_DIM, DEF_NIGHT_DIM);
    }

    public Prefs setNightDim(boolean v) {
        sp.edit().putBoolean(K_NIGHT_DIM, v).apply();
        return this;
    }

    /** 从 00:00 起的分钟数。 */
    public int nightStart() {
        return sp.getInt(K_NIGHT_START, DEF_NIGHT_START);
    }

    public Prefs setNightStart(int minutes) {
        sp.edit().putInt(K_NIGHT_START, minutes).apply();
        return this;
    }

    public int nightEnd() {
        return sp.getInt(K_NIGHT_END, DEF_NIGHT_END);
    }

    public Prefs setNightEnd(int minutes) {
        sp.edit().putInt(K_NIGHT_END, minutes).apply();
        return this;
    }

    public int nightBrightness() {
        return clamp(sp.getInt(K_NIGHT_BRIGHTNESS, DEF_NIGHT_BRIGHTNESS),
                MIN_NIGHT_BRIGHTNESS, MAX_NIGHT_BRIGHTNESS);
    }

    public Prefs setNightBrightness(int v) {
        sp.edit().putInt(K_NIGHT_BRIGHTNESS,
                clamp(v, MIN_NIGHT_BRIGHTNESS, MAX_NIGHT_BRIGHTNESS)).apply();
        return this;
    }

    public boolean hintShown() {
        return sp.getBoolean(K_HINT_SHOWN, false);
    }

    public void setHintShown(boolean v) {
        sp.edit().putBoolean(K_HINT_SHOWN, v).apply();
    }

    /**
     * 旧版本可能留下「主题键」和「配色」不同步的状态：
     * 配色恰好等于某套预置主题的配色时，把主题键校正到那一套。
     */
    public void syncThemeWithColors() {
        Theme current = Theme.get(theme());
        int fg = textColor();
        int bg = bgColor();
        if (current.fg == fg && current.bg == bg) {
            return;
        }
        for (Theme theme : Theme.ALL) {
            if (theme.fg == fg && theme.bg == bg) {
                setTheme(theme.key);
                return;
            }
        }
    }

    public void restoreDefaults() {
        sp.edit()
                .putString(K_THEME, DEF_THEME)
                .putString(K_TIME_ZONE, DEF_TIME_ZONE)
                .putString(K_FORMAT, DEF_FORMAT)
                .putInt(K_TEXT_COLOR, DEF_TEXT_COLOR)
                .putInt(K_BG_COLOR, DEF_BG_COLOR)
                .putInt(K_TEXT_SIZE, DEF_TEXT_SIZE)
                .putBoolean(K_SHOW_DATE, DEF_SHOW_DATE)
                .putBoolean(K_CHIME, DEF_CHIME)
                .putBoolean(K_KEEP_AWAKE, DEF_KEEP_AWAKE)
                .putBoolean(K_BURN_IN, DEF_BURN_IN)
                .putBoolean(K_SCREEN_REST, DEF_SCREEN_REST)
                .putInt(K_BRIGHTNESS, DEF_BRIGHTNESS)
                .putBoolean(K_NIGHT_DIM, DEF_NIGHT_DIM)
                .putInt(K_NIGHT_START, DEF_NIGHT_START)
                .putInt(K_NIGHT_END, DEF_NIGHT_END)
                .putInt(K_NIGHT_BRIGHTNESS, DEF_NIGHT_BRIGHTNESS)
                .apply();
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}

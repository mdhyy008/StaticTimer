package cn.mdhyy.staticclock;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * 桌钟主界面：主题化的时间与日期，长按进设置，整点报时，夜间自动减光。
 */
public class MainActivity extends Activity {

    private static final String CHANNEL_ID = "hourly_chime";
    private static final int NOTIFICATION_ID = 1;

    /** 位移幅度占字号的比例，以及上下限（dp）。 */
    private static final float BURN_AMPLITUDE_RATIO = 0.10f;
    private static final int BURN_MIN_DP = 8;
    private static final int BURN_MAX_DP = 36;

    private Prefs prefs;
    private Theme theme;

    private TextView clockView;
    private TextView dateView;
    private TextView hintView;
    private LinearLayout clockBox;
    private View restOverlay;
    private View rootView;

    /** 主题自带的装饰：窗口标题、命令行、闪烁光标、状态行、日历牌、屏框。 */
    private TextView termTop;
    private TextView termCmd;
    private TextView termCursor;
    private TextView termStatus;
    private TextView calBadge;
    private View screenFrame;
    private DayLineView dayLine;
    private CornerFrameView cornerFrame;
    private TextView termBottom;
    private View focusRule;
    private TextView mossDot;
    private ObjectAnimator mossBreath;
    private String decor = "";
    private boolean blinkOn = true;
    private int lastChromeMinute = -1;
    private int batteryPercent = -1;
    private boolean charging;
    private final long startedAt = SystemClock.elapsedRealtime();

    private TimeZone zone = TimeZone.getDefault();
    private String appliedZoneId;
    private SimpleDateFormat timeFormatter;
    private SimpleDateFormat dateFormatter;
    private String appliedPattern;

    private int lastChimeHour = -1;
    private int lastBurnInMinute = -1;

    /** 整屏休息：每 30 分钟黑屏 3 秒。 */
    private static final long REST_INTERVAL_MS = 30L * 60L * 1000L;
    private static final long REST_DURATION_MS = 3000L;
    private static final long REST_FADE_MS = 450L;
    private long lastRestAt = 0L;
    private boolean resting;
    private int appliedBrightness = Integer.MIN_VALUE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        setContentView(R.layout.activity_main);

        clockView = (TextView) findViewById(R.id.clock);
        dateView = (TextView) findViewById(R.id.date);
        hintView = (TextView) findViewById(R.id.hint);
        clockBox = (LinearLayout) findViewById(R.id.clockBox);
        termTop = (TextView) findViewById(R.id.termTop);
        termCmd = (TextView) findViewById(R.id.termCmd);
        termCursor = (TextView) findViewById(R.id.termCursor);
        termStatus = (TextView) findViewById(R.id.termStatus);
        calBadge = (TextView) findViewById(R.id.calBadge);
        screenFrame = findViewById(R.id.screenFrame);
        dayLine = (DayLineView) findViewById(R.id.dayLine);
        cornerFrame = (CornerFrameView) findViewById(R.id.cornerFrame);
        termBottom = (TextView) findViewById(R.id.termBottom);
        focusRule = findViewById(R.id.focusRule);
        mossDot = (TextView) findViewById(R.id.mossDot);
        restOverlay = findViewById(R.id.restOverlay);
        rootView = findViewById(R.id.root);

        rootView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openSettings();
                return true;
            }
        });
        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flashHint();
            }
        });

        dateFormatter = buildDateFormatter();
        ensureChannel();
        lastChimeHour = Calendar.getInstance(zone).get(Calendar.HOUR_OF_DAY);
        applyPrefs();

        if (!prefs.hintShown()) {
            prefs.setHintShown(true);
            rootView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    flashHint();
                }
            }, 600L);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPrefs();
        goImmersive();
        handler.removeCallbacks(ticker);
        lastRestAt = SystemClock.elapsedRealtime();
        tick();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(blinker);
        stopMossBreath();
        endRest();
        super.onPause();
    }

    /** 返回键只回桌面，不结束时钟。 */
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            goImmersive();
        }
    }

    /** 横竖屏切换后重算终端标题里的字符网格等依赖屏幕尺寸的装饰。 */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyPrefs();
    }

    // ------------------------------------------------------------------ 时钟

    private void applyPrefs() {
        prefs.syncThemeWithColors();
        theme = Theme.get(prefs.theme());

        // 先定下时区和格式，装饰文字里要显示真实的制式与时区
        String zoneId = prefs.timeZone();
        if (!zoneId.equals(appliedZoneId)) {
            appliedZoneId = zoneId;
            zone = resolveZone(zoneId);
            dateFormatter.setTimeZone(zone);
            appliedPattern = null;
        }

        String pattern = prefs.format();
        if (!pattern.equals(appliedPattern)) {
            appliedPattern = pattern;
            timeFormatter = buildTimeFormatter(pattern);
        }
        timeFormatter.setTimeZone(zone);

        applyTheme();
        android.util.Log.i("StaticClock", "theme=" + prefs.theme()
                + " fg=" + Integer.toHexString(prefs.textColor())
                + " bg=" + Integer.toHexString(prefs.bgColor()));

        int textColor = prefs.textColor();
        rootView.setBackgroundColor(prefs.bgColor());
        clockView.setTextColor(textColor);
        dateView.setTextColor(textColor);
        dateView.setAlpha(0.62f);
        // 日期已经由主题自己的页眉/日历牌承担的，就不要再显示一行
        boolean dateInChrome = "calendar".equals(decor) || "paper".equals(decor)
                || "focus".equals(decor);
        boolean showDate = prefs.showDate() && !dateInChrome;
        dateView.setVisibility(showDate ? View.VISIBLE : View.GONE);

        if (prefs.keepAwake()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        appliedBrightness = Integer.MIN_VALUE;
        updateClock();
        applyBrightness();
    }

    /** 把主题的字体、字距、位置、日期顺序和装饰落到视图上。 */
    private void applyTheme() {
        decor = theme.decor;
        boolean inline = "terminal".equals(decor);

        // 每次按主题重排：终端主题在时间上下各多一行命令行
        clockBox.removeAllViews();
        if (inline) {
            clockBox.addView(termCmd);
        }
        if (theme.dateAbove) {
            clockBox.addView(dateView);
            clockBox.addView(clockView);
        } else {
            clockBox.addView(clockView);
            clockBox.addView(dateView);
        }
        if ("moss".equals(decor)) {
            LinearLayout.LayoutParams dotLp = (LinearLayout.LayoutParams) mossDot.getLayoutParams();
            dotLp.topMargin = dp(10);
            mossDot.setLayoutParams(dotLp);
            clockBox.addView(mossDot);
        }
        if (inline) {
            clockBox.addView(termCursor);
        }

        LinearLayout.LayoutParams dateLp = (LinearLayout.LayoutParams) dateView.getLayoutParams();
        dateLp.topMargin = theme.dateAbove ? 0 : dp(12);
        dateLp.bottomMargin = theme.dateAbove ? dp(10) : 0;
        dateView.setLayoutParams(dateLp);

        if (inline) {
            LinearLayout.LayoutParams cmdLp = (LinearLayout.LayoutParams) termCmd.getLayoutParams();
            cmdLp.bottomMargin = dp(6);
            termCmd.setLayoutParams(cmdLp);

            LinearLayout.LayoutParams cursorLp = (LinearLayout.LayoutParams) termCursor.getLayoutParams();
            cursorLp.topMargin = dp(10);
            termCursor.setLayoutParams(cursorLp);
        }

        FrameLayout.LayoutParams boxLp = (FrameLayout.LayoutParams) clockBox.getLayoutParams();
        boxLp.gravity = theme.gravity;
        clockBox.setLayoutParams(boxLp);

        clockView.setGravity(theme.align);
        dateView.setGravity(theme.align);

        // 数字宽度不一致会让整行时间左右横移，这里实测一遍，不合格就退回等宽字体
        Typeface clockTypeface = theme.typeface();
        Typeface dateTypeface = theme.dateTypeface();
        if (!digitsAreTabular(clockTypeface)) {
            clockTypeface = Typeface.create("monospace", Typeface.NORMAL);
            dateTypeface = Typeface.create("monospace", Typeface.NORMAL);
        }
        clockView.setTypeface(clockTypeface);
        dateView.setTypeface(dateTypeface);
        clockView.setLetterSpacing(theme.letterSpacing);
        // 终端用带斜杠的零，更像等宽终端字体
        clockView.setFontFeatureSettings(inline ? "tnum,zero" : "tnum");
        dateView.setLetterSpacing(0.04f);

        applyTextEffect();
        applyChrome();
    }

    // ---------------------------------------------------------------- 装饰

    /** 霓虹主题：描边字 + 辉光；其它主题把这两种效果清掉。 */
    private void applyTextEffect() {
        boolean neon = "neon".equals(decor);
        int fg = prefs.textColor();
        Paint clockPaint = clockView.getPaint();
        clockPaint.setStrokeJoin(Paint.Join.ROUND);
        clockPaint.setStyle(neon ? Paint.Style.STROKE : Paint.Style.FILL);
        if (neon) {
            clockPaint.setStrokeWidth(neonStrokeWidth());
            clockView.setShadowLayer(neonGlowRadius(), 0f, 0f, fg);
            dateView.setShadowLayer(neonGlowRadius() * 0.4f, 0f, 0f, fg);
        } else {
            clockPaint.setStrokeWidth(0f);
            clockView.setShadowLayer(0f, 0f, 0f, 0);
            dateView.setShadowLayer(0f, 0f, 0f, 0);
        }
    }

    /** 当前字号换算成像素，霓虹的描边和辉光都按它取比例。 */
    private float textPx() {
        return effectiveTextSize() * getResources().getDisplayMetrics().scaledDensity;
    }

    /**
     * 0–9 十个数字逐个量宽度，全部一样才算合格。
     * 主题字体都挑了等宽数字的（Roboto / Noto Serif / Cutive Mono / Carrois），
     * 这条只是兜底：哪个设备上字体映射不同、数字变宽窄不一，就退回等宽字体，绝不横移。
     */
    private boolean digitsAreTabular(Typeface typeface) {
        Paint probe = new Paint(Paint.ANTI_ALIAS_FLAG);
        probe.setTypeface(typeface);
        probe.setTextSize(120f);
        probe.setFontFeatureSettings("tnum");
        float reference = probe.measureText("0");
        for (char digit = '1'; digit <= '9'; digit++) {
            if (Math.abs(probe.measureText(String.valueOf(digit)) - reference) > 0.5f) {
                return false;
            }
        }
        return true;
    }

    private float neonStrokeWidth() {
        return textPx() * 0.028f;
    }

    /** 6 秒一呼一吸的辉光，幅度很小，桌钟上看不出在“动”。 */
    private float neonGlowRadius() {
        double phase = (SystemClock.uptimeMillis() % 6000L) / 6000.0 * Math.PI * 2.0;
        float pulse = 0.85f + 0.15f * (float) Math.sin(phase);
        return textPx() * 0.14f * pulse;
    }

    private void applyChrome() {
        boolean terminal = "terminal".equals(decor);
        boolean neon = "neon".equals(decor);
        boolean eink = "eink".equals(decor);
        boolean calendar = "calendar".equals(decor);
        boolean ink = "ink".equals(decor);
        boolean amber = "amber".equals(decor);
        boolean paper = "paper".equals(decor);
        boolean focus = "focus".equals(decor);
        boolean moss = "moss".equals(decor);

        readBattery();

        int fg = prefs.textColor();
        int accent = theme.accent != 0 ? theme.accent : fg;

        // 墨水屏只留页脚，不要标题栏——电子书阅读器本来就不在屏幕里挂标题
        // 纸和黑体的页眉就是日期本身，跟着“显示日期”开关走
        termTop.setVisibility(terminal || neon || calendar || ink
                || ((paper || focus) && prefs.showDate())
                ? View.VISIBLE : View.GONE);
        termStatus.setVisibility(terminal || neon || eink || calendar || paper
                ? View.VISIBLE : View.GONE);
        calBadge.setVisibility(calendar ? View.VISIBLE : View.GONE);
        screenFrame.setVisibility(eink ? View.VISIBLE : View.GONE);
        dayLine.setVisibility(ink ? View.VISIBLE : View.GONE);
        cornerFrame.setVisibility(amber ? View.VISIBLE : View.GONE);
        focusRule.setVisibility(focus ? View.VISIBLE : View.GONE);
        termBottom.setVisibility(amber && prefs.chime() ? View.VISIBLE : View.GONE);
        mossDot.setVisibility(moss ? View.VISIBLE : View.GONE);

        handler.removeCallbacks(blinker);
        if (!moss) {
            stopMossBreath();
        } else {
            startMossBreath();
        }

        float chromeSize = Math.max(12f, Math.min(22f, effectiveTextSize() * 0.18f));
        termTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, chromeSize * 0.92f);
        termStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, chromeSize * 0.85f);
        termBottom.setTextSize(TypedValue.COMPLEX_UNIT_SP, chromeSize * 0.8f);
        termTop.setTypeface(Typeface.create(theme.family, Typeface.NORMAL));
        termStatus.setTypeface(Typeface.create(theme.family, Typeface.NORMAL));
        termBottom.setTypeface(Typeface.create(theme.family, Typeface.NORMAL));
        termTop.setAlpha(terminal ? 0.72f : 1f);
        termStatus.setAlpha(terminal ? 0.55f : 1f);

        FrameLayout.LayoutParams topLp = (FrameLayout.LayoutParams) termTop.getLayoutParams();
        topLp.gravity = calendar ? (Gravity.TOP | Gravity.END)
                : (paper ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                : (Gravity.TOP | Gravity.START));
        topLp.setMarginStart(dp(44));
        topLp.setMarginEnd(dp(44));
        termTop.setLayoutParams(topLp);

        FrameLayout.LayoutParams statusLp = (FrameLayout.LayoutParams) termStatus.getLayoutParams();
        statusLp.gravity = paper
                ? (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
                : (Gravity.BOTTOM | Gravity.START);
        statusLp.setMarginStart(dp(44));
        statusLp.setMarginEnd(dp(44));
        termStatus.setLayoutParams(statusLp);

        termTop.setLetterSpacing(terminal ? 0.02f : 0.1f);
        termStatus.setLetterSpacing(terminal ? 0f : 0.06f);
        termTop.setTextColor(terminal ? fg : accent);
        termStatus.setTextColor(terminal || neon ? fg : accent);
        termBottom.setTextColor(fg);

        dayLine.setTextColor(fg);
        cornerFrame.setFrameColor(withAlpha(fg, 0x55));
        focusRule.setBackgroundColor(withAlpha(fg, 0x38));
        mossDot.setTextColor(fg);

        if (terminal) {
            termCmd.setTextSize(TypedValue.COMPLEX_UNIT_SP, chromeSize);
            termCursor.setTextSize(TypedValue.COMPLEX_UNIT_SP, chromeSize);
            blinkOn = true;
            handler.postDelayed(blinker, 530L);
        } else if (ink) {
            termTop.setTextColor(withAlpha(fg, 0xAA));
        }

        fillChromeText();
    }

    /** 装饰文字：每一项都来自真实状态，不放只起装饰作用假标签。 */
    private void fillChromeText() {
        int fg = prefs.textColor();
        if ("terminal".equals(decor)) {
            termTop.setText(buildTopBar(fg));
            termCmd.setText(promptLine(dateCommand(), fg));
            applyCursorText();
            termStatus.setText("bat=" + batteryField()
                    + " · night=" + (prefs.nightDim() ? nightRange() : "off")
                    + " · chime=" + (prefs.chime() ? "on" : "off")
                    + " · up=" + uptimeLabel());
        } else if ("neon".equals(decor)) {
            termTop.setText(batteryLine());
            termStatus.setText(nightLabel());
        } else if ("eink".equals(decor)) {
            termStatus.setText(batteryLine() + " · " + utcOffsetLabel());
        } else if ("calendar".equals(decor)) {
            termTop.setText(calendarHeadline());
            termStatus.setText(calendarProgress()
                    + (batteryPercent >= 0 ? " · " + batteryLine() : ""));
            updateCalendarBadge();
        } else if ("paper".equals(decor)) {
            termTop.setText(chineseFullDate());
            termStatus.setText("第 " + dayOfYear() + " 天");
        } else if ("focus".equals(decor)) {
            termTop.setText(isoDate() + " · " + weekdayText());
        } else if ("amber".equals(decor)) {
            termBottom.setText(prefs.chime()
                    ? "下次报时 · " + minutesToNextHour() + " 分钟后" : "");
        } else if ("ink".equals(decor)) {
            float fraction = dayFraction();
            dayLine.setFraction(fraction);
            termTop.setText("DAY " + Math.round(fraction * 100) + "%");
        }
    }

    /** 一天过去了多少，0–1。 */
    private float dayFraction() {
        Calendar now = Calendar.getInstance(zone);
        int seconds = now.get(Calendar.HOUR_OF_DAY) * 3600
                + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        return seconds / 86400f;
    }

    private int dayOfYear() {
        return Calendar.getInstance(zone).get(Calendar.DAY_OF_YEAR);
    }

    /** 距离下一个整点还有几分钟，整点报时就是按它触发。 */
    private int minutesToNextHour() {
        int minute = Calendar.getInstance(zone).get(Calendar.MINUTE);
        return minute == 0 ? 60 : 60 - minute;
    }

    private String isoDate() {
        return new SimpleDateFormat("yyyy.MM.dd", Locale.US)
                .format(Calendar.getInstance(zone).getTime());
    }

    /** 页眉用的中文年月，例如「二〇二六年 十月」。 */
    private String chineseFullDate() {
        Calendar now = Calendar.getInstance(zone);
        return chineseYear(now.get(Calendar.YEAR))
                + "年 " + chineseMonth(now.get(Calendar.MONTH) + 1)
                + "月" + chineseDay(now.get(Calendar.DAY_OF_MONTH))
                + "日 · " + weekdayText();
    }

    private static String chineseYear(int year) {
        StringBuilder out = new StringBuilder();
        for (char c : String.valueOf(year).toCharArray()) {
            out.append("〇一二三四五六七八九".charAt(c - '0'));
        }
        return out.toString();
    }

    private static String chineseMonth(int month) {
        if (month == 10) {
            return "十";
        }
        if (month == 11) {
            return "十一";
        }
        if (month == 12) {
            return "十二";
        }
        return String.valueOf("〇一二三四五六七八九".charAt(month));
    }

    /** 1–31 的中文写法：一日、十日、十一日、二十日、三十一日。 */
    private static String chineseDay(int day) {
        if (day <= 10) {
            return day == 10 ? "十" : String.valueOf("〇一二三四五六七八九".charAt(day));
        }
        if (day < 20) {
            return "十" + "〇一二三四五六七八九".charAt(day - 10);
        }
        int tens = day / 10;
        int ones = day % 10;
        String head = "〇一二三四五六七八九".charAt(tens) + "十";
        return ones == 0 ? head : head + "〇一二三四五六七八九".charAt(ones);
    }

    private String weekdayText() {
        return new SimpleDateFormat("EEEE", Locale.getDefault())
                .format(Calendar.getInstance(zone).getTime());
    }

    // -------------------------------------------------------------- 呼吸点

    private void startMossBreath() {
        if (mossBreath != null) {
            return;
        }
        mossBreath = ObjectAnimator.ofFloat(mossDot, "alpha", 0.85f, 0.18f);
        mossBreath.setDuration(4000L);
        mossBreath.setRepeatCount(ValueAnimator.INFINITE);
        mossBreath.setRepeatMode(ValueAnimator.REVERSE);
        mossBreath.setInterpolator(new LinearInterpolator());
        mossBreath.start();
    }

    private void stopMossBreath() {
        if (mossBreath != null) {
            mossBreath.cancel();
            mossBreath = null;
        }
    }

    // ------------------------------------------------------------ 真实状态

    private void readBattery() {
        try {
            Intent status = registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (status == null) {
                return;
            }
            int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            charging = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            batteryPercent = (level >= 0 && scale > 0)
                    ? Math.round(level * 100f / scale) : -1;
        } catch (RuntimeException e) {
            batteryPercent = -1;
        }
    }

    /** 终端风格的电量字段：82% 表示放电，82%+ 表示在充电。 */
    private String batteryField() {
        if (batteryPercent < 0) {
            return "n/a";
        }
        return batteryPercent + "%" + (charging ? "+" : "");
    }

    private String batteryLine() {
        if (batteryPercent < 0) {
            return "";
        }
        return charging ? batteryPercent + "% · 充电中" : batteryPercent + "%";
    }

    private String uptimeLabel() {
        long minutes = (SystemClock.elapsedRealtime() - startedAt) / 60000L;
        return minutes >= 60 ? (minutes / 60) + "h" + (minutes % 60) + "m" : minutes + "m";
    }

    private String nightRange() {
        return hhmm(prefs.nightStart()) + "-" + hhmm(prefs.nightEnd());
    }

    private static String hhmm(int minutesOfDay) {
        return String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    /**
     * 只在夜间减光真正生效时才说话：屏幕上正被压暗，还剩多久恢复。
     * 没开、或白天没生效时返回空串，不在钟面上占一行看不懂的字。
     */
    private String nightLabel() {
        int start = prefs.nightStart();
        int end = prefs.nightEnd();
        if (!prefs.nightDim() || start == end || !isNightNow()) {
            return "";
        }
        Calendar now = Calendar.getInstance(zone);
        int minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int remain;
        if (start < end) {
            remain = end - minutes;
        } else {
            remain = minutes >= start ? end + 1440 - minutes : end - minutes;
        }
        if (remain < 0) {
            remain = 0;
        }
        String span = remain >= 60
                ? (remain / 60) + "h" + (remain % 60) + "m"
                : remain + "m";
        return "屏幕已调暗 · " + span + " 后恢复";
    }

    /** 月份和日号由左上角日历牌负责，右上角只补年份和星期。 */
    private String calendarHeadline() {
        return Calendar.getInstance(zone).get(Calendar.YEAR) + "年 · " + weekdayText();
    }

    private String calendarProgress() {
        Calendar now = Calendar.getInstance(zone);
        return "第 " + now.get(Calendar.DAY_OF_YEAR) + " 天 / "
                + now.getActualMaximum(Calendar.DAY_OF_YEAR) + " 天";
    }

    private void updateCalendarBadge() {
        Calendar now = Calendar.getInstance(zone);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(theme.accent);
        badge.setCornerRadius(dp(10));
        calBadge.setBackground(badge);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(String.valueOf(now.get(Calendar.MONTH) + 1)).append("月\n");
        int start = sb.length();
        sb.append(String.valueOf(now.get(Calendar.DAY_OF_MONTH)));
        sb.setSpan(new RelativeSizeSpan(2.1f), start, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        calBadge.setText(sb);
    }

    /** 当前生效时区的 UTC 偏移，例如 UTC+8:00。 */
    private String utcOffsetLabel() {
        int totalMinutes = zone.getOffset(System.currentTimeMillis()) / 60000;
        int hours = totalMinutes / 60;
        int minutes = Math.abs(totalMinutes % 60);
        String sign = totalMinutes < 0 ? "-" : "+";
        return String.format(Locale.US, "UTC%s%d:%02d", sign, Math.abs(hours), minutes);
    }

    /**
     * 把显示格式翻译成 date(1) 的格式串，命令行写的就是真正能产出这行时间的命令。
     * 含年月日等日期记号时，退回不带格式的 date。
     */
    private String dateCommand() {
        String pattern = prefs.format();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pattern.length(); ) {
            char c = pattern.charAt(i);
            int run = 1;
            while (i + run < pattern.length() && pattern.charAt(i + run) == c) {
                run++;
            }
            switch (c) {
                case 'H':
                    out.append("%H");
                    break;
                case 'h':
                    out.append("%I");
                    break;
                case 'm':
                    out.append("%M");
                    break;
                case 's':
                    out.append("%S");
                    break;
                case 'a':
                    out.append("%p");
                    break;
                case 'y':
                case 'M':
                case 'd':
                case 'E':
                case 'D':
                case 'F':
                case 'G':
                    return "date";
                default:
                    out.append(c);
            }
            i += run;
        }
        if (out.length() == 0) {
            return "date";
        }
        return "date +\"" + out + "\"";
    }

    /** 左上角窗口标题：三个圆点 + 进程名 + 当前真正生效的时区。 */
    private CharSequence buildTopBar(int fg) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int[] dots = {0xFFFF5F57, 0xFFFEBC2E, 0xFF28C840};
        for (int dot : dots) {
            int start = sb.length();
            sb.append("● ");
            sb.setSpan(new ForegroundColorSpan(dot), start, start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int start = sb.length();
        sb.append("staticclock — ").append(zone.getID());
        sb.setSpan(new ForegroundColorSpan(fg), start, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** 一行提示符：用户@主机:~$ 命令，命令部分可以留空。 */
    private CharSequence promptLine(String command, int fg) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append("mdhyy@staticclock");
        sb.setSpan(new ForegroundColorSpan(fg), start, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = sb.length();
        sb.append(":~$ ");
        sb.setSpan(new ForegroundColorSpan(withAlpha(fg, 0x99)), start, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (!command.isEmpty()) {
            start = sb.length();
            sb.append(command);
            sb.setSpan(new ForegroundColorSpan(withAlpha(fg, 0xE6)), start, sb.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    /** 光标用颜色透明来“熄灭”，等宽字体下不会把整行挤动。 */
    private void applyCursorText() {
        int fg = prefs.textColor();
        SpannableStringBuilder sb = new SpannableStringBuilder(promptLine("", fg));
        int start = sb.length();
        sb.append("█");
        sb.setSpan(new ForegroundColorSpan(blinkOn ? fg : 0), start, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        termCursor.setText(sb);
    }

    private final Runnable blinker = new Runnable() {
        @Override
        public void run() {
            if (!"terminal".equals(decor)) {
                return;
            }
            blinkOn = !blinkOn;
            applyCursorText();
            handler.postDelayed(this, 530L);
        }
    };

    /** 装饰文字每分钟刷新一次（电量、夜间倒计时、剩余时长都会变）。 */
    private void updateChrome() {
        if (decor.isEmpty()) {
            return;
        }
        int minute = (int) ((System.currentTimeMillis() / 60000L) & 0x7FFFFFFF);
        if (minute != lastChromeMinute) {
            lastChromeMinute = minute;
            readBattery();
            fillChromeText();
        }
        if ("neon".equals(decor)) {
            applyTextEffect();
            double phase = (SystemClock.uptimeMillis() % 4800L) / 4800.0 * Math.PI * 2.0;
            termStatus.setAlpha(0.45f + 0.35f * (float) (0.5 + 0.5 * Math.sin(phase)));
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** 主题不再缩放字号：显示多大完全由设置里的字号决定。 */
    private float effectiveTextSize() {
        return prefs.textSize();
    }

    private void tick() {
        updateClock();
        updateChrome();
        applyBrightness();
        maybeChime();
        maybeRest();
        long delay = 1000L - (System.currentTimeMillis() % 1000L) + 20L;
        handler.postDelayed(ticker, delay);
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance(zone);
        clockView.setText(formatOrFallback(timeFormatter, now.getTime()));
        if (prefs.showDate()) {
            dateView.setText(formatOrFallback(dateFormatter, now.getTime()));
        }
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                Math.max(13f, effectiveTextSize() * 0.26f));
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveTextSize());
        if ("neon".equals(decor)) {
            applyTextEffect();  // 描边宽度、辉光半径都跟着字号走
        }
        applyBurnInOffset(now);
    }

    private static String formatOrFallback(SimpleDateFormat formatter, Date date) {
        try {
            return formatter.format(date);
        } catch (RuntimeException e) {
            return "--";
        }
    }

    /**
     * 防烧屏：每分钟在矩形范围内随机挪一次。
     * 幅度取字号的 10%（8–36dp），比固定几像素更能打散累积点亮时间；
     * 用分钟数做种子，同一分钟内位置稳定，不会每秒抖动。
     */
    private void applyBurnInOffset(Calendar now) {
        if (!prefs.burnIn()) {
            setChromeOffset(0f, 0f);
            lastBurnInMinute = -1;
            return;
        }
        int minute = (int) ((now.getTimeInMillis() / 60000L) & 0x7FFFFFFF);
        if (minute == lastBurnInMinute) {
            return;
        }
        lastBurnInMinute = minute;

        float density = getResources().getDisplayMetrics().density;
        float amplitude = effectiveTextSize() * density * BURN_AMPLITUDE_RATIO;
        amplitude = Math.max(dp(BURN_MIN_DP), Math.min(dp(BURN_MAX_DP), amplitude));

        Random random = new Random(minute * 2654435761L);
        float x = (random.nextFloat() * 2f - 1f) * amplitude;
        float y = (random.nextFloat() * 2f - 1f) * amplitude;
        setChromeOffset(x, y);
    }

    /** 装饰文字跟着时间块一起挪，静态角落里的字也不会长期压在同一个像素上。 */
    private void setChromeOffset(float x, float y) {
        clockBox.setTranslationX(x);
        clockBox.setTranslationY(y);
        termTop.setTranslationX(x);
        termTop.setTranslationY(y);
        termStatus.setTranslationX(x);
        termStatus.setTranslationY(y);
        dayLine.setTranslationX(x);
        dayLine.setTranslationY(y);
        termBottom.setTranslationX(x);
        termBottom.setTranslationY(y);
        calBadge.setTranslationX(x);
        calBadge.setTranslationY(y);
    }

    private static TimeZone resolveZone(String id) {
        if (id == null || id.isEmpty()) {
            return TimeZone.getDefault();
        }
        return TimeZone.getTimeZone(id);
    }

    private static SimpleDateFormat buildTimeFormatter(String pattern) {
        try {
            return new SimpleDateFormat(pattern, Locale.getDefault());
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat(Prefs.DEF_FORMAT, Locale.getDefault());
        }
    }

    private static SimpleDateFormat buildDateFormatter() {
        String pattern = "M月d日 EEEE";
        try {
            String best = android.text.format.DateFormat
                    .getBestDateTimePattern(Locale.getDefault(), "MMMdEEE");
            if (best != null && !best.isEmpty()) {
                pattern = best;
            }
        } catch (Throwable ignored) {
            // 老系统上拿不到骨架就退回手写模式
        }
        try {
            return new SimpleDateFormat(pattern, Locale.getDefault());
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat("M月d日 EEEE", Locale.getDefault());
        }
    }

    // ---------------------------------------------------------------- 亮度

    private void maybeRest() {
        if (!prefs.screenRest()) {
            lastRestAt = SystemClock.elapsedRealtime();
            return;
        }
        if (resting || lastRestAt == 0L) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastRestAt < REST_INTERVAL_MS) {
            return;
        }
        lastRestAt = now;
        startRest();
    }

    private void startRest() {
        resting = true;
        restOverlay.animate().cancel();
        restOverlay.setAlpha(0f);
        restOverlay.setVisibility(View.VISIBLE);
        restOverlay.animate().alpha(1f).setDuration(REST_FADE_MS).start();
        handler.postDelayed(restEnd, REST_FADE_MS + REST_DURATION_MS);
    }

    private final Runnable restEnd = new Runnable() {
        @Override
        public void run() {
            restOverlay.animate().alpha(0f).setDuration(REST_FADE_MS)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            restOverlay.setVisibility(View.GONE);
                            resting = false;
                        }
                    }).start();
        }
    };

    private void endRest() {
        if (!resting) {
            return;
        }
        handler.removeCallbacks(restEnd);
        restOverlay.animate().cancel();
        restOverlay.setAlpha(0f);
        restOverlay.setVisibility(View.GONE);
        resting = false;
    }

    private void applyBrightness() {
        int percent = (prefs.nightDim() && isNightNow())
                ? prefs.nightBrightness()
                : prefs.brightness();
        if (percent == appliedBrightness) {
            return;
        }
        appliedBrightness = percent;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (percent < 0) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            lp.screenBrightness = percent / 100f;
        }
        getWindow().setAttributes(lp);
    }

    private boolean isNightNow() {
        Calendar now = Calendar.getInstance(zone);
        int minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int start = prefs.nightStart();
        int end = prefs.nightEnd();
        if (start == end) {
            return false;
        }
        if (start < end) {
            return minutes >= start && minutes < end;
        }
        return minutes >= start || minutes < end;
    }

    // -------------------------------------------------------------- 整点报时

    private void maybeChime() {
        Calendar now = Calendar.getInstance(zone);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour == lastChimeHour) {
            return;
        }
        boolean onTheHour = now.get(Calendar.MINUTE) == 0;
        lastChimeHour = hour;
        if (onTheHour && prefs.chime()) {
            chime(timeFormatter.format(new Date()));
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_hourly),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("每到整点响一次提示音");
        channel.enableVibration(false);
        channel.setSound(chimeUri(), chimeAudioAttributes());
        nm.createNotificationChannel(channel);
    }

    private void chime(String text) {
        if (!canNotify()) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this)
                    .setSound(chimeUri())
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_LIGHTS);
        }

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_stat_clock)
                .setContentTitle(getString(R.string.chime_title))
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setColor(0xFF5EE0C0)
                .build();
        nm.notify(NOTIFICATION_ID, notification);
    }

    private boolean canNotify() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return false;
            }
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    private Uri chimeUri() {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + getPackageName() + "/" + R.raw.elegant);
    }

    private static AudioAttributes chimeAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    // ------------------------------------------------------------ 界面与窗口

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** 底部提示条：淡入，停留几秒，淡出。 */
    private void flashHint() {
        hintView.animate().cancel();
        hintView.setAlpha(0f);
        hintView.setVisibility(View.VISIBLE);
        hintView.animate()
                .alpha(1f)
                .setDuration(220)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        hintView.animate()
                                .alpha(0f)
                                .setStartDelay(3200)
                                .setDuration(700)
                                .start();
                    }
                })
                .start();
    }

    private void goImmersive() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }
}

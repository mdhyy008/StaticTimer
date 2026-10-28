package cn.mdhyy.staticclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 设置页：卡片式分组，所有改动实时预览，点「保存」才落盘。
 */
public class SettingsActivity extends Activity {

    private static final String CHANNEL_ID = "hourly_chime";

    private static final String[] FORMAT_PRESETS = {"HH:mm:ss", "HH:mm", "hh:mm a", "HH:mm:ss a"};

    private static final String[] ZONE_IDS = {
            "", "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul", "Asia/Singapore",
            "Asia/Bangkok", "Asia/Kolkata", "Asia/Dubai", "Europe/Moscow",
            "Europe/London", "Europe/Paris", "America/New_York",
            "America/Los_Angeles", "Australia/Sydney"
    };
    private static final String[] ZONE_NAMES = {
            "跟随系统", "北京", "东京", "首尔", "新加坡",
            "曼谷", "新德里", "迪拜", "莫斯科",
            "伦敦", "巴黎", "纽约",
            "洛杉矶", "悉尼"
    };

    private Prefs prefs;

    private LinearLayout content;
    private FrameLayout previewCard;
    private TextView previewClock;
    private TextView previewDate;

    private EditText etFormat;
    private EditText etTextColor;
    private EditText etBgColor;
    private LinearLayout previewBox;
    private Switch swDate;
    private Switch swChime;
    private Switch swAwake;
    private Switch swBurnIn;
    private Switch swRest;
    private Switch swNight;
    private SeekBar seekSize;
    private SeekBar seekBrightness;
    private SeekBar seekNightBrightness;
    private TextView tvSizeValue;
    private TextView tvBrightnessValue;
    private TextView tvNightBrightnessValue;
    private TextView tvChimeHint;
    private TextView tvThemeNote;
    private TextView pillNightStart;
    private TextView pillNightEnd;

    private final List<TextView> formatChips = new ArrayList<TextView>();
    private final List<TextView> themeTiles = new ArrayList<TextView>();
    private final List<TextView> themeNames = new ArrayList<TextView>();

    private SimpleDateFormat dateFormatter;
    private int pendingNightStart = Prefs.DEF_NIGHT_START;
    private int pendingNightEnd = Prefs.DEF_NIGHT_END;

    private final TextWatcher previewWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            updatePreview();
            persistTextFields();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        prefs.syncThemeWithColors();
        setContentView(R.layout.activity_settings);
        android.util.Log.i("StaticClock", "settings theme=" + prefs.theme()
                + " fg=" + Integer.toHexString(prefs.textColor())
                + " bg=" + Integer.toHexString(prefs.bgColor()));

        applyOrientation();
        content = (LinearLayout) findViewById(R.id.content);
        previewCard = (FrameLayout) findViewById(R.id.previewCard);
        previewBox = (LinearLayout) findViewById(R.id.previewBox);
        previewClock = (TextView) findViewById(R.id.previewClock);
        previewDate = (TextView) findViewById(R.id.previewDate);
        dateFormatter = buildDateFormatter();

        buildSections();
        populate();
        updatePreview();
        updateChimeHint();
    }

    /** 窄屏手机竖屏，平板 / 电视这类宽屏横屏。 */
    private void applyOrientation() {
        boolean wide = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        setRequestedOrientation(wide
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateChimeHint();
    }

    // ------------------------------------------------------------ 界面搭建

    private void buildSections() {
        buildDisplaySection();
        buildBrightnessSection();
        buildMiscSection();
        buildButtons();
    }

    private void buildDisplaySection() {
        section("显示");
        LinearLayout card = card();

        // 时间格式
        LinearLayout formatBlock = new LinearLayout(this);
        formatBlock.setOrientation(LinearLayout.VERTICAL);
        formatBlock.setPadding(0, dp(12), 0, dp(12));
        TextView formatTitle = rowTitle("时间格式");
        formatBlock.addView(formatTitle);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(10), 0, dp(2));
        for (final String preset : FORMAT_PRESETS) {
            TextView chip = chip(preset);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etFormat.setText(preset);
                    etFormat.setSelection(preset.length());
                }
            });
            formatChips.add(chip);
            chips.addView(chip);
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chips);
        formatBlock.addView(chipScroll);

        etFormat = fieldEdit("自定义格式，如 MM月dd日 HH:mm");
        LinearLayout.LayoutParams formatLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        formatLp.topMargin = dp(10);
        formatBlock.addView(etFormat, formatLp);
        etFormat.addTextChangedListener(previewWatcher);

        TextView formatHint = hint("yyyy 年 · MM 月 · dd 日 · HH 时 · mm 分 · ss 秒 · a 上午/下午 · EEE 星期");
        formatBlock.addView(formatHint);
        append(card, formatBlock);

        // 时区
        append(card, buildTimeZoneBlock());

        // 显示日期
        swDate = switchRow(card, "显示日期", "时间下方显示日期与星期", prefs.showDate());
        swDate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setShowDate(isChecked);
                updatePreview();
            }
        });

        // 字号
        seekSize = sliderRow(card, "字号",
                Prefs.MIN_TEXT_SIZE, Prefs.MAX_TEXT_SIZE, prefs.textSize(), "sp", "字号");
        seekSize.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSizeValue.setText((Prefs.MIN_TEXT_SIZE + progress) + "sp");
                prefs.setTextSize(Prefs.MIN_TEXT_SIZE + progress);
                updatePreview();
            }
        });

        // 主题
        append(card, buildThemeBlock());

        // 自定义颜色
        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setPadding(0, dp(12), 0, dp(12));
        LinearLayout fgBlock = colorField("字体颜色", "#FFFFFF");
        etTextColor = (EditText) fgBlock.getChildAt(1);
        LinearLayout bgBlock = colorField("背景颜色", "#000000");
        etBgColor = (EditText) bgBlock.getChildAt(1);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colors.addView(fgBlock, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.setMargins(dp(8), 0, 0, 0);
        colors.addView(bgBlock, half2);
        etTextColor.addTextChangedListener(previewWatcher);
        etBgColor.addTextChangedListener(previewWatcher);
        append(card, colors);

    }

    private void buildBrightnessSection() {
        section("亮度与夜间");
        LinearLayout card = card();

        int brightness = prefs.brightness();
        int brightnessValue = brightness < 0 ? 0 : brightness;
        seekBrightness = sliderRow(card, "屏幕亮度", 0, 100, brightnessValue, "%", "屏幕亮度");
        refreshBrightnessLabel(brightnessValue);
        seekBrightness.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                refreshBrightnessLabel(progress);
                prefs.setBrightness(progress == 0 ? -1 : progress);
            }
        });

        swNight = switchRow(card, "夜间自动减光", "到点把屏幕亮度降到最低", prefs.nightDim());
        swNight.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setNightDim(isChecked);
            }
        });

        // 减光时段
        LinearLayout nightRow = row();
        nightRow.addView(textBlock("减光时段", "起止时间点一下可以改"));
        pillNightStart = timePill(true);
        pillNightEnd = timePill(false);
        nightRow.addView(pillNightStart);
        TextView dash = new TextView(this);
        dash.setText("–");
        dash.setTextColor(c(R.color.text_hint));
        dash.setPadding(dp(6), 0, dp(6), 0);
        nightRow.addView(dash);
        nightRow.addView(pillNightEnd);
        append(card, nightRow);

        seekNightBrightness = sliderRow(card, "夜间亮度",
                Prefs.MIN_NIGHT_BRIGHTNESS, Prefs.MAX_NIGHT_BRIGHTNESS,
                prefs.nightBrightness(), "%", "夜间亮度");
        seekNightBrightness.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvNightBrightnessValue.setText(
                        (Prefs.MIN_NIGHT_BRIGHTNESS + progress) + "%");
                prefs.setNightBrightness(Prefs.MIN_NIGHT_BRIGHTNESS + progress);
            }
        });
    }

    private void buildMiscSection() {
        section("提醒");
        LinearLayout card = card();

        swChime = switchRow(card, "整点报时", null, prefs.chime());
        tvChimeHint = hint("整点播放提示音并弹出通知（需要通知权限）");
        tvChimeHint.setPadding(0, 0, 0, dp(12));
        card.addView(tvChimeHint);
        swChime.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setChime(isChecked);
                if (isChecked) {
                    requestNotificationPermission();
                }
                updateChimeHint();
            }
        });

        swAwake = switchRow(card, "保持屏幕常亮", "做桌钟时关闭自动息屏", prefs.keepAwake());
        swAwake.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setKeepAwake(isChecked);
            }
        });
        swBurnIn = switchRow(card, "防烧屏微移", "每分钟把画面挪动几个像素", prefs.burnIn());
        swBurnIn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setBurnIn(isChecked);
            }
        });
        swRest = switchRow(card, "定期整屏休息", "每 30 分钟全屏黑 3 秒，所有像素同时熄灭", prefs.screenRest());
        swRest.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setScreenRest(isChecked);
            }
        });
    }

    private void buildButtons() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.topMargin = dp(26);

        TextView reset = pillButton("恢复默认", c(R.color.field), c(R.color.text_primary));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmResetDefaults();
            }
        });
        content.addView(reset, lp);
    }

    private void confirmResetDefaults() {
        new AlertDialog.Builder(this)
                .setTitle("恢复默认设置")
                .setMessage("时间格式、主题、颜色、字号、时区、亮度和夜间设置都会重置为初始状态。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复默认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.restoreDefaults();
                        populate();
                        updatePreview();
                        toast("已恢复默认设置");
                    }
                })
                .show();
    }

    /** 时区下拉：跟随系统 + 常见时区，标签里带当前实际偏移（含夏令时）。 */
    private LinearLayout buildTimeZoneBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(12), 0, dp(12));
        block.addView(rowTitle("时区"));
        block.addView(hint("时间、日期和整点报时都按这里走；默认跟随系统"));

        FrameLayout holder = new FrameLayout(this);
        LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        holderLp.topMargin = dp(10);
        block.addView(holder, holderLp);

        final String[] labels = new String[ZONE_IDS.length];
        for (int i = 0; i < ZONE_IDS.length; i++) {
            labels[i] = zoneLabel(ZONE_IDS[i], ZONE_NAMES[i]);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return zoneItem(position, false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return zoneItem(position, true);
            }

            private View zoneItem(int position, boolean dropdown) {
                TextView view = new TextView(SettingsActivity.this);
                view.setText(getItem(position));
                view.setTextColor(c(R.color.text_primary));
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                view.setPadding(dp(14), dp(dropdown ? 13 : 12), dp(14), dp(dropdown ? 13 : 12));
                return view;
            }
        };

        Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        spinner.setBackground(rounded(c(R.color.field), 12));
        spinner.setPadding(dp(4), 0, dp(30), 0);
        if (Build.VERSION.SDK_INT >= 16) {
            spinner.setPopupBackgroundDrawable(rounded(0xFF1E222A, 12));
        }
        spinner.setSelection(indexOfZone(prefs.timeZone()));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.setTimeZone(ZONE_IDS[position]);
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        holder.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        arrow.setTextColor(c(R.color.text_secondary));
        arrow.setGravity(Gravity.CENTER);
        holder.addView(arrow, new FrameLayout.LayoutParams(dp(40), dp(50), Gravity.END));
        return block;
    }

    private String zoneLabel(String id, String name) {
        TimeZone zone = (id == null || id.isEmpty())
                ? TimeZone.getDefault() : TimeZone.getTimeZone(id);
        int minutes = zone.getOffset(System.currentTimeMillis()) / 60000;
        String offset = String.format(Locale.US, "UTC%+d:%02d",
                minutes / 60, Math.abs(minutes % 60));
        return name + " · " + offset;
    }

    private static int indexOfZone(String id) {
        String key = (id == null) ? "" : id;
        for (int i = 0; i < ZONE_IDS.length; i++) {
            if (ZONE_IDS[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }

    private TimeZone selectedZone() {
        String id = prefs.timeZone();
        return (id == null || id.isEmpty())
                ? TimeZone.getDefault() : TimeZone.getTimeZone(id);
    }

    /** 主题选择：每个主题一块小样，直接展示它的配色/字体/对齐。 */
    private LinearLayout buildThemeBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(12), 0, dp(12));
        block.addView(rowTitle("主题"));
        tvThemeNote = hint("当前：" + Theme.get(prefs.theme()).name);
        tvThemeNote.setTextColor(c(R.color.accent));
        block.addView(tvThemeNote);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        for (final Theme theme : Theme.ALL) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(
                    dp(104), ViewGroup.LayoutParams.WRAP_CONTENT);
            tileLp.setMargins(0, 0, dp(10), 0);
            tile.setLayoutParams(tileLp);

            TextView sample = new TextView(this);
            sample.setText("12:00");
            sample.setTextColor(theme.fg);
            sample.setGravity((theme.align & Gravity.HORIZONTAL_GRAVITY_MASK)
                    | Gravity.CENTER_VERTICAL);
            sample.setTypeface(theme.typeface());
            sample.setLetterSpacing(theme.letterSpacing);
            sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            sample.setPadding(dp(10), dp(8), dp(10), dp(8));
            sample.setBackground(rounded(theme.bg, 14));
            tile.addView(sample, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

            TextView name = new TextView(this);
            name.setText(theme.name);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            name.setTextColor(c(R.color.text_secondary));
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(6), 0, 0);
            tile.addView(name);

            tile.setClickable(true);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.setTheme(theme.key);
                    etTextColor.setText(hex(theme.fg));
                    etBgColor.setText(hex(theme.bg));
                    updatePreview();
                }
            });
            themeTiles.add(sample);
            themeNames.add(name);
            row.addView(tile);
        }

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        block.addView(scroll);
        return block;
    }

    // ------------------------------------------------------------ 控件工厂

    private void section(String title) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setLetterSpacing(0.08f);
        view.setTextColor(c(R.color.text_secondary));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(26);
        content.addView(view, lp);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(c(R.color.surface_card), 20));
        card.setPadding(dp(18), dp(2), dp(18), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        content.addView(card, lp);
        return card;
    }

    private void append(LinearLayout card, View child) {
        if (card.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(c(R.color.divider));
            card.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp(1))));
        }
        card.addView(child);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54));
        row.setPadding(0, dp(8), 0, dp(8));
        return row;
    }

    private LinearLayout textBlock(String title, String subtitle) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        block.addView(rowTitle(title));
        if (subtitle != null) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            sub.setTextColor(c(R.color.text_hint));
            sub.setPadding(0, dp(2), 0, 0);
            block.addView(sub);
        }
        return block;
    }

    private TextView rowTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setTextColor(c(R.color.text_primary));
        return view;
    }

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setTextColor(c(R.color.text_hint));
        view.setPadding(0, dp(4), 0, 0);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private Switch switchRow(LinearLayout card, String title, String subtitle, boolean checked) {
        LinearLayout row = row();
        row.addView(textBlock(title, subtitle));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        row.addView(toggle);
        append(card, row);
        return toggle;
    }

    private SeekBar sliderRow(LinearLayout card, String title, int min, int max,
                              int value, String unit, String labelMode) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(12), 0, dp(4));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = rowTitle(title);
        head.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = new TextView(this);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        valueView.setTypeface(Typeface.MONOSPACE);
        valueView.setTextColor(c(R.color.accent));
        head.addView(valueView);
        block.addView(head);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(clamp(value - min, 0, max - min));
        block.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        append(card, block);

        if ("字号".equals(labelMode)) {
            tvSizeValue = valueView;
            tvSizeValue.setText(value + unit);
        } else if ("夜间亮度".equals(labelMode)) {
            tvNightBrightnessValue = valueView;
            tvNightBrightnessValue.setText(value + unit);
        } else {
            tvBrightnessValue = valueView;
        }
        return bar;
    }

    private void refreshBrightnessLabel(int progress) {
        if (tvBrightnessValue == null) {
            return;
        }
        tvBrightnessValue.setText(progress == 0 ? "跟随系统" : progress + "%");
    }

    private TextView chip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setTypeface(Typeface.MONOSPACE);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(9), dp(14), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);
        chip.setClickable(true);
        return chip;
    }

    private LinearLayout colorField(String title, String hint) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTextColor(c(R.color.text_secondary));
        block.addView(label);
        EditText edit = fieldEdit(hint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        block.addView(edit, lp);
        return block;
    }

    private EditText fieldEdit(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        edit.setTextColor(c(R.color.text_primary));
        edit.setHintTextColor(c(R.color.text_hint));
        edit.setBackground(rounded(c(R.color.field), 12));
        edit.setPadding(dp(14), dp(11), dp(14), dp(11));
        edit.setSingleLine(true);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return edit;
    }

    private TextView pillButton(String text, int bg, int fg) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(fg);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(bg, 14));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView timePill(final boolean isStart) {
        final TextView pill = new TextView(this);
        int minutes = isStart ? pendingNightStart : pendingNightEnd;
        pill.setText(formatMinutes(minutes));
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pill.setTypeface(Typeface.MONOSPACE);
        pill.setTextColor(c(R.color.text_primary));
        pill.setBackground(rounded(c(R.color.field), 10));
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setClickable(true);
        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int minutes = isStart ? pendingNightStart : pendingNightEnd;
                new TimePickerDialog(SettingsActivity.this,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                if (isStart) {
                                    pendingNightStart = hourOfDay * 60 + minute;
                                    prefs.setNightStart(pendingNightStart);
                                    pill.setText(formatMinutes(pendingNightStart));
                                } else {
                                    pendingNightEnd = hourOfDay * 60 + minute;
                                    prefs.setNightEnd(pendingNightEnd);
                                    pill.setText(formatMinutes(pendingNightEnd));
                                }
                            }
                        },
                        minutes / 60, minutes % 60,
                        DateFormat.is24HourFormat(SettingsActivity.this)).show();
            }
        });
        return pill;
    }

    // ------------------------------------------------------------ 数据填充

    private void populate() {
        etFormat.setText(prefs.format());
        etTextColor.setText(hex(prefs.textColor()));
        etBgColor.setText(hex(prefs.bgColor()));
        swDate.setChecked(prefs.showDate());
        seekSize.setProgress(prefs.textSize() - Prefs.MIN_TEXT_SIZE);
        tvSizeValue.setText(prefs.textSize() + "sp");

        int brightness = prefs.brightness();
        int brightnessValue = brightness < 0 ? 0 : brightness;
        seekBrightness.setProgress(brightnessValue);
        refreshBrightnessLabel(brightnessValue);

        swNight.setChecked(prefs.nightDim());
        pendingNightStart = prefs.nightStart();
        pendingNightEnd = prefs.nightEnd();
        pillNightStart.setText(formatMinutes(pendingNightStart));
        pillNightEnd.setText(formatMinutes(pendingNightEnd));
        seekNightBrightness.setProgress(
                prefs.nightBrightness() - Prefs.MIN_NIGHT_BRIGHTNESS);
        tvNightBrightnessValue.setText(prefs.nightBrightness() + "%");

        swChime.setChecked(prefs.chime());
        swAwake.setChecked(prefs.keepAwake());
        swBurnIn.setChecked(prefs.burnIn());
        swRest.setChecked(prefs.screenRest());
    }

    // ------------------------------------------------------------ 预览与保存

    private void updatePreview() {
        Integer bg = parseColor(etBgColor.getText().toString());
        if (bg == null) {
            bg = prefs.bgColor();
        }
        Integer fg = parseColor(etTextColor.getText().toString());
        if (fg == null) {
            fg = prefs.textColor();
        }
        Theme theme = Theme.get(prefs.theme());

        boolean dateFirst = previewBox.getChildCount() > 0
                && previewBox.getChildAt(0) == previewDate;
        if (theme.dateAbove != dateFirst) {
            previewBox.removeAllViews();
            if (theme.dateAbove) {
                previewBox.addView(previewDate);
                previewBox.addView(previewClock);
            } else {
                previewBox.addView(previewClock);
                previewBox.addView(previewDate);
            }
        }
        previewBox.setGravity(theme.gravity);

        LinearLayout.LayoutParams dateLp =
                (LinearLayout.LayoutParams) previewDate.getLayoutParams();
        dateLp.topMargin = theme.dateAbove ? 0 : dp(6);
        dateLp.bottomMargin = theme.dateAbove ? dp(6) : 0;
        previewDate.setLayoutParams(dateLp);

        previewClock.setGravity(theme.align);
        previewDate.setGravity(theme.align);
        previewClock.setTypeface(theme.typeface());
        previewDate.setTypeface(theme.dateTypeface());
        previewClock.setLetterSpacing(theme.letterSpacing);

        previewCard.setBackground(rounded(bg, 24));
        previewDate.setTextColor(fg);
        previewDate.setAlpha(0.62f);

        float scale = Math.max(10f, Math.min(94f, textSizeValue() * 0.31f));
        previewClock.setTextColor(fg);
        previewClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, scale);
        previewDate.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                Math.max(9f, Math.min(28f, scale * 0.32f)));
        previewDate.setVisibility(swDate.isChecked() ? View.VISIBLE : View.GONE);

        String pattern = etFormat.getText().toString().trim();
        if (pattern.isEmpty()) {
            pattern = Prefs.DEF_FORMAT;
        }
        Date now = new Date();
        TimeZone zone = selectedZone();
        try {
            SimpleDateFormat previewFormat = new SimpleDateFormat(pattern, Locale.getDefault());
            previewFormat.setTimeZone(zone);
            previewClock.setText(previewFormat.format(now));
        } catch (RuntimeException e) {
            previewClock.setText("--:--:--");
        }
        try {
            dateFormatter.setTimeZone(zone);
            previewDate.setText(dateFormatter.format(now));
        } catch (RuntimeException ignored) {
            previewDate.setText("");
        }
        styleChips();
        updateThemeNote(theme);
    }

    private void updateThemeNote(Theme theme) {
        if (tvThemeNote == null) {
            return;
        }
        Integer fg = parseColor(etTextColor.getText().toString());
        Integer bg = parseColor(etBgColor.getText().toString());
        boolean custom = (fg != null && fg != theme.fg) || (bg != null && bg != theme.bg);
        tvThemeNote.setText("当前：" + theme.name + (custom ? " · 配色已自定义" : ""));
    }

    private void styleChips() {
        String current = etFormat.getText().toString().trim();
        for (TextView chip : formatChips) {
            styleChip(chip, chip.getText().toString().equals(current));
        }

        for (int i = 0; i < themeTiles.size(); i++) {
            Theme theme = Theme.ALL[i];
            TextView sample = themeTiles.get(i);
            boolean selected = theme.key.equals(prefs.theme());
            GradientDrawable drawable = rounded(theme.bg, 14);
            drawable.setStroke((int) (selected ? dp(3) : dp(1)),
                    selected ? c(R.color.accent) : 0x33FFFFFF);
            sample.setBackground(drawable);

            TextView name = themeNames.get(i);
            name.setText(selected ? "✓ " + theme.name : theme.name);
            name.setTextColor(selected ? c(R.color.accent) : c(R.color.text_secondary));
            name.setTypeface(Typeface.create(
                    selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
    }

    private void styleChip(TextView chip, boolean selected) {
        GradientDrawable drawable = rounded(selected ? 0x335EE0C0 : c(R.color.field), 20);
        if (selected) {
            drawable.setStroke((int) dp(1), c(R.color.accent));
        }
        chip.setBackground(drawable);
        chip.setTextColor(selected ? c(R.color.accent) : 0xFFC9CDD4);
    }

    /** 格式和颜色随输入即时落盘；非法值不覆盖已有配置。 */
    private void persistTextFields() {
        String pattern = etFormat.getText().toString().trim();
        if (!pattern.isEmpty() && isValidPattern(pattern)) {
            prefs.setFormat(pattern);
        }
        Integer fg = parseColor(etTextColor.getText().toString());
        if (fg != null) {
            prefs.setTextColor(fg);
        }
        Integer bg = parseColor(etBgColor.getText().toString());
        if (bg != null) {
            prefs.setBgColor(bg);
        }
    }

    // ------------------------------------------------------------ 通知权限

    private void updateChimeHint() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            tvChimeHint.setText("通知权限未开启，整点不会响 · 打开开关时会请求授权");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = nm == null ? null : nm.getNotificationChannel(CHANNEL_ID);
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                tvChimeHint.setText("「整点报时」通知已被系统关闭，请到系统设置里打开");
                return;
            }
        }
        tvChimeHint.setText("整点播放提示音并弹出通知（需要通知权限）");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateChimeHint();
    }

    // ------------------------------------------------------------ 小工具

    private int textSizeValue() {
        return Prefs.MIN_TEXT_SIZE + seekSize.getProgress();
    }

    private static String formatMinutes(int minutes) {
        return String.format(Locale.US, "%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    private static SimpleDateFormat buildDateFormatter() {
        String pattern = "M月d日 EEEE";
        try {
            String best = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdEEE");
            if (best != null && !best.isEmpty()) {
                pattern = best;
            }
        } catch (Throwable ignored) {
        }
        try {
            return new SimpleDateFormat(pattern, Locale.getDefault());
        } catch (IllegalArgumentException e) {
            return new SimpleDateFormat("M月d日 EEEE", Locale.getDefault());
        }
    }

    private boolean isValidPattern(String pattern) {
        try {
            new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int c(int colorRes) {
        return getResources().getColor(colorRes);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setColor(color);
        return drawable;
    }

    private static Integer parseColor(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (!s.startsWith("#")) {
            s = "#" + s;
        }
        if (!s.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
            return null;
        }
        try {
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String hex(int color) {
        if (Color.alpha(color) == 255) {
            return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
        }
        return String.format(Locale.US, "#%08X", color);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** SeekBar 的四个回调只需要一个。 */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}

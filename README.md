# 静态时钟 StaticClock

把手机变成床头桌钟。重写自 `StaticTimer`（com.dabai.time，2018 年），
仓库：<https://github.com/mdhyy008/StaticTimer>
不是照着原逻辑翻译，而是按「桌钟」这个用途重新设计。

- 包名 `cn.mdhyy.staticclock`，versionName 2.0（versionCode 200）
- minSdk 21（Android 5.0）/ targetSdk 34
- 纯 Java + framework，无 AndroidX、无第三方依赖，APK 约 115 KB

## 设计取舍

原版只做三件事：全屏显示一行时间、长按填 4 个输入框、整点发通知。
但真把手机立在床头用时，缺的是这几样：夜里刺眼、亮度只能靠系统、
方向被锁死横屏、日期看不到。所以新版把重点放在「能整夜开着」：

**夜间自动减光** —— 到点把窗口亮度压到 12%（可调 3–60%），
起止时间可点开时间选择器修改，跨零点也支持。这是桌钟最该有的功能。

**屏幕亮度** —— 0 为跟随系统，1–100 手动固定，只作用于本应用窗口。

**屏幕方向** —— 自动 / 横屏 / 竖屏三选。原版硬编码 `sensorLandscape`，
但很多人是竖着插在支架上的。

**主题** —— 九套预置，每套连字体、字距、位置、日期顺序、装饰一起换：

| 主题 | 配色 | 字体 | 位置 | 特点 |
|---|---|---|---|---|
| 墨 | 黑 / 白 | sans-serif-thin | 居中 | 极细字，左侧竖向日线 |
| 暖夜 | 暖黑 / 琥珀 | monospace | 居中 | 字距宽，四角角框，下次报时倒计时 |
| 纸 | 米白 / 墨黑 | serif-monospace | 居中 | 打字机般的老式印刷感，页眉中文全日期、页脚页码 |
| 终端 | 纯黑 / 亮绿 | monospace 粗体 | 居中 | 窗口标题栏、命令行、闪烁光标、状态行 |
| 黑体 | 深灰 / 白 | sans-serif-condensed 粗体 | 居中 | 紧排粗黑，ISO 日期与星期、细横线 |
| 苔 | 墨绿 / 薄荷 | sans-serif-smallcaps | 居中 | 几何小型大写，字距最宽，8 秒呼吸点 |
| 霓虹 | 深紫黑 / 荧光粉 | sans-serif-black | 居中 | 特粗字 + 描边 + 6 秒呼吸辉光，青色标签 |
| 墨水屏 | 冷灰 / 炭灰 | serif | 居中 | 低对比，屏框边框，`E–INK` 与刷新时间 |
| 日历页 | 暖白 / 近黑 | sans-serif-medium | 居中 | 左上红色日历牌，右上「2026年 · 星期三」，左下当年进度 |

每套主题的字体都不相同，全部取自系统字体族，不打包字体文件：

| 主题 | 字体族 | 实际字体 |
|---|---|---|
| 墨 | sans-serif-thin | Roboto Thin |
| 暖夜 | monospace | Roboto Mono |
| 纸 | serif-monospace | Cutive Mono（打字机） |
| 终端 | monospace（粗） | Roboto Mono Bold |
| 黑体 | sans-serif-condensed（粗） | Roboto Condensed Bold |
| 苔 | sans-serif-smallcaps | Carrois Gothic SC（几何小型大写） |
| 霓虹 | sans-serif-black | Roboto Black |
| 墨水屏 | serif | Noto Serif |
| 日历页 | sans-serif-medium | Roboto Medium |

数字宽度是直接读字体表 `hmtx` 里 `0`–`9` 十个字形的 advance width 验的：
Cutive Mono 1240/2048、Carrois Gothic SC 540/1000、Noto Serif 1145/2048、
Roboto 1151/2048，十个数字完全一致，同一字族的各个字重共用这套数字。

即便如此仍加了一道运行时兜底 `digitsAreTabular()`：用当前主题字体逐个量十个数字，
宽度差超过 0.5px 就把这套主题退回等宽字体，保证秒数跳变时整行不会横移。

主题只决定版式和配色，**不缩放字号** —— 所有主题按设置里的字号原样显示，
换主题不会让字变大变小。颜色仍可单独改。

终端主题额外带一套终端装饰（其它主题不显示）：

```
● ● ●  staticclock — Asia/Shanghai
        mdhyy@staticclock:~$ date +"%H:%M:%S"
             10月28日 星期二
                 21:07:42
        mdhyy@staticclock:~$ █
bat=82%+ · night=22:00-07:00 · chime=on · up=12m
```

- 标题栏：三个彩色圆点 + 当前真正生效的时区 ID
- 命令行：不是写死的，由显示格式翻译成 `date` 的格式串；12 小时制会自动变成
  `date +"%I:%M:%S %p"`，年月日格式则退回 `date`
- 光标：530ms 闪一次，靠改颜色透明实现，等宽字体下不推动整行
- 状态行：电量（`+` 表示在充电）、夜间减光时段、整点报时开关、本次运行时长
- 时间数字启用斜杠零（`fontFeatureSettings="tnum,zero"`）

另外三套也各有装饰（同样只在各自主题里出现）：

**霓虹** —— 时间用描边字（`Paint.Style.STROKE`，描边宽度为字号的 2.8%），
外加 `setShadowLayer` 的粉色辉光；辉光半径每 6 秒做一次 ±15% 的正弦呼吸。
左上角是电量（充电时写「82% · 充电中」）；左下角只在夜间减光真正生效时出现
「屏幕已调暗 · 6h12m 后恢复」，白天或没开该功能时什么都不显示。

**墨水屏** —— 冷灰底、低对比炭灰字，四周一圈 1dp 屏框（`bg_frame.xml`）模仿阅读器边框；
页脚只留电量和 UTC 偏移（`82% · UTC+8:00`）。

**日历页** —— 左上角一块红色圆角日历牌（月份小字 + 大号日号，`RelativeSizeSpan` 放大 2.1 倍），
右上「2026年10月 · 星期三」，左下「第 301 天 / 365 天 · 82%」。
这个主题隐藏默认那行日期，避免和日历牌重复。

**墨 / 暖夜 / 纸 / 黑体 / 苔** 也各有装饰：

- **墨**：左侧一条竖向日线（`DayLineView`），顶端 0 点、底端 24 点，每小时一根短刻度、
  每 6 小时一根长刻度，当前时刻一道横杠；刻度不写数字，左上角只用一行 `DAY 28%` 说明含义
- **暖夜**：四角细角标（`CornerFrameView`），右下「下次报时 · 47 分钟后」，
  只在整点报时开启时出现，倒计时按真实整点算
- **纸**：页眉「二〇二六年 十月二十八日 · 星期三」、页脚「第 301 天」；
  完整日期已在页眉，原来那行日期不再重复显示
- **黑体**：左上 `2026.10.28 · 星期二` 加一条贯穿的 1dp 横线，日期行同样不再重复
- **苔**：时间下方一个 8 秒一呼一吸的圆点（4 秒暗、4 秒亮），唯一的纯视觉元素

这些装饰的共同原则：**显示的每一项都必须是当前真实状态**——电量、时区、夜间时段、
报时开关、运行时长、当年进度、一天进度，没有一个是写死的装饰标签。电量通过
`ACTION_BATTERY_CHANGED` 的粘性广播读取，不需要任何权限，每分钟刷新一次。

防烧屏的随机位移同时作用于这些角落装饰，静态文字不会长期压在同一个像素上。

其余：字号 16–300sp（严格按设定值显示，不做自动缩小）、格式预设 + 任意自定义模式、显示日期（自动取
`getBestDateTimePattern` 的本地化骨架，中文是「10月28日 星期三」）、
整点报时、屏幕常亮、防烧屏随机位移（字号的 10%，8–36dp，每分钟一次）、定期整屏休息（每 30 分钟黑屏 3 秒）、点击显示「长按设置」提示。

## 与旧版的实现差异

| | 旧版 | 新版 |
|---|---|---|
| 配置存储 | `MODE_WORLD_READABLE` + `/sdcard/.time.txt` | 私有 SharedPreferences |
| 权限 | `WRITE_EXTERNAL_STORAGE` | 仅 `POST_NOTIFICATIONS` |
| 设置界面 | AlertDialog + 4 个裸 EditText | 卡片分组的独立页面，实时预览 |
| 应用设置 | 存完 `killProcess` 重启 | 返回后 `onResume` 重新应用 |
| 通知 | 无 channel，8.0+ 被系统丢弃 | NotificationChannel + 13+ 权限 |
| 全屏 | `SYSTEM_UI_FLAG_*` | 30+ `WindowInsetsController`，低版本回退 |
| 时间格式化 | `new SimpleDateFormat(form)` 空值即崩 | 非法格式回退默认，字号严格按设定 |

旧版在 Android 7.0+ 上其实起不来：`MODE_WORLD_READABLE` 从 N 开始直接抛
`SecurityException`。

沿用旧版 `res/raw/elegant.ogg` 报时音，项目仍为 GPL-3.0（见 LICENSE）。

## 目录

```
StaticClock/
├── AndroidManifest.xml
├── src/cn/mdhyy/staticclock/
│   ├── MainActivity.java      显示、各主题装饰、亮度、整点报时、沉浸式
│   ├── SettingsActivity.java  设置页（代码构建卡片与控件）
│   ├── Prefs.java             配置项读写与默认值
│   ├── Theme.java             九套预置主题
│   ├── DayLineView.java       墨主题左侧的竖向日线
│   └── CornerFrameView.java   暖夜主题的四角角框
├── res/
│   ├── layout/                主界面、设置页骨架
│   ├── drawable/              卡片、输入框、提示胶囊、屏框、通知图标、图标前景
│   ├── mipmap-anydpi-v26|v33/ 自适应图标（v33 含 monochrome）
│   ├── mipmap-*dpi/           旧系统 PNG 图标
│   ├── raw/elegant.ogg
│   └── values/                strings、colors、themes
├── build.sh
├── zipalign.py / check_align.py
├── README.md
└── LICENSE
```

`debug.keystore` 首次构建时由 `build.sh` 用 `keytool` 自动生成，不进版本库。

## 构建

本机没有 Gradle Android 插件，用 aapt2 + javac + d8 手工构建：

```sh
git clone https://github.com/mdhyy008/StaticTimer.git
cd StaticTimer
./build.sh
```

依赖 `aapt2 d8 javac apksigner keytool python3` 与 API 34 的 `android.jar`。
`android.jar` 的查找顺序：

1. 环境变量 `ANDROID_JAR`
2. `$ANDROID_HOME/platforms/android-34/android.jar`
3. `~/Android/Sdk/platforms/android-34/android.jar`
4. `/opt/android-sdk/platforms/android-34/android.jar`

`aapt2 link` 的 `-I` 参数：设备上存在 `/system/framework/framework-res.apk` 就用它，
电脑上直接用上面的 `android.jar`（也可用 `FRAMEWORK_RES` 指定）。

流程：`aapt2 compile` → `aapt2 link --java` → `javac --release 11` →
`d8 --min-api 21` → `zipalign.py` → `apksigner`。产物 `StaticClock.apk`（debug 签名），
签名用的 `debug.keystore` 缺失时会自动生成。

## 已知边界

- 整点报时只在主界面处于前台时触发（旧版同样如此）。要做后台报时得加
  前台服务或精确闹钟，会常驻通知，暂不做。
- 屏幕亮度只覆盖本应用窗口，退出应用后恢复系统亮度。

#!/bin/bash
# 手工构建静态时钟 APK：aapt2 -> javac -> d8 -> zipalign(py) -> apksigner
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
OUT_APK="$ROOT/StaticClock.apk"
KEYSTORE="$ROOT/debug.keystore"

# android.jar：优先用 ANDROID_JAR，其次常见的 SDK 位置
if [ -z "${ANDROID_JAR:-}" ]; then
    for sdk in "${ANDROID_HOME:-}" "$HOME/Android/Sdk" "/opt/android-sdk"; do
        if [ -n "$sdk" ] && [ -f "$sdk/platforms/android-34/android.jar" ]; then
            ANDROID_JAR="$sdk/platforms/android-34/android.jar"
            break
        fi
    done
fi
ANDROID_JAR="${ANDROID_JAR:-/data/user/0/cc.codroid/files/home/workspace/DevTools/android-sdk/platforms/android-34.jar}"

# aapt2 的 -I 参数：设备上用 framework-res.apk，电脑上直接用 android.jar
if [ -z "${FRAMEWORK_RES:-}" ]; then
    if [ -f /system/framework/framework-res.apk ]; then
        FRAMEWORK_RES="/system/framework/framework-res.apk"
    else
        FRAMEWORK_RES="$ANDROID_JAR"
    fi
fi

MIN_SDK=21
TARGET_SDK=34
VERSION_NAME="2.0"
VERSION_CODE=200

AAPT2="${AAPT2:-aapt2}"
D8="${D8:-d8}"
JAVAC="${JAVAC:-javac}"
KEYTOOL="${KEYTOOL:-keytool}"
APKSIGNER="${APKSIGNER:-apksigner}"
PYTHON="${PYTHON:-python3}"

for tool in "$AAPT2" "$D8" "$JAVAC" "$KEYTOOL" "$APKSIGNER" "$PYTHON"; do
    command -v "$tool" >/dev/null || { echo "缺少工具：$tool" >&2; exit 1; }
done
[ -f "$FRAMEWORK_RES" ] || { echo "找不到 $FRAMEWORK_RES" >&2; exit 1; }
[ -f "$ANDROID_JAR" ] || { echo "找不到 android.jar：$ANDROID_JAR" >&2; exit 1; }

echo "==> 0/7 清理 build/"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/aligned"

echo "==> 1/7 编译资源"
"$AAPT2" compile --dir "$ROOT/res" -o "$BUILD/res.zip"

echo "==> 2/7 链接资源与清单（生成 R.java）"
"$AAPT2" link -o "$BUILD/base.apk" \
    -I "$FRAMEWORK_RES" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    --java "$BUILD/gen" \
    "$BUILD/res.zip"

echo "==> 3/7 编译 Java"
find "$ROOT/src" "$BUILD/gen" -name '*.java' > "$BUILD/src.list"
"$JAVAC" -encoding UTF-8 --release 11 -Xlint:-options -nowarn \
    -classpath "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/src.list"

echo "==> 4/7 d8 生成 classes.dex"
find "$BUILD/classes" -name '*.class' -print0 \
    | xargs -0 "$D8" --lib "$ANDROID_JAR" --min-api "$MIN_SDK" --output "$BUILD/dex"

echo "==> 5/7 打包 dex"
( cd "$BUILD/dex" && zip -q "$BUILD/base.apk" classes*.dex )

echo "==> 6/7 对齐（resources.arsc 不压缩且 4 字节对齐）"
"$PYTHON" "$ROOT/zipalign.py" "$BUILD/base.apk" "$BUILD/aligned/app.apk" 4
"$PYTHON" "$ROOT/check_align.py" "$BUILD/aligned/app.apk"

echo "==> 7/7 签名"
if [ ! -f "$KEYSTORE" ]; then
    "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
        -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android --min-sdk-version "$MIN_SDK" \
    --out "$OUT_APK" "$BUILD/aligned/app.apk"
"$APKSIGNER" verify --verbose "$OUT_APK"
"$PYTHON" "$ROOT/check_align.py" "$OUT_APK"

echo
echo "构建完成：$OUT_APK"
"$AAPT2" dump badging "$OUT_APK" | grep -E "^(package|sdkVersion|targetSdkVersion|launchable-activity|application-label)" || true

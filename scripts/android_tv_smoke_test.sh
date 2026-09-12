#!/usr/bin/env bash
set -euo pipefail

ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  ADB="$(command -v adb || true)"
fi
if [[ -z "${ADB:-}" ]]; then
  echo "adb not found" >&2
  exit 2
fi

GRADLE_CMD="${GRADLE_CMD:-gradle}"
$GRADLE_CMD --version

mkdir -p build/tv-smoke/screenshots

# app/build.gradle.kts adds .debug to the debug application id.
DEBUG_PACKAGE="com.brotv.iptv.debug"
SCREENSHOT_DEVICE_DIR="/sdcard/Android/data/${DEBUG_PACKAGE}/files/qa-screenshots"

# Run the full Android-TV instrumentation suite. Screenshots are written by
# FullTvQaTest to the app-specific external files directory while the tested
# Home, Movies and Series screens are actually visible on the emulator.
set +e
$GRADLE_CMD connectedDebugAndroidTest
TEST_EXIT=$?
set -e

$ADB pull "$SCREENSHOT_DEVICE_DIR/." build/tv-smoke/screenshots/ || true
ls -lah build/tv-smoke/screenshots || true

# Preserve screenshot/test evidence even when an assertion fails. The workflow
# uploads build/tv-smoke with `if: always()` after this script returns.
if [[ $TEST_EXIT -ne 0 ]]; then
  echo "Instrumentation tests failed; emulator screenshots were pulled when available." >&2
  exit "$TEST_EXIT"
fi

APK="app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "APK not produced: $APK" >&2; exit 3; }

$ADB logcat -c || true
$ADB install -r "$APK"
$ADB shell am force-stop "$DEBUG_PACKAGE"
$ADB shell monkey -p "$DEBUG_PACKAGE" -c android.intent.category.LEANBACK_LAUNCHER 1
sleep 8

$ADB shell dumpsys window windows > build/tv-smoke/window.txt || true
$ADB shell dumpsys activity activities > build/tv-smoke/activity.txt || true
$ADB logcat -d > build/tv-smoke/logcat.txt || true
$ADB exec-out screencap -p > build/tv-smoke/final-smoke.png || true

if grep -E "FATAL EXCEPTION|ANR in com\.brotv\.iptv" build/tv-smoke/logcat.txt; then
  echo "Runtime crash/ANR detected" >&2
  exit 4
fi

if ! $ADB shell pidof "$DEBUG_PACKAGE" >/dev/null 2>&1; then
  echo "BRO PLUS TV debug process is not running after launch" >&2
  exit 5
fi

echo "Android TV QA passed: instrumentation tests completed, screenshots captured, and app stayed running without detected FATAL EXCEPTION/ANR."

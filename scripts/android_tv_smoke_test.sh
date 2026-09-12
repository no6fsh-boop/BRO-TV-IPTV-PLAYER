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

# The workflow already builds app-debug.apk. Run instrumentation tests here
# without a clean rebuild so the slow software-emulated TV gets maximum test time.
$GRADLE_CMD connectedDebugAndroidTest

APK="app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "APK not produced: $APK" >&2; exit 3; }

mkdir -p build/tv-smoke
$ADB logcat -c || true
$ADB install -r "$APK"
$ADB shell am force-stop com.brotv.iptv
$ADB shell monkey -p com.brotv.iptv -c android.intent.category.LEANBACK_LAUNCHER 1
sleep 8

$ADB shell dumpsys window windows > build/tv-smoke/window.txt || true
$ADB shell dumpsys activity activities > build/tv-smoke/activity.txt || true
$ADB logcat -d > build/tv-smoke/logcat.txt || true
$ADB exec-out screencap -p > build/tv-smoke/home.png || true

if grep -E "FATAL EXCEPTION|ANR in com\.brotv\.iptv" build/tv-smoke/logcat.txt; then
  echo "Runtime crash/ANR detected" >&2
  exit 4
fi

if ! $ADB shell pidof com.brotv.iptv >/dev/null 2>&1; then
  echo "BRO PLUS TV process is not running after launch" >&2
  exit 5
fi

echo "Android TV smoke test passed: instrumentation tests completed and app stayed running without detected FATAL EXCEPTION/ANR."

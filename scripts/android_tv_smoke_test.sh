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

APP_PACKAGE="com.brotv.iptv"
TEST_PACKAGE="com.brotv.iptv.test"
RUNNER="${TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SCREENSHOT_DEVICE_DIR="/sdcard/Android/data/${APP_PACKAGE}/files/qa-screenshots"
EVIDENCE_DIR="build/tv-smoke"
SCREENSHOT_DIR="${EVIDENCE_DIR}/screenshots"

mkdir -p "$SCREENSHOT_DIR"
[[ -f "$APP_APK" ]] || { echo "APK not produced: $APP_APK" >&2; exit 3; }
[[ -f "$TEST_APK" ]] || { echo "Test APK not produced: $TEST_APK" >&2; exit 3; }

$ADB uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
$ADB uninstall "$APP_PACKAGE" >/dev/null 2>&1 || true
$ADB install -r "$APP_APK"
$ADB install -r "$TEST_APK"
$ADB logcat -c || true

run_test_class() {
  local class_name="$1"
  local output_file="$2"
  set +e
  "$ADB" shell am instrument -w -r -e class "$class_name" "$RUNNER" | tee "$output_file"
  local adb_exit=${PIPESTATUS[0]}
  set -e

  if [[ $adb_exit -ne 0 ]] || grep -Eq "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=" "$output_file"; then
    return 1
  fi
  return 0
}

# Run the two login/startup smoke tests against a genuinely fresh install first.
SMOKE_EXIT=0
run_test_class "com.brotv.iptv.MainActivitySmokeTest" "${EVIDENCE_DIR}/smoke-tests.txt" || SMOKE_EXIT=$?

# Run the four full Android-TV scenarios separately. Direct instrumentation keeps
# the target app installed after JUnit finishes, which lets us pull the real
# 1920x1080 screenshots before any Gradle cleanup removes app data.
FULL_EXIT=0
run_test_class "com.brotv.iptv.FullTvQaTest" "${EVIDENCE_DIR}/full-tv-tests.txt" || FULL_EXIT=$?

$ADB pull "$SCREENSHOT_DEVICE_DIR/." "$SCREENSHOT_DIR/" || true
ls -lah "$SCREENSHOT_DIR" || true
$ADB logcat -d > "${EVIDENCE_DIR}/instrumentation-logcat.txt" || true
$ADB shell dumpsys window windows > "${EVIDENCE_DIR}/window-after-tests.txt" || true
$ADB shell dumpsys activity activities > "${EVIDENCE_DIR}/activity-after-tests.txt" || true

if [[ $SMOKE_EXIT -ne 0 || $FULL_EXIT -ne 0 ]]; then
  echo "Android TV instrumentation tests failed; evidence and screenshots were preserved." >&2
  exit 1
fi

# Final cold-ish launcher smoke after the suite. FullTvQaTest clears its temporary
# credentials in tearDown, so the app should still launch safely to its login flow.
$ADB shell am force-stop "$APP_PACKAGE" || true
$ADB shell monkey -p "$APP_PACKAGE" -c android.intent.category.LEANBACK_LAUNCHER 1
sleep 8
$ADB logcat -d > "${EVIDENCE_DIR}/final-logcat.txt" || true
$ADB exec-out screencap -p > "${EVIDENCE_DIR}/final-smoke.png" || true

if grep -E "FATAL EXCEPTION|ANR in com\.brotv\.iptv" "${EVIDENCE_DIR}/final-logcat.txt"; then
  echo "Runtime crash/ANR detected" >&2
  exit 4
fi

if ! $ADB shell pidof "$APP_PACKAGE" >/dev/null 2>&1; then
  echo "BRO PLUS TV process is not running after launcher smoke" >&2
  exit 5
fi

for required in home.png movies.png series.png; do
  if [[ ! -s "${SCREENSHOT_DIR}/${required}" ]]; then
    echo "Required emulator screenshot missing or empty: ${required}" >&2
    exit 6
  fi
done

echo "Android TV QA passed: 6 tests completed, screenshots preserved, and app stayed running without detected FATAL EXCEPTION/ANR."

# BRO PLUS TV — Android TV build & emulator test

This package now contains a complete Gradle project and an Android TV emulator smoke-test workflow.

## Local requirements
- JDK 17
- Android SDK Platform 35 + Build Tools 35.0.0
- Android Emulator + `system-images;android-35;android-tv;x86_64`
- Gradle 8.9
- Linux KVM / hardware virtualization for practical emulator speed

## Build
```bash
gradle clean assembleDebug
```
APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## Instrumented tests
Start an Android TV AVD, then:
```bash
gradle connectedDebugAndroidTest
```

## Full smoke test
With the TV emulator already booted:
```bash
GRADLE_CMD=gradle bash scripts/android_tv_smoke_test.sh
```
The script builds, installs, launches through the LEANBACK launcher category, saves a screenshot/logcat/window dump, and fails if it detects a fatal exception or ANR for the app.

## GitHub Actions
`.github/workflows/android-tv-emulator.yml` builds and runs the same checks on an Android TV API 35 x86_64 emulator, then uploads the APK plus test evidence.

## Additional source fix included
`BroTvNavGraph` no longer resolves `app.player` at the root of the navigation graph. ExoPlayer is now requested only inside destinations that actually need playback, preserving the intended lazy player initialization on the login/home startup path.

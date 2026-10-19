#!/usr/bin/env bash
set -euo pipefail
apk=$(find release-artifacts -name hoodie-from-bundle.apk)
test -f "$apk"
bash .github/scripts/release-runtime-smoke.sh "$apk"
# CI-only emulator: remove the signed public ID before installing the debug test host.
# These database tests complement, but do not impersonate, tests of the R8 release APK.
adb uninstall com.hoodie.app
output=app/build/reports/native-runtime
mkdir -p "$output"
trap 'adb logcat -d -v threadtime > "$output/logcat.txt"; adb exec-out screencap -p > "$output/screen.png"' EXIT
test "$(adb shell getconf PAGE_SIZE | tr -d '\r')" = 16384
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.hoodie.app.database,com.hoodie.app.backup

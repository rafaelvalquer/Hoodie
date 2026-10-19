#!/usr/bin/env bash

# The emulator action executes each script input line in a separate shell.
# Keep status and diagnostics in one process before that action stops the AVD.
set -uo pipefail
evidence=app/build/instrumentation-evidence
mkdir -p "$evidence"
capture() {
  local stage=$1
  local output="$evidence/$stage"
  mkdir -p "$output"
  adb logcat -d -v threadtime > "$output/logcat.txt" 2>&1 || true
  adb shell dumpsys power > "$output/power.txt" 2>&1 || true
  adb shell dumpsys window > "$output/window.txt" 2>&1 || true
  adb shell getconf PAGE_SIZE > "$output/page-size.txt" 2>&1 || true
  adb exec-out screencap -p > "$output/screen.png" || true
  adb pull /sdcard/Android/data/com.hoodie.app/files/ "$output/device-files" >/dev/null 2>&1 || true
  cp -R app/build/reports/androidTests "$output/reports" 2>/dev/null || true
  cp -R app/build/outputs/androidTest-results "$output/results" 2>/dev/null || true
  cp -R app/build/outputs/connected_android_test_additional_output "$output/additional-output" 2>/dev/null || true
  python3 .github/scripts/summarize-instrumentation.py "$output" || true
}
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
if adb shell dumpsys window | grep -q 'mCurrentFocus=.*Application Not Responding'; then
  capture environment-not-ready
  echo '::error::Android system ANR dialog owns window focus before instrumentation. See environment evidence.'
  exit 1
fi
adb logcat -c
suspects='com.hoodie.app.pixel.ScenePerformanceDeviceTest,com.hoodie.app.ui.HoodieTouchInteractionTest,com.hoodie.app.ui.PlacePickerScreenTest,com.hoodie.app.ui.SetupAndDemoUiTest,com.hoodie.app.ui.DemoReplayUiTest,com.hoodie.app.ui.StoreSceneDeviceTest'
test_status=0
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$suspects" || test_status=$?
capture targeted
# No automatic retry: a failed targeted run blocks the full suite and the job.
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
python3 .github/scripts/verify-junit.py "$evidence/targeted/results" || exit $?
adb logcat -c
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.verifyGoldens=true || test_status=$?
capture full
if [ "$test_status" -eq 0 ]; then
  python3 .github/scripts/verify-junit.py "$evidence/full/results" || test_status=$?
fi
exit "$test_status"

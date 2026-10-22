#!/usr/bin/env bash

# The emulator action executes each script input line in a separate shell.
# Keep status and diagnostics in one process before that action stops the AVD.
set -uo pipefail
evidence=app/build/instrumentation-evidence
mkdir -p "$evidence"
capture() {
  local stage=$1
  local output="$evidence/$stage"
  local capture_status=0
  mkdir -p "$output"
  adb logcat -d -v threadtime > "$output/logcat.txt" 2>&1 || capture_status=1
  adb logcat -d -b events -v threadtime -s am_anr > "$output/anr-events.txt" 2>&1 || capture_status=1
  adb logcat -d -b crash -v threadtime > "$output/crashes.txt" 2>&1 || capture_status=1
  adb shell dumpsys power > "$output/power.txt" 2>&1 || true
  adb shell dumpsys window > "$output/window.txt" 2>&1 || true
  adb shell getconf PAGE_SIZE > "$output/page-size.txt" 2>&1 || true
  adb exec-out screencap -p > "$output/screen.png" || true
  adb pull /sdcard/Android/data/com.hoodie.app/files/ "$output/device-files" >/dev/null 2>&1 || true
  cp -R app/build/reports/androidTests "$output/reports" 2>/dev/null || true
  cp -R app/build/outputs/androidTest-results "$output/results" 2>/dev/null || true
  cp -R app/build/outputs/connected_android_test_additional_output "$output/additional-output" 2>/dev/null || true
  python3 .github/scripts/summarize-instrumentation.py "$output" || true
  return "$capture_status"
}
# run-qa-emulator.sh owns device setup; no key is injected before focus exists.
# boot_completed can precede the launcher's focused window. Wait for that state,
# preserving every complete snapshot; a focused ANR still fails immediately.
if ! python3 .github/scripts/verify-emulator-window.py --wait-for-focus "$evidence"; then
  capture environment-not-ready
  exit 1
fi
# Keep boot failures even when their dialog no longer owns focus. Clearing the
# buffer for the tests must not erase the environment's earlier ANR evidence.
preflight_status=0
adb logcat -d -v threadtime > "$evidence/preflight-logcat.txt" 2>&1 || preflight_status=1
adb logcat -d -b events -v threadtime -s am_anr > "$evidence/preflight-anr-events.txt" 2>&1 || preflight_status=1
adb logcat -d -b crash -v threadtime > "$evidence/preflight-crashes.txt" 2>&1 || preflight_status=1
if [ "$preflight_status" -ne 0 ]; then
  capture environment-preflight-failure
  echo '::error::Preflight log capture failed; no tests started and buffers not cleared.'
  exit 1
fi
if ! python3 .github/scripts/verify-emulator-boot.py "$evidence/preflight-anr-events.txt" "$evidence/preflight-crashes.txt"; then
  capture environment-preflight-failure
  exit 1
fi
adb logcat -c
suspects='com.hoodie.app.pixel.ScenePerformanceDeviceTest,com.hoodie.app.ui.HoodieTouchInteractionTest,com.hoodie.app.ui.PlacePickerScreenTest,com.hoodie.app.ui.SetupAndDemoUiTest,com.hoodie.app.ui.DemoReplayUiTest,com.hoodie.app.ui.StoreSceneDeviceTest,com.hoodie.app.ui.DigitalConsentTest'
test_status=0
./gradlew :app:connectedDebugAndroidTest --no-parallel --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8' -Pandroid.testInstrumentationRunnerArguments.class="$suspects" || test_status=$?
if ! capture targeted && [ "$test_status" -eq 0 ]; then test_status=1; fi
# No automatic retry: a failed targeted run blocks the full suite and the job.
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
python3 .github/scripts/verify-emulator-boot.py "$evidence/targeted/anr-events.txt" "$evidence/targeted/crashes.txt" --phase runtime || exit $?
python3 .github/scripts/verify-junit.py "$evidence/targeted/results" || exit $?
adb logcat -c
./gradlew :app:connectedDebugAndroidTest --no-parallel --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8' -Pandroid.testInstrumentationRunnerArguments.verifyGoldens=true || test_status=$?
if ! capture full && [ "$test_status" -eq 0 ]; then test_status=1; fi
if [ "$test_status" -eq 0 ]; then
  python3 .github/scripts/verify-emulator-boot.py "$evidence/full/anr-events.txt" "$evidence/full/crashes.txt" --phase runtime || test_status=$?
  python3 .github/scripts/verify-junit.py "$evidence/full/results" || test_status=$?
fi
exit "$test_status"

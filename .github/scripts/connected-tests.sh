#!/usr/bin/env bash

# The emulator action executes each script input line in a separate shell.
# Keep status and diagnostics in one process before that action stops the AVD.
test_status=0
./gradlew :app:connectedDebugAndroidTest || test_status=$?
if [ "$test_status" -ne 0 ]; then
  mkdir -p app/build/reports/androidTests/diagnostics
  adb shell dumpsys power > app/build/reports/androidTests/diagnostics/power.txt || true
  adb shell dumpsys window > app/build/reports/androidTests/diagnostics/window.txt || true
  adb pull /sdcard/Android/data/com.hoodie.app/files/stabilization-window-focus-timeout.png app/build/reports/androidTests/diagnostics/ || true
  adb pull /sdcard/Android/data/com.hoodie.app/files/stabilization-ime-timeout.png app/build/reports/androidTests/diagnostics/ || true
fi
exit "$test_status"

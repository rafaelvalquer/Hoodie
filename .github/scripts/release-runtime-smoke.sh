#!/usr/bin/env bash
set -euo pipefail
apk=$1
package=${2:-com.hoodie.app}
activity=${3:-com.hoodie.app.MainActivity}
[[ "$package" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]]
output=${RUNTIME_REPORT_DIR:-app/build/reports/release-runtime}
mkdir -p "$output"
capture() {
  adb logcat -d -v threadtime > "$output/logcat.txt" 2>&1 || true
  adb logcat -d -b crash > "$output/crashes.txt" 2>&1 || true
  adb exec-out screencap -p > "$output/screen.png" || true
}
trap capture EXIT
test "$(adb shell getconf PAGE_SIZE | tr -d '\r')" = 16384
adb shell getprop > "$output/device-properties.txt"
# Preserve boot diagnostics before isolating application launch logs. A past
# SystemUI ANR must remain visible even if the current app focus is healthy.
adb logcat -d -v threadtime > "$output/preflight-logcat.txt" 2>&1
adb logcat -d -b events -s am_anr > "$output/preflight-anr-events.txt" 2>&1
adb logcat -d -b crash > "$output/preflight-crashes.txt" 2>&1
adb logcat -c
adb install -r "$apk"
for opening in 1 2; do
  # Force-stop is only for process restart, not geofence delivery. WorkManager
  # can start the NEW process before the Activity and Android may call it WARM.
  adb shell am force-stop "$package"
  adb shell "pidof $package || [ \$? -eq 1 ]" > "$output/stopped-pid-$opening.txt"
  test ! -s "$output/stopped-pid-$opening.txt"
  adb shell am start -W -n "$package/$activity" > "$output/launch-$opening.txt"
  grep -q 'Status: ok' "$output/launch-$opening.txt"
  deadline=$((SECONDS + 30))
  while true; do
    # Read complete current snapshots. Full `dumpsys window` also contains
    # historical last-ANR focus, which cannot prove the current release is focused.
    adb shell dumpsys activity activities > "$output/activity-$opening.txt"
    adb shell dumpsys window displays > "$output/window-$opening.txt"
    if grep -E 'mResumedActivity|topResumedActivity' "$output/activity-$opening.txt" | grep -F " $package/" > /dev/null &&
      python3 .github/scripts/verify-emulator-window.py "$output/window-$opening.txt" "$package"; then
      break
    fi
    test "$SECONDS" -lt "$deadline"
    sleep 1
  done
  adb shell pidof "$package" > "$output/pid-$opening.txt"
done
capture
if grep -E 'UnsatisfiedLinkError|Fatal signal|FATAL EXCEPTION' "$output/logcat.txt"; then exit 1; fi
python3 .github/scripts/summarize-runtime-launches.py "$output"

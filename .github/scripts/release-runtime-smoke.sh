#!/usr/bin/env bash
set -euo pipefail
apk=$1
package=${2:-com.hoodie.app}
activity=${3:-com.hoodie.app.MainActivity}
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
adb logcat -c
adb install -r "$apk"
for opening in 1 2; do
  # Here force-stop deliberately exercises a cold launch, not geofence delivery.
  adb shell am force-stop "$package"
  adb shell am start -W -n "$package/$activity" > "$output/launch-$opening.txt"
  grep -q 'Status: ok' "$output/launch-$opening.txt"
  deadline=$((SECONDS + 30))
  until adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | grep -q "$package" &&
    adb shell dumpsys window | grep 'mCurrentFocus=' | grep -q "$package"; do
    test "$SECONDS" -lt "$deadline"
    sleep 1
  done
  adb shell pidof "$package" > "$output/pid-$opening.txt"
done
capture
if grep -E 'UnsatisfiedLinkError|Fatal signal|FATAL EXCEPTION' "$output/logcat.txt"; then exit 1; fi
printf '%s\n' '{"pageSize":16384,"derivedApkColdLaunch":true,"databaseMigrationAndBackupCoverage":"separate instrumented suite; full release journey requires homologation"}' > "$output/result.json"

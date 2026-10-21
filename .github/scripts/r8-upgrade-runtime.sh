#!/usr/bin/env bash
set -euo pipefail
out=app/build/reports/r8-upgrade-qa/runtime
capture() {
  local status=0
  adb logcat -d -v threadtime > "$out/final-logcat.txt" 2> "$out/logcat.stderr.txt" || status=1
  adb logcat -d -b crash > "$out/final-crashes.txt" 2> "$out/crashes.stderr.txt" || status=1
  adb shell dumpsys activity exit-info com.hoodie.app > "$out/exit-info.txt" 2> "$out/exit-info.stderr.txt" || status=1
  adb exec-out screencap -p > "$out/final-screen.png" 2> "$out/screen.stderr.txt" || status=1
  if [[ "$status" -ne 0 ]]; then printf '%s\n' 'Evidence capture failed' >> "$out/capture-failures.txt"; fi
  return "$status"
}
trap 'capture || true' EXIT
serial=$(adb get-serialno | tr -d '\r')
python3 .github/scripts/r8-upgrade-ui.py --serial "$serial" --output "$out/scenario"
capture
trap - EXIT
if grep -E 'UnsatisfiedLinkError|Fatal signal|FATAL EXCEPTION|am_anr[[:space:]]*:' "$out/final-logcat.txt"; then
  echo '::error::Upgrade runtime recorded a fatal error or ANR; no runtime approval.'
  exit 1
fi

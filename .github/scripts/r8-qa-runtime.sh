#!/usr/bin/env bash
set -euo pipefail
out=app/build/reports/r8-qa/runtime
mkdir -p "$out"
export RUNTIME_REPORT_DIR="$out"
capture() {
  adb logcat -d -v threadtime > "$out/final-logcat.txt" 2>&1 || true
  adb logcat -d -b crash > "$out/final-crashes.txt" 2>&1 || true
  adb shell dumpsys activity exit-info com.hoodie.app.homologacao > "$out/exit-info.txt" 2>&1 || true
  adb shell dumpsys window displays > "$out/final-window.txt" 2>&1 || true
  adb exec-out screencap -p > "$out/final-screen.png" || true
}
trap capture EXIT
bash .github/scripts/release-runtime-smoke.sh \
  app/build/reports/r8-qa/homologacao-from-aab.apk com.hoodie.app.homologacao com.hoodie.app.MainActivity
# This is a new disposable CI AVD: every boot ANR remains a failure, even when
# its dialog no longer owns focus. Never dismiss it and retry the tests.
if grep -E 'am_anr[[:space:]]*:' "$out/preflight-anr-events.txt"; then
  echo '::error::Boot ANR recorded; accessibility diagnostic not started.'
  exit 1
fi
serial=$(adb get-serialno | tr -d '\r')
python3 .github/scripts/r8-qa-accessibility.py --serial "$serial" --output "$out/accessibility"
python3 .github/scripts/r8-qa-backup.py --serial "$serial" --output "$out/backup-fixture"
: "${HOODIE_BACKUP_PYTHON:?Independent verifier runtime required}"
"$HOODIE_BACKUP_PYTHON" .github/scripts/verify-synthetic-backup.py \
  "$out/backup-fixture/Hoodie_R8_fixture_reexport.hoodiebackup" \
  --output "$out/backup-fixture/independent-authentication.json"
# Distinct debug package: database tests complement the derived R8 APK's UI
# journey. They do not replace it or claim to test R8 migration bytecode.
# Check and preserve this segment before UTP can clear buffers for debug tests.
capture
if grep -E 'UnsatisfiedLinkError|Fatal signal|FATAL EXCEPTION|am_anr[[:space:]]*:' "$out/final-logcat.txt"; then
  echo '::error::R8 journey recorded a fatal error or ANR; database tests not started.'
  exit 1
fi
cp "$out/final-logcat.txt" "$out/r8-journey-logcat.txt"
cp "$out/final-crashes.txt" "$out/r8-journey-crashes.txt"
bash .github/scripts/r8-qa-database.sh
capture
if grep -E 'UnsatisfiedLinkError|Fatal signal|FATAL EXCEPTION|am_anr[[:space:]]*:' "$out/final-logcat.txt"; then
  echo '::error::Runtime recorded a fatal error or ANR; no runtime approval.'
  exit 1
fi

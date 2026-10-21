#!/usr/bin/env bash
# Controlled observation of the exact APK that crashed on Home in run 39521350075.
# Not a release gate, rebuild, retry-to-green, or export-crash acceptance test.
set -euo pipefail
: "${FROZEN_R8_ROOT:?Preserved artifact required}"
: "${CONTROL_PAGE_SIZE:?Explicit page size required}"
test "${GITHUB_ACTIONS:-false}" = true
: "${ANDROID_SERIAL:?Explicit disposable emulator required}"
[[ "$ANDROID_SERIAL" =~ ^emulator-[0-9]+$ ]]
case "$CONTROL_PAGE_SIZE" in 4096|16384) ;; *) exit 1 ;; esac
out=app/build/reports/frozen-r8-control/runtime
test ! -e "$out"
mkdir -p "$out"
package=com.hoodie.app.homologacao
apk="$FROZEN_R8_ROOT/reports/r8-qa/homologacao-from-aab.apk"
capture() {
  adb logcat -d -v threadtime > "$out/final-logcat.txt" 2>&1 || true
  adb logcat -d -b crash > "$out/final-crashes.txt" 2>&1 || true
  adb logcat -d -b events -v threadtime -s am_anr > "$out/final-anr.txt" 2>&1 || true
  adb shell dumpsys activity exit-info "$package" > "$out/exit-info.txt" 2>&1 || true
  adb shell dumpsys window displays > "$out/final-window.txt" 2>&1 || true
}
trap capture EXIT
printf '%s  %s\n' 'c28975b55805f9123a4b8014e45eb73fe464c43c8cd9516c9467f777c55c8025' "$apk" | sha256sum --check -
test "$(adb emu avd name | tr -d '\r' | sed -n '1p')" = Hoodie_QA_API35
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 35
test "$(adb shell getconf PAGE_SIZE | tr -d '\r')" = "$CONTROL_PAGE_SIZE"
adb shell pm list packages > "$out/packages-before.txt"
if grep -Fx "package:$package" "$out/packages-before.txt"; then
  echo 'Existing app data must not be replaced' >&2; exit 1
fi
adb shell getprop > "$out/properties.txt"
adb shell sha256sum /apex/com.android.art/lib64/libart.so > "$out/art-sha256.txt"
adb shell sha256sum /system/lib64/libandroid_runtime.so > "$out/android-runtime-sha256.txt"
adb install "$apk" > "$out/install.txt"
for opening in 1 2; do
  adb shell am force-stop "$package"
  adb shell "pidof $package || [ \$? -eq 1 ]" > "$out/stopped-pid-$opening.txt"
  test ! -s "$out/stopped-pid-$opening.txt"
  adb shell am start -W -n "$package/com.hoodie.app.MainActivity" > "$out/launch-$opening.txt"
  grep -q 'Status: ok' "$out/launch-$opening.txt"
  deadline=$((SECONDS + 30))
  attempt=0
  while true; do
    attempt=$((attempt + 1))
    window="$out/window-$opening-$attempt.txt"
    adb shell dumpsys window displays > "$window"
    if python3 .github/scripts/verify-emulator-window.py "$window" "$package"; then break; fi
    test "$SECONDS" -lt "$deadline"
    sleep 1
  done
  adb shell pidof "$package" > "$out/pid-$opening.txt"
done
python3 .github/scripts/r8-qa-accessibility.py --serial "$ANDROID_SERIAL" --output "$out/accessibility"
capture
python3 .github/scripts/verify-emulator-boot.py "$out/final-anr.txt" "$out/final-crashes.txt" --phase runtime
export FROZEN_R8_REPORT_DIR="$out"
python3 - <<'PY'
import json, os, pathlib, re
p = pathlib.Path(os.environ['FROZEN_R8_REPORT_DIR'])
pids = [(p/f'pid-{i}.txt').read_text().strip() for i in (1,2)]
assert all(re.fullmatch(r'[1-9]\d*', value) for value in pids) and pids[0] != pids[1]
states = []
for i in (1,2):
    found = re.findall(r'^LaunchState: (COLD|WARM)\s*$', (p/f'launch-{i}.txt').read_text(), re.M)
    assert len(found) == 1
    states += found
result = dict(sourceCommit='2875f7817f5f3b4f026ef1d09f705bd4a16f5c7c',
              apkSha256='c28975b55805f9123a4b8014e45eb73fe464c43c8cd9516c9467f777c55c8025',
              pageSize=int(os.environ['CONTROL_PAGE_SIZE']),processIds=pids,androidLaunchStates=states,
              observationCompleted=True,releaseApproved=False,
              scope='Same frozen R8 APK; two app processes and original unchanged-screen accessibility sequence. Images/ART may differ, so page size is not isolated as sole cause. No export/restore, physical or current-candidate acceptance.')
(p/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
PY

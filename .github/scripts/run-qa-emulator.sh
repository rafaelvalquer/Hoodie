#!/usr/bin/env bash
# Wait for an actual focused window. Do not inject the early MENU key which
# caused a boot ANR in runs 39344406307 and 39416243303, before any test ran.
set -euo pipefail
: "${ANDROID_HOME:?Android SDK required}"
: "${HOODIE_EMULATOR_BIN:?Pinned emulator binary required}"
: "${RUNNER_TEMP:?Runner temporary directory required}"
test "$#" -eq 3
api=$1
target=$2
test_script=$3
case "$api:$target:$test_script" in
  34:google_apis:.github/scripts/connected-tests.sh) out=app/build/instrumentation-evidence ;;
  34:google_apis:.github/scripts/exact-golden-control.sh) out=app/build/reports/exact-golden-control/emulator ;;
  34:google_apis:.github/scripts/screen-review-runtime.sh) out=app/build/reports/screen-review/emulator ;;
  35:google_apis_ps16k:.github/scripts/r8-qa-runtime.sh|36:google_apis_ps16k:.github/scripts/r8-qa-runtime.sh) out=app/build/reports/r8-qa/emulator ;;
  35:google_apis_ps16k:.github/scripts/r8-upgrade-runtime.sh|36:google_apis_ps16k:.github/scripts/r8-upgrade-runtime.sh) out=app/build/reports/r8-upgrade-qa/emulator ;;
  35:google_apis_ps16k:.github/scripts/native-runtime-ci.sh) out=app/build/reports/native-runtime/emulator ;;
  35:google_apis:.github/scripts/frozen-r8-runtime.sh|35:google_apis_ps16k:.github/scripts/frozen-r8-runtime.sh) out=app/build/reports/frozen-r8-control/emulator ;;
  *) echo 'Unsupported QA emulator configuration' >&2; exit 1 ;;
esac
gpu=swiftshader_indirect
if [[ -n "${QA_REVIEW_GPU:-}" ]]; then
  # Backend experiment only; mandatory CI/release configurations stay intact.
  test "$test_script" = .github/scripts/screen-review-runtime.sh
  test "$QA_REVIEW_GPU" = swangle
  gpu=$QA_REVIEW_GPU
fi
mkdir -p "$out"
printf '%s\n' "$gpu" > "$out/requested-gpu.txt"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export ANDROID_AVD_HOME="$RUNNER_TEMP/hoodie-avd-$api"
export ANDROID_SERIAL=emulator-5554
avd_name="Hoodie_QA_API$api"
test ! -e "$ANDROID_AVD_HOME"
mkdir "$ANDROID_AVD_HOME"
image="$ANDROID_HOME/system-images/android-$api/$target/x86_64"
test -s "$image/system.img"
printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name "$avd_name" --package "system-images;android-$api;$target;x86_64" \
  --device pixel_8 --path "$ANDROID_AVD_HOME/$avd_name.avd" --abi x86_64
export QA_AVD_CONFIG="$ANDROID_AVD_HOME/$avd_name.avd/config.ini"
python3 - <<'PY'
import os, pathlib
p = pathlib.Path(os.environ['QA_AVD_CONFIG'])
# Match the effective reviewed display before Android boots. A runtime density
# change is unnecessary and can recreate the launcher's window during setup.
values = {'hw.lcd.width':'1080','hw.lcd.height':'2400','hw.lcd.density':'420',
          'hw.cpu.ncore':'4','hw.ramSize':'4096','hw.keyboard':'yes','vm.heapSize':'256'}
lines = [line for line in p.read_text(encoding='utf-8').splitlines()
         if line.partition('=')[0].strip() not in values]
p.write_text('\n'.join(lines+[f'{key}={value}' for key,value in values.items()])+'\n',encoding='utf-8')
PY
cp "$QA_AVD_CONFIG" "$out/emulator-config.ini"
cp "$image/source.properties" "$out/emulator-image.properties"
"$HOODIE_EMULATOR_BIN" -no-window -version > "$out/emulator-version.txt" 2>&1
setsid "$HOODIE_EMULATOR_BIN" -avd "$avd_name" -sysdir "$image" -port 5554 \
  -no-window -gpu "$gpu" -memory 4096 -cores 4 -accel on \
  -no-snapshot -noaudio -no-boot-anim > "$out/emulator-stdout.txt" 2> "$out/emulator-stderr.txt" &
emulator_pid=$!
cleanup() {
  adb logcat -d -v threadtime > "$out/emulator-final-logcat.txt" 2>&1 || true
  adb logcat -d -b crash > "$out/emulator-final-crashes.txt" 2>&1 || true
  # Only terminate the process group created above; never other SDK processes.
  if kill -0 "$emulator_pid" 2>/dev/null; then
    kill -TERM -- "-$emulator_pid" 2>/dev/null || true
  fi
  wait "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
deadline=$((SECONDS + 300))
while true; do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    echo '::error::Emulator exited during boot; see captured stderr.'
    exit 1
  fi
  boot=$(timeout 10s adb shell getprop sys.boot_completed 2>> "$out/emulator-boot-adb-errors.txt" | tr -d '\r') || boot=''
  printf '%s %s\n' "$SECONDS" "$boot" >> "$out/emulator-boot-progress.txt"
  if [[ "$boot" == 1 ]]; then break; fi
  if (( SECONDS >= deadline )); then
    echo '::error::Emulator did not complete boot within the startup gate; no tests started.'
    exit 1
  fi
  sleep 2
done
# These are server-side configuration commands, not synthetic key events.
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 2147483647
adb shell wm dismiss-keyguard
adb shell settings put global window_animation_scale 0.0
adb shell settings put global transition_animation_scale 0.0
adb shell settings put global animator_duration_scale 0.0
adb shell setprop debug.hwui.renderer skiagl
adb shell settings put system font_scale 1.0
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell settings put secure stylus_handwriting_enabled 0
python3 .github/scripts/verify-emulator-window.py --wait-for-focus "$out/initial-focus"
adb shell getprop > "$out/emulator-boot-properties.txt"
adb shell dumpsys SurfaceFlinger > "$out/emulator-boot-surfaceflinger.txt"
adb shell dumpsys power > "$out/emulator-boot-power.txt"
adb logcat -d -v threadtime > "$out/emulator-boot-logcat.txt"
adb logcat -d -b events -v threadtime -s am_anr > "$out/emulator-boot-anr.txt"
adb logcat -d -b crash -v threadtime > "$out/emulator-boot-crashes.txt"
python3 .github/scripts/verify-emulator-boot.py "$out/emulator-boot-anr.txt" "$out/emulator-boot-crashes.txt"
bash "$test_script"

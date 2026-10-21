#!/usr/bin/env bash
# Official pinned tools in an isolated runner directory; no SDK replacement.
set -euo pipefail
: "${RUNNER_TEMP:?Runner temporary directory required}"
: "${GITHUB_ENV:?GitHub environment file required}"
out=${1:-app/build/instrumentation-evidence/emulator-install}
case "$out" in
  app/build/instrumentation-evidence/emulator-install|app/build/reports/r8-qa/emulator-install|app/build/reports/native-runtime/emulator-install|app/build/reports/exact-golden-control/emulator-install|app/build/reports/screen-review/emulator-install|app/build/reports/frozen-r8-control/emulator-install) ;;
  *) echo 'Unsupported emulator evidence directory' >&2; exit 1 ;;
esac
mkdir -p "$out"
archive="$RUNNER_TEMP/emulator-linux_x64-15917651.zip"
tools="$RUNNER_TEMP/hoodie-emulator-15917651"
test ! -e "$tools"
curl --fail --location --retry 3 \
  https://dl.google.com/android/repository/emulator-linux_x64-15917651.zip -o "$archive"
printf '%s  %s\n' '95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266' "$archive" | sha256sum --check -
mkdir "$tools"
unzip -q "$archive" -d "$tools"
binary="$tools/emulator/emulator"
test -x "$binary"
ldd "$binary" > "$out/loader-dependencies.txt" 2>&1 || true
# Probe the same headless executable selected for the actual Android boot.
# The graphical QEMU binary requires libpulse even when only asking its version.
ldd "$tools/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless" \
  > "$out/headless-loader-dependencies.txt" 2>&1 || true
status=0
"$binary" -no-window -version > "$out/version.txt" 2>&1 || status=$?
cat "$out/version.txt"
if [[ "$status" != 0 ]]; then
  cat "$out/loader-dependencies.txt"
  cat "$out/headless-loader-dependencies.txt"
  echo "::error::Pinned emulator could not execute (exit $status); see loader/version evidence."
  exit "$status"
fi
grep -q 15917651 "$out/version.txt"
echo "HOODIE_EMULATOR_BIN=$binary" >> "$GITHUB_ENV"

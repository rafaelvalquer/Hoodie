#!/usr/bin/env bash
# Official archived Intel package; isolated CI tools, never a local SDK update.
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
test "${RUNNER_OS:-}" = macOS
test "$(uname -m)" = x86_64
test "$(sysctl -n machdep.cpu.vendor)" = GenuineIntel
: "${RUNNER_TEMP:?Runner temporary directory required}"
: "${GITHUB_ENV:?GitHub environment file required}"
out=app/build/reports/screen-review/emulator-install
mkdir -p "$out"
archive="$RUNNER_TEMP/emulator-darwin_x64-15917651.zip"
tools="$RUNNER_TEMP/hoodie-emulator-macos-15917651"
test ! -e "$tools"
curl --fail --location --retry 3 \
  https://dl.google.com/android/repository/emulator-darwin_x64-15917651.zip -o "$archive"
# Computed from the official HTTPS archive; the current SDK index no longer lists this build.
printf '%s  %s\n' 'c1a3890f95b8868198918fad05ffca16fa20404d93547ba545ff5a5867ee7005' "$archive" | shasum -a 256 -c -
mkdir "$tools"
unzip -q "$archive" -d "$tools"
binary="$tools/emulator/emulator"
test -x "$binary"
cp "$tools/emulator/source.properties" "$out/source.properties"
python3 - "$out/source.properties" <<'PY'
from pathlib import Path
import sys
fields = dict(line.split('=', 1) for line in Path(sys.argv[1]).read_text().splitlines() if '=' in line)
assert fields['Pkg.BuildId'] == '15917651' and fields['Pkg.Revision'] == '37.1.11'
PY
lipo -archs "$binary" > "$out/architectures.txt"
test "$(cat "$out/architectures.txt")" = x86_64
otool -L "$binary" > "$out/loader-dependencies.txt"
"$binary" -no-window -version > "$out/version.txt" 2>&1
cat "$out/version.txt"
"$binary" -accel-check > "$out/acceleration.txt" 2>&1
cat "$out/acceleration.txt"
grep -Eq 'Hypervisor[.]Framework|HVF' "$out/acceleration.txt"
echo "HOODIE_EMULATOR_BIN=$binary" >> "$GITHUB_ENV"

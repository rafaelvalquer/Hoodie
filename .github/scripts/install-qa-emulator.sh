#!/usr/bin/env bash
# Official pinned tools in an isolated runner directory; no SDK replacement.
set -euo pipefail
: "${RUNNER_TEMP:?Runner temporary directory required}"
: "${GITHUB_ENV:?GitHub environment file required}"
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
"$binary" -version > "$tools/version.txt" 2>&1
grep -q 15917651 "$tools/version.txt"
echo "HOODIE_EMULATOR_BIN=$binary" >> "$GITHUB_ENV"

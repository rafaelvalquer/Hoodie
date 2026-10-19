#!/usr/bin/env bash
set -euo pipefail
# Supply the AAB AND every installable APK derived from it. Static evidence only.
: "${BUNDLETOOL_JAR:?Set BUNDLETOOL_JAR to the verified bundletool distribution}"
: "${ANDROID_HOME:?Android SDK required}"
aab=$1
shift
test "$#" -gt 0
output=${NATIVE_REPORT_DIR:-app/build/reports/native-16kb}
mkdir -p "$output"
java -jar "$BUNDLETOOL_JAR" dump config --bundle="$aab" > "$output/bundle-config.txt"
grep -q PAGE_ALIGNMENT_16K "$output/bundle-config.txt"
zipalign=$(find "$ANDROID_HOME/build-tools" -name zipalign -type f | sort -V | tail -1)
readelf=$(find "$ANDROID_HOME/ndk" -name llvm-readelf -type f | sort -V | tail -1)
test -x "$zipalign"
test -x "$readelf"
for apk in "$@"; do
  "$zipalign" -c -P 16 -v 4 "$apk" > "$output/$(basename "$apk").zipalign.txt"
done
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
unzip -q "$aab" '*.so' -d "$temporary"
while IFS= read -r -d '' library; do
  printf '\n%s\n' "${library#"$temporary"/}"
  "$readelf" --program-headers "$library"
done < <(find "$temporary" -name '*.so' -print0) > "$output/elf-segments.txt"
python3 .github/scripts/verify-native-16kb.py "$aab" "$@" --report "$output/static-results.json"

#!/usr/bin/env bash
# Exporta todos os .aseprite desta pasta para app/src/main/assets/pixel/hoodie/
set -euo pipefail
ASEPRITE="${ASEPRITE:-aseprite}"
SRC="$(cd "$(dirname "$0")" && pwd)"
OUT="$SRC/../../app/src/main/assets/pixel/hoodie"
mkdir -p "$OUT"
for f in "$SRC"/hoodie_*.aseprite; do
  [ -e "$f" ] || continue
  name="$(basename "$f" .aseprite)"
  [ "$name" = "hoodie_master" ] && continue
  "$ASEPRITE" -b "$f" --sheet "$OUT/$name.png" --data "$OUT/$name.json" \
    --format json-array --list-tags --list-slices --sheet-type horizontal
  echo "exportado: $name"
done

#!/usr/bin/env bash
# Exporta todos os .aseprite desta pasta para app/src/main/assets/pixel/hoodie/
# Para cada arquivo: <nome>.png + <nome>.json (desenho) e <nome>.anchors.png (camada anchors).
set -euo pipefail
ASEPRITE="${ASEPRITE:-aseprite}"
SRC="$(cd "$(dirname "$0")" && pwd)"
OUT="$SRC/../../app/src/main/assets/pixel/hoodie"
mkdir -p "$OUT"
for f in "$SRC"/hoodie_*.aseprite; do
  [ -e "$f" ] || continue
  name="$(basename "$f" .aseprite)"
  [ "$name" = "hoodie_master" ] && continue
  # Grupos em produção (ArtBootstrapStudio.PENDING_GROUPS) ficam fora do APK até a aprovação.
  [ "$name" = "hoodie_transport" ] && continue
  "$ASEPRITE" -b "$f" --ignore-layer "baseline (referencia)" --ignore-layer "anchors" \
    --sheet "$OUT/$name.png" --data "$OUT/$name.json" \
    --format json-array --list-tags --list-slices --sheet-type horizontal
  "$ASEPRITE" -b "$f" --layer "anchors" --sheet "$OUT/$name.anchors.png" --sheet-type horizontal
  echo "exportado: $name"
done

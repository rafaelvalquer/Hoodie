#!/usr/bin/env bash
set -euo pipefail
: "${EXACT_PAIR_ROOT:?Preserved artifact directory required}"
python3 .github/scripts/diagnose-exact-apk-pair.py \
  --serial "$ANDROID_SERIAL" \
  --app "$EXACT_PAIR_ROOT/outputs/apk/debug/app-debug.apk" \
  --test "$EXACT_PAIR_ROOT/outputs/apk/androidTest/debug/app-debug-androidTest.apk" \
  --output app/build/reports/exact-golden-control/tests

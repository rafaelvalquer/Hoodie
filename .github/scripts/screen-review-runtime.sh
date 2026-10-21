#!/usr/bin/env bash
set -euo pipefail
: "${REVIEW_PAIR_ROOT:?Frozen APK directory required}"
: "${REVIEW_CAPTURE_LABEL:?Independent capture label required}"
case "$REVIEW_CAPTURE_LABEL" in capture-a|capture-b) ;; *) exit 1 ;; esac
python3 .github/scripts/capture-screen-review.py --pair-root "$REVIEW_PAIR_ROOT" \
  --output "app/build/reports/screen-review/$REVIEW_CAPTURE_LABEL"

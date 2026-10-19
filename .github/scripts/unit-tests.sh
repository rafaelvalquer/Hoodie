#!/usr/bin/env bash
set -euo pipefail
# The six opt-in visual exporters are tests too: run them, keep candidates in build/,
# and never approve/update reference goldens automatically.
./gradlew :app:testDebugUnitTest \
  -PfrontSeatedReview=true -PfrontSeatedReviewOutput=build/pixel-preview/front-seated \
  -PofficeNpcReview=true -PofficeNpcReviewOutput=build/pixel-preview/office-live \
  -PshoppingNpcReview=true -PshoppingNpcReviewOutput=build/pixel-preview/shopping-live \
  -PnpcVisualReview=true -PnpcVisualReviewOutput=build/pixel-preview/npc-v3-review \
  -PrestaurantLiveReview=true -PtransportThumbs=true
python3 .github/scripts/verify-junit.py app/build/test-results/testDebugUnitTest

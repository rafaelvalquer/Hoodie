# Office scene performance

Measurements collected with `OfficePerformanceDeviceTest` on `Pixel_8(AVD) - 17`.
These are debug/instrumentation measurements from an emulator; physical-device
validation is still pending.

## Five-minute virtual simulation

| Measurement | Earlier run | Intermediate run | Latest run |
| --- | ---: | ---: | ---: |
| 9,100 rendered frames | 5,351 ms | 3,133 ms | 8,719 ms |
| Allocated bytes (about per frame) | 409,960,448 (~45.0 KB) | 328,630,272 (~36.1 KB) | 303,038,464 (~33.3 KB) |
| GC count / time | 80 / 970 ms | 66 / 789 ms | 62 / 977 ms |
| Office sessions / brains | 1 / 3 | 1 / 3 | 1 / 3 |
| Timeline resets | 0 | 0 | 0 |
| Timeline rebuilds | — | — | 238 |
| Sprite cache | 96 / 96 | 96 / 96 | 96 / 96 |

These measurements came from separate emulator test runs, not a controlled
paired benchmark. The latest run allocated less but took longer and spent more
time in GC than the intermediate run; warm cache and emulator state affect the
wall time. The allocation and GC figures are directional.

## Warm Compose loop

The Compose benchmark warms the renderer for 120 frames, resets the rolling
measurement window, then records the next 120 frames:

- FPS: 30.05
- Render P50 / P95 / P99: 2.29 / 5.95 / 11.18 ms
- NPC drawing P95: 3.20 ms
- Scene drawing P95: 2.27 ms
- Bitmap update P95: 0.23 ms
- Render thread: `DefaultDispatcher-worker-5`

This latest 120-frame window stayed within the 33 ms frame budget at P95 and
P99 on the Pixel 8 AVD. It is an emulator measurement, not a physical-device
acceptance result.

## Cold entry and resume

On the latest isolated instrumentation run, the first bitmap appeared 734 ms
after mounting the scene; its first renderer call took 67 ms (65 ms drawing,
6 ms NPC drawing). A later isolated cold-render probe measured 153 ms: 23 ms
planning, 14 ms state resolution, and 114 ms drawing. NPC sprite painting
accounted for 88 ms of drawing (71 ms for the rabbit), while the other scene
drawing took 8 ms. These cold probes varied substantially, and the 153 ms
renderer call exceeds the 33 ms frame budget. Office rendering runs on
`Dispatchers.Default`, so this work does not block the UI thread.
After reverting the synchronous warmup experiment, a fresh 120-frame AVD run
measured a 261 ms cold renderer call; P50 / P95 / P99 were 1.94 / 21.16 / 31.39
ms, at 28.85 FPS. Earlier paced runs were faster (P95 16.05 ms, P99 28.47 ms,
28.86 FPS), illustrating emulator variance. The latest run met the 33 ms P99
budget but did not reach 30 FPS, and neither run is a physical-device result.
The first frame after returning from background appeared in 51 ms and reused
the existing session. An isolated cold-render experiment measured 21 ms for the
first render, 28 ms after code warm-up with an empty sprite cache, then 18 ms
for exact-pose preparation plus a 19 ms render with zero sprite misses. These
are separate cold-path probes; they do not demonstrate that ordinary scene
entry is below budget. In the latest paired probe, exact-pose preparation took
30 ms and the following render took 24 ms with zero sprite misses; the first
cold render took 159 ms. Earlier isolated runs varied: raw first render ranged
from 74 to 202 ms, while exact-pose preparation took 43–90 ms. An integrated
synchronous warmup experiment was rejected: in a later cold AVD process the
warmup took 527 ms and the first renderer call took 1,017 ms. The experiment
kept the cache bounded but made first presentation substantially slower, so
the renderer does not warm sprites automatically. Cold-entry latency needs a
physical-device trace before choosing an asynchronous or lifecycle-level
warmup strategy.

## Thirty virtual minutes

- 54,546 frames rendered in 22.5 s of emulator wall time
- 1 session, 3 brains, 0 timeline resets, 509 incremental timeline entries
- Sprite cache remained at its 96-entry capacity
- Heap samples fluctuated between ~42.8 and ~49.2 MB after initial rendering
- Heap after explicit GC: ~40.0 MB
- Total allocated bytes: ~2.23 GB across all frames

The latest 30-minute instrumented run completed in 56.1 s of emulator wall
time, allocated 1.73 GB across all frames, and ended at 46.7 MB Java heap; after
explicit GC, retained heap was 40.1 MB. It still used one session, three brains,
zero timeline resets, and a 96-entry sprite cache. The earlier run took 22.5 s
and allocated 2.23 GB, illustrating substantial emulator-run variance. Heap
values are live-process measurements, not native/process-wide memory; they do
not replace a longer physical-device memory profile.

## Thirty-minute time jump after backgrounding

The first resumed render at virtual time +30 minutes initially took 2,365 ms;
state resolution accounted for 2,302 ms. Reservation planning queried historical
base actions repeatedly, rebuilding each action history from its start. An
append-only cache for the uncoordinated base timeline now serves those queries
with binary-search lookup while keeping the social/reservation timeline intact.
The latest Pixel 8 AVD run took 45 ms (state: 36 ms), a 98% reduction in
total time and a 98% reduction in state time versus the 2,365 ms baseline. It
retained one session and three brains, with zero timeline resets and 1,070
incremental timeline entries. Earlier optimized runs measured 177 ms and
281 ms; isolated emulator measurements vary, so these numbers are directional.
Social reservation memoization is capped at 24,576 decisions with access-order
eviction. Each cached assignment packs the three NPC spots into one integer,
avoiding a per-decision map and reservation objects. A one-hour regression
forces coordinated-timeline pruning and then seeks back to an older state; it
matches an unpruned session and completed in 0.29 s in the local JVM test. The
auxiliary uncoordinated action timeline is capped at 512 entries and
deterministically replays older raw queries from the seed. The coordinated
action timeline is capped at 8,192 entries per brain and pruned in batches of
up to 1,024. A query older than the retained window deterministically replays
from the seed. The 30-minute AVD run generated 1,070 coordinated entries across
the session, so it did not reach the production cap. A separate local JVM
regression advanced all three brains through 24 virtual hours until the
24,576-entry reservation cache was full, then sought back to the first hour.
The replay matched an unpruned reference state and took 757 ms in the latest
local JVM run (`OFFICE_DAY_BACKSEEK`; the whole test took 1.75 s).

All timelines remain owned by the renderer's one active Office session and are
released on scene exit or renderer disposal. In the Home flow, the Office
session key includes the current day/context variant. Office Live Lab also
disposes its separately owned diagnostic session when the tab leaves
composition. Physical-device validation is still pending.

## Other scene sweep

Warm render results from the latest run on the same AVD (30 warm-up frames,
then 120 measured frames):

| Scene | P50 | P95 | P99 |
| --- | ---: | ---: | ---: |
| Home | 0.34 ms | 1.75 ms | 2.26 ms |
| Restaurant | 0.61 ms | 3.05 ms | 4.52 ms |
| Shopping | 0.41 ms | 1.55 ms | 2.14 ms |
| School | 0.22 ms | 0.73 ms | 1.01 ms |
| Family | 0.20 ms | 0.66 ms | 0.84 ms |
| Transit | 0.36 ms | 0.90 ms | 1.09 ms |
| Bus | 1.83 ms | 2.97 ms | 3.84 ms |
| Train | 1.52 ms | 2.39 ms | 2.62 ms |

## Twenty-four-hour virtual cache stress

The instrumented stress test advanced all three office brains by 24 virtual
hours in 3.11 s on the AVD. The coordinated timelines retained 22,468 of
24,576 allowed entries after 28 batch prunes; the social reservation cache
reached its 24,576-entry limit. Repeating the same-time query returned identical
states. No cache exceeded its configured capacity.

Restaurant had the highest P95 in the latest sweep at 3.05 ms, still below the
33 ms render budget. An earlier full instrumentation run had restaurant P99 at
42.6 ms, so its tail latency should be rechecked on a physical device before
ruling out further work.

## Verification

- Earlier `:app:connectedDebugAndroidTest` run for `OfficePerformanceDeviceTest` — 9/9 passed on `Pixel_8(AVD) - 17`, including the 24-hour cache stress test, before the rejected synchronous-warmup experiment.
- Final source `office120FramesReuseSessionAndReportMeasuredStages` instrumentation test — 1/1 passed on `Pixel_8(AVD) - 17` after reverting that experiment.
- Office unit suite on the final source — 34 passed, 1 visual review test skipped.
- `OfficePerformanceRegressionTest` — 9/9 passed, including saturated-cache day backseek.
- The coordinated-timeline pruning and deterministic replay regression — passed with an injected 16-entry limit.
- Latest full `:app:testDebugUnitTest` run — 865 tests completed, 2 failed, 6 skipped. `NewPlaceScenesTest` reports the `shopping_look` golden mismatch while the workspace contains uncommitted shopping-scene visual changes. `FullDayIntelligenceIntegrationTest` still expects `PROBABLE` but receives `IDENTIFIED`; both failures are outside the Office performance code.
- `:app:compileDebugKotlin` — passed after the latest renderer and Compose-loop changes.

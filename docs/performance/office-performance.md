# Office scene performance

Measurements collected with `OfficePerformanceDeviceTest` on `Pixel_8(AVD) - 17`.
These are debug/instrumentation measurements from an emulator; physical-device
validation is still pending.

## Five-minute virtual simulation

| Measurement | Earlier run | Current run |
| --- | ---: | ---: |
| 9,100 rendered frames | 5,351 ms | 3,133 ms |
| Allocated bytes | 409,960,448 | 328,630,272 |
| GC count / time | 80 / 970 ms | 66 / 789 ms |
| Office sessions / brains | 1 / 3 | 1 / 3 |
| Timeline resets | 0 | 0 |
| Sprite cache | 96 / 96 | 96 / 96 |

The earlier and current measurements came from separate emulator test runs, not
a controlled paired benchmark. The allocation and GC figures are directional;
warm cache and emulator state can affect elapsed time.

## Warm Compose loop

The Compose benchmark warms the renderer for 120 frames, resets the rolling
measurement window, then records the next 120 frames:

- FPS: 29.90
- Render P50 / P95 / P99: 0.90 / 2.83 / 3.71 ms
- NPC drawing P95: 1.66 ms
- Scene drawing P95: 1.56 ms
- Bitmap update P95: 0.16 ms
- Render thread: `DefaultDispatcher-worker-10`

## Cold entry and resume

On an isolated instrumentation run, the first bitmap appeared 1,564 ms after
mounting the scene. The first renderer call took 250 ms (179 ms drawing, of
which 107 ms was NPC drawing). This cold-frame spike exceeds the 33 ms frame
budget, although the rendering work runs on `Dispatchers.Default` and does not
block the UI thread. The first frame after returning from background appeared
in 115 ms and reused the existing session. Cold-entry latency remains a follow-up
for a physical-device trace; it should not be conflated with the warmed cadence.

A second experiment separated raw first render, code warm-up, and exact-pose
preparation. Isolated runs varied substantially: raw first render ranged from
74 to 202 ms; code-warmed renders with an empty sprite cache ranged from 77 to
132 ms. Exact-pose preparation took 43–90 ms, followed by a 50–63 ms render
with zero sprite misses. In the full test class, the raw render was 74 ms while
preparation plus render totaled 93 ms. These are not paired measurements, and
the prewarm did not consistently reduce total first-image latency, so the
synchronous production warm-up experiment was discarded. The cold-entry path
needs measurement on a physical device before choosing a production strategy.

## Thirty virtual minutes

- 54,546 frames rendered in 22.5 s of emulator wall time
- 1 session, 3 brains, 0 timeline resets, 509 incremental timeline entries
- Sprite cache remained at its 96-entry capacity
- Heap samples fluctuated between ~42.8 and ~49.2 MB after initial rendering
- Heap after explicit GC: ~40.0 MB
- Total allocated bytes: ~2.23 GB across all frames

The heap values are live-process measurements, not native/process-wide memory.
They indicate bounded caches and no monotonic increase in sampled Java heap;
they do not replace a longer physical-device memory profile.

## Thirty-minute time jump after backgrounding

The first resumed render at virtual time +30 minutes initially took 2,365 ms;
state resolution accounted for 2,302 ms. Reservation planning queried historical
base actions repeatedly, rebuilding each action history from its start. An
append-only cache for the uncoordinated base timeline now serves those queries
with binary-search lookup while keeping the social/reservation timeline intact.
After the change, the same Pixel 8 AVD test took 177 ms (state: 156 ms), a 92%
reduction. It retained one session and three brains, with zero timeline resets.
This is a single emulator measurement; device validation is still pending.
Social reservation memoization is now capped at 512 decisions with access-order
eviction; an hour-long unit simulation reaches that cap and preserves repeatable
state queries. Action timelines remain owned by the renderer's one active Office
session and are released on scene exit or renderer disposal, preserving exact
backward-time queries during that session.

## Other scene sweep

Warm render P95 on the same AVD (30 warm-up frames, then 120 measured frames):

| Scene | P50 | P95 | P99 |
| --- | ---: | ---: | ---: |
| Home | 0.28 ms | 0.57 ms | 0.72 ms |
| Restaurant | 0.53 ms | 5.39 ms | 6.13 ms |
| Shopping | 0.49 ms | 1.43 ms | 1.95 ms |
| School | 0.17 ms | 0.29 ms | 0.36 ms |
| Family | 0.16 ms | 0.26 ms | 0.38 ms |
| Transit | 0.31 ms | 0.52 ms | 0.75 ms |
| Bus | 1.61 ms | 2.86 ms | 3.13 ms |
| Train | 1.54 ms | 2.59 ms | 3.75 ms |

Restaurant was the slowest in the isolated sweep, with P95 below the 33 ms
render budget; that window included one GC pause totaling 11 ms. In the full
instrumentation class, the restaurant P99 once reached 42.6 ms, so its tail
latency should be rechecked on a physical device before ruling out further work.

## Verification

- `:app:testDebugUnitTest --tests 'com.hoodie.app.pixel.*'` — passed.
- `:app:connectedDebugAndroidTest` for `OfficePerformanceDeviceTest` — 8/8 passed.
- Office ambient brain, timeline regression, and performance unit tests — passed.
- `:app:assembleRelease` — passed.

The full unit-test suite has a known unrelated failure in
`FullDayIntelligenceIntegrationTest.completeDayUsesOneStoryLearnsCorrectionsAndRestoresSleepAfterMidnight`
(expected `PROBABLE`, actual `IDENTIFIED`).

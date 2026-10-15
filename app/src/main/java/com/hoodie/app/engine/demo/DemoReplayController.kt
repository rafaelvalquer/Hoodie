package com.hoodie.app.engine.demo

import com.hoodie.app.presentation.screens.diary.ReplaySpeed
import com.hoodie.app.presentation.screens.diary.ReplayState

data class DemoReplaySnapshot(
    val timestamp: Long,
    val state: ReplayState = ReplayState.IDLE,
    val mode: DemoReplayMode = DemoReplayMode.QUICK_TOUR,
    val speed: ReplaySpeed = ReplaySpeed.FAST,
    val tourElapsedMs: Long = 0,
)

/** Pure playback clock. The owner supplies monotonic time; UI frame frequency never drives progress.
 * Restoring a snapshot does not start a background clock. A visible owner must explicitly resume. */
class DemoReplayController(val timeline: DemoReplayTimeline, restored: DemoReplaySnapshot? = null) {
    var snapshot: DemoReplaySnapshot = (restored ?: DemoReplaySnapshot(timeline.startAt)).let { saved ->
        val timestamp = saved.timestamp.coerceIn(timeline.startAt, timeline.endAt)
        saved.copy(timestamp = timestamp, state = when {
            timestamp == timeline.endAt -> ReplayState.FINISHED
            saved.state == ReplayState.PLAYING || saved.state == ReplayState.FINISHED -> ReplayState.PAUSED
            else -> saved.state
        }, tourElapsedMs = if (timeline.timestampAtTourElapsed(saved.tourElapsedMs) == timestamp) saved.tourElapsedMs.coerceIn(0, timeline.tourDurationMs)
            else timeline.tourElapsedAt(timestamp))
    }
        private set
    private var anchorAt: Long? = null
    private var anchorTimestamp = snapshot.timestamp
    private var anchorTourElapsed = snapshot.tourElapsedMs

    fun tick(monotonicMs: Long): DemoReplaySnapshot {
        val anchor = anchorAt ?: return snapshot
        if (snapshot.state != ReplayState.PLAYING) return snapshot
        val elapsed = (monotonicMs - anchor).coerceAtLeast(0)
        if (elapsed == 0L) return snapshot
        val timestamp: Long
        val tourElapsed: Long
        if (snapshot.mode == DemoReplayMode.QUICK_TOUR) {
            tourElapsed = anchorTourElapsed + minOf(elapsed, timeline.tourDurationMs - anchorTourElapsed)
            timestamp = timeline.timestampAtTourElapsed(tourElapsed)
        } else {
            val factor = snapshot.speed.minutesPerSecond * 60L
            val remaining = timeline.endAt - anchorTimestamp
            val boundedElapsed = minOf(elapsed, (remaining + factor - 1) / factor)
            timestamp = minOf(timeline.endAt, anchorTimestamp + boundedElapsed * factor)
            tourElapsed = timeline.tourElapsedAt(timestamp)
        }
        val at = maxOf(snapshot.timestamp, timestamp)
        snapshot = snapshot.copy(timestamp = at, tourElapsedMs = tourElapsed,
            state = if (at == timeline.endAt) ReplayState.FINISHED else ReplayState.PLAYING)
        if (snapshot.state == ReplayState.FINISHED) anchorAt = null
        return snapshot
    }

    fun play(monotonicMs: Long): DemoReplaySnapshot {
        if (snapshot.state == ReplayState.PLAYING) return tick(monotonicMs)
        if (snapshot.state == ReplayState.FINISHED) restart()
        snapshot = snapshot.copy(state = ReplayState.PLAYING)
        rebase(monotonicMs)
        return snapshot
    }

    fun pause(monotonicMs: Long): DemoReplaySnapshot {
        tick(monotonicMs)
        if (snapshot.state == ReplayState.PLAYING) snapshot = snapshot.copy(state = ReplayState.PAUSED)
        anchorAt = null
        return snapshot
    }

    fun restart(): DemoReplaySnapshot {
        snapshot = snapshot.copy(timestamp = timeline.startAt, state = ReplayState.IDLE, tourElapsedMs = 0)
        anchorAt = null
        return snapshot
    }

    fun seek(timestamp: Long, monotonicMs: Long): DemoReplaySnapshot {
        val playing = snapshot.state == ReplayState.PLAYING
        val at = timestamp.coerceIn(timeline.startAt, timeline.endAt)
        snapshot = snapshot.copy(timestamp = at, tourElapsedMs = timeline.tourElapsedAt(at), state = when {
            at == timeline.endAt -> ReplayState.FINISHED
            playing -> ReplayState.PLAYING
            else -> ReplayState.PAUSED
        })
        if (snapshot.state == ReplayState.PLAYING) rebase(monotonicMs) else anchorAt = null
        return snapshot
    }

    fun next(monotonicMs: Long): DemoReplaySnapshot {
        tick(monotonicMs)
        return seek(timeline.next(snapshot.timestamp, snapshot.mode), monotonicMs)
    }

    fun previous(monotonicMs: Long): DemoReplaySnapshot {
        tick(monotonicMs)
        val previous = timeline.previous(snapshot.timestamp, snapshot.mode)
        val anchor = anchorAt
        // A fast replay moves past an event again before a second press. Within one real
        // second of resuming/seeking that event, allow Previous to reach the preceding one.
        val reference = if (anchor != null && monotonicMs - anchor in 0L..1_000L && previous == anchorTimestamp)
            anchorTimestamp else snapshot.timestamp
        return seek(timeline.previous(reference, snapshot.mode), monotonicMs)
    }

    fun setSpeed(speed: ReplaySpeed, monotonicMs: Long): DemoReplaySnapshot {
        tick(monotonicMs)
        snapshot = snapshot.copy(speed = speed)
        if (snapshot.state == ReplayState.PLAYING) rebase(monotonicMs)
        return snapshot
    }

    /** Selecting another experience starts its own script at 07:00, without autoplay. */
    fun setMode(mode: DemoReplayMode, monotonicMs: Long): DemoReplaySnapshot {
        if (mode == snapshot.mode) return tick(monotonicMs)
        snapshot = snapshot.copy(mode = mode)
        return restart()
    }

    private fun rebase(monotonicMs: Long) {
        anchorAt = monotonicMs
        anchorTimestamp = snapshot.timestamp
        anchorTourElapsed = snapshot.tourElapsedMs
    }
}

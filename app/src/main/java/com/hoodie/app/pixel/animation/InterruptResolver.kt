package com.hoodie.app.pixel.animation

/** Frame and cycle boundaries use durations supplied by the active sprite provider. */
internal object InterruptResolver {
    fun boundary(animation: AnimationId, durations: LongArray, start: Long, now: Long): Long =
        when (animation.clip.interruptPolicy) {
            InterruptPolicy.IMMEDIATE, InterruptPolicy.PLAY_EXIT -> now
            InterruptPolicy.FINISH_FRAME -> start + ClipTiming.frameEnd(durations, now - start, animation.loop)
            InterruptPolicy.FINISH_CYCLE -> start + ClipTiming.cycleEnd(durations, now - start, animation.loop)
        }

    fun loopBoundary(animation: AnimationId, durations: LongArray, start: Long, now: Long): Long {
        if (animation.clip.interruptPolicy != InterruptPolicy.FINISH_CYCLE) return now
        val elapsed = now - start
        return if (elapsed.mod(ClipTiming.total(durations)) < FRAME_TOLERANCE) now
        else start + ClipTiming.cycleEnd(durations, elapsed, animation.loop)
    }

    private const val FRAME_TOLERANCE = 40L
}

package com.hoodie.app.pixel.performance

/** A stable deadline, rather than a fresh delay after each slightly late display callback.
 * Missed slots are skipped; each callback can publish at most one current draw. */
internal class SceneFramePacer(private val intervalNanos: Long = FRAME_INTERVAL_NANOS) {
    internal companion object {
        /** 30 FPS aligns to 2, 3, and 4 display refreshes at 60, 90, and 120 Hz. */
        const val FRAME_INTERVAL_NANOS = 1_000_000_000L / 30L
        private const val DEADLINE_ROUNDING_TOLERANCE_NANOS = 2L
    }
    init { require(intervalNanos > 0) }
    private var nextFrame = -1L

    fun shouldRender(frameNanos: Long): Boolean {
        if (nextFrame < 0) { nextFrame = frameNanos + intervalNanos; return true }
        if (frameNanos < nextFrame - DEADLINE_ROUNDING_TOLERANCE_NANOS) return false
        nextFrame += ((frameNanos - nextFrame) / intervalNanos + 1) * intervalNanos
        return true
    }
}

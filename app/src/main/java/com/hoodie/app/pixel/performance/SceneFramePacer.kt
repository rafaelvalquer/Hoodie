package com.hoodie.app.pixel.performance

/** A stable deadline, rather than a fresh delay after each slightly late display callback.
 * Missed slots are skipped; each callback can publish at most one current draw. */
internal class SceneFramePacer(private val intervalNanos: Long = 33_000_000L) {
    init { require(intervalNanos > 0) }
    private var nextFrame = -1L

    fun shouldRender(frameNanos: Long): Boolean {
        if (nextFrame < 0) { nextFrame = frameNanos + intervalNanos; return true }
        if (frameNanos < nextFrame) return false
        nextFrame += ((frameNanos - nextFrame) / intervalNanos + 1) * intervalNanos
        return true
    }
}

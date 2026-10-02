package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.sprite.Direction

/** Active clip clock and frame cursor. Scene and posture orchestration stay outside. */
internal class AnimationPlayer {
    var animation: AnimationId = AnimationId.IDLE
        private set
    var direction: Direction = Direction.FRONT
    var startedAt: Long = 0L
        private set
    var lastFrameIndex: Int = -1

    fun play(anim: AnimationId, dir: Direction, now: Long, restart: Boolean = false): Boolean {
        if (!restart && anim == animation && dir == direction) return false
        val sameLoop = anim == animation && anim.loop && !restart
        animation = anim
        direction = dir
        if (!sameLoop) {
            startedAt = now
            lastFrameIndex = -1
        }
        return true
    }

    fun frameIndex(now: Long, durations: LongArray): Int =
        ClipTiming.indexAt(durations, now - startedAt, animation.loop)
}

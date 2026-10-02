package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.animation.AnimationStateMachine.Step

/** Pending overlay reaction, consumed once when the current clip permits interruption. */
internal class ReactionResolver {
    private var pending: List<AnimationId> = emptyList()
    private var readyAt = 0L

    fun request(animations: List<AnimationId>, boundary: Long) {
        if (animations.isEmpty()) return
        pending = animations.toList()
        readyAt = boundary
    }

    fun takeReady(now: Long): AnimationSequence? {
        if (pending.isEmpty() || now < readyAt) return null
        val sequence = AnimationSequence(pending.map { Step.Play(it) } + Step.Loop)
        pending = emptyList()
        return sequence
    }
}

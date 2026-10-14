package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.animation.AnimationStateMachine.Step
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.scene.VisualState

internal data class PendingInteractiveReaction(val response: InteractionResponse, val visual: VisualState,
    val animation: AnimationId, val readyAt: Long, val expiresAt: Long)

/** Pending overlay reaction, consumed once when the current clip permits interruption. */
internal class ReactionResolver {
    private var pending: List<AnimationId> = emptyList()
    private var readyAt = 0L
    private var interactive: PendingInteractiveReaction? = null
    val hasPending: Boolean get() = pending.isNotEmpty()
    val hasInteractivePending: Boolean get() = interactive != null

    /** Does not modify the established automatic reaction path when the feature is off. */
    fun cancelInteractive() { interactive = null }

    fun requestInteractive(response: InteractionResponse, visual: VisualState, animation: AnimationId,
        boundary: Long, expiresAt: Long,
    ): Boolean {
        if (hasPending || hasInteractivePending) return false
        interactive = PendingInteractiveReaction(response, visual, animation, boundary, expiresAt)
        return true
    }

    fun takeInteractiveReady(now: Long, visual: VisualState?, animation: AnimationId, canStart: Boolean): PendingInteractiveReaction? {
        val request = interactive ?: return null
        if (now > request.expiresAt || visual != request.visual || animation != request.animation || !canStart) {
            cancelInteractive()
            return null
        }
        if (now < request.readyAt) return null
        interactive = null
        return request
    }

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

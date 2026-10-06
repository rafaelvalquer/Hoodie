package com.hoodie.app.pixel.npc.brain

data class NpcSocialEvent(
    val startAt: Long,
    val durationMs: Long,
    val speakerId: String,
    val withSpeechBubble: Boolean,
) {
    val endsAt: Long get() = startAt + durationMs
}

/** Shared deterministic social calendar exposed to the scene-specific coordinator. */
interface NpcSocialCoordinator {
    fun activeEventAt(timeMs: Long): NpcSocialEvent?
}

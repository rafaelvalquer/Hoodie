package com.hoodie.app.engine.reaction

import com.hoodie.app.engine.personality.ReactionPriority
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.InterruptPolicy
import com.hoodie.app.pixel.scene.SceneId

data class ReactionIntent(
    val id: String,
    val source: ReactionSource,
    val sceneId: SceneId,
    val contextVersion: Long,
    val requestedAt: Long,
    val expiresAt: Long,
    val animations: List<AnimationId> = emptyList(),
    val dialogue: String? = null,
    val priority: ReactionPriority = source.priority,
    val interruptPolicy: InterruptPolicy = InterruptPolicy.FINISH_CYCLE,
    val durationMs: Long = 3_500,
) {
    init {
        require(id.isNotBlank() && expiresAt > requestedAt && durationMs > 0)
        require(priority == source.priority) { "A source cannot promote its own priority" }
    }
}

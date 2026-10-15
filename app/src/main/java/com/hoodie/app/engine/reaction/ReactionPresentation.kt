package com.hoodie.app.engine.reaction

import com.hoodie.app.engine.personality.ReactionPriority
import com.hoodie.app.pixel.scene.SceneId

data class ReactionPresentation(
    val id: String, val text: String?, val source: ReactionSource, val priority: ReactionPriority,
    val startedAt: Long, val durationMs: Long, val sceneId: SceneId, val contextVersion: Long,
) {
    val expiresAt get() = startedAt + durationMs
}

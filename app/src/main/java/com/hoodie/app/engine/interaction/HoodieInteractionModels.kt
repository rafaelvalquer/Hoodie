package com.hoodie.app.engine.interaction

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Legs

enum class InteractionGesture { TAP, LONG_PRESS }
/** Injectable gate: production reads HoodieConfig; integration fixtures can exercise both states. */
data class HoodieInteractionFeature(val enabled: Boolean)
enum class InteractionElement { BULLDOG, RABBIT, BUTTERFLY }
enum class InteractionCondition {
    ANY, WAKING, RESTING, TV, GAMING, READING, COOKING, CLEANING, FREE, OCCUPIED,
    TYPING, FOCUSED, COFFEE, TIRED, BULLDOG, RABBIT, WORKING, BREAK,
    MENU, WAITING, EATING, FINISHING, TABLE, WARMUP, LIFT, WATER,
    WALKING, LANDSCAPE, BENCH, BUTTERFLY, SHELVES, PICKING, CHECKOUT,
    CLOTHES, GARMENT, LEAVING, STUDY_READ, STUDY_WRITE, STUDY_THINK,
    TALKING, LISTENING, LAUGH, SAFE_STOP,
}

/** Values describe the last presented frame, not a predicted animation or NPC. */
data class InteractionContext(
    val scene: SceneId,
    val userContext: UserContextType? = null,
    val activity: HoodieActivity = HoodieActivity.IDLE,
    val animation: AnimationId = AnimationId.IDLE,
    val energy: Float = 100f,
    val mood: Float = 100f,
    val vehicleMoving: Boolean? = null,
    val safePassengerStop: Boolean = false,
    val asleep: Boolean = false,
    val canInterrupt: Boolean = true,
    val transitioning: Boolean = false,
    val screenActive: Boolean = true,
    val sequenceBusy: Boolean = false,
    val seated: Boolean = false,
    val walking: Boolean = false,
    val presentElements: Set<InteractionElement> = emptySet(),
) {
    val walkingNow: Boolean get() = walking || animation.frames.any { it.pose.legs in setOf(Legs.WALK, Legs.RUN_A, Legs.RUN_B) }
}

data class InteractionRule(
    val id: String,
    val scene: SceneId?,
    val gesture: InteractionGesture,
    val condition: InteractionCondition,
    val text: String,
    val animations: List<AnimationId>,
    val priority: Int,
    val weight: Int = 1,
    val activity: HoodieActivity? = null,
)

data class InteractionResponse(
    val ruleId: String,
    val animations: List<AnimationId>,
    val text: String?,
    val bubbleDurationMs: Long,
    val maxDurationMs: Long = 2_500,
    val gesture: InteractionGesture = InteractionGesture.TAP,
)

data class InteractionRequest(val response: InteractionResponse, val scene: SceneId, val sessionId: Long,
    val context: InteractionContext? = null)

sealed interface InteractionDecision {
    data class Accepted(val response: InteractionResponse) : InteractionDecision
    data class Cooldown(val remainingMs: Long) : InteractionDecision
    data class Blocked(val reason: String) : InteractionDecision
}

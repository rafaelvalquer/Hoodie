package com.hoodie.app.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import com.hoodie.app.engine.interaction.HoodieInteractionHitTest
import com.hoodie.app.engine.interaction.HoodieInteractionConfig
import com.hoodie.app.engine.interaction.InteractionGesture
import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.animation.AnimationId

/** The reference is read only on gestures; publishing animation frames never recomposes this target. */
internal fun Modifier.hoodieInteractionTarget(
    enabled: Boolean,
    frame: () -> RenderFrame?,
    onGesture: (InteractionGesture, RenderFrame) -> Unit,
): Modifier {
    if (!enabled) return this
    fun accessible(gesture: InteractionGesture): Boolean {
        val current = frame() ?: return false
        onGesture(gesture, current)
        return true
    }
    return testTag("hoodie_interaction_target").semantics {
        contentDescription = "Hoodie"
        onClick("Tocar no Hoodie") { accessible(InteractionGesture.TAP) }
        onLongClick("Fazer carinho no Hoodie") { accessible(InteractionGesture.LONG_PRESS) }
        customActions = listOf(CustomAccessibilityAction("Fazer carinho no Hoodie") { accessible(InteractionGesture.LONG_PRESS) })
    }.pointerInput(Unit) {
        val tolerance = HoodieInteractionConfig().gestureExitToleranceLogicalPx
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val initial = frame() ?: return@awaitEachGesture
            if (initial.interactionTransitioning || initial.interactionBusy ||
                initial.animation in setOf(AnimationId.SLEEP, AnimationId.SLEEP_TURN, AnimationId.NAP_SIT)) return@awaitEachGesture
            if (!HoodieInteractionHitTest.hit(initial, down.position.x, down.position.y, size.width, size.height)) return@awaitEachGesture
            var lastPosition = down.position
            // 0 = cancelled, 1 = released (tap), null = Android's long-press timeout.
            val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var outcome: Int? = null
                while (outcome == null) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    val current = frame()
                    if (change != null) lastPosition = change.position
                    outcome = when {
                        change == null || change.isConsumed || current?.scene?.id != initial.scene.id -> 0
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> 0
                        !HoodieInteractionHitTest.nearHit(current, change.position.x, change.position.y, size.width, size.height, tolerance) -> 0
                        !change.pressed -> { change.consume(); 1 }
                        else -> null
                    }
                }
                outcome
            }
            if (result == 0) return@awaitEachGesture
            val current = frame() ?: return@awaitEachGesture
            // A hold begun while exercising/walking cannot turn into petting after a pause starts.
            // A new gesture is required for the new action, even if no pointer event occurred meanwhile.
            if (current.scene.id != initial.scene.id || current.interactionTransitioning || current.interactionBusy ||
                initial.interactionVisual != null && current.interactionVisual != initial.interactionVisual ||
                (current.occupancy.animation ?: current.animation) != (initial.occupancy.animation ?: initial.animation) ||
                !HoodieInteractionHitTest.nearHit(current, lastPosition.x, lastPosition.y, size.width, size.height, tolerance)) return@awaitEachGesture
            onGesture(if (result == 1) InteractionGesture.TAP else InteractionGesture.LONG_PRESS, current)
            if (result == null) {
                // Consume the release after a long press, without generating a second tap.
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

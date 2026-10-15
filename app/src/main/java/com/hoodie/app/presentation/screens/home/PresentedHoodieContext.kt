package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.*
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualState

data class HomePresentationFeatures(
    val context: Boolean = HoodieConfig.HOODIE_CONTEXT_PRESENTATION_V1,
    val coordinator: Boolean = HoodieConfig.HOODIE_REACTION_COORDINATOR_V1,
)

/** Derived presentation identity, never stored in Room or used as routine evidence. */
data class PresentedHoodieContext(
    val contextEventId: Long?,
    val contextType: UserContextType,
    val placeId: Long?,
    val venueType: PlaceType?,
    val hoodieActivity: HoodieActivity,
    val sceneId: SceneId,
    val visualRevision: Long,
    val isReconciled: Boolean,
    val sourceConsistent: Boolean,
) {
    fun presented(revision: Long) = copy(isReconciled = sourceConsistent && revision == visualRevision)
    fun accepts(visual: VisualState?) = isReconciled && visual?.presentationRevision == visualRevision &&
        visual.scene == sceneId && visual.placeId == placeId
}

/** Revisions change only with meaningful identity/visual changes, not clock/bubble updates. */
class PresentedHoodieContextTracker {
    private var previous: PresentedHoodieContext? = null
    private var previousVisual: VisualState? = null
    private var nextRevision = 0L
    private var presentedRevision = 0L

    fun acknowledge(revision: Long): Boolean {
        if (previous?.visualRevision != revision || presentedRevision == revision) return false
        presentedRevision = revision
        return true
    }
    fun resetPresentation() { presentedRevision = 0 }

    fun select(event: ContextEvent?, snapshotContext: UserContextType, activity: HoodieActivity,
        visual: VisualState,
    ): PresentedHoodieContext {
        val candidate = PresentedHoodieContext(event?.id, event?.type ?: snapshotContext, event?.placeId,
            event?.venueType, activity, visual.scene, 0, false, event == null || event.type == snapshotContext)
        val baseVisual = visual.copy(presentationRevision = 0)
        if (previous?.copy(visualRevision = 0, isReconciled = false) != candidate || previousVisual != baseVisual) {
            nextRevision++
            previous = candidate.copy(visualRevision = nextRevision)
            previousVisual = baseVisual
        }
        return requireNotNull(previous).presented(presentedRevision)
    }
}

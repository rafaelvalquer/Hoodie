package com.hoodie.app.presentation

import com.hoodie.app.core.model.*
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.presentation.screens.home.PresentedHoodieContextTracker
import org.junit.Assert.*
import org.junit.Test

internal fun presentationEvent(id: Long = 1, type: UserContextType = UserContextType.WORK,
    place: Long = 10, venue: PlaceType? = null) = ContextEvent(id, type, 100, null, 1f, place, ContextSource.USER_CORRECTION, venue)
internal fun presentationVisual(scene: SceneId = SceneId.OFFICE, place: Long = 10) =
    VisualState(scene, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1)), placeId = place)

class PresentedHoodieContextTest {
    @Test fun `version remains stable and reconciliation requires the actual presented revision`() {
        val t = PresentedHoodieContextTracker()
        val first = t.select(presentationEvent(), UserContextType.WORK, HoodieActivity.WORKING, presentationVisual())
        assertFalse(first.isReconciled)
        assertFalse(t.acknowledge(first.visualRevision + 1))
        assertTrue(t.acknowledge(first.visualRevision))
        val current = t.select(presentationEvent(), UserContextType.WORK, HoodieActivity.WORKING, presentationVisual())
        assertEquals(first.visualRevision, current.visualRevision)
        assertTrue(current.isReconciled)
        assertTrue(current.accepts(presentationVisual().copy(presentationRevision = current.visualRevision)))
        t.resetPresentation()
        assertFalse(t.select(presentationEvent(), UserContextType.WORK, HoodieActivity.WORKING, presentationVisual()).isReconciled)
    }
    @Test fun `manual context wins but stale simulator state cannot receive gestures`() {
        val t = PresentedHoodieContextTracker()
        val event = presentationEvent(type = UserContextType.LUNCH, venue = PlaceType.RESTAURANT)
        val visual = presentationVisual(SceneId.RESTAURANT)
        val pending = t.select(event, UserContextType.WORK, HoodieActivity.WORKING, visual)
        t.acknowledge(pending.visualRevision)
        val stale = t.select(event, UserContextType.WORK, HoodieActivity.WORKING, visual)
        assertEquals(UserContextType.LUNCH, stale.contextType)
        assertFalse(stale.isReconciled)
        val reconciled = t.select(event, UserContextType.LUNCH, HoodieActivity.EATING, visual)
        assertTrue(reconciled.visualRevision > stale.visualRevision)
        t.acknowledge(reconciled.visualRevision)
        assertTrue(t.select(event, UserContextType.LUNCH, HoodieActivity.EATING, visual).isReconciled)
    }
}

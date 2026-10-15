package com.hoodie.app.presentation

import com.hoodie.app.core.model.*
import com.hoodie.app.engine.reaction.*
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.presentation.screens.home.PresentedHoodieContextTracker
import org.junit.Assert.*
import org.junit.Test

class InteractionPlaceChangeTest {
    @Test fun `restaurant A to B cancels old reaction even with the same scene and activity`() {
        val t = PresentedHoodieContextTracker()
        val a = t.select(presentationEvent(1, UserContextType.LUNCH, 10), UserContextType.LUNCH,
            HoodieActivity.EATING, presentationVisual(SceneId.RESTAURANT))
        val c = HoodieReactionCoordinator()
        c.context(a.sceneId, a.visualRevision, true, 0)
        c.request(ReactionIntent("pet", ReactionSource.USER_INTERACTION, a.sceneId, a.visualRevision, 1, 2_001), 1)
        val b = t.select(presentationEvent(2, UserContextType.LUNCH, 20), UserContextType.LUNCH,
            HoodieActivity.EATING, presentationVisual(SceneId.RESTAURANT, 20))
        assertNotEquals(a.visualRevision, b.visualRevision)
        c.context(b.sceneId, b.visualRevision, false, 2)
        assertNull(c.dispatch(3, true))
        assertFalse(c.started("pet", 3))
        assertTrue(c.lifecycle.any { it.id == "pet" && it.state == ReactionState.CANCELLED })
        assertFalse(b.accepts(presentationVisual(SceneId.RESTAURANT).copy(presentationRevision = a.visualRevision)))
    }
}

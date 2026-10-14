package com.hoodie.app.engine.interaction

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractionSessionTest {
    private val response = InteractionResponse("pet", listOf(AnimationId.PET_HEAD), "Gostei!", 3_500)

    @Test fun `pending and completed responses never survive a new Home session`() {
        val session = HoodieInteractionSession()
        assertNull(session.reserve(response, SceneId.HOME, 0))
        session.start()
        val old = session.reserve(response, SceneId.HOME, 1_000)!!
        assertNull(session.reserve(response, SceneId.HOME, 1_001))
        session.stop()
        session.start()
        assertFalse(session.current(old))
        assertFalse(session.started(old, 10_000))
        assertTrue(session.history.recentPhrases.isEmpty())
        val fresh = session.reserve(response, SceneId.HOME, 20_000)!!
        assertTrue(session.started(fresh, 20_000))
        assertFalse(session.started(fresh, 20_001))
        session.finish(old)
        assertTrue(session.current(fresh))
        session.finish(fresh)
        assertFalse(session.current(fresh))
        assertEquals(listOf("Gostei!"), session.history.recentPhrases)
    }

    @Test fun `cancelled reservations keep gesture cooldown but do not consume speech`() {
        val session = HoodieInteractionSession()
        session.start()
        val pending = session.reserve(response, SceneId.HOME, 1_000)!!
        session.cancelPending()
        assertFalse(session.current(pending))
        assertEquals(1_000L, session.history.lastGestureAt)
        assertNull(session.history.lastSpeechAt)
        assertTrue(HoodieInteractionPolicy().evaluate(InteractionContext(SceneId.HOME), InteractionGesture.TAP, session.history, 1_100) is InteractionDecision.Cooldown)
    }
}

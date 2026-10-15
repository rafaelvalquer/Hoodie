package com.hoodie.app.engine.reaction

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test

internal fun intent(id: String, source: ReactionSource, revision: Long = 1, at: Long = 10) =
    ReactionIntent(id, source, SceneId.HOME, revision, at, at + 2_000,
        listOf(AnimationId.SMILE), "Oi!", durationMs = 3_500)

class HoodieReactionCoordinatorTest {
    @Test fun `user replaces unpresented personality but never overlaps a started clip`() {
        val c = HoodieReactionCoordinator()
        c.context(SceneId.HOME, 1, true, 0)
        val spontaneous = intent("personality", ReactionSource.PERSONALITY_REACTION)
        val user = intent("touch", ReactionSource.USER_INTERACTION)
        assertTrue(c.request(spontaneous, 10))
        assertEquals(spontaneous, c.dispatch(11, true))
        assertTrue(c.request(user, 12))
        assertFalse(c.current(spontaneous.id, 12))
        assertFalse(c.started(spontaneous.id, 12))
        assertEquals(user, c.dispatch(13, true))
        assertTrue(c.started(user.id, 14))
        assertFalse(c.started(user.id, 15))
        assertTrue(c.request(intent("ambient", ReactionSource.AMBIENT_SPEECH), 16))
        assertNull(c.dispatch(17, true))
        assertEquals(user.id, c.presentation!!.id)
    }

    @Test fun `higher priority waits for essential animation or Hoodie social attention`() {
        val c = HoodieReactionCoordinator()
        c.context(SceneId.HOME, 1, true, 0)
        val user = intent("touch", ReactionSource.USER_INTERACTION)
        assertTrue(c.request(user, 10))
        assertNull(c.dispatch(20, true, essential = true))
        assertNull(c.dispatch(21, true, socialBusy = true))
        assertNull(c.presentation)
        assertEquals(user, c.dispatch(22, true))
    }

    @Test fun `user takes priority when lower animation finishes without waiting for its speech`() {
        val c = HoodieReactionCoordinator()
        c.context(SceneId.HOME, 1, true, 0)
        c.request(intent("personal", ReactionSource.PERSONALITY_REACTION), 10)
        c.dispatch(11, true); c.started("personal", 12)
        c.request(intent("touch", ReactionSource.USER_INTERACTION), 20)
        assertNull(c.dispatch(21, true))
        c.animationFinished("personal", 22)
        assertEquals("touch", c.dispatch(23, true)!!.id)
        assertNull(c.presentation)
    }
}

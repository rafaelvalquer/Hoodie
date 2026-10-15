package com.hoodie.app.engine.reaction

import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test

class ReactionLifecycleTest {
    @Test fun `expiry cancellation and context revision never count as presentation`() {
        for (cancel in listOf(false, true)) {
            val c = HoodieReactionCoordinator()
            c.context(SceneId.HOME, 1, true, 0)
            c.request(intent("old", ReactionSource.USER_INTERACTION), 10)
            c.dispatch(11, safe = false)
            if (cancel) c.context(SceneId.HOME, 2, true, 12) else c.expire(2_010)
            assertFalse(c.started("old", 2_011))
            assertTrue(c.lifecycle.none { it.state == ReactionState.STARTED })
            assertTrue(c.lifecycle.any { it.state == if (cancel) ReactionState.CANCELLED else ReactionState.EXPIRED })
            assertNull(c.presentation)
        }
    }
    @Test fun `actual start and finish are idempotent and diagnostics stay bounded`() {
        val c = HoodieReactionCoordinator()
        c.context(SceneId.HOME, 1, true, 0)
        repeat(40) { index ->
            val at = index * 5_000L + 10
            val request = intent("$index", ReactionSource.AMBIENT_SPEECH, at = at)
            c.request(request, at); c.dispatch(at, true)
            assertNull(c.presentation)
            assertTrue(c.started(request.id, at + 1))
            assertFalse(c.started(request.id, at + 2))
            c.expire(at + 3_501)
            assertNull(c.presentation)
        }
        assertEquals(64, c.lifecycle.size)
        assertTrue(c.lifecycle.all { it.reason.isNotBlank() })
    }
}

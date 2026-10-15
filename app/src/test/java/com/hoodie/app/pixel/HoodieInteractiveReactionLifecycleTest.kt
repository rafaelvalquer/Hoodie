package com.hoodie.app.pixel

import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.scene.*
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractiveReactionLifecycleTest {
    private val visual = VisualState(SceneId.HOME, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1)))
    private val response = InteractionResponse("pet", listOf(AnimationId.PET_HEAD), null, 0)

    @Test fun `cancelling interactive pending does not change the existing automatic path`() {
        val resolver = ReactionResolver()
        resolver.request(listOf(AnimationId.WAVE), 100)
        assertFalse(resolver.requestInteractive(response, visual, AnimationId.IDLE, 100, 200))
        resolver.cancelInteractive()
        assertNotNull(resolver.takeReady(100))
    }

    @Test fun `expiry visual action and explicit cancellation discard only the interactive request`() {
        for (reason in 0..3) {
            val resolver = ReactionResolver()
            assertTrue(resolver.requestInteractive(response, visual, AnimationId.IDLE, 100, 200))
            assertNull(resolver.takeInteractiveReady(50, visual, AnimationId.IDLE, true))
            if (reason == 3) resolver.cancelInteractive()
            val result = when (reason) {
                0 -> resolver.takeInteractiveReady(201, visual, AnimationId.IDLE, true)
                1 -> resolver.takeInteractiveReady(100, visual.copy(tvOn = true), AnimationId.IDLE, true)
                2 -> resolver.takeInteractiveReady(100, visual, AnimationId.WATCH_TV, true)
                else -> resolver.takeInteractiveReady(100, visual, AnimationId.IDLE, true)
            }
            assertNull(result)
            assertFalse(resolver.hasInteractivePending)
        }
    }

    @Test fun `deadline is exclusive even when safe boundary arrives at expiry`() {
        val resolver = ReactionResolver()
        assertTrue(resolver.requestInteractive(response, visual, AnimationId.IDLE, 200, 200))
        assertNull(resolver.takeInteractiveReady(200, visual, AnimationId.IDLE, true))
        assertFalse(resolver.hasInteractivePending)
    }
}

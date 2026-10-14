package com.hoodie.app.engine.interaction

import com.hoodie.app.pixel.animation.AnimationId
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractionHistoryTest {
    @Test fun `last three phrases survive silent reactions and clear with home session`() {
        val h = HoodieInteractionHistory()
        repeat(4) {
            h.acceptGesture(it * 10_000L)
            h.remember(InteractionResponse("$it", listOf(AnimationId.SMILE), "phrase$it", 3_500), it * 10_000L)
        }
        assertEquals(listOf("phrase1", "phrase2", "phrase3"), h.recentPhrases)
        h.acceptGesture(31_000)
        h.remember(InteractionResponse("repeat", listOf(AnimationId.GLANCE), null, 0), 31_000)
        assertFalse(h.canSpeak(39_999))
        assertTrue(h.canSpeak(40_000))
        assertEquals(3, h.recentPhrases.size)
        h.clear()
        assertTrue(h.canSpeak(0))
        assertNull(h.lastGestureAt)
        assertTrue(h.recentPhrases.isEmpty())
    }
}

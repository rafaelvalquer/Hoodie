package com.hoodie.app.engine.reaction

import com.hoodie.app.engine.personality.ReactionPriority
import org.junit.Assert.*
import org.junit.Test

class ReactionPriorityTest {
    @Test fun `complete priority hierarchy is strict and source cannot promote itself`() {
        val order = listOf(ReactionSource.AMBIENT_SPEECH, ReactionSource.PERSONALITY_REACTION,
            ReactionSource.SOCIAL_EVENT, ReactionSource.USER_INTERACTION,
            ReactionSource.ESSENTIAL_ANIMATION, ReactionSource.SAFETY_TRANSITION)
        assertTrue(order.zipWithNext().all { (a, b) -> a.priority.rank < b.priority.rank })
        assertTrue(runCatching { intent("bad", ReactionSource.AMBIENT_SPEECH)
            .copy(priority = ReactionPriority.SAFETY_TRANSITION) }.isFailure)
    }
}

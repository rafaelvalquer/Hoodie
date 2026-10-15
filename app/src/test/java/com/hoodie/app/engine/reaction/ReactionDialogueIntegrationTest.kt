package com.hoodie.app.engine.reaction

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.scene.*
import org.junit.Assert.*
import org.junit.Test

class ReactionDialogueIntegrationTest {
    @Test fun `presentation revision alone preserves the original animation cursor and props`() {
        val visual = VisualState(SceneId.OFFICE, SpotId.DESK,
            listOf(MicroAction(AnimationId.COFFEE_SIT, 1, 60_000, 60_000)),
            steadyFlags = setOf(SceneFlag.MUG_IN_HAND), presentationRevision = 1)
        val changed = AnimationStateMachine(kotlin.random.Random(1))
        val control = AnimationStateMachine(kotlin.random.Random(1))
        for (m in listOf(changed, control)) { m.place(visual, 1); m.frame(702, 600, DayPeriod.DAY) }
        changed.setVisual(visual.copy(presentationRevision = 2), 703)
        for (at in listOf(704L, 999L, 1_500L)) {
            val actual = changed.frame(at, 600, DayPeriod.DAY)!!
            val expected = control.frame(at, 600, DayPeriod.DAY)!!
            assertEquals(expected.animation, actual.animation)
            assertEquals(expected.frameIndex, actual.frameIndex)
            assertEquals(expected.env.flags, actual.env.flags)
            assertEquals(expected.x, actual.x); assertEquals(expected.y, actual.y)
            assertEquals(2L, actual.interactionVisual!!.presentationRevision)
            assertEquals(AnimationStateMachine.Phase.LOOP, changed.phase)
        }
    }
    @Test fun `speech only waits for safe presentation without freezing or replacing coffee sprite`() {
        val m = AnimationStateMachine()
        val visual = VisualState(SceneId.OFFICE, SpotId.DESK,
            listOf(MicroAction(AnimationId.COFFEE_SIT, 1, 60_000, 60_000)), steadyFlags = setOf(SceneFlag.MUG_IN_HAND))
        m.place(visual, 1)
        val before = m.frame(702, 600, DayPeriod.DAY)!!
        val c = HoodieReactionCoordinator()
        c.context(SceneId.OFFICE, 1, true, 700)
        val intent = ReactionIntent("speech", ReactionSource.PERSONALITY_REACTION, SceneId.OFFICE, 1,
            703, 2_703, dialogue = "Só uma pausa.", durationMs = 6_000)
        c.request(intent, 703); c.dispatch(703, true)
        val response = InteractionResponse(intent.id, emptyList(), intent.dialogue, 6_000,
            presentationOnly = true, source = intent.source)
        assertTrue(m.interact(response, SceneId.OFFICE, 703))
        assertNull(c.presentation)
        var starts = 0
        val frames = (704L..4_000L step 20).map { at ->
            m.frame(at, 600, DayPeriod.DAY)!!.also { f ->
                if (f.interactionStarted != null) {
                    starts++
                    assertTrue(at >= 2_101)
                    assertTrue(c.started(intent.id, at))
                }
            }
        }
        assertEquals(1, starts)
        assertEquals(intent.dialogue, c.presentation!!.text)
        assertTrue(frames.all { it.animation == AnimationId.COFFEE_SIT && it.env.flags == before.env.flags })
        assertTrue(frames.map { it.frameIndex }.distinct().size > 1)
    }
}

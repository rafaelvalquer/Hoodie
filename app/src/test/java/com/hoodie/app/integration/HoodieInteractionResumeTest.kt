package com.hoodie.app.integration

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.scene.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HoodieInteractionResumeTest {
    private val response = InteractionResponse("typing", listOf(AnimationId.GLANCE, AnimationId.SMILE), "Só mais esse e-mail!", 3_500)
    private fun machine(scene: SceneId, animation: AnimationId) = AnimationStateMachine(Random(1)).also {
        it.place(VisualState(scene, SpotId.CENTER, listOf(MicroAction(animation, 1, 60_000, 60_000))), 1)
        it.frame(2, 600, DayPeriod.DAY)
    }
    @Test fun `typing respects boundary resumes same action and ignores excess`() {
        val m = machine(SceneId.OFFICE, AnimationId.WORK_TYPING)
        assertTrue(m.interact(response, SceneId.OFFICE, 3))
        assertFalse(m.interact(response, SceneId.OFFICE, 4))
        var starts = 0
        for (t in 5L..8_000L step 20) {
            val frame = m.frame(t, 600, DayPeriod.DAY)!!
            if (frame.interactionStarted != null) starts++
        }
        assertEquals(1, starts)
        assertEquals(AnimationId.WORK_TYPING, m.animation)
        assertEquals(AnimationStateMachine.Phase.LOOP, m.phase)
    }
    @Test fun `scene change and explicit Home cancellation discard pending reaction`() {
        for (changeScene in listOf(true, false)) {
            val m = machine(SceneId.RESTAURANT, AnimationId.EAT)
            assertTrue(m.interact(response, SceneId.RESTAURANT, 3))
            if (changeScene) m.setVisual(VisualState(SceneId.HOME, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1))), 4)
            else m.cancelInteractiveReactions()
            val frames = (5L..8_000L step 20).mapNotNull { m.frame(it, 600, DayPeriod.DAY) }
            assertTrue(frames.none { it.interactionStarted != null })
        }
    }
    @Test fun `sleep and obsolete scene cannot receive engine reactions`() {
        val m = machine(SceneId.HOME, AnimationId.SLEEP)
        assertFalse(m.interact(response, SceneId.HOME, 3))
        assertFalse(m.interact(response, SceneId.OFFICE, 3))
    }

    @Test fun `delivery preserves remaining deadline instead of granting another waiting window`() {
        val m = machine(SceneId.OFFICE, AnimationId.WORK_TYPING)
        assertFalse(m.interact(response, SceneId.OFFICE, 3, maxPendingMs = 0))
        assertTrue(m.interact(response, SceneId.OFFICE, 3, maxPendingMs = 25))
        val expired = m.frame(28, 600, DayPeriod.DAY)!!
        assertNull(expired.interactionStarted)
        assertFalse(expired.interactionBusy)
        assertEquals(AnimationId.WORK_TYPING, expired.animation)
    }

    @Test fun `coffee with a safe boundary beyond two seconds expires without interrupting the mug`() {
        val m = machine(SceneId.OFFICE, AnimationId.COFFEE_SIT)
        val before = m.frame(2, 600, DayPeriod.DAY)!!
        assertTrue(m.interact(response, SceneId.OFFICE, 3))
        val frames = (4L..4_000L step 20).map { m.frame(it, 600, DayPeriod.DAY)!! }
        assertTrue(frames.none { it.interactionStarted != null || it.animation in response.animations })
        assertTrue(frames.all { it.x == before.x && it.y == before.y && it.env.flags == before.env.flags })
        assertEquals(AnimationId.COFFEE_SIT, frames.last().animation)
        assertFalse(frames.last().interactionBusy)
    }

    @Test fun `interaction never replays page or food completion events`() {
        for ((scene, animation, flag) in listOf(
            Triple(SceneId.SCHOOL, AnimationId.STUDY_PAGE_TURN, SceneFlag.PAGE_TURNED),
            Triple(SceneId.RESTAURANT, AnimationId.FINISH_FOOD, SceneFlag.FOOD_DONE),
        )) {
            val m = machine(scene, animation)
            assertTrue(m.interact(response, scene, 3))
            var started: RenderFrame? = null
            var after: RenderFrame? = null
            var previous = false
            var changes = 0
            for (t in 4L..8_000L step 20) {
                val frame = m.frame(t, 600, DayPeriod.DAY)!!
                if (frame.interactionStarted != null) started = frame
                val present = flag in frame.env.flags
                if (present != previous) changes++
                previous = present
                after = frame
            }
            assertNotNull(started)
            assertEquals("$animation event changes", 1, changes)
            assertTrue(flag in after!!.env.flags)
        }
    }
}

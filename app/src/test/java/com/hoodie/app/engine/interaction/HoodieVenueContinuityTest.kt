package com.hoodie.app.engine.interaction

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.scene.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HoodieVenueContinuityTest {
    @Test fun `venue reactions wait for safe cycles and preserve objects position and original cursor`() {
        val cases = listOf(
            Triple(SceneId.BAKERY, AnimationId.BAKERY_EAT_PASTRY, SceneFlag.PASTRY_IN_HAND),
            Triple(SceneId.BAKERY, AnimationId.COFFEE_SIT, SceneFlag.MUG_IN_HAND),
            Triple(SceneId.CINEMA, AnimationId.CINEMA_EAT_POPCORN, SceneFlag.POPCORN_HELD),
            Triple(SceneId.PHARMACY, AnimationId.PHARMACY_EXAMINE, SceneFlag.ITEM_HELD),
            Triple(SceneId.HOTEL, AnimationId.HOTEL_CHECK_IN, SceneFlag.SUITCASE_HELD),
            Triple(SceneId.HOTEL, AnimationId.HOTEL_ELEVATOR, SceneFlag.SUITCASE_HELD),
        )
        for ((scene, animation, flag) in cases) {
            val machine = AnimationStateMachine(Random(9))
            val visual = VisualState(scene, SpotId.CENTER, listOf(MicroAction(animation, 1, 60_000, 60_000)), steadyFlags = setOf(flag))
            machine.place(visual, 1)
            val requestAt = (animation.durationMs - 600).coerceAtLeast(100) + 1
            for (at in 1L..requestAt step 20) machine.frame(at, 600, DayPeriod.DAY)
            val before = machine.frame(requestAt, 600, DayPeriod.DAY)!!
            val response = InteractionResponse("venue", listOf(AnimationId.GLANCE, AnimationId.SMILE), "Uma pausa.", 3_000)
            assertTrue("$scene/$animation request", machine.interact(response, scene, requestAt + 1))
            var startedAt: Long? = null
            var flagsAtStart: Set<SceneFlag>? = null
            var frozenIndex: Int? = null
            var overlayFrames = 0
            for (at in requestAt + 2..requestAt + 5_000 step 20) {
                val frame = machine.frame(at, 600, DayPeriod.DAY)!!
                if (frame.interactionStarted === response) {
                    assertNull("$scene starts twice", startedAt)
                    startedAt = at
                    flagsAtStart = frame.env.flags
                    frozenIndex = frame.frameIndex
                    if (animation.clip.interruptPolicy == InterruptPolicy.FINISH_CYCLE) assertTrue("$animation started before cycle end", at >= animation.durationMs + 1)
                }
                if (frame.interactionBusy && startedAt != null) {
                    overlayFrames++
                    assertEquals("$animation flags during overlay", flagsAtStart, frame.env.flags)
                    assertEquals("$animation cursor during overlay", frozenIndex, frame.frameIndex)
                    assertEquals(before.x, frame.x)
                    assertEquals(before.y, frame.y)
                }
            }
            assertNotNull("$animation never started", startedAt)
            assertTrue("$animation no overlay", overlayFrames > 0)
            assertFalse(machine.frame(requestAt + 5_100, 600, DayPeriod.DAY)!!.interactionBusy)
        }
    }
}

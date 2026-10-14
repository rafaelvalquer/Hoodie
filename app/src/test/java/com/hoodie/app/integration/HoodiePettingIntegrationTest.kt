package com.hoodie.app.integration

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.pixel.sprite.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HoodiePettingIntegrationTest {
    private val pet = InteractionResponse("pet", listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE), "Hmmm...", 3_500, 3_000)

    @Test fun `petting keeps seat orientation position and all scene props`() {
        val cases = listOf(
            Triple(SceneId.HOME, AnimationId.IDLE, emptySet()),
            Triple(SceneId.OFFICE, AnimationId.COFFEE_SIT, setOf(SceneFlag.MUG_IN_HAND)),
            Triple(SceneId.RESTAURANT, AnimationId.WAIT_FOOD, setOf(SceneFlag.FOOD_SERVED)),
            Triple(SceneId.STORE, AnimationId.STORE_HOLD_GARMENT, setOf(SceneFlag.GARMENT_HELD)),
        )
        for ((scene, animation, flags) in cases) {
            val machine = AnimationStateMachine(Random(2))
            machine.place(VisualState(scene, SpotId.CENTER, listOf(MicroAction(animation, 1, 60_000, 60_000)), steadyFlags = flags), 1)
            val before = machine.frame(2, 600, DayPeriod.DAY)!!
            val posture = machine.posture
            assertTrue(machine.interact(pet, scene, 3))
            val reacted = (4L..8_000L step 20).map { machine.frame(it, 600, DayPeriod.DAY)!! }
            assertTrue(reacted.any { it.interactionStarted == pet })
            assertEquals(1, reacted.count { it.interactionStarted == pet })
            assertTrue(reacted.all { it.x == before.x && it.y == before.y })
            assertTrue(reacted.all { it.env.flags.containsAll(flags) })
            assertEquals(posture, machine.posture)
            assertEquals(animation, reacted.last().animation)
        }
    }

    @Test fun `new clips inherit standing seated and front seated without events`() {
        for (posture in Posture.entries) for (id in pet.animations) {
            val expected = if (posture == Posture.STANDING) Legs.STAND else Legs.SIT
            for (index in id.frames.indices) {
                val pose = ProceduralSpriteProvider.poseFor(SpriteRequest(id, Direction.FRONT, index, posture))
                assertEquals(expected, pose.legs)
                if (posture == Posture.SIT_FRONT) assertEquals(Posture.SIT_FRONT, pose.posture)
                assertTrue(id.frames[index].events.isEmpty())
            }
        }
    }
}

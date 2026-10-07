package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.restaurant.RestaurantNavigationGraph
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcIntent
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcSpot
import com.hoodie.app.pixel.npc.restaurant.RestaurantMealState
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.sprite.Facing
import org.junit.Assert.*
import org.junit.Test

class RestaurantSeatedOrientationTest {
    @Test fun everyDiningSeatUsesInteractionFacingFront() {
        listOf(RestaurantNpcSpot.TABLE_A_SEAT_LEFT, RestaurantNpcSpot.TABLE_A_SEAT_RIGHT,
            RestaurantNpcSpot.TABLE_B_SEAT_LEFT, RestaurantNpcSpot.TABLE_B_SEAT_RIGHT).forEach {
            assertEquals(Facing.FRONT, RestaurantNavigationGraph.spots.getValue(it).interactionFacing)
        }
        listOf(RestaurantNpcSpot.COUNTER, RestaurantNpcSpot.WINDOW, RestaurantNpcSpot.AISLE,
            RestaurantNpcSpot.DOOR).forEach { assertEquals(Facing.SIDE, RestaurantNavigationGraph.spots.getValue(it).interactionFacing) }
    }

    @Test fun guestIsFrontWhileSeatedAndTurnsSideBeforeLeavingTheChair() {
        val seatedAnimations = mapOf(
            RestaurantNpcIntent.EAT to NpcAnimation.SIT_EAT,
            RestaurantNpcIntent.DRINK to NpcAnimation.SIT_DRINK,
            RestaurantNpcIntent.CHECK_PHONE to NpcAnimation.SIT_PHONE,
            RestaurantNpcIntent.LOOK_AROUND to NpcAnimation.SIT_LOOK,
            RestaurantNpcIntent.LOOK_WINDOW to NpcAnimation.SIT_LOOK,
            RestaurantNpcIntent.READ_MENU to NpcAnimation.SIT_READ_MENU,
            RestaurantNpcIntent.TALK to NpcAnimation.TALK,
            RestaurantNpcIntent.IDLE to NpcAnimation.IDLE,
            RestaurantNpcIntent.WAIT_FOOD to NpcAnimation.IDLE,
        )
        val seen = mutableSetOf<RestaurantNpcIntent>()
        for (seed in 1..12) {
            val seededEnv = SceneEnv(DayPeriod.DAY, 12 * 60, variant = seed, daySeed = 4)
            val seededSlot = RestaurantNpcDirector.plan(seededEnv).single()
            val seededBrain = seededSlot.restaurantBrain!!
            for (time in 0L..420_000L step 500) {
                val state = seededBrain.stateAt(time)
                val movement = seededBrain.movementAt(time, state)
                if (state.currentIntent in seatedAnimations) {
                    seen += state.currentIntent
                    assertEquals("$seed/${state.currentIntent}", seatedAnimations.getValue(state.currentIntent), movement.animation)
                    assertEquals(Facing.FRONT, NpcMotionController.frame(seededSlot, time, movement).pose.facing)
                }
                if (state.currentIntent == RestaurantNpcIntent.ENTER && movement.seated) {
                    assertEquals(Facing.FRONT, NpcMotionController.frame(seededSlot, time, movement).pose.facing)
                }
                if (state.currentIntent == RestaurantNpcIntent.ENTER && movement.animation == NpcAnimation.TURN_LEFT) {
                    val expectedFacing = if (movement.localTimeMs < 240L) Facing.SIDE else Facing.FRONT
                    assertEquals(expectedFacing, NpcMotionController.frame(seededSlot, time, movement).pose.facing)
                }
                if (state.currentIntent == RestaurantNpcIntent.LEAVE && movement.animation == NpcAnimation.STAND_UP) {
                    assertEquals(Facing.FRONT, NpcMotionController.frame(seededSlot, time, movement).pose.facing)
                }
                if (state.currentIntent == RestaurantNpcIntent.LEAVE && state.currentSpot != RestaurantNpcSpot.DOOR &&
                    state.mealState != RestaurantMealState.WAITING && movement.animation == NpcAnimation.TURN_RIGHT) {
                    val expectedFacing = if (movement.localTimeMs < 240L) Facing.FRONT else Facing.SIDE
                    assertEquals("$seed/$time/${movement.localTimeMs}", expectedFacing,
                        NpcMotionController.frame(seededSlot, time, movement).pose.facing)
                }
                if (movement.animation == NpcAnimation.WALK) assertEquals(Facing.SIDE, NpcMotionController.frame(seededSlot, time, movement).pose.facing)
            }
        }
        val menuSample = (1..64).firstNotNullOfOrNull { seed ->
            val candidate = RestaurantNpcDirector.brain(SceneEnv(DayPeriod.DAY, 12 * 60, variant = seed, daySeed = 4))
            (0L..60_000L step 500).firstOrNull { candidate.stateAt(it).currentIntent == RestaurantNpcIntent.READ_MENU }
        }
        assertNotNull("guest should sometimes read the menu", menuSample)
        assertTrue("visited intents $seen", seen.containsAll(seatedAnimations.keys - RestaurantNpcIntent.READ_MENU))
    }
}

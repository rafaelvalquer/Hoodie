package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.office.OfficeNavigationGraph
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.office.OfficeNpcSpot
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.sprite.Facing
import org.junit.Assert.*
import org.junit.Test

class OfficeSeatedOrientationTest {
    @Test fun desksFaceForwardAndWalkingDestinationsStaySideOn() {
        assertEquals(Facing.FRONT, OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DESK_LEFT).interactionFacing)
        assertEquals(Facing.FRONT, OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DESK_RIGHT).interactionFacing)
        listOf(OfficeNpcSpot.COFFEE, OfficeNpcSpot.WINDOW, OfficeNpcSpot.PRINTER, OfficeNpcSpot.WHITEBOARD,
            OfficeNpcSpot.CENTER, OfficeNpcSpot.DOOR).forEach { assertEquals(Facing.SIDE, OfficeNavigationGraph.spots.getValue(it).interactionFacing) }
        val slots = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = 21))
        slots.filter { it.definition.id != "bulldog_exec" }.forEach { slot ->
            val desk = slot.officeBrain!!.movementAt(0)
            assertEquals(NpcAnimation.TYPE, desk.animation)
            assertEquals(Facing.FRONT, NpcMotionController.frame(slot, 0, desk).pose.facing)
            val walkingAt = (0L..180_000L step 500).firstOrNull { slot.officeBrain.movementAt(it).animation == NpcAnimation.WALK }
            assertNotNull("${slot.definition.id} should leave its desk during the sample", walkingAt)
            assertEquals(Facing.SIDE, NpcMotionController.frame(slot, walkingAt!!).pose.facing)
            val movements = (0L..180_000L step 500).map { it to slot.officeBrain.movementAt(it) }
            val standUp = movements.firstOrNull { it.second.animation == NpcAnimation.STAND_UP }
            assertNotNull("${slot.definition.id} should stand up from FRONT", standUp)
            assertEquals(Facing.FRONT, NpcMotionController.frame(slot, standUp!!.first, standUp.second).pose.facing)
            val turnOut = movements.firstOrNull { it.second.animation == NpcAnimation.TURN_RIGHT }
            assertNotNull("${slot.definition.id} should turn before walking", turnOut)
            val turnOutPoseFacing = if (turnOut!!.second.localTimeMs < 240L) Facing.FRONT else Facing.SIDE
            assertEquals(turnOutPoseFacing, NpcMotionController.frame(slot, turnOut.first, turnOut.second).pose.facing)
            val turnIn = movements.firstOrNull { it.second.animation == NpcAnimation.TURN_LEFT }
            if (turnIn != null) {
                val turnInPoseFacing = if (turnIn.second.localTimeMs < 240L) Facing.SIDE else Facing.FRONT
                assertEquals(turnInPoseFacing, NpcMotionController.frame(slot, turnIn.first, turnIn.second).pose.facing)
            }
        }
    }

    @Test fun frontDeskIncludesChairAndKeyboardLayers() {
        val scene = com.hoodie.app.pixel.scene.OfficeScene()
        val baselines = scene.sortedProps.map { it.baseline }
        assertTrue(baselines.any { it == 192 })
        assertTrue(baselines.count { it == 208 } >= 2)
    }
}

package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.office.OfficeNpcSpot
import com.hoodie.app.pixel.scene.SceneEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeSocialReturnTest {
    @Test fun colleaguesCompletePhysicalReturnBeforeBaseRoutineResumes() {
        val colleagues = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = 42))
            .mapNotNull { it.officeBrain }
            .filter { it.npcId in setOf("rabbit_analyst", "cat_colleague") }
        val eventEnd = (0L..600_000L step 250).firstNotNullOfOrNull { time ->
            val states = colleagues.map { it.stateAt(time) }
            states.firstOrNull()?.takeIf {
                states.all { it.currentIntent.name == "SOCIALIZE" && it.nextDecisionAt == states.first().nextDecisionAt }
            }?.nextDecisionAt
        } ?: error("No coordinated office conversation was scheduled")

        for (brain in colleagues) {
            val home = if (brain.npcId == "rabbit_analyst") OfficeNpcSpot.DESK_LEFT else OfficeNpcSpot.DESK_RIGHT
            val meeting = if (brain.npcId == "rabbit_analyst") OfficeNpcSpot.CENTER else OfficeNpcSpot.WHITEBOARD
            for (time in (eventEnd - 25_000L).coerceAtLeast(0L)..eventEnd step 250L) {
                val state = brain.stateAt(time)
                if (state.currentIntent.name != "SOCIALIZE") continue
                if (state.targetSpot == meeting) {
                    assertTrue(state.currentSpot == home || state.currentSpot == meeting)
                } else {
                    assertEquals(home, state.targetSpot)
                    assertEquals(meeting, state.currentSpot)
                }
            }
            val atReturn = brain.movementAt(eventEnd - 1)
            val desk = com.hoodie.app.pixel.npc.office.OfficeNavigationGraph.spots.getValue(home)
            assertTrue("${brain.npcId} should return to $home before ${eventEnd} ms",
                maxOf(kotlin.math.abs(atReturn.x - desk.x), kotlin.math.abs(atReturn.floorY - desk.floorY)) <= 2)
            assertTrue("${brain.npcId} should sit before resuming work", atReturn.seated || atReturn.animation == NpcAnimation.SIT)
            val resumed = brain.movementAt(eventEnd)
            assertEquals(desk.x, resumed.x)
        }
    }
}

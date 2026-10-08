package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.office.OfficeNpcSpot
import com.hoodie.app.pixel.npc.office.OfficeExecutiveTimeline
import com.hoodie.app.pixel.scene.OfficeScene
import com.hoodie.app.pixel.scene.SceneEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeTimelineRegressionTest {
    private fun env(seed: Int = 42) = SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed, daySeed = seed)

    @Test fun actualOfficeSceneBulldogSpeechMatchesTalkAndVisitVisibility() {
        val slot = OfficeScene().ambientNpcs(env()).single { it.definition.id == "bulldog_exec" }
        val brain = slot.officeBrain!!
        val speech = brain.frameStateAt(10_000).speech

        assertNotNull("the executive in the real scene must have a speech event", speech)
        assertEquals(OfficeExecutiveTimeline.ENTRY_MS + 2_500L, speech!!.startedAt)
        assertEquals(1_800L, speech.durationMs)
        assertEquals(NpcAnimation.TALK, brain.frameStateAt(speech.startedAt + 500).animation)
        assertTrue(brain.frameStateAt(speech.startedAt + speech.durationMs).speech == null)
        assertNull("speech must be absent once the executive is offscreen", brain.frameStateAt(66_100).speech)
        assertTrue(brain.movementAt(66_100).x < 0)
        assertNotNull("director used by OfficeScene must configure a speech profile", slot.definition.speechProfile)
    }

    @Test fun executiveWalksFromItsLastActivityToDoorAndContinuesAcrossExitBoundary() {
        val brain = OfficeScene().ambientNpcs(env()).single { it.definition.id == "bulldog_exec" }.officeBrain!!
        val lastActivity = brain.movementAt(OfficeExecutiveTimeline.RETURN_START_MS)
        val beforeDoor = brain.movementAt(OfficeExecutiveTimeline.DOOR_OPEN_MS - 1)
        val atDoor = brain.movementAt(OfficeExecutiveTimeline.DOOR_OPEN_MS)
        val door = com.hoodie.app.pixel.npc.office.OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DOOR)

        assertTrue(lastActivity.x != door.x || lastActivity.floorY != door.floorY)
        assertTrue(kotlin.math.abs(door.x - beforeDoor.x) <= 1)
        assertTrue(kotlin.math.abs(door.floorY - beforeDoor.floorY) <= 1)
        assertTrue(kotlin.math.abs(beforeDoor.x - atDoor.x) <= 1)
        assertTrue(kotlin.math.abs(beforeDoor.floorY - atDoor.floorY) <= 1)
        assertEquals(door.x, atDoor.x)
        assertTrue(brain.movementAt(OfficeExecutiveTimeline.EXIT_END_MS + 1).x < 0)
    }

    @Test fun frameTimelineIsIndependentOfQueryOrderForMultipleSeeds() {
        val times = listOf(600_000L, 0L, 59_000L, 35_000L, 240_000L, 180_000L, 22_000L, 10_000L)
        for (seed in listOf(7, 42, 91)) {
            val ascendingPlan = OfficeNpcDirector.plan(env(seed)).mapNotNull { it.officeBrain }
            val shuffledPlan = OfficeNpcDirector.plan(env(seed)).mapNotNull { it.officeBrain }
            val expected = ascendingPlan.associate { brain ->
                brain.npcId to times.associateWith(brain::frameStateAt)
            }
            times.reversed().forEach { time -> shuffledPlan.forEach { it.frameStateAt(time) } }
            val actual = shuffledPlan.associate { brain ->
                brain.npcId to times.associateWith(brain::frameStateAt)
            }
            assertEquals("seed $seed", expected, actual)
        }
    }
}

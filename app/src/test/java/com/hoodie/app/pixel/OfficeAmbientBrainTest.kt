package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.npc.brain.NpcIntent
import com.hoodie.app.pixel.npc.office.OfficeNavigationGraph
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.office.OfficeNpcSpot
import com.hoodie.app.pixel.npc.office.OfficeSocialCoordinator
import com.hoodie.app.pixel.npc.office.OfficeSpeechLibrary
import com.hoodie.app.pixel.npc.office.NpcSpeechTopic
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.scene.SceneEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeAmbientBrainTest {
    @Test fun deterministicRandomRepeatsForSameIdentityAndChangesWithSeed() {
        assertEquals(NpcDeterministicRandom.value("cat_colleague", 42, 18), NpcDeterministicRandom.value("cat_colleague", 42, 18))
        assertTrue(NpcDeterministicRandom.value("cat_colleague", 42, 18) != NpcDeterministicRandom.value("cat_colleague", 43, 18))
    }

    @Test fun officeBrainsAreStableAndProduceSeveralActivitiesInFiveMinutes() {
        val slots = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = 42))
        val brains = slots.mapNotNull { it.officeBrain }
        for (brain in brains) {
            assertEquals(brain.stateAt(150_000), brain.stateAt(150_000))
            assertEquals(brain.movementAt(150_000), brain.movementAt(150_000))
        }
        val rabbit = brains.first { it.npcId == "rabbit_analyst" }
        val cat = brains.first { it.npcId == "cat_colleague" }
        val rabbitIntents = (0L..300_000L step 1_000).map { rabbit.stateAt(it).currentIntent }.toSet()
        val catIntents = (0L..300_000L step 1_000).map { cat.stateAt(it).currentIntent }.toSet()
        assertTrue("rabbit intents: $rabbitIntents", rabbitIntents.size >= 3)
        assertTrue("cat intents: $catIntents", catIntents.size >= 4)
    }

    @Test fun everyOfficeSpotHasAnInBoundsRouteToEveryOtherSpot() {
        OfficeNpcSpot.entries.forEach { from -> OfficeNpcSpot.entries.forEach { to ->
            val route = OfficeNavigationGraph.route(from, to)
            assertEquals(OfficeNavigationGraph.spots.getValue(from), route.first())
            assertEquals(OfficeNavigationGraph.spots.getValue(to), route.last())
            assertTrue(route.all { it.x in 0..239 && it.floorY in 0..319 })
        } }
    }

    @Test fun exclusiveOfficeSpotsNeverReceiveTwoReservations() {
        val brains = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = 42))
            .mapNotNull { it.officeBrain }
        for (time in 0L..300_000L step 1_000) {
            val activeOccupants = brains.map { brain -> brain.npcId to brain.stateAt(time) }
                .filter { (id, state) -> id != "bulldog_exec" &&
                    !(state.currentIntent == NpcIntent.SOCIALIZE && state.currentSpot in setOf(OfficeNpcSpot.WINDOW, OfficeNpcSpot.COFFEE, OfficeNpcSpot.CENTER, OfficeNpcSpot.WHITEBOARD)) }
                .map { (id, state) -> id to (state.targetSpot ?: state.currentSpot) }
            val occupied = activeOccupants.map { it.second }
            val details = activeOccupants.joinToString { (id, spot) -> "$id:$spot" }
            assertEquals("duplicate active spot at $time ms: $occupied ($details)", occupied.size, occupied.toSet().size)
        }
    }

    @Test fun bulldogStaysOutForMostOfTheVisitCycle() {
        val brain = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = 2))
            .first { it.definition.id == "bulldog_exec" }.officeBrain!!
        val absent = brain.movementAt(100_000)
        val entering = brain.movementAt(1_000)
        assertTrue(absent.x < 0)
        assertTrue(entering.phase == com.hoodie.app.pixel.npc.PathPhase.ENTER && entering.x in 0..228)
    }

    @Test fun socialEventsAreDeterministicSparseAndSpeechIsContextual() {
        val first = (0L..600_000L step 250).mapNotNull { OfficeSocialCoordinator.activeEventAt(it, 42) }
            .distinctBy { it.startAt }
        val second = (0L..600_000L step 250).mapNotNull { OfficeSocialCoordinator.activeEventAt(it, 42) }
            .distinctBy { it.startAt }
        assertEquals(first, second)
        assertTrue(first.size in 4..9)
        first.zipWithNext().forEach { (a, b) -> assertTrue(b.startAt - a.endsAt >= 60_000) }
        val sampledEvents = (0..40).flatMap { seed ->
            (0L..600_000L step 1_000).mapNotNull { OfficeSocialCoordinator.activeEventAt(it, seed) }
                .distinctBy { it.startAt }
        }
        val speechRatio = sampledEvents.count { it.withSpeechBubble }.toFloat() / sampledEvents.size
        assertTrue("speech ratio: $speechRatio", speechRatio in 0.18f..0.42f)

        assertTrue(OfficeSpeechLibrary.linesAt(9 * 60).any { it.topic == NpcSpeechTopic.GREETING })
        assertTrue(OfficeSpeechLibrary.linesAt(12 * 60).all { it.topic == NpcSpeechTopic.LUNCH || it.topic == NpcSpeechTopic.COFFEE || it.topic == NpcSpeechTopic.WORK })
        assertTrue(OfficeSpeechLibrary.linesAt(18 * 60).all { it.topic == NpcSpeechTopic.END_OF_DAY })
    }
}

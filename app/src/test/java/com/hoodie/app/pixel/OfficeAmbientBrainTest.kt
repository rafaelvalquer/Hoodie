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

    @Test fun completeHistoriesRepeatForSameSeedAndChangeForAnotherSeed() {
        fun histories(seed: Int) = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed))
            .mapNotNull { slot -> slot.officeBrain?.let { brain ->
                brain.npcId to (0L..600_000L step 1_000).map { brain.stateAt(it) }
            } }.toMap()
        val first = histories(42)
        assertEquals(first, histories(42))
        assertTrue("different seed must produce different NPC histories", first != histories(43))
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
            val activeOccupants = brains.filter { it.npcId != "bulldog_exec" || it.movementAt(time).x >= 0 }
                .map { brain -> brain.npcId to brain.stateAt(time) }
                .map { (id, state) -> id to (state.targetSpot ?: state.currentSpot) }
            val occupied = activeOccupants.map { it.second }
            val details = activeOccupants.joinToString { (id, spot) -> "$id:$spot" }
            assertEquals("duplicate active spot at $time ms: $occupied ($details)", occupied.size, occupied.toSet().size)
            activeOccupants.map { it.second }.forEachIndexed { index, spot ->
                activeOccupants.drop(index + 1).forEach { (_, otherSpot) ->
                    val a = OfficeNavigationGraph.spots.getValue(spot)
                    val b = OfficeNavigationGraph.spots.getValue(otherSpot)
                    val dx = a.x - b.x
                    val dy = a.floorY - b.floorY
                    assertTrue("overlapping reservations at $time ms: $spot/$otherSpot", dx * dx + dy * dy >= 36 * 36)
                }
            }
        }
    }

    @Test fun bulldogStaysOutForMostOfTheVisitCycle() {
        val brain = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = 2))
            .first { it.definition.id == "bulldog_exec" }.officeBrain!!
        val absent = brain.movementAt(100_000)
        val entering = brain.movementAt(1_000)
        assertTrue(absent.x < 0)
        assertTrue(entering.phase == com.hoodie.app.pixel.npc.PathPhase.ENTER && entering.x > 239)
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
        assertTrue("speech ratio: $speechRatio", speechRatio in 0.03f..0.55f)

        assertTrue(OfficeSpeechLibrary.linesAt(9 * 60).any { it.topic == NpcSpeechTopic.GREETING })
        assertTrue(OfficeSpeechLibrary.linesAt(12 * 60).all { it.topic == NpcSpeechTopic.LUNCH || it.topic == NpcSpeechTopic.COFFEE || it.topic == NpcSpeechTopic.WORK })
        assertTrue(OfficeSpeechLibrary.linesAt(18 * 60).all { it.topic == NpcSpeechTopic.END_OF_DAY })
    }

    @Test fun tenMinuteSpeechTimelineHasGlobalAndPerSpeakerCooldowns() {
        val daySeed = 42
        val slots = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = daySeed))
        val brains = slots.mapNotNull { it.officeBrain }
        val events = (0L..600_000L step 250).mapNotNull { OfficeSocialCoordinator.activeEventAt(it, daySeed) }
            .distinctBy { it.startAt }
        events.zipWithNext().forEach { (a, b) -> assertTrue("global cooldown: $a -> $b", b.startAt - a.endsAt >= 60_000) }
        events.filter { it.withSpeechBubble }.groupBy { it.speakerId }.values.forEach { speakerEvents ->
            speakerEvents.zipWithNext().forEach { (a, b) -> assertTrue("speaker cooldown: $a -> $b", b.startAt - a.endsAt >= 45_000) }
        }
        for (time in 0L..600_000L step 250) {
            assertTrue("multiple bubbles at $time", brains.count { it.shouldSpeak(time) } <= 1)
        }
    }

    @Test fun fiveMinuteHistoriesAvoidImmediateCoffeeAndSocialRepeatsAcrossSeeds() {
        val bulldogHistories = mutableSetOf<List<NpcIntent>>()
        for (seed in 1..40) {
            val brains = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed))
                .mapNotNull { it.officeBrain }
            for (brain in brains.filter { it.npcId != "bulldog_exec" }) {
                val history = (0L..300_000L step 1_000).map { brain.stateAt(it).currentIntent }.distinctRuns()
                history.zipWithNext().forEach { (a, b) ->
                    if (a == NpcIntent.GET_COFFEE || a == NpcIntent.SOCIALIZE) assertTrue("${brain.npcId} repeated $a for seed $seed", b != a)
                }
            }
            val bulldog = brains.first { it.npcId == "bulldog_exec" }
            bulldogHistories += (0L..300_000L step 1_000).map { bulldog.stateAt(it).currentIntent }.distinctRuns()
        }
        assertTrue("bulldog histories: ${bulldogHistories.size}", bulldogHistories.size >= 10)
    }

    @Test fun coordinatedMeetingsOccurAndOnlyAboutThirtyPercentShowTextAcrossSeeds() {
        var meetings = 0
        var meetingsWithText = 0
        for (seed in 1..40) {
            val colleagues = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed))
                .mapNotNull { it.officeBrain }.filter { it.npcId in setOf("rabbit_analyst", "cat_colleague") }
            val seenMeetings = mutableMapOf<Long, Boolean>()
            val meetingSpeakers = mutableMapOf<Long, String>()
            val meetingStarts = mutableMapOf<Long, Long>()
            val socialCooldowns = mutableMapOf<Long, Map<String, Long>>()
            val speechCooldowns = mutableMapOf<Long, Long>()
            for (time in 0L..600_000L step 250) {
                val states = colleagues.map { it.stateAt(time) }
                if (states.all { it.currentIntent == NpcIntent.SOCIALIZE }) {
                    val eventEnd = states.first().nextDecisionAt
                    seenMeetings[eventEnd] = seenMeetings.getOrDefault(eventEnd, false) || colleagues.any { it.shouldSpeak(time) }
                    val rabbitTravel = OfficeNavigationGraph.route(OfficeNpcSpot.DESK_LEFT, OfficeNpcSpot.CENTER)
                        .zipWithNext().sumOf { (a, b) -> maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * 24L }
                    meetingStarts[eventEnd] = states.first().intentStartedAt - rabbitTravel
                    socialCooldowns[eventEnd] = colleagues.mapIndexed { index, brain -> brain.npcId to states[index].cooldowns.socialUntil }.toMap()
                    colleagues.firstOrNull { it.shouldSpeak(time) }?.let { speaker ->
                        val speakerIndex = colleagues.indexOf(speaker)
                        meetingSpeakers[eventEnd] = speaker.npcId
                        speechCooldowns[eventEnd] = states[speakerIndex].cooldowns.speechUntil
                    }
                }
            }
            val meetingOrder = seenMeetings.keys.sortedBy { meetingStarts.getValue(it) }
            meetingOrder.zipWithNext().forEach { (a, b) ->
                assertTrue("production global cooldown: $a -> $b", meetingStarts.getValue(b) - meetingStarts.getValue(a) >= 72_000)
                socialCooldowns.getValue(a).forEach { (id, until) ->
                    assertTrue("$id social cooldown: $a -> $b", meetingStarts.getValue(b) >= until)
                }
            }
            meetingSpeakers.entries.groupBy { it.value }.values.forEach { speakerEvents ->
                speakerEvents.map { it.key }.sortedBy { meetingStarts.getValue(it) }.zipWithNext().forEach { (a, b) ->
                    assertTrue("speech cooldown: $a -> $b", meetingStarts.getValue(b) >= speechCooldowns.getValue(a))
                }
            }
            meetings += seenMeetings.size
            meetingsWithText += seenMeetings.values.count { it }
        }
        assertTrue("meetings=$meetings", meetings >= 100)
        val ratio = meetingsWithText.toFloat() / meetings
        assertTrue("speech incidence=$ratio", ratio in .03f..42f)
    }

    private fun <T> List<T>.distinctRuns(): List<T> = fold(mutableListOf()) { acc, item ->
        if (acc.lastOrNull() != item) acc += item
        acc
    }
}

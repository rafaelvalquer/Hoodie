package com.hoodie.app.pixel.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.VenueNpcDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mesma chave e mesmo instante → mesmo estado, em qualquer taxa de quadros. */
class VenueNpcDeterminismTest {

    @Test fun sameKeyAndTimeGiveTheSameScene() {
        VenueTestFixtures.scenes.forEach { scene ->
            val a = VenueTestFixtures.session(scene, 1, 40_000)
            val b = VenueTestFixtures.session(scene, 1, 40_000)
            (0L until 300_000L step 1_234L).forEach { t -> assertEquals("$scene t=$t", a.snapshotAt(t), b.snapshotAt(t)) }
        }
    }

    @Test fun updateRateDoesNotChangeTheScene() {
        VenueTestFixtures.scenes.forEach { scene ->
            val fast = VenueTestFixtures.session(scene, 0, 41_000)
            val slow = VenueTestFixtures.session(scene, 0, 41_000)
            val jump = VenueTestFixtures.session(scene, 0, 41_000)
            val fastStates = (0L..150_000L step 16L).associateWith { fast.snapshotAt(it) }
            (0L..150_000L step 47L).filter { it in fastStates }.forEach { t -> assertEquals("$scene t=$t", fastStates.getValue(t), slow.snapshotAt(t)) }
            assertEquals(fastStates.getValue(150_000L), jump.snapshotAt(150_000L))
            assertEquals(fastStates.getValue(752L), jump.snapshotAt(752L))
        }
    }

    @Test fun differentDaysAndPlacesTellDifferentStories() {
        VenueTestFixtures.scenes.forEach { scene ->
            val a = VenueTestFixtures.session(scene, 0, 42_000)
            val b = VenueTestFixtures.session(scene, 0, 42_001)
            val other = VenueNpcDirector.createSession(scene, VenueTestFixtures.env(0, 42_000, placeId = 99L))
            fun story(s: com.hoodie.app.pixel.npc.venue.VenueNpcSession) = (0L until 300_000L step 2_000L).map { t -> s.snapshotAt(t).npcs.map { it.x to it.movement.animation } }
            val sa = story(a)
            assertTrue("$scene: dias iguais", sa.zip(story(b)).count { (x, y) -> x != y } > 20)
            assertTrue("$scene: lugares iguais", sa.zip(story(other)).count { (x, y) -> x != y } > 20)
        }
    }

    @Test fun keyCarriesSceneDayPlaceVariantAndNight() {
        VenueTestFixtures.scenes.forEach { scene ->
            val key = VenueNpcDirector.sessionKey(scene, VenueTestFixtures.env(variant = 4, daySeed = 9, period = DayPeriod.NIGHT, placeId = 3L))
            assertEquals(scene, key.scene); assertEquals(9, key.daySeed); assertEquals(3L, key.placeId)
            assertEquals(4 % VenueNpcDirector.VARIANTS, key.variant); assertTrue(key.night)
        }
    }
}

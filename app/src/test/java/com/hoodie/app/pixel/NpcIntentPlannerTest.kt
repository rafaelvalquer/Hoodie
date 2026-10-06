package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.brain.*
import com.hoodie.app.pixel.npc.office.OfficeNpcSpot
import org.junit.Assert.*
import org.junit.Test

class NpcIntentPlannerTest {
    @Test fun excludesCoolingAndImmediatelyRepeatedActivities() {
        val profile = NpcPersonalityProfile(mapOf(NpcIntent.GET_COFFEE to 1, NpcIntent.WORK to 1), 3_000, 20_000, .5f, .3f)
        val state = NpcBrainState(NpcIntent.WORK, OfficeNpcSpot.DESK_LEFT, 0, 0,
            recentIntents = listOf(NpcIntent.GET_COFFEE), cooldowns = NpcCooldowns(coffeeUntil = 100))
        assertEquals(NpcIntent.WORK, NpcIntentPlanner.choose("rabbit", 7, state, profile, 0))
    }

    @Test fun activityDurationIsStableAndWithinIntentRange() {
        val profile = NpcPersonalityProfile(mapOf(NpcIntent.WORK to 1), 3_000, 20_000, .5f, .3f)
        val a = NpcIntentPlanner.durationMs("rabbit", 4, 2, profile, NpcIntent.WORK)
        assertEquals(a, NpcIntentPlanner.durationMs("rabbit", 4, 2, profile, NpcIntent.WORK))
        assertTrue(a in 15_000L..40_000L)
    }
}

package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.brain.*
import com.hoodie.app.pixel.npc.office.*
import org.junit.Assert.*
import org.junit.Test

class OfficeBehaviorPlannerTest {
    @Test fun intentsMapToOfficeLocationsAndHaveBoundedDurations() {
        val profile = NpcPersonalityProfile(mapOf(NpcIntent.WORK to 1), 3_000, 20_000, .5f, .3f)
        assertEquals(OfficeNpcSpot.COFFEE, OfficeBehaviorPlanner.target(NpcIntent.GET_COFFEE, OfficeNpcSpot.DESK_LEFT, OfficeNpcSpot.DESK_LEFT))
        assertTrue(OfficeBehaviorPlanner.durationMs("rabbit", 2, 0, profile, NpcIntent.WORK) in 15_000L..40_000L)
    }
}

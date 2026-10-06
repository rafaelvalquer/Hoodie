package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.office.OfficeSocialCoordinator
import org.junit.Assert.*
import org.junit.Test

class OfficeSocialCoordinatorTest {
    @Test fun socialEventsRepeatExactlyAndRespectCooldownGaps() {
        fun events(seed: Int) = (0L..600_000L step 250).mapNotNull { OfficeSocialCoordinator.activeEventAt(it, seed) }.distinctBy { it.startAt }
        val one = events(42)
        assertEquals(one, events(42))
        assertTrue(one.size >= 4)
        one.zipWithNext().forEach { (a, b) -> assertTrue(b.startAt - a.endsAt >= 60_000) }
    }
}

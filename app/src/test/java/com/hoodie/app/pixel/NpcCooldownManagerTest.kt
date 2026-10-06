package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.brain.*
import org.junit.Assert.*
import org.junit.Test

class NpcCooldownManagerTest {
    @Test fun coffeeCooldownUsesDefinedRangeAndPreservesOtherWindows() {
        val old = NpcCooldowns(phoneUntil = 19, socialUntil = 29)
        val result = NpcCooldownManager.afterIntent("cat", 8, 3, NpcIntent.GET_COFFEE, 1000, old)
        assertTrue(result.coffeeUntil in 61_000L..91_000L)
        assertEquals(old.phoneUntil, result.phoneUntil)
        assertEquals(old.socialUntil, result.socialUntil)
    }
}

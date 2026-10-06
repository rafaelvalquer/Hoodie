package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import org.junit.Assert.*
import org.junit.Test

class NpcDeterministicRandomTest {
    @Test fun stableForIdentitySeedIndexAndBoundedSelection() {
        val value = NpcDeterministicRandom.value("cat", 42, 9)
        assertEquals(value, NpcDeterministicRandom.value("cat", 42, 9))
        assertTrue(value != NpcDeterministicRandom.value("cat", 43, 9))
        repeat(50) { assertTrue(NpcDeterministicRandom.choose("rabbit", 2, it.toLong(), 7) in 0..6) }
    }
}

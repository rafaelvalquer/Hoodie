package com.hoodie.app.pixel

import com.hoodie.app.pixel.performance.SceneFramePacer
import org.junit.Assert.*
import org.junit.Test

class SceneFramePacerTest {
    @Test fun `small display callback jitter does not accumulate into missed frames`() {
        val pacer = SceneFramePacer()
        assertTrue(pacer.shouldRender(0))
        assertFalse(pacer.shouldRender(16_700_000))
        assertTrue(pacer.shouldRender(33_400_000))
        assertFalse(pacer.shouldRender(49_800_000))
        assertTrue(pacer.shouldRender(66_200_000))
        assertFalse(pacer.shouldRender(82_900_000))
        assertTrue(pacer.shouldRender(99_500_000))
    }

    @Test fun `a long stall skips old deadlines instead of replaying queued draws`() {
        val pacer = SceneFramePacer()
        assertTrue(pacer.shouldRender(0))
        assertTrue(pacer.shouldRender(200_000_000))
        assertFalse(pacer.shouldRender(200_000_000))
        assertFalse(pacer.shouldRender(201_000_000))
        assertTrue(pacer.shouldRender(231_000_000))
    }

    @Test fun `display rates of 60 90 and 120 Hz stay close to thirty scene frames per second`() {
        for (hz in listOf(60, 90, 120)) {
            val pacer = SceneFramePacer()
            val frames = (0 until hz * 10).count { pacer.shouldRender(it * 1_000_000_000L / hz) }
            assertTrue("$hz Hz produced $frames scene frames in ten seconds", frames in 299..304)
        }
    }
}

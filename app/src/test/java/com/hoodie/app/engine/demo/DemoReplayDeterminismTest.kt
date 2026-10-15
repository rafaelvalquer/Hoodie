package com.hoodie.app.engine.demo

import com.hoodie.app.presentation.screens.diary.ReplaySpeed
import org.junit.Assert.*
import org.junit.Test

class DemoReplayDeterminismTest {
    @Test fun `skipped UI updates and different tick frequencies produce the same fictitious instant`() {
        for (mode in DemoReplayMode.entries) {
            val t = demoTimeline(); val frequent = DemoReplayController(t); val delayed = DemoReplayController(t)
            for (c in listOf(frequent, delayed)) { c.setMode(mode, 0); c.play(0) }
            for (at in 17L..12_000L step 17) frequent.tick(at)
            frequent.tick(12_000); delayed.tick(12_000)
            assertEquals(frequent.snapshot, delayed.snapshot)
        }
    }

    @Test fun `the same commands and monotonic instants always reproduce the same result`() {
        fun run(): List<DemoReplaySnapshot> {
            val c = DemoReplayController(demoTimeline())
            return listOf(c.play(0), c.tick(3_333), c.pause(4_000), c.tick(20_000), c.play(21_000),
                c.tick(25_000), c.next(25_001), c.previous(25_002), c.setMode(DemoReplayMode.FULL_DAY, 26_000),
                c.setSpeed(ReplaySpeed.VERY_FAST, 26_000), c.play(26_000), c.tick(78_000), c.restart())
        }
        assertEquals(run(), run())
    }
}

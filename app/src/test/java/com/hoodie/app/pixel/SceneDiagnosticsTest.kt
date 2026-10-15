package com.hoodie.app.pixel

import com.hoodie.app.pixel.performance.SceneDiagnostics
import com.hoodie.app.pixel.performance.ScenePerformanceMonitor
import com.hoodie.app.pixel.performance.SceneDiagnosticReport
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test

class SceneDiagnosticsTest {
    @Test fun `scene metrics remain independent and FPS requires actual presentation`() {
        SceneDiagnostics.acquire().use {
            SceneDiagnostics.reset()
            repeat(200) {
                SceneDiagnostics.render(SceneId.MALL, 2_000_000, 300_000, 100_000, 200_000, 400_000, 500_000, 100_000, 100_000, 7, 4)
                SceneDiagnostics.render(SceneId.HOTEL, 9_000_000, 100_000, 200_000, 300_000, 400_000, 500_000, 600_000, 700_000, 5, 2)
            }
            assertNull(SceneDiagnostics.snapshot(SceneId.MALL).fps)
            assertEquals(2.0, SceneDiagnostics.snapshot(SceneId.MALL).renderP95Ms, .0001)
            assertEquals(9.0, SceneDiagnostics.snapshot(SceneId.HOTEL).renderP95Ms, .0001)
            assertEquals(180, SceneDiagnostics.snapshot(SceneId.MALL).frames)
            SceneDiagnostics.presented(SceneId.MALL, 800_000, 1_000_000_000)
            SceneDiagnostics.presented(SceneId.MALL, 900_000, 1_033_000_000)
            val s = SceneDiagnostics.snapshot(SceneId.MALL)
            assertEquals(1000.0 / 33, s.fps!!, .001)
            assertEquals(33.0, s.intervalP95Ms, .001)
            assertEquals(.9, s.bitmapP95Ms, .001)
            assertEquals(7, s.activeNpcs); assertEquals(4, s.visibleNpcs)
            assertNull(SceneDiagnostics.snapshot(SceneId.HOTEL).fps)
            SceneDiagnostics.stopPresentation(SceneId.MALL)
            SceneDiagnostics.presented(SceneId.MALL, 700_000, 40_000_000_000)
            assertEquals(33.0, SceneDiagnostics.snapshot(SceneId.MALL).intervalP95Ms, .001)
            assertTrue(ScenePerformanceMonitor.nowNanos() > 0)
        }
    }

    @Test fun `session and snapshot counters count actual events rather than frames`() {
        SceneDiagnostics.acquire().use {
            SceneDiagnostics.reset()
            SceneDiagnostics.sessionCreated(SceneId.CINEMA)
            SceneDiagnostics.snapshotBuilt(SceneId.CINEMA)
            SceneDiagnostics.timelineRebuilt(SceneId.CINEMA)
            assertEquals(1L, SceneDiagnostics.snapshot(SceneId.CINEMA).sessionsCreated)
            assertEquals(1L, SceneDiagnostics.snapshot(SceneId.CINEMA).snapshotsBuilt)
            assertEquals(1L, SceneDiagnostics.snapshot(SceneId.CINEMA).timelineRebuilds)
            assertEquals(0L, SceneDiagnostics.snapshot(SceneId.BEACH).sessionsCreated)
        }
    }

    @Test fun `export preserves unavailable values and distinguishes median from effective FPS`() {
        SceneDiagnostics.acquire().use {
            SceneDiagnostics.reset()
            for (at in listOf(1_000L, 1_033L, 1_083L, 1_183L)) SceneDiagnostics.presented(SceneId.BEACH, 100_000, at * 1_000_000)
            val s = SceneDiagnostics.snapshot(SceneId.BEACH)
            assertEquals(3000.0 / 183, s.fps!!, .001)
            assertEquals(20.0, s.medianFps!!, .001)
            val row = SceneDiagnosticReport.row("steady", s)
            assertEquals(SceneDiagnosticReport.HEADER.split(',').size, row.split(',').size)
            assertEquals("BEACH", row.substringBefore(','))
            assertTrue(runCatching { SceneDiagnosticReport.row("invalid,stage", s) }.isFailure)
        }
    }
}

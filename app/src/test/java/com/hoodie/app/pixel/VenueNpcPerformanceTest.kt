package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.VenueNpcDirector
import com.hoodie.app.pixel.performance.SceneDiagnostics
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test

class VenueNpcPerformanceTest {
    @Test fun `all actor queries at the same instant reuse the exact snapshot without simulation rebuild`() {
        SceneDiagnostics.acquire().use {
            for (scene in listOf(SceneId.BAKERY, SceneId.MALL, SceneId.BEACH, SceneId.CINEMA, SceneId.PHARMACY, SceneId.HOTEL)) {
                SceneDiagnostics.reset()
                val session = VenueNpcDirector.createSession(scene, SceneEnv(DayPeriod.DAY, 600, daySeed = 2026, placeId = 42))
                try {
                    val expected = session.snapshotAt(600_000)
                    val retained = session.retainedSizeForTests()
                    repeat(1_000) {
                        assertSame(expected, session.snapshotAt(600_000))
                        session.actors.forEachIndexed { index, actor -> assertSame(expected.npcs[index], actor.frameStateAt(600_000)) }
                    }
                    val metrics = SceneDiagnostics.snapshot(scene)
                    assertEquals(1L, metrics.snapshotsBuilt)
                    assertEquals(0L, metrics.timelineRebuilds)
                    assertEquals(retained, session.retainedSizeForTests())
                } finally { session.dispose() }
            }
        }
    }
}

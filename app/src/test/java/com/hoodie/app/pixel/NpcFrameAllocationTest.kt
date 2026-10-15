package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.VenueNpcDirector
import com.hoodie.app.pixel.performance.SceneDiagnostics
import com.hoodie.app.pixel.renderer.SceneCompositionPlan
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.IdentityHashMap

class NpcFrameAllocationTest {
    @Test fun `warmed composition reuses entries while every actor shares a single frame snapshot`() {
        SceneDiagnostics.acquire().use {
            for (sceneId in listOf(SceneId.BAKERY, SceneId.MALL, SceneId.BEACH, SceneId.CINEMA, SceneId.PHARMACY, SceneId.HOTEL)) {
                SceneDiagnostics.reset()
                val scene = SceneRegistry[sceneId]
                val env = SceneEnv(DayPeriod.DAY, 600, daySeed = 2026, placeId = 42)
                val session = VenueNpcDirector.createSession(sceneId, env)
                val plan = SceneCompositionPlan()
                try {
                    plan.update(scene, session.npcSlots, env, 0)
                    val created = plan.entriesCreated
                    val original = IdentityHashMap<SceneCompositionPlan.Entry, Boolean>()
                    plan.entries.forEach { original[it] = true }
                    for (frame in 1..300) {
                        plan.update(scene, session.npcSlots, env, frame * 33L)
                        assertEquals("$sceneId new composition entries on a warm frame", created, plan.entriesCreated)
                        assertTrue(plan.entries.all { original.containsKey(it) })
                        for (i in 1 until plan.entries.size) assertTrue(plan.entries[i - 1].baseline <= plan.entries[i].baseline)
                    }
                    assertEquals("$sceneId one snapshot per timestamp, shared by all actors", 301L, SceneDiagnostics.snapshot(sceneId).snapshotsBuilt)
                    assertEquals(scene.sortedProps.size + session.npcSlots.size, created)
                } finally { plan.clear(); session.dispose() }
            }
        }
    }
}

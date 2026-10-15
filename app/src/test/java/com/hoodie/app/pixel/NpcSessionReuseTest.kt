package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSession
import com.hoodie.app.pixel.npc.venue.VenueNpcSession
import com.hoodie.app.pixel.performance.SceneBenchmarkScenes
import com.hoodie.app.pixel.performance.SceneDiagnostics
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class NpcSessionReuseTest {
    private fun session(renderer: SceneRenderer, scene: SceneId): Any =
        SceneRenderer::class.java.getDeclaredField(if (scene == SceneId.LEISURE) "leisureSession" else "venueSession")
            .apply { isAccessible = true }.get(renderer)!!

    private fun snapshot(session: Any, time: Long): Any = when (session) {
        is VenueNpcSession -> session.snapshotAt(time)
        is LeisureNpcSession -> session.snapshotAt(time)
        else -> error("unsupported session")
    }

    @Test fun `light and presentation revisions preserve session cast reservations and cached snapshot`() {
        SceneDiagnostics.acquire().use {
            for (case in SceneBenchmarkScenes.cases.filter { it.scene == SceneId.LEISURE || it.place != null && it.scene != SceneId.STORE }) {
                SceneDiagnostics.reset()
                val renderer = SceneRenderer(true)
                val machine = AnimationStateMachine(Random(26))
                machine.place(case.visual(), 1)
                try {
                    val frame = machine.frame(3_000, 600, DayPeriod.DAY)!!
                    renderer.render(frame, 3_000)
                    val original = session(renderer, case.scene)
                    val before = snapshot(original, 3_000)
                    val initialRebuilds = SceneDiagnostics.snapshot(case.scene).timelineRebuilds
                    renderer.render(frame.copy(env = frame.env.copy(period = DayPeriod.NIGHT, clockMinute = 22 * 60),
                        interactionVisual = frame.interactionVisual?.copy(presentationRevision = 2)), 3_000)
                    assertSame("${case.scene} lighting reset the NPCs", original, session(renderer, case.scene))
                    assertSame("${case.scene} lost cached actors/objects/reservations", before, snapshot(original, 3_000))
                    renderer.render(frame, 3_000)
                    assertSame(original, session(renderer, case.scene))
                    assertEquals(1L, SceneDiagnostics.snapshot(case.scene).sessionsCreated)
                    assertEquals(initialRebuilds, SceneDiagnostics.snapshot(case.scene).timelineRebuilds)
                    // Another saved venue or another day is a legitimate new session.
                    val changed = if (case.scene == SceneId.LEISURE) frame.env.copy(daySeed = frame.env.daySeed + 1)
                        else frame.env.copy(placeId = 43)
                    renderer.render(frame.copy(env = changed), 3_033)
                    assertNotSame(original, session(renderer, case.scene))
                    assertEquals(2L, SceneDiagnostics.snapshot(case.scene).sessionsCreated)
                } finally { renderer.dispose() }
            }
        }
    }
}

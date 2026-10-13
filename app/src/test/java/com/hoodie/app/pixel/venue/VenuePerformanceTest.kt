package com.hoodie.app.pixel.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orçamento: 30 FPS = 33 ms por quadro. Medido na JVM de teste (o dispositivo de homologação é
 * mais lento; o p95 é impresso para acompanhar). O histórico da sessão não cresce sem limite.
 */
class VenuePerformanceTest {
    private fun p95(samples: LongArray): Long = samples.sorted()[(samples.size * 95) / 100]

    @Test fun simulationAndRenderStayWithinTheFrameBudget() {
        VenueTestFixtures.scenes.forEach { scene ->
            val env = VenueTestFixtures.env(0, 60_000, DayPeriod.EVENING)
            val renderer = SceneRenderer()
            (0L until 600L).forEach { renderer.renderEmpty(SceneRegistry[scene], env, it * 33) }
            val frames = 10 * 60 * 30
            val render = LongArray(frames)
            val session = VenueTestFixtures.session(scene, 0, 60_000)
            val simulate = LongArray(frames)
            for (i in 0 until frames) {
                val t = 20_000L + i * 33L
                var start = System.nanoTime()
                session.snapshotAt(t)
                simulate[i] = System.nanoTime() - start
                start = System.nanoTime()
                renderer.renderEmpty(SceneRegistry[scene], env, t)
                render[i] = System.nanoTime() - start
            }
            val simP95 = p95(simulate) / 1_000
            val renderP95 = p95(render) / 1_000
            println("$scene p95: simulação ${simP95}µs, quadro completo ${renderP95}µs")
            assertTrue("$scene simulação p95 ${simP95}µs", simP95 < 2_000)
            assertTrue("$scene quadro p95 ${renderP95}µs acima de 33 ms", renderP95 < 33_000)
        }
    }

    @Test fun historyStaysBounded() {
        VenueTestFixtures.scenes.forEach { scene ->
            val session = VenueTestFixtures.session(scene, 1, 61_000)
            var t = 0L
            var maxRetained = 0
            while (t < 6 * 60 * 60 * 1_000L) {
                session.snapshotAt(t)
                maxRetained = maxOf(maxRetained, session.retainedSizeForTests())
                t += 5_000
            }
            println("$scene histórico máximo: $maxRetained")
            assertTrue("$scene histórico guardado $maxRetained", maxRetained <= session.npcSlots.size * 2_048 + 4_000)
        }
    }
}

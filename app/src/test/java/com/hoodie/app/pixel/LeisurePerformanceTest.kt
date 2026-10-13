package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSession
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orçamento do Passeio 2.0: a simulação cabe folgada no quadro (30 FPS = 33 ms) e o histórico
 * não cresce sem limite. Os tempos são medidos na JVM de teste; o p95 é impresso para acompanhar.
 */
class LeisurePerformanceTest {

    private fun p95(samples: LongArray): Long = samples.sorted()[(samples.size * 95) / 100]

    @Test fun simulationAndRenderStayWithinTheFrameBudget() {
        val scene = SceneRegistry[SceneId.LEISURE]
        val env = LeisureTestFixtures.env(0, 90_000, DayPeriod.DAY)
        val renderer = SceneRenderer()
        // Aquecimento (JIT) antes de medir.
        (0L until 600L).forEach { renderer.renderEmpty(scene, env, it * 33) }
        val frames = 10 * 60 * 30 // dez minutos a 30 FPS
        val render = LongArray(frames)
        val session = LeisureTestFixtures.session(0, daySeed = 90_000)
        val simulate = LongArray(frames)
        val base = 20_000L
        for (i in 0 until frames) {
            val t = base + i * 33L
            var start = System.nanoTime()
            session.snapshotAt(t)
            simulate[i] = System.nanoTime() - start
            start = System.nanoTime()
            renderer.renderEmpty(scene, env, t)
            render[i] = System.nanoTime() - start
        }
        val simP95 = p95(simulate) / 1_000
        val renderP95 = p95(render) / 1_000
        println("leisure-v2 p95: simulação ${simP95}µs, quadro completo ${renderP95}µs")
        assertTrue("simulação p95 ${simP95}µs", simP95 < 2_000)
        assertTrue("quadro p95 ${renderP95}µs acima de 33 ms", renderP95 < 33_000)
    }

    /** Seis horas de cena: a linha do tempo guardada continua pequena (sem crescimento ilimitado). */
    @Test fun historyStaysBounded() {
        val session = LeisureTestFixtures.session(1, daySeed = 91_000)
        var t = 0L
        var maxRetained = 0
        while (t < 6 * 60 * 60 * 1_000L) {
            session.snapshotAt(t)
            maxRetained = maxOf(maxRetained, retained(session))
            t += 5_000
        }
        assertTrue("histórico guardado $maxRetained", maxRetained <= 3 * 2_048 + 64)
    }

    @Suppress("UNCHECKED_CAST")
    private fun retained(session: LeisureNpcSession): Int =
        LeisureNpcSession::class.java.getDeclaredField("timelines").let { f ->
            f.isAccessible = true
            (f.get(session) as List<List<*>>).sumOf { it.size }
        }
}

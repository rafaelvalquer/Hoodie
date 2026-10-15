package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.performance.SceneBenchmarkCase
import com.hoodie.app.pixel.performance.SceneBenchmarkScenes
import com.hoodie.app.pixel.renderer.SceneRenderer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import kotlin.random.Random

class SceneTransitionPerformanceTest {
    private fun render(case: SceneBenchmarkCase): Long {
        val renderer = SceneRenderer(true)
        val machine = AnimationStateMachine(Random(26))
        machine.place(case.visual(), 1)
        val checksum = CRC32()
        try {
            repeat(100) { index ->
                val now = 1 + index * 33L
                val frame = machine.frame(now, 600, DayPeriod.DAY)!!
                val buffer = renderer.render(frame, now)
                if (index == 99) for (pixel in buffer.pixels) {
                    checksum.update(pixel); checksum.update(pixel ushr 8); checksum.update(pixel ushr 16); checksum.update(pixel ushr 24)
                }
            }
        } finally { renderer.dispose() }
        return checksum.value
    }

    @Test fun `independent renderers preserve pixels under concurrent worker rendering`() {
        val cases = SceneBenchmarkScenes.cases
        val expected = cases.map(::render)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val futures = cases.map { case -> workers.submit(Callable { render(case) }) }
            assertEquals(expected, futures.map { it.get(30, TimeUnit.SECONDS) })
        } finally { workers.shutdownNow() }
    }
}

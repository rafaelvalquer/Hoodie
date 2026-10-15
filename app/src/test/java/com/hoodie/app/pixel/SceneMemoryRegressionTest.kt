package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.performance.SceneBenchmarkScenes
import com.hoodie.app.pixel.renderer.SceneRenderer
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Repeated real render/scene cycles, not just a bound on a configured cache size. */
class SceneMemoryRegressionTest {
    private fun retainedHeap(): Long {
        System.gc()
        Thread.sleep(50)
        return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }

    @Test fun `repeated scene cycles retain bounded heap and reusable composition storage`() {
        val renderer = SceneRenderer(true)
        val machine = AnimationStateMachine(Random(26))
        var time = 1L
        fun cycle(index: Int) {
            val period = if (index % 2 == 0) DayPeriod.DAY else DayPeriod.NIGHT
            for (case in SceneBenchmarkScenes.cases) {
                machine.place(case.visual(), time)
                repeat(200) {
                    time += 33
                    renderer.render(machine.frame(time, if (period == DayPeriod.DAY) 600 else 1320, period)!!, time)
                }
                val cache = CharacterPainter.cacheStats()
                assertTrue(cache.entries <= cache.capacity)
                assertTrue(com.hoodie.app.pixel.sprite.HoodiePainter.cachedPoseCount() <= com.hoodie.app.pixel.sprite.HoodiePainter.CACHE_LIMIT)
            }
        }
        try {
            cycle(0); cycle(1)
            val baseline = retainedHeap()
            val initialEntries = renderer.compositionEntriesCreated
            val samples = LongArray(18)
            fun cacheSize(owner: Any, field: String): Int =
                (owner.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(owner) as Map<*, *>).size
            for (i in samples.indices) {
                cycle(i + 2); samples[i] = retainedHeap()
                println("SCENE_MEMORY_CYCLE $i retained=${samples[i]} hoodiePoses=${cacheSize(com.hoodie.app.pixel.sprite.HoodiePainter, "cache")} lightPlans=${cacheSize(com.hoodie.app.pixel.renderer.Lighting, "cache")}")
            }
            val finalWindow = samples.takeLast(3)
            println("SCENE_MEMORY baseline=$baseline retained=${samples.joinToString()} compositionEntries=$initialEntries->${renderer.compositionEntriesCreated}")
            assertTrue("retained heap grew beyond the bounded caches", samples.last() - baseline < 24 * 1_048_576L)
            assertTrue("retained heap keeps growing after repeated warm cycles", finalWindow.max() - finalWindow.min() < 8 * 1_048_576L)
            // New story floor items may require a few extra entries, but never one pool per frame.
            assertTrue(renderer.compositionEntriesCreated <= initialEntries + 8)
            val backgrounds = SceneRenderer::class.java.getDeclaredField("backgrounds").apply { isAccessible = true }.get(renderer) as Map<*, *>
            assertTrue(backgrounds.size <= 16)
        } finally { renderer.dispose() }
    }
}

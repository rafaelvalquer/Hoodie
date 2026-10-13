package com.hoodie.app.pixel

import android.os.Debug
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.performance.ScenePerformanceMonitor
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.presentation.components.HoodieSceneView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** Records measured values; emulator timing is evidence, not physical acceptance. */
@RunWith(AndroidJUnit4::class)
class StorePerformanceDeviceTest {
    private fun visual() = VisualState(scene = SceneId.STORE, spot = SpotId.RACK_A, variant = 422,
        actions = listOf(MicroAction(AnimationId.SHOP_LOOK, weight = 1, minMs = 120_000, maxMs = 120_000)))

    @Test fun storeComposeLoopReportsWarmFrameCadenceAndBitmapCost() {
        ScenePerformanceMonitor.resetMeasurements()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(ComposeView(activity).apply {
                    setContent { HoodieSceneView(visual(), Modifier.size(240.dp, 320.dp), greet = false) }
                })
            }
            fun awaitWindow() {
                val deadline = SystemClock.elapsedRealtime() + 20_000
                while (SystemClock.elapsedRealtime() < deadline && ScenePerformanceMonitor.snapshot().frames < 120) {
                    SystemClock.sleep(50)
                }
                assertEquals("complete 120-frame measurement window", 120, ScenePerformanceMonitor.snapshot().frames)
            }
            awaitWindow()
            ScenePerformanceMonitor.resetMeasurements()
            awaitWindow()
            val report = ScenePerformanceMonitor.snapshot()
            println("STORE_COMPOSE_PERF frames=${report.frames} fps=${report.fps} " +
                "renderP50=${report.frameP50Ms}ms renderP95=${report.frameP95Ms}ms renderP99=${report.frameP99Ms}ms " +
                "npcP95=${report.npcDrawingP95Ms}ms bitmapP95=${report.bitmapP95Ms}ms " +
                "heap=${report.heapUsedBytes} delta=${report.heapDeltaBytes} " +
                "gc=${report.gcCount}/${report.gcTimeMs}ms thread=${report.renderThreadName}")
            assertTrue("measured render duration", report.frameP95Ms > 0)
            assertTrue("measured bitmap upload", report.bitmapP95Ms > 0)
            assertTrue("measured shopper drawing", report.npcDrawingP95Ms > 0)
            assertTrue("measured real cadence", report.fps.isFinite() && report.fps > 0)
        }
    }

    @Test fun storeThirtyVirtualMinutesReportRetainedMemoryAfterCacheWarmup() {
        val renderer = SceneRenderer()
        val machine = AnimationStateMachine(Random(0)).apply { place(visual(), 0) }
        val runtime = Runtime.getRuntime()
        val retainedJava = mutableListOf<Long>()
        val retainedNative = mutableListOf<Long>()
        var time = 1_000L
        fun renderAt(t: Long) {
            renderer.render(requireNotNull(machine.frame(t, 600, DayPeriod.DAY)), t)
        }
        fun retainedSample() {
            // Deliberate GC belongs to this diagnostic probe, never the app frame loop.
            System.gc()
            SystemClock.sleep(100)
            retainedJava += runtime.totalMemory() - runtime.freeMemory()
            retainedNative += Debug.getNativeHeapAllocatedSize()
        }
        try {
            // Fill the bounded trip cache before measuring its retained-memory plateau.
            repeat(40) { cycle -> renderAt(time + cycle * 300_000L + 75_000) }
            time += 40 * 300_000L
            repeat(9_100) { renderAt(time); time += 33 }
            retainedSample()
            val started = SystemClock.elapsedRealtime()
            repeat(6) {
                repeat(9_100) { renderAt(time); time += 33 }
                retainedSample()
                val cache = CharacterPainter.cacheStats()
                assertTrue("sprite cache stays bounded", cache.entries <= cache.capacity)
            }
            println("STORE_MEMORY_PROBE virtualMinutes=30 measuredFrames=54600 " +
                "elapsedMs=${SystemClock.elapsedRealtime() - started} retainedJava=$retainedJava " +
                "retainedNative=$retainedNative spriteCache=${CharacterPainter.cacheStats()}")
            assertEquals(7, retainedJava.size)
        } finally { renderer.dispose() }
    }
}

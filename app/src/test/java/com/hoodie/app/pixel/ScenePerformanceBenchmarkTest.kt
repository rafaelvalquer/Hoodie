package com.hoodie.app.pixel

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.VisualDirector
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Same workload before/after optimization. Unpaced JVM costs do not establish device FPS.
 * Detailed per-stage metrics and paced bitmap presentation are separate Android gates. */
class ScenePerformanceBenchmarkTest {
    private data class Case(val scene: SceneId, val activity: HoodieActivity, val context: UserContextType, val place: PlaceType? = null) {
        fun visual() = VisualDirector.resolve(activity, context, shoppingVenue = place,
            venueScenes = VisualDirector.allVenueScenes, now = 0, personality = null).copy(daySeed = 20_026, placeId = 42)
    }

    @Test fun `reproducible baseline covers eleven scenes cold warm stories night return and touch`() {
        val cases = listOf(
            Case(SceneId.OFFICE, HoodieActivity.WORKING, UserContextType.WORK),
            Case(SceneId.RESTAURANT, HoodieActivity.EATING, UserContextType.DINING),
            Case(SceneId.SHOPPING, HoodieActivity.SHOPPING, UserContextType.SHOPPING),
            Case(SceneId.STORE, HoodieActivity.SHOPPING, UserContextType.SHOPPING, PlaceType.STORE),
            Case(SceneId.LEISURE, HoodieActivity.SIGHTSEEING, UserContextType.LEISURE),
            Case(SceneId.BAKERY, HoodieActivity.EATING, UserContextType.DINING, PlaceType.BAKERY),
            Case(SceneId.MALL, HoodieActivity.SHOPPING, UserContextType.SHOPPING, PlaceType.MALL),
            Case(SceneId.BEACH, HoodieActivity.SIGHTSEEING, UserContextType.LEISURE, PlaceType.BEACH),
            Case(SceneId.CINEMA, HoodieActivity.IDLE, UserContextType.LEISURE, PlaceType.CINEMA),
            Case(SceneId.PHARMACY, HoodieActivity.SHOPPING, UserContextType.SHOPPING, PlaceType.PHARMACY),
            Case(SceneId.HOTEL, HoodieActivity.IDLE, UserContextType.TRAVEL, PlaceType.HOTEL),
        )
        val label = (System.getenv("HOODIE_BENCHMARK_LABEL") ?: "latest").also { require(it.matches(Regex("[a-z0-9-]+"))) }
        val output = File("../build/validation/scene-benchmark-$label").apply { mkdirs() }
        val csv = StringBuilder("scene,stage,frames,renderP50Ms,renderP95Ms,renderP99Ms,allocatedBytes,heapBefore,heapAfter,gcCount,gcMs,pixelChecksum,touchStarts\n")
        // Android's compile bootclasspath excludes java.management. The benchmark runs on the
        // host JVM; reflection keeps those diagnostic-only dependencies out of the Android app.
        val factory = Class.forName("java.lang.management.ManagementFactory")
        val alloc = factory.getMethod("getThreadMXBean").invoke(null)
        val allocType = Class.forName("com.sun.management.ThreadMXBean")
        val allocSupported = allocType.getMethod("isThreadAllocatedMemorySupported").invoke(alloc) == true
        if (allocSupported) allocType.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(alloc, true)
        fun allocated(): Long = if (allocSupported) allocType.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType).invoke(alloc, Thread.currentThread().id) as Long else -1
        fun heap() = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val gcBeans = factory.getMethod("getGarbageCollectorMXBeans").invoke(null) as List<*>
        val gcType = Class.forName("java.lang.management.GarbageCollectorMXBean")
        fun gcCount() = gcBeans.sumOf { (gcType.getMethod("getCollectionCount").invoke(it) as Long).coerceAtLeast(0) }
        fun gcMs() = gcBeans.sumOf { (gcType.getMethod("getCollectionTime").invoke(it) as Long).coerceAtLeast(0) }
        for (case in cases) {
            val v = case.visual()
            assertEquals(case.scene, v.scene)
            val machine = AnimationStateMachine(Random(26))
            var renderer = SceneRenderer()
            machine.place(v, 1)
            var now = 1L
            fun stage(name: String, frames: Int, period: DayPeriod, touch: Boolean = false) {
                val samples = LongArray(frames)
                val checksum = CRC32()
                val heapBefore = heap(); val gcBefore = gcCount(); val gcTimeBefore = gcMs(); val allocBefore = allocated()
                var touchStarts = 0
                for (i in samples.indices) {
                    now += 33
                    val frame = machine.frame(now, if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60, period)!!
                    if (touch && i % 30 == 10 && !frame.interactionBusy && !frame.interactionTransitioning) {
                        machine.interact(InteractionResponse("bench", listOf(AnimationId.PET_HEAD, AnimationId.PURR), null, 0), case.scene, now)
                    }
                    if (frame.interactionStarted != null) touchStarts++
                    val start = System.nanoTime()
                    val buffer = renderer.render(frame, now)
                    samples[i] = System.nanoTime() - start
                    if (i == 0 || i == samples.lastIndex) for (pixel in buffer.pixels) {
                        checksum.update(pixel); checksum.update(pixel ushr 8); checksum.update(pixel ushr 16); checksum.update(pixel ushr 24)
                    }
                }
                val allocAfter = allocated(); val heapAfter = heap(); val gcAfter = gcCount(); val gcTimeAfter = gcMs()
                samples.sort()
                fun p(q: Double) = samples[(kotlin.math.ceil(q * samples.size).toInt() - 1).coerceAtLeast(0)] / 1_000_000.0
                csv.append("${case.scene},$name,$frames,${p(.5)},${p(.95)},${p(.99)},${if (allocBefore < 0) -1 else allocAfter - allocBefore},$heapBefore,$heapAfter,${gcAfter - gcBefore},${gcTimeAfter - gcTimeBefore},${checksum.value},$touchStarts\n")
                println("SCENE_BENCH ${case.scene} $name p95=${p(.95)}ms")
                if (touch) assertTrue("${case.scene}: benchmark must actually present a reaction", touchStarts > 0)
            }
            try {
                stage("cold", 60, DayPeriod.DAY)
                stage("warmup", 180, DayPeriod.DAY)
                stage("steady", 300, DayPeriod.DAY)
                // Sample later social/order/film/elevator stories, then continuous frames.
                now = 90_000; stage("story-90s", 90, DayPeriod.DAY)
                now = 240_000; stage("story-240s", 90, DayPeriod.DAY)
                stage("night", 120, DayPeriod.NIGHT)
                machine.setVisual(VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.HOME, now = 0, personality = null), now)
                stage("exit", 240, DayPeriod.NIGHT)
                machine.setVisual(v, now)
                stage("return", 300, DayPeriod.DAY)
                // Separate mask/player cost from the old non-interactive path.
                renderer.dispose(); renderer = SceneRenderer(collectInteractionElements = true)
                // The same safe stationary pause in every scene avoids timing the rejection of
                // a gesture during walking instead of an actual reaction. NPC stories stay real.
                machine.place(v.copy(actions = listOf(MicroAction(AnimationId.IDLE, 1, 60_000, 60_000)),
                    approach = emptyList(), enter = emptyList(), exit = emptyList(), steadyFlags = emptySet()), now)
                stage("touch", 180, DayPeriod.DAY, touch = true)
            } finally { renderer.dispose() }
        }
        File(output, "frames.csv").writeText(csv.toString())
        val sources = File("src/main/java/com/hoodie/app/pixel").walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
        val digest = MessageDigest.getInstance("SHA-256")
        sources.forEach { digest.update(it.relativeTo(File("src/main/java")).path.toByteArray()); digest.update(it.readBytes()) }
        File(output, "environment.txt").writeText("""
            label=$label
            pixelSourceSha256=${digest.digest().joinToString("") { "%02x".format(it) }}
            benchmarkSourceSha256=${MessageDigest.getInstance("SHA-256").digest(File("src/test/java/com/hoodie/app/pixel/ScenePerformanceBenchmarkTest.kt").readBytes()).joinToString("") { "%02x".format(it) }}
            java=${System.getProperty("java.version")}
            vm=${System.getProperty("java.vm.name")}
            os=${System.getProperty("os.name")} ${System.getProperty("os.arch")}
            processors=${Runtime.getRuntime().availableProcessors()}
            seed=20026; random=26; variant=0; place=42; simulationTickMs=33
            Unpaced JVM renderer workload. No Android bitmap, main-thread scheduling or device FPS claim.
            Allocated bytes include frame/model/checksum overhead, not only renderer allocations.
            Later story anchors are intentional timeline seeks; compare each stage separately.
            Cold stage has a cold renderer cache; process-wide sprite caches may be warm from previous scenes.
        """.trimIndent() + "\n")
        assertEquals(11 * 9 + 1, csv.lines().count { it.isNotBlank() })
        println("SCENE_BENCH_REPORT ${output.canonicalPath}")
    }
}

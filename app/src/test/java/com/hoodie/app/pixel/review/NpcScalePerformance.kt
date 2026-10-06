package com.hoodie.app.pixel.review

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.pixel.diary.journey.JourneyMapRenderer
import com.hoodie.app.pixel.diary.journey.JourneyRenderCache
import com.hoodie.app.pixel.diary.journey.JourneyScene
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures
import com.hoodie.app.pixel.npc.NpcDirector
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector
import kotlin.random.Random

/** Informational local timing: no flaky performance threshold or automatic golden approval. */
object NpcScalePerformance {
    private const val WARMUP = 3
    private const val SAMPLES = 12
    private val targetScenes = listOf(SceneId.OFFICE, SceneId.BUS, SceneId.METRO, SceneId.RESTAURANT)

    fun measure(): List<PerformanceSample> = buildList {
        targetScenes.forEach { scene ->
            val npcCount = NpcDirector.plan(scene, variant = 0).size
            add(measure("${scene.name.lowercase()}-max-npcs", npcCount) { i ->
                NpcSceneReviewRenderer.render(scene, DayPeriod.DAY, timeMs = 1_000L + i * 16L)
            })
        }
        val restaurantRenderer = SceneRenderer()
        val restaurantVisual = VisualDirector.resolve(HoodieActivity.EATING, UserContextType.LUNCH)
        require(restaurantVisual.scene == SceneId.RESTAURANT)
        val hoodie = AnimationStateMachine(Random(0)).apply { place(restaurantVisual, 1L) }
        add(measure("restaurant-live-hoodie-cat", npcCount = 2) { i ->
            val time = 1_000L + i * 16L
            val frame = requireNotNull(hoodie.frame(time, 12 * 60, DayPeriod.DAY))
            restaurantRenderer.render(frame, time)
        })
        val data = JourneyTestFixtures.longData
        val layout = com.hoodie.app.pixel.diary.journey.JourneyLayoutEngine.layout(data)
        val cache = JourneyRenderCache.create(layout)
        val out = PixelBuffer(layout.width, layout.height)
        add(measure("journey-longest-day-${data.nodes.size}-stops", npcCount = 1) { i ->
            JourneyMapRenderer.render(
                JourneyScene(
                    data = data,
                    layout = layout,
                    replay = JourneyReplayAssembler.stateAt(data, data.startAt + i * 16L, replaying = true),
                    timeMs = 1_000L + i * 16L,
                ),
                cache = cache,
                out = out,
            )
        })
    }

    private inline fun measure(name: String, npcCount: Int, render: (Int) -> PixelBuffer): PerformanceSample {
        repeat(WARMUP) { render(it) }
        val start = System.nanoTime()
        repeat(SAMPLES) { render(WARMUP + it) }
        return PerformanceSample(name, npcCount, SAMPLES, (System.nanoTime() - start) / SAMPLES)
    }
}

data class PerformanceSample(val scene: String, val npcCount: Int, val frames: Int, val nanosPerFrame: Long) {
    fun json() = """{"scene":"$scene","npcCount":$npcCount,"frames":$frames,"nanosPerFrame":$nanosPerFrame}"""
}

package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.office.OfficeSocialCoordinator
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.OfficeScene
import com.hoodie.app.pixel.scene.SceneEnv
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Candidate image/export and renderer timing for human review, never an auto-approved golden. */
class OfficeVisualReviewTest {
    private val seed = 42
    private fun env(period: DayPeriod = DayPeriod.DAY, minute: Int = 9 * 60, seedValue: Int = seed) =
        SceneEnv(period, minute, variant = seedValue, daySeed = seedValue)

    private fun render(renderer: SceneRenderer, scene: OfficeScene, environment: SceneEnv, timeMs: Long) =
        PixelBuffer(240, 320).also { it.copyFrom(renderer.renderAmbientNpc(scene, environment, timeMs)) }

    private fun contact(frames: List<PixelBuffer>, columns: Int): PixelBuffer {
        val rows = (frames.size + columns - 1) / columns
        return PixelBuffer(columns * 240, rows * 320).also { sheet ->
            sheet.fill(0xFF2B2E4A.toInt())
            frames.forEachIndexed { index, frame -> sheet.blit(frame, index % columns * 240, index / columns * 320) }
        }
    }

    @Test fun exportOfficeLiveReviewCandidatesWhenRequested() {
        assumeTrue(System.getProperty("officeNpcReview") == "true")
        val root = File(System.getProperty("officeNpcReviewOutput") ?: "docs/npc-art-review/v3/office-live")
        root.mkdirs()
        val renderer = SceneRenderer()
        val scene = OfficeScene()
        val dayEnv = env()

        val minuteTimes = listOf(0L, 60_000L, 120_000L, 180_000L, 240_000L)
        val fiveMinute = (0..10).map { it * 30_000L }
        PreviewExport.write(root.resolve("office-live-v2.png"), contact(minuteTimes.map { render(renderer, scene, dayEnv, it) }, 5), 1, 0xFF2B2E4A.toInt())
        PreviewExport.write(root.resolve("five-minute-contact-sheet.png"), contact(fiveMinute.map { render(renderer, scene, dayEnv, it) }, 4), 1, 0xFF2B2E4A.toInt())

        val periods = listOf(
            "morning.png" to env(DayPeriod.MORNING, 8 * 60),
            "lunch-near.png" to env(DayPeriod.DAY, 12 * 60),
            "afternoon.png" to env(DayPeriod.DAY, 15 * 60),
            "end-of-day.png" to env(DayPeriod.EVENING, 18 * 60),
        )
        periods.forEach { (name, environment) ->
            PreviewExport.write(root.resolve(name), render(renderer, scene, environment, 90_000L), 2, 0xFF2B2E4A.toInt())
        }

        val bulldogTimes = listOf(0L, 6_200L, 9_000L, 60_000L, 66_100L)
        PreviewExport.write(root.resolve("bulldog-events.png"), contact(bulldogTimes.map { render(renderer, scene, dayEnv, it) }, 3), 1, 0xFF2B2E4A.toInt())
        val slots = OfficeNpcDirector.plan(dayEnv)
        val socialTime = (0L..600_000L step 250).firstOrNull { time ->
            val pair = slots.filter { it.definition.id in setOf("rabbit_analyst", "cat_colleague") }
                .mapNotNull { it.officeBrain?.stateAt(time) }
            pair.size == 2 && pair.all { it.currentIntent == com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE } &&
                pair.all { it.currentSpot == it.targetSpot }
        }
        assertTrue("seed $seed must yield at least one eligible social meeting", socialTime != null)
        val socialFrame = render(renderer, scene, dayEnv, socialTime!!)
        PreviewExport.write(root.resolve("social-events.png"), socialFrame, 2, 0xFF2B2E4A.toInt())
        root.resolve("social-events-time.txt").writeText("seed=$seed time_ms=$socialTime\n")

        val speechMoment = (1..40).firstNotNullOfOrNull { socialSeed ->
            val socialEnv = env(seedValue = socialSeed)
            val socialBrains = OfficeNpcDirector.plan(socialEnv).mapNotNull { it.officeBrain }
            val time = (0L..600_000L step 250).firstOrNull { t -> socialBrains.any { it.shouldSpeak(t) } }
            time?.let { Triple(socialSeed, it, socialEnv) }
        }
        assertTrue("multi-seed office must produce a visible speech bubble", speechMoment != null)
        speechMoment!!
        PreviewExport.write(root.resolve("speech-events.png"), render(renderer, scene, speechMoment.third, speechMoment.second), 2, 0xFF2B2E4A.toInt())
        root.resolve("speech-events-time.txt").writeText("seed=${speechMoment.first} time_ms=${speechMoment.second}\n")

        val brains = slots.mapNotNull { it.officeBrain }
        root.resolve("timeline.csv").writeText(buildString {
            appendLine("time_ms,npc,intent,current_spot,target_spot,decision_index,next_decision_ms")
            fiveMinute.forEach { time -> brains.forEach { brain ->
                val state = brain.stateAt(time)
                appendLine("$time,${brain.npcId},${state.currentIntent},${state.currentSpot},${state.targetSpot ?: state.currentSpot},${state.decisionIndex},${state.nextDecisionAt}")
            } }
        })

        val performanceStartMs = 300_000L
        repeat(20) { renderer.renderAmbientNpc(scene, dayEnv, performanceStartMs + it * 250L) }
        val start = System.nanoTime()
        repeat(120) { renderer.renderAmbientNpc(scene, dayEnv, performanceStartMs + 5_000L + it * 250L) }
        val msPerFrame = (System.nanoTime() - start) / 1_000_000.0 / 120
        root.resolve("performance-report.json").writeText(
            """{"frames":120,"warmup":20,"msPerFrame":${String.format(Locale.ROOT, "%.4f", msPerFrame)},"npcs":3,"targetMsPerFrame":8.0}""",
        )
        root.resolve("review-manifest.json").writeText(
            """{"status":"PENDING","seed":$seed,"note":"Candidate images for human review; do not treat as approved goldens."}""",
        )
        root.resolve("index.html").writeText(buildString {
            appendLine("<!doctype html><meta charset=\"utf-8\"><title>Office Live review</title>")
            appendLine("<style>body{font:16px system-ui;background:#242742;color:#eee;padding:2rem}img{image-rendering:pixelated;max-width:100%}a{color:#f1c66d}</style>")
            appendLine("<h1>Office Live — seed $seed</h1><p>Status PENDING · <a href=\"review-manifest.json\">manifest</a> · <a href=\"timeline.csv\">timeline</a> · <a href=\"performance-report.json\">performance</a></p>")
            appendLine("<h2>09:00–09:04</h2><img src=\"office-live-v2.png\"><h2>Cinco minutos</h2><img src=\"five-minute-contact-sheet.png\">")
            periods.forEach { (name, _) -> appendLine("<h2>${name.removeSuffix(".png")}</h2><img src=\"$name\">") }
            appendLine("<h2>Entradas e saídas do Bulldog</h2><img src=\"bulldog-events.png\"><h2>Conversa silenciosa</h2><img src=\"social-events.png\"><h2>Conversa com balão</h2><img src=\"speech-events.png\">")
        })
        assertTrue(root.resolve("office-live-v2.png").isFile)
        assertTrue(root.resolve("five-minute-contact-sheet.png").isFile)
    }
}

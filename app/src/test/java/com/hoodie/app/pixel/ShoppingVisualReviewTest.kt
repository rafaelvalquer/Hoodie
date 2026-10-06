package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcDirector
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcIntent
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.ShoppingScene
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Creates review candidates only; the manifest stays PENDING until human approval. */
class ShoppingVisualReviewTest {
    private fun render(renderer: SceneRenderer, scene: ShoppingScene, env: SceneEnv, time: Long) =
        PixelBuffer(240, 320).also { it.copyFrom(renderer.renderAmbientNpc(scene, env, time)) }

    @Test fun exportShoppingReviewCandidatesWhenRequested() {
        assumeTrue(System.getProperty("shoppingNpcReview") == "true")
        val root = File(System.getProperty("shoppingNpcReviewOutput") ?: "docs/npc-art-review/v3/shopping-live")
        root.mkdirs()
        val captures = listOf(
            "browsing.png" to ShoppingNpcIntent.BROWSE_AISLE,
            "reading-product.png" to ShoppingNpcIntent.READ_LABEL,
            "picking-product.png" to ShoppingNpcIntent.PICK_PRODUCT,
            "comparing-products.png" to ShoppingNpcIntent.COMPARE_PRODUCTS,
            "checking-list.png" to ShoppingNpcIntent.CHECK_LIST,
            "checking-phone.png" to ShoppingNpcIntent.CHECK_PHONE,
            "promotion.png" to ShoppingNpcIntent.LOOK_PROMOTION,
            "basket.png" to ShoppingNpcIntent.PUT_IN_BASKET,
            "checkout-wait.png" to ShoppingNpcIntent.WAIT_CHECKOUT,
            "paying.png" to ShoppingNpcIntent.PAY,
            "leaving.png" to ShoppingNpcIntent.EXIT_STORE,
        )
        val seed = (1..4096).first { candidate ->
            val env = SceneEnv(DayPeriod.DAY, 9 * 60, variant = candidate, daySeed = candidate)
            val brain = ShoppingNpcDirector.plan(env).single().shoppingBrain!!
            captures.all { (_, intent) -> (0L..280_000L step 1_000).any { brain.stateAt(it).currentIntent == intent } }
        }
        val renderer = SceneRenderer()
        val scene = ShoppingScene()
        val dayEnv = SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed, daySeed = seed)
        val dayBrain = ShoppingNpcDirector.plan(dayEnv).single().shoppingBrain!!
        val momentRows = mutableListOf("name,time_ms,intent")
        captures.forEach { (name, intent) ->
            val time = (0L..280_000L step 250).first { dayBrain.stateAt(it).currentIntent == intent }
            PreviewExport.write(root.resolve(name), render(renderer, scene, dayEnv, time), 2, 0xFF2B2E4A.toInt())
            momentRows += "$name,$time,$intent"
        }
        val nightEnv = SceneEnv(DayPeriod.NIGHT, 21 * 60, variant = seed, daySeed = seed)
        val nightBrain = ShoppingNpcDirector.plan(nightEnv).single().shoppingBrain!!
        val nightTime = (0L..280_000L step 250).first { nightBrain.stateAt(it).currentIntent == ShoppingNpcIntent.READ_LABEL }
        PreviewExport.write(root.resolve("night.png"), render(renderer, scene, nightEnv, nightTime), 2, 0xFF2B2E4A.toInt())
        momentRows += "night.png,$nightTime,READ_LABEL"
        root.resolve("moments.csv").writeText(momentRows.joinToString("\n") + "\n")

        val sampleTimes = (0..10).map { it * 30_000L }
        val frames = sampleTimes.map { render(renderer, scene, dayEnv, it) }
        val contact = PixelBuffer(240 * 4, 320 * 3).also { sheet ->
            sheet.fill(0xFF2B2E4A.toInt())
            frames.forEachIndexed { i, frame -> sheet.blit(frame, (i % 4) * 240, (i / 4) * 320) }
        }
        PreviewExport.write(root.resolve("shopping-live-five-minute-sheet.png"), contact, 1, 0xFF2B2E4A.toInt())
        val simRows = sampleTimes.map { time ->
            val state = dayBrain.stateAt(time)
            "$time,${state.tripState},${state.currentIntent},${state.currentSpot},${state.basket.itemCount}/${state.basket.maxItems}"
        }
        root.resolve("five-minute.csv").writeText("time_ms,trip_state,intent,spot,basket\n${simRows.joinToString("\n")}\n")

        repeat(20) { renderer.renderAmbientNpc(scene, dayEnv, it * 250L) }
        val start = System.nanoTime()
        repeat(120) { renderer.renderAmbientNpc(scene, dayEnv, 5_000L + it * 250L) }
        val msPerFrame = (System.nanoTime() - start) / 1_000_000.0 / 120
        root.resolve("performance-report.json").writeText("""{"frames":120,"warmup":20,"msPerFrame":${"%.4f".format(java.util.Locale.ROOT, msPerFrame)},"npcs":1}""")
        root.resolve("review-manifest.json").writeText("""{"status":"PENDING","seed":$seed,"note":"Candidate images; human review required before approval."}""")
        root.resolve("index.html").writeText(buildString {
            appendLine("<!doctype html><meta charset=\"utf-8\"><title>Shopping Live review</title>")
            appendLine("<style>body{font:16px system-ui;background:#242742;color:#eee;padding:2rem}img{image-rendering:pixelated;max-width:100%}a{color:#f1c66d}</style>")
            appendLine("<h1>Shopping Live — seed $seed</h1><p>Status PENDING · <a href=\"review-manifest.json\">manifest</a> · <a href=\"moments.csv\">momentos</a> · <a href=\"five-minute.csv\">cinco minutos</a> · <a href=\"performance-report.json\">performance</a></p>")
            appendLine("<h2>Simulação acelerada de cinco minutos</h2><img src=\"shopping-live-five-minute-sheet.png\">")
            captures.forEach { (name, _) -> appendLine("<h2>${name.removeSuffix(".png")}</h2><img src=\"$name\">") }
            appendLine("<h2>Night</h2><img src=\"night.png\">")
        })
        assertTrue(root.resolve("index.html").isFile)
        assertTrue(root.resolve("shopping-live-five-minute-sheet.png").isFile)
    }
}

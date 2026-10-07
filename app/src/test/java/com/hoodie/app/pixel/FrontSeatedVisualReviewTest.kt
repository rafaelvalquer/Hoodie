package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.brain.NpcIntent
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcIntent
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.OfficeScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SceneId
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exports front seated office and restaurant candidate frames; never promotes image goldens. */
class FrontSeatedVisualReviewTest {
    @Test fun exportsOfficeAndRestaurantFrontSeatedCandidatesWhenRequested() {
        assumeTrue(System.getProperty("frontSeatedReview") == "true")
        val root = File(System.getProperty("frontSeatedReviewOutput") ?: "docs/npc-art-review/v3/front-seated-review")
        root.mkdirs()
        val renderer = SceneRenderer()
        val captures = mutableListOf<Pair<String, PixelBuffer>>()

        val officeCases = listOf(
            Triple("office-rabbit-work.png", "rabbit_analyst", NpcIntent.WORK),
            Triple("office-rabbit-phone.png", "rabbit_analyst", NpcIntent.CHECK_PHONE),
            Triple("office-cat-work.png", "cat_colleague", NpcIntent.WORK),
            Triple("office-cat-phone.png", "cat_colleague", NpcIntent.CHECK_PHONE),
        )
        officeCases.forEach { (name, npcId, intent) ->
            val found = (1..16).firstNotNullOfOrNull { seed ->
                val env = SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed, daySeed = seed)
                val brain = OfficeNpcDirector.plan(env).first { it.definition.id == npcId }.officeBrain!!
                val time = (0L..360_000L step 1_000).firstOrNull { t ->
                    val state = brain.stateAt(t)
                    state.currentIntent == intent && state.currentSpot == state.targetSpot
                }
                time?.let { Triple(env, it, seed) }
            }
            assertTrue("office sample not found: $name", found != null)
            val scene = OfficeScene()
            val frame = renderer.renderAmbientNpc(scene, found!!.first, found.second)
            PreviewExport.write(File(root, name), frame, 2, 0xFF2B2E4A.toInt())
            captures += name to frame
        }

        val restaurantCases = listOf(
            "restaurant-eat.png" to RestaurantNpcIntent.EAT,
            "restaurant-drink.png" to RestaurantNpcIntent.DRINK,
            "restaurant-phone.png" to RestaurantNpcIntent.CHECK_PHONE,
            "restaurant-menu.png" to RestaurantNpcIntent.READ_MENU,
            "restaurant-look.png" to RestaurantNpcIntent.LOOK_AROUND,
            "restaurant-talk.png" to RestaurantNpcIntent.TALK,
        )
        restaurantCases.forEach { (name, intent) ->
            val found = (1..16).firstNotNullOfOrNull { seed ->
                val env = SceneEnv(DayPeriod.DAY, 12 * 60, variant = seed, daySeed = seed)
                val brain = RestaurantNpcDirector.brain(env)
                val time = (0L..900_000L step 1_000).firstOrNull { brain.stateAt(it).currentIntent == intent }
                time?.let { Triple(env, it, seed) }
            }
            assertTrue("restaurant sample not found: $name", found != null)
            val scene = SceneRegistry[SceneId.RESTAURANT]
            val frame = renderer.renderEmpty(scene, found!!.first, found.second)
            PreviewExport.write(File(root, name), frame, 2, 0xFF2B2E4A.toInt())
            captures += name to frame
        }
        val columns = 2
        val rows = (captures.size + columns - 1) / columns
        val sheet = PixelBuffer(columns * 240, rows * 320).also { it.fill(0xFF2B2E4A.toInt()) }
        captures.forEachIndexed { index, (_, frame) -> sheet.blit(frame, index % columns * 240, index / columns * 320) }
        PreviewExport.write(File(root, "front-seated-contact-sheet.png"), sheet, 1, 0xFF2B2E4A.toInt())
        File(root, "index.html").writeText(buildString {
            appendLine("<!doctype html><meta charset=\"utf-8\"><title>Front seated review</title>")
            appendLine("<style>body{font:16px system-ui;background:#242742;color:#eee;padding:2rem}img{image-rendering:pixelated;max-width:100%}</style>")
            appendLine("<h1>Front seated candidates</h1><p>Visual review required; these are candidates, not approved goldens.</p>")
            captures.forEach { (name, _) -> appendLine("<h2>$name</h2><img src=\"$name\">") }
            appendLine("<h2>Contact sheet</h2><img src=\"front-seated-contact-sheet.png\">")
        })
    }
}

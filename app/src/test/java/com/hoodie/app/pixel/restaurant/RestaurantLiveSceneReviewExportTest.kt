package com.hoodie.app.pixel.restaurant

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.restaurant.RestaurantMealState
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcIntent
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.VisualDirector
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Capturas candidatas sempre incluem móveis, convidado, Hoodie e iluminação da cena inteira. */
class RestaurantLiveSceneReviewExportTest {
    @Test fun `exporta cenas completas candidatas sem promover goldens`() {
        assumeTrue(System.getProperty("restaurantLiveReview") == "true")
        val root = File("build/pixel-preview/restaurant-live")
        root.deleteRecursively()
        val baseEnv = SceneEnv(DayPeriod.DAY, 12 * 60, variant = 2, daySeed = 0)
        val scene = SceneRegistry[SceneId.RESTAURANT]
        val moments = linkedMapOf(
            "sit" to { brain: com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain, slot: com.hoodie.app.pixel.npc.AmbientNpcSlot, time: Long -> NpcMotionController.movement(slot, time).animation == NpcAnimation.SIT },
            "eating" to { brain: com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain, _: com.hoodie.app.pixel.npc.AmbientNpcSlot, time: Long -> brain.stateAt(time).currentIntent == RestaurantNpcIntent.EAT },
            "drinking" to { brain: com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain, _: com.hoodie.app.pixel.npc.AmbientNpcSlot, time: Long -> brain.stateAt(time).currentIntent == RestaurantNpcIntent.DRINK },
            "phone" to { brain: com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain, _: com.hoodie.app.pixel.npc.AmbientNpcSlot, time: Long -> brain.stateAt(time).currentIntent == RestaurantNpcIntent.CHECK_PHONE },
            "talking" to { brain: com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain, _: com.hoodie.app.pixel.npc.AmbientNpcSlot, time: Long -> brain.stateAt(time).currentIntent == RestaurantNpcIntent.TALK },
            "looking" to { brain: com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain, _: com.hoodie.app.pixel.npc.AmbientNpcSlot, time: Long -> brain.stateAt(time).currentIntent in setOf(RestaurantNpcIntent.LOOK_AROUND, RestaurantNpcIntent.LOOK_WINDOW) },
            "menu" to { brain: com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain, _: com.hoodie.app.pixel.npc.AmbientNpcSlot, time: Long -> brain.stateAt(time).currentIntent == RestaurantNpcIntent.READ_MENU },
            "finished" to { brain: com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain, _: com.hoodie.app.pixel.npc.AmbientNpcSlot, time: Long ->
                val state = brain.stateAt(time)
                state.mealState == RestaurantMealState.FINISHED && state.currentIntent != RestaurantNpcIntent.LEAVE
            },
        )
        val renderer = SceneRenderer()
        val hoodieVisual = VisualDirector.resolve(
            HoodieActivity.EATING,
            UserContextType.LUNCH,
            variant = baseEnv.variant,
        )
        require(hoodieVisual.scene == SceneId.RESTAURANT) {
            "Hoodie comendo no almoço deve usar a cena completa do restaurante"
        }
        val hoodieMachine = AnimationStateMachine(Random(baseEnv.daySeed)).apply { place(hoodieVisual, 1L) }
        val hoodieFrame = requireNotNull(hoodieMachine.frame(6_001L, baseEnv.clockMinute, baseEnv.period))
        val candidates = moments.map { (name, matches) ->
            val found = (0..64).firstNotNullOfOrNull { seed ->
                val env = baseEnv.copy(daySeed = seed)
                val brain = RestaurantNpcDirector.brain(env)
                val slot = RestaurantNpcDirector.plan(env).single()
                val time = (0L..900_000L step 250L).firstOrNull { matches(brain, slot, it) }
                time?.let { Capture(env, it) }
            } ?: error("nenhum momento candidato para $name nas seeds percorridas")
            val env = found.env
            val time = found.timeMs
            val frame = hoodieFrame.copy(
                env = hoodieFrame.env.copy(
                    period = env.period,
                    clockMinute = env.clockMinute,
                    variant = env.variant,
                    daySeed = env.daySeed,
                    flags = hoodieFrame.env.flags + env.flags,
                ),
            )
            val image = renderer.render(frame, time)
            PreviewExport.write(File(root, "$name.png"), image, scale = 3, background = 0xFF2B2E4A.toInt())
            name to Capture(env, time, sha256(image.pixels.joinToString(",")))
        }.toMap()
        val nightCapture = candidates.getValue("eating")
        val nightTime = nightCapture.timeMs
        val nightEnv = nightCapture.env.copy(period = DayPeriod.NIGHT, clockMinute = 22 * 60)
        val nightFrame = hoodieFrame.copy(
            env = hoodieFrame.env.copy(
                period = nightEnv.period,
                clockMinute = nightEnv.clockMinute,
                variant = nightEnv.variant,
                daySeed = nightEnv.daySeed,
                flags = hoodieFrame.env.flags + nightEnv.flags,
            ),
        )
        val night = renderer.render(nightFrame, nightTime)
        val nightDigest = sha256(night.pixels.joinToString(","))
        PreviewExport.write(File(root, "night.png"), night, scale = 3, background = 0xFF2B2E4A.toInt())

        val sheet = com.hoodie.app.pixel.renderer.PixelBuffer(240 * 4, 320 * 2)
        val names = listOf("sit", "eating", "drinking", "phone", "talking", "looking", "menu")
        names.forEachIndexed { index, name ->
            val candidate = candidates.getValue(name)
            val frame = hoodieFrame.copy(
                env = hoodieFrame.env.copy(
                    period = candidate.env.period,
                    clockMinute = candidate.env.clockMinute,
                    variant = candidate.env.variant,
                    daySeed = candidate.env.daySeed,
                    flags = hoodieFrame.env.flags + candidate.env.flags,
                ),
            )
            val tile = renderer.render(frame, candidate.timeMs)
            sheet.blit(tile, (index % 4) * 240, (index / 4) * 320)
        }
        PreviewExport.write(File(root, "restaurant-guest-seat-matrix.png"), sheet, scale = 2, background = 0xFF2B2E4A.toInt())
        val manifest = buildString {
            appendLine("{")
            appendLine("  \"review_state\": \"PENDING_HUMAN_REVIEW\",")
            appendLine("  \"official_goldens_promoted\": false,")
            appendLine("  \"captures\": {")
            val entries = candidates + listOf("night" to Capture(nightCapture.env, nightTime, nightDigest))
            entries.entries.forEachIndexed { index, (name, data) ->
                append("    \"$name\": {\"day_seed\": ${data.env.daySeed}, \"time_ms\": ${data.timeMs}, \"sha256\": \"${data.digest}\"}")
                appendLine(if (index == entries.size - 1) "" else ",")
            }
            appendLine("  }")
            appendLine("}")
        }
        File(root, "review-manifest.json").writeText(manifest)
        assertTrue(File(root, "restaurant-guest-seat-matrix.png").isFile)
        assertTrue("as capturas cobrem todos os sete estados sentados", candidates.keys.containsAll(names))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class Capture(val env: SceneEnv, val timeMs: Long, val digest: String = "")
}

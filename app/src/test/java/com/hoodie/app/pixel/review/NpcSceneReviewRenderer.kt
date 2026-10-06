package com.hoodie.app.pixel.review

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcRenderer
import com.hoodie.app.pixel.npc.NpcDirector
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.NpcScalePolicy
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth

/** Renderiza as cenas públicas reais: fundo, iluminação, props e NPCs do NpcDirector. */
object NpcSceneReviewRenderer {
    val publicScenes = listOf(
        SceneId.OFFICE,
        SceneId.BUS,
        SceneId.TRAIN,
        SceneId.METRO,
        SceneId.RESTAURANT,
        SceneId.SHOPPING,
        SceneId.LEISURE,
    )
    val nightScenes = setOf(SceneId.OFFICE, SceneId.BUS, SceneId.METRO, SceneId.RESTAURANT)

    fun render(sceneId: SceneId, period: DayPeriod, timeMs: Long = 2_400): PixelBuffer {
        val scene = SceneRegistry[sceneId]
        val env = SceneEnv(period = period, clockMinute = if (period == DayPeriod.NIGHT) 1_320 else 600, variant = 0)
        val rendered = SceneRenderer().renderEmpty(scene, env, timeMs)
        return PixelBuffer(rendered.width, rendered.height).also { it.copyFrom(rendered) }
    }

    private val moments = mapOf(
        SceneId.OFFICE to listOf("enter" to 500L, "walk" to 3_000L, "talk" to firstMoment(SceneId.OFFICE, NpcAnimation.TALK), "exit" to 8_750L),
        SceneId.BUS to listOf(
            "phone" to 500L, "window-sleep" to 4_500L,
            "head-drop" to firstMoment(SceneId.BUS, NpcAnimation.SIT_HEAD_DROP),
            "wake" to firstMoment(SceneId.BUS, NpcAnimation.SIT_WAKE),
            "dog-reaction" to firstMoment(SceneId.BUS, NpcAnimation.REACTION),
        ),
        SceneId.TRAIN to listOf("reader" to 1_860L, "window" to 6_150L),
        SceneId.METRO to listOf(
            "ride" to 500L,
            "reaction" to firstMoment(SceneId.METRO, NpcAnimation.REACTION),
            "talk" to firstMoment(SceneId.METRO, NpcAnimation.TALK),
            "ride-late" to 10_000L,
        ),
        SceneId.RESTAURANT to listOf(
            "eating" to firstMoment(SceneId.RESTAURANT, NpcAnimation.SIT_EAT),
            "talk" to firstMoment(SceneId.RESTAURANT, NpcAnimation.TALK),
            "rest" to 18_900L,
        ),
        SceneId.SHOPPING to listOf("standing" to 4_960L, "walking" to 2_860L, "looking" to 860L, "walking-late" to 3_860L),
        SceneId.LEISURE to listOf("walking" to 2_164L, "turning" to 4_864L, "strolling" to 5_264L, "returning" to 6_000L),
    )

    /** Escolhe o primeiro frame em que a própria cena realmente apresenta o estado rotulado. */
    private fun firstMoment(scene: SceneId, animation: NpcAnimation): Long {
        val slots = NpcDirector.plan(scene, variant = 0)
        // Restaurant speech has long deterministic cooldowns; search a full meal before failing the scene gallery.
        return (0L..900_000L step 50L).firstOrNull { time ->
            slots.any { slot ->
                val movement = NpcMotionController.movement(slot, time)
                movement.animation == animation &&
                    (animation != NpcAnimation.TALK || NpcRenderer.shouldSpeak(slot, time))
            }
        } ?: error("nenhum instante $animation encontrado em $scene")
    }

    fun all(): Map<String, PixelBuffer> = buildMap {
        publicScenes.forEach { scene ->
            val slug = scene.name.lowercase()
            moments.getValue(scene).forEach { (moment, time) ->
                put("scene-$slug-day-$moment", render(scene, DayPeriod.DAY, time))
            }
            if (scene in nightScenes) {
                val nightTime = when (scene) {
                    SceneId.OFFICE, SceneId.METRO, SceneId.RESTAURANT -> firstMoment(scene, NpcAnimation.TALK)
                    SceneId.BUS -> firstMoment(scene, NpcAnimation.SIT_HEAD_DROP)
                    else -> 2_400L
                }
                put("scene-$slug-night", render(scene, DayPeriod.NIGHT, nightTime))
            }
        }
    }

    /** Cenários de painter: testa BOOK e PRODUCT integrados ao cenário, antes dos comportamentos. */
    fun propScenes(): Map<String, PixelBuffer> = mapOf(
        "scene-train-rabbit-reader-book" to withNpcProp(
            SceneId.TRAIN, NpcCharacterRegistry.RABBIT_READER,
            CharacterPose(
                facing = Facing.SIDE, legs = Legs.SIT, eyes = Eyes.LOOK_DOWN,
                mouth = Mouth.SMILE, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST,
                item = Item.BOOK, itemInBothHands = true,
            ), centerX = 119, floorY = 240,
        ),
        "scene-shopping-dog-shopper-product" to withNpcProp(
            SceneId.SHOPPING, NpcCharacterRegistry.DOG_SHOPPER,
            CharacterPose(
                facing = Facing.SIDE, legs = Legs.STAND, eyes = Eyes.LOOK_DOWN,
                mouth = Mouth.SMILE, rightArm = Arm.HOLD_CHEST, item = Item.PRODUCT,
            ), centerX = 80, floorY = 212,
        ),
    )

    private fun withNpcProp(
        sceneId: SceneId,
        style: com.hoodie.app.pixel.character.CharacterStyle,
        pose: CharacterPose,
        centerX: Int,
        floorY: Int,
    ): PixelBuffer {
        val scene = SceneRegistry[sceneId]
        val env = SceneEnv(period = DayPeriod.DAY, clockMinute = 600, variant = 0)
        val rendered = SceneRenderer().renderEmpty(scene, env, timeMs = 2_400, includeAmbientNpcs = false)
        val frame = NpcRenderer.scaleFrameForAmbient(
            CharacterPainter.paint(style, pose), NpcScalePolicy.scale(style, NpcDepth.SCENE),
        )
        rendered.blit(frame.image, centerX - frame.anchors.feet.x, floorY - frame.anchors.feet.y)
        return PixelBuffer(rendered.width, rendered.height).also { it.copyFrom(rendered) }
    }
}

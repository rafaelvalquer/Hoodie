package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterFrame
import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.character.CharacterAnchors
import com.hoodie.app.pixel.character.mirrored
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Point
import kotlin.math.roundToInt

/** Definição de cena seleciona identidade registrada, comportamento e fala — nunca anatomia. */
data class AmbientNpcDefinition(
    val id: String,
    val characterStyle: CharacterStyle,
    val behaviorProfile: NpcBehaviorProfile,
    val speechProfile: NpcSpeechProfile? = null,
)

/** Coordenadas usam centro e chão na resolução lógica da cena. */
data class AmbientNpcSlot(
    val definition: AmbientNpcDefinition,
    val x: Int,
    val floorY: Int,
    val baseline: Int,
    val seed: Int,
    val path: NpcPath? = null,
)

object NpcDirector {
    private val exec = definition(NpcCharacterRegistry.BULLDOG_EXEC, NpcAnimation.WALK,
        listOf("Reunião em 5 min.", "Café primeiro.", "Bom dia!"))
    private val rabbit = definition(NpcCharacterRegistry.RABBIT_ANALYST, NpcAnimation.LOOK)
    private val cat = definition(NpcCharacterRegistry.CAT_COLLEAGUE, NpcAnimation.WALK)
    private val mouse = definition(NpcCharacterRegistry.MOUSE_COMMUTER, NpcAnimation.SIT_PHONE)
    private val duck = definition(NpcCharacterRegistry.DUCK_SLEEPY, NpcAnimation.SIT_SLEEP)
    private val dog = definition(NpcCharacterRegistry.DOG_WORKER, NpcAnimation.STAND)
    private val bunny = definition(NpcCharacterRegistry.RABBIT_READER, NpcAnimation.SIT_PHONE)
    private val raccoon = definition(NpcCharacterRegistry.RACCOON_COMMUTER, NpcAnimation.LOOK)
    private val guest = definition(NpcCharacterRegistry.CAT_GUEST, NpcAnimation.SIT_EAT)
    private val shopper = definition(NpcCharacterRegistry.DOG_SHOPPER, NpcAnimation.WALK)
    private val walker = definition(NpcCharacterRegistry.RABBIT_WALKER, NpcAnimation.WALK)

    fun plan(scene: SceneId, variant: Int): List<AmbientNpcSlot> {
        val v = variant and 1
        return when (scene) {
            SceneId.OFFICE -> listOf(
                if (v == 0) AmbientNpcSlot(exec, 211, 204, 204, 11 + v, officePath())
                else AmbientNpcSlot(cat, 211, 204, 204, 11 + v),
                AmbientNpcSlot(rabbit, 34, 204, 204, 31),
            )
            SceneId.BUS -> listOf(AmbientNpcSlot(mouse, 38, 237, 240, 41), AmbientNpcSlot(duck, 202, 237, 240, 42), AmbientNpcSlot(dog, 119, 221, 222, 43))
            SceneId.TRAIN -> listOf(AmbientNpcSlot(bunny, 43, 240, 242, 51), AmbientNpcSlot(raccoon, 193, 240, 242, 52))
            SceneId.METRO -> listOf(AmbientNpcSlot(mouse, 44, 240, 242, 61), AmbientNpcSlot(dog, 194, 222, 224, 62), AmbientNpcSlot(duck, 119, 240, 242, 63))
            SceneId.RESTAURANT -> listOf(AmbientNpcSlot(guest, 207, 251, 252, 71))
            SceneId.SHOPPING -> listOf(AmbientNpcSlot(shopper, 207, 212, 214, 81))
            SceneId.LEISURE -> listOf(AmbientNpcSlot(walker, 204, 252, 254, 91), AmbientNpcSlot(cat, 38, 252, 254, 92))
            else -> emptyList()
        }
    }

    private fun definition(style: CharacterStyle, animation: NpcAnimation, lines: List<String> = emptyList()) =
        AmbientNpcDefinition(style.id, style, NpcBehaviorProfile(animation, SpeciesMotionProfiles.forCharacter(style)),
            lines.takeIf { it.isNotEmpty() }?.let(::NpcSpeechProfile))

    private fun officePath() = NpcPath(
        points = listOf(
            NpcPathPoint(-24, 204),
            NpcPathPoint(64, 204, NpcAnimation.LOOK, 1_000),
            NpcPathPoint(122, 204, NpcAnimation.TALK, 2_100),
            NpcPathPoint(192, 204),
            NpcPathPoint(264, 204),
        ),
        millisPerPixel = 24,
    )
}

/** Renderer de cena apenas resolve movimento, alinha pés e delega os pixels do personagem. */
object NpcRenderer {
    const val AMBIENT_SCALE = 0.8f

    fun draw(b: PixelBuffer, slot: AmbientNpcSlot, timeMs: Long) {
        val movement = NpcMotionController.movement(slot, timeMs)
        val profile = slot.definition.behaviorProfile.motion
        val pose = NpcMotionController.pose(movement.animation, movement.localTimeMs, slot.seed, profile)
        val paintedFrame = CharacterPainter.paint(slot.definition.characterStyle, pose, profile.renderMotion()).let {
            if (movement.facingRight && pose.facing == Facing.SIDE) it.mirrored() else it
        }
        val frame = scaleFrameForAmbient(paintedFrame)
        val left = movement.x - frame.anchors.feet.x
        val top = movement.floorY - frame.anchors.feet.y
        b.blit(frame.image, left, top)
        if (movement.animation == NpcAnimation.TALK) slot.definition.speechProfile?.let { speech ->
            NpcSpeechBubbleRenderer.draw(
                b, speech, movement.x, movement.floorY, slot.seed, timeMs,
                movement.localTimeMs, frame.anchors.head.y,
                (slot.definition.characterStyle.species.headHeight * AMBIENT_SCALE).roundToInt().coerceAtLeast(1),
            )
        }
    }

    /** Downsamples around the foot anchor, preserving crisp pixels and scene placement. */
    internal fun scaleFrameForAmbient(frame: CharacterFrame): CharacterFrame {
        val scale = AMBIENT_SCALE
        val feet = frame.anchors.feet
        val source = frame.image
        val scaled = PixelBuffer(source.width, source.height)
        for (y in 0 until scaled.height) {
            val sourceY = (feet.y + (y - feet.y) / scale).roundToInt()
            if (sourceY !in 0 until source.height) continue
            for (x in 0 until scaled.width) {
                val sourceX = (feet.x + (x - feet.x) / scale).roundToInt()
                if (sourceX in 0 until source.width) {
                    val color = source[sourceX, sourceY]
                    if (color ushr 24 != 0) scaled.set(x, y, color)
                }
            }
        }
        fun shrink(point: Point) = Point(
            (feet.x + (point.x - feet.x) * scale).roundToInt().coerceIn(0, scaled.width - 1),
            (feet.y + (point.y - feet.y) * scale).roundToInt().coerceIn(0, scaled.height - 1),
        )
        val anchors = frame.anchors
        return CharacterFrame(
            scaled,
            CharacterAnchors(
                feet = feet,
                head = shrink(anchors.head),
                leftHand = shrink(anchors.leftHand),
                rightHand = shrink(anchors.rightHand),
                mouth = shrink(anchors.mouth),
                back = shrink(anchors.back),
            ),
        )
    }
}

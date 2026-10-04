package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterAnchors
import com.hoodie.app.pixel.character.CharacterFrame
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterStyle
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
    /** Escala por profundidade (fundo .75, meio .80/.85, frente .90). */
    val scale: Float = AmbientScale.DEFAULT,
    /** Para NPCs parados de perfil: olhando para a direita. */
    val facingRight: Boolean = false,
) {
    init { require(scale in AmbientScale.allowed) { "escala $scale fora de ${AmbientScale.allowed}" } }
}

object NpcDirector {
    private val exec = definition(
        NpcCharacterRegistry.BULLDOG_EXEC, NpcAnimation.WALK,
        lines = listOf("Reunião em 5 min.", "Café primeiro.", "Bom dia!"),
    )
    /** Escritório: olhar → café → celular → olhar. */
    private val rabbit = definition(
        NpcCharacterRegistry.RABBIT_ANALYST, NpcAnimation.LOOK,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.LOOK, 2_600), NpcStep(NpcAnimation.STAND_COFFEE, 3_000),
            NpcStep(NpcAnimation.STAND_PHONE, 2_800), NpcStep(NpcAnimation.LOOK, 2_000),
        ),
    )
    /** Colega que anda, para, olha e volta. */
    private val cat = definition(NpcCharacterRegistry.CAT_COLLEAGUE, NpcAnimation.WALK)
    /** Ônibus: celular → janela → celular. */
    private val mouse = definition(
        NpcCharacterRegistry.MOUSE_COMMUTER, NpcAnimation.SIT_PHONE,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.SIT_PHONE, 4_000), NpcStep(NpcAnimation.LOOK_WINDOW, 2_200, seated = true),
            NpcStep(NpcAnimation.SIT_PHONE, 3_000),
        ),
    )
    /** Dorme → cabeça cai → acorda de leve → dorme. */
    private val duck = definition(
        NpcCharacterRegistry.DUCK_SLEEPY, NpcAnimation.SIT_SLEEP,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.SIT_SLEEP, 3_600), NpcStep(NpcAnimation.SIT_HEAD_DROP, 1_600),
            NpcStep(NpcAnimation.SIT_WAKE, 1_200), NpcStep(NpcAnimation.SIT_SLEEP, 3_000),
        ),
    )
    /** Em pé na barra → olha → tranco do ônibus → segura de novo. */
    private val dog = definition(
        NpcCharacterRegistry.DOG_WORKER, NpcAnimation.STAND,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.STAND, 3_000), NpcStep(NpcAnimation.LOOK, 2_000),
            NpcStep(NpcAnimation.REACTION, 900, reaction = NpcReaction.SURPRISED), NpcStep(NpcAnimation.STAND, 2_000),
        ),
    )
    private val bunny = definition(
        NpcCharacterRegistry.RABBIT_READER, NpcAnimation.SIT_PHONE,
        NpcBehaviorSequence.of(NpcStep(NpcAnimation.SIT_PHONE, 4_400), NpcStep(NpcAnimation.LOOK_WINDOW, 2_000, seated = true)),
    )
    /** Janela → celular → janela. */
    private val raccoon = definition(
        NpcCharacterRegistry.RACCOON_COMMUTER, NpcAnimation.LOOK_WINDOW,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.LOOK_WINDOW, 3_000), NpcStep(NpcAnimation.STAND_PHONE, 2_400),
            NpcStep(NpcAnimation.LOOK_WINDOW, 2_600),
        ),
    )
    /** Restaurante: come, comenta algo, volta a comer. */
    private val guest = definition(
        NpcCharacterRegistry.CAT_GUEST, NpcAnimation.SIT_EAT,
        NpcBehaviorSequence.of(NpcStep(NpcAnimation.SIT_EAT, 6_000), NpcStep(NpcAnimation.TALK, 1_800, seated = true, facing = Facing.SIDE)),
    )
    private val shopper = definition(NpcCharacterRegistry.DOG_SHOPPER, NpcAnimation.WALK)
    private val walker = definition(NpcCharacterRegistry.RABBIT_WALKER, NpcAnimation.WALK)

    fun plan(scene: SceneId, variant: Int): List<AmbientNpcSlot> {
        val v = variant and 1
        return when (scene) {
            SceneId.OFFICE -> listOf(
                if (v == 0) AmbientNpcSlot(exec, 211, 204, 204, 11 + v, officePath())
                else AmbientNpcSlot(cat, 211, 204, 204, 11 + v, colleaguePath()),
                AmbientNpcSlot(rabbit, 34, 204, 204, 31),
            )
            SceneId.BUS -> listOf(
                AmbientNpcSlot(mouse, 38, 237, 240, 41, facingRight = true),
                AmbientNpcSlot(duck, 202, 237, 240, 42),
                AmbientNpcSlot(dog, 119, 221, 222, 43, scale = AmbientScale.BACKGROUND),
            )
            SceneId.TRAIN -> listOf(
                AmbientNpcSlot(bunny, 43, 240, 242, 51, facingRight = true),
                AmbientNpcSlot(raccoon, 193, 240, 242, 52),
            )
            SceneId.METRO -> listOf(
                AmbientNpcSlot(mouse, 44, 240, 242, 61, facingRight = true),
                AmbientNpcSlot(dog, 194, 222, 224, 62, scale = AmbientScale.BACKGROUND),
                AmbientNpcSlot(duck, 119, 240, 242, 63),
            )
            SceneId.RESTAURANT -> listOf(AmbientNpcSlot(guest, 207, 251, 252, 71))
            SceneId.SHOPPING -> listOf(AmbientNpcSlot(shopper, 207, 212, 214, 81, shopperPath(), scale = AmbientScale.BACKGROUND))
            SceneId.LEISURE -> listOf(
                AmbientNpcSlot(walker, 204, 252, 254, 91, walkerPath(), scale = AmbientScale.MIDGROUND),
                AmbientNpcSlot(cat, 38, 252, 254, 92, strollPath(), scale = AmbientScale.MIDGROUND),
            )
            else -> emptyList()
        }
    }

    private fun definition(
        style: CharacterStyle,
        animation: NpcAnimation,
        sequence: NpcBehaviorSequence? = null,
        lines: List<String> = emptyList(),
    ) = AmbientNpcDefinition(
        style.id, style, NpcBehaviorProfile(animation, SpeciesMotionProfiles.forCharacter(style), sequence),
        lines.takeIf { it.isNotEmpty() }?.let(::NpcSpeechProfile),
    )

    /** Bulldog: ENTRA pela esquerda → anda → olha o Hoodie → fala → espera → anda → SAI pela direita. */
    private fun officePath() = NpcPath(
        points = listOf(
            NpcPathPoint(-24, 204),
            NpcPathPoint(64, 204, NpcAnimation.LOOK, 1_000),
            NpcPathPoint(122, 204, NpcAnimation.TALK, 1_500),
            NpcPathPoint(122, 204, NpcAnimation.IDLE, 600),
            NpcPathPoint(192, 204),
            NpcPathPoint(264, 204),
        ),
        millisPerPixel = 24,
    )

    /** Gato colega: anda → para → olha → volta (com virada). */
    private fun colleaguePath() = NpcPath(
        points = listOf(
            NpcPathPoint(214, 204, NpcAnimation.IDLE, 1_800),
            NpcPathPoint(150, 204, NpcAnimation.LOOK, 1_600),
            NpcPathPoint(214, 204),
        ),
        millisPerPixel = 30,
    )

    private fun shopperPath() = NpcPath(
        points = listOf(
            NpcPathPoint(214, 212, NpcAnimation.IDLE, 1_400),
            NpcPathPoint(150, 212, NpcAnimation.LOOK, 2_000),
            NpcPathPoint(214, 212),
        ),
        millisPerPixel = 30,
    )

    private fun walkerPath() = NpcPath(
        points = listOf(
            NpcPathPoint(212, 252, NpcAnimation.IDLE, 1_200),
            NpcPathPoint(150, 252, NpcAnimation.LOOK, 1_800),
            NpcPathPoint(212, 252),
        ),
        millisPerPixel = 26,
    )

    private fun strollPath() = NpcPath(
        points = listOf(
            NpcPathPoint(30, 252, NpcAnimation.IDLE, 1_600),
            NpcPathPoint(78, 252, NpcAnimation.LOOK, 1_400),
            NpcPathPoint(30, 252),
        ),
        millisPerPixel = 32,
    )
}

/** Renderer de cena: resolve movimento, pinta a pose, escala por profundidade, sombra no chão e fala. */
object NpcRenderer {
    const val AMBIENT_SCALE = AmbientScale.DEFAULT
    private const val SHADOW = 0xFF1A1C33.toInt()

    fun draw(b: PixelBuffer, slot: AmbientNpcSlot, timeMs: Long) {
        val movement = NpcMotionController.movement(slot, timeMs)
        val frameData = NpcMotionController.frame(slot, timeMs, movement)
        val pose = frameData.pose
        val paintedFrame = CharacterPainter.paint(slot.definition.characterStyle, pose, frameData.motion).let {
            if (movement.facingRight && pose.facing == Facing.SIDE) it.mirrored() else it
        }
        val frame = scaleFrameForAmbient(paintedFrame, slot.scale)
        val left = movement.x - frame.anchors.feet.x
        val top = movement.floorY - frame.anchors.feet.y
        drawGroundShadow(b, movement.x, movement.floorY, slot.definition.characterStyle, slot.scale)
        b.blit(frame.image, left, top)
        if (movement.animation == NpcAnimation.TALK) slot.definition.speechProfile?.let { speech ->
            NpcSpeechBubbleRenderer.draw(
                b, speech, movement.x, movement.floorY, slot.seed, timeMs,
                movement.localTimeMs, frame.anchors.head.y,
                (slot.definition.characterStyle.species.headHeight * slot.scale).roundToInt().coerceAtLeast(1),
            )
        }
    }

    /** Sombra suave sob os pés: escurece o cenário (não acrescenta cor ao personagem). */
    internal fun drawGroundShadow(b: PixelBuffer, x: Int, floorY: Int, style: CharacterStyle, scale: Float) {
        val half = ((style.artProfile.proportions.hipWidth + 6) * scale / 2f).roundToInt()
        for (dy in -1..1) {
            val w = if (dy == 0) half else half - 2
            for (dx in -w..w) {
                val px = x + dx; val py = floorY + dy
                if (px in 0 until b.width && py in 0 until b.height) b.set(px, py, PixelBuffer.mix(b[px, py], SHADOW, 0.28f))
            }
        }
    }

    fun scaleFrameForAmbient(frame: CharacterFrame): CharacterFrame = scaleFrameForAmbient(frame, AMBIENT_SCALE)

    /** Reduz em torno da âncora dos pés, com pixels nítidos (nearest-neighbor). */
    fun scaleFrameForAmbient(frame: CharacterFrame, scale: Float): CharacterFrame {
        if (scale >= 0.999f) return frame
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

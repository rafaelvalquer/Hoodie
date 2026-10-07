package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.animation.AnimationEvent
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.abs

/**
 * Direção do personagem. Só existem três vistas desenhadas (frente, costas,
 * lado); RIGHT é o espelho de LEFT.
 */
enum class Direction(val facing: Facing, val flipX: Boolean) {
    FRONT(Facing.FRONT, false),
    BACK(Facing.BACK, false),
    LEFT(Facing.SIDE, false),
    RIGHT(Facing.SIDE, true);

    companion object {
        /** Direção de um deslocamento em tela (y cresce para baixo). */
        fun of(dx: Float, dy: Float): Direction = when {
            abs(dx) >= abs(dy) && dx != 0f -> if (dx < 0) LEFT else RIGHT
            dy < 0 -> BACK
            else -> FRONT
        }
    }
}

data class Point(val x: Int, val y: Int) {
    fun mirror(width: Int) = Point(width - 1 - x, y)
}

/** Pontos de encaixe de um frame (coordenadas dentro do sprite). */
data class SpriteAnchors(
    val rightHand: Point,
    val leftHand: Point,
    val head: Point,
    val back: Point,
    /** Ponto de contato com o chão — é ele que é alinhado ao spot da cena. */
    val feet: Point,
    /** Ponto de encaixe da bacia em bancos; null para posturas ancoradas pelos pés. */
    val seatHip: Point? = null,
) {
    /**
     * Espelhar troca as mãos de lado. Os pés marcam a linha central do corpo
     * (entre os pixels 23 e 24 num sprite de 48), então espelham por `width - x`:
     * virar de lado não desloca o gato 1 px.
     */
    fun mirror(width: Int) = SpriteAnchors(
        rightHand = leftHand.mirror(width), leftHand = rightHand.mirror(width),
        head = head.mirror(width), back = back.mirror(width), feet = Point(width - feet.x, feet.y),
        seatHip = seatHip?.mirror(width),
    )

    operator fun get(a: Anchor): Point = when (a) {
        Anchor.RIGHT_HAND -> rightHand
        Anchor.LEFT_HAND -> leftHand
        Anchor.HEAD -> head
        Anchor.BACK -> back
        Anchor.FEET -> feet
        Anchor.SEAT_HIP -> seatHip ?: feet
    }
}

enum class Anchor { RIGHT_HAND, LEFT_HAND, HEAD, BACK, FEET, SEAT_HIP }

/** Um frame pronto para desenhar, venha de sprite sheet ou do pintor procedural. */
data class SpriteFrame(
    val image: PixelBuffer,
    val anchors: SpriteAnchors,
    val durationMs: Long,
    val events: Set<AnimationEvent> = emptySet(),
    /** Item a desenhar na âncora da mão (null quando o item já está na imagem). */
    val itemOverlay: Item? = null,
    val source: String = "procedural",
)

enum class Posture { STANDING, SITTING, SIT_FRONT }

/**
 * Ajustes por cima do clip: piscada, olhar, expressão e orelhas. Só substituem
 * olhos "neutros" — uma pose que já fecha os olhos (gole de café) não é trocada.
 */
data class PoseOverlay(
    val blink: Eyes? = null,
    val gaze: Eyes? = null,
    val expression: Expression? = null,
    val ears: Ears? = null,
) {
    fun apply(pose: HoodiePose): HoodiePose {
        if (pose.headOnly) return pose
        var p = pose
        expression?.eyes?.let { if (p.eyes == Eyes.OPEN) p = p.copy(eyes = it) }
        expression?.ears?.let { if (p.ears == Ears.NORMAL) p = p.copy(ears = it) }
        gaze?.let { if (p.eyes in GAZEABLE) p = p.copy(eyes = it) }
        blink?.let { if (p.eyes in BLINKABLE) p = p.copy(eyes = it) }
        ears?.let { if (p.ears == Ears.NORMAL || p.ears == Ears.RELAXED) p = p.copy(ears = it) }
        return p
    }

    companion object {
        val NONE = PoseOverlay()
        private val GAZEABLE = setOf(Eyes.OPEN, Eyes.LOOK_LEFT, Eyes.LOOK_RIGHT, Eyes.LOOK_UP, Eyes.LOOK_DOWN)
        private val BLINKABLE = GAZEABLE + setOf(Eyes.FOCUSED, Eyes.SLEEPY)
    }
}

data class SpriteRequest(
    val animation: com.hoodie.app.pixel.animation.AnimationId,
    val direction: Direction = Direction.FRONT,
    val frameIndex: Int = 0,
    val posture: Posture = Posture.STANDING,
    val overlay: PoseOverlay = PoseOverlay.NONE,
)

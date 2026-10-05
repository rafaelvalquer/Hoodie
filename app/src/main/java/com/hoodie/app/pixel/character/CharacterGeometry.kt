package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs

/** Medidas resolvidas no canvas lógico para uma escala e pose compartilhadas. */
data class CharacterGeometry(
    val torsoOffsetY: Int,
    val lift: Int,
    /** Extensão horizontal do tronco (ombros). */
    val bodyLeft: Int,
    val bodyRight: Int,
    /** Linha dos ombros. */
    val bodyTop: Int,
    /** Barra da roupa/quadril. */
    val bodyBottom: Int,
    val headTop: Int,
    val headWidth: Int,
) {
    companion object {
        /** Geometria do Hoodie legado expressa pelo mesmo contrato, sem normalizar os seus offsets. */
        fun resolveHoodie(pose: CharacterPose): CharacterGeometry {
            val torsoOffsetY = (if (pose.legs == Legs.SIT) 5 else 0) + pose.bob
            val side = pose.facing == Facing.SIDE
            val bodyLeft = if (side) 13 else 11
            val bodyRight = 36
            val bodyTop = (if (side) 28 else 33) + torsoOffsetY
            val bodyBottom = (if (side) 55 else 60) + torsoOffsetY
            return CharacterGeometry(
                torsoOffsetY = torsoOffsetY,
                lift = pose.lift,
                bodyLeft = bodyLeft,
                bodyRight = bodyRight,
                bodyTop = bodyTop,
                bodyBottom = bodyBottom,
                headTop = 9 + torsoOffsetY + pose.headDy,
                headWidth = 34,
            )
        }

        /** Geometria V3 dos NPCs, derivada de [BodyLayout]. */
        fun resolve(style: CharacterStyle, pose: CharacterPose): CharacterGeometry {
            val l = BodyLayout.resolve(style, pose)
            return CharacterGeometry(
                torsoOffsetY = l.drop, lift = l.lift,
                bodyLeft = l.shoulderLeft, bodyRight = l.shoulderRight,
                bodyTop = l.shoulderY, bodyBottom = l.torsoBottom,
                headTop = l.headTop, headWidth = l.headRight - l.headLeft + 1,
            )
        }
    }
}

/**
 * Esqueleto V3 de um quadro: cabeça → pescoço → ombros → tronco → quadril → pernas → pés.
 * Tudo em coordenadas do canvas, já com pose (sentar, bob, salto) aplicada. Vista lateral
 * é autorada olhando para a DIREITA e espelhada no fim (convenção do Hoodie).
 */
data class BodyLayout(
    val facing: Facing,
    val cx: Int,
    val drop: Int,
    val lift: Int,
    val sitting: Boolean,
    val groundY: Int,
    val ankleY: Int,
    val hipY: Int,
    val hipLeft: Int,
    val hipRight: Int,
    val torsoBottom: Int,
    val shoulderY: Int,
    val shoulderLeft: Int,
    val shoulderRight: Int,
    val neckY: Int,
    val headLeft: Int,
    val headTop: Int,
    val headRight: Int,
    val headBottom: Int,
    val headCx: Int,
    val eyeY: Int,
) {
    val headWidth get() = headRight - headLeft + 1
    val headHeight get() = headBottom - headTop + 1

    companion object {
        /** Quanto o tronco desce ao sentar (as coxas dobram para frente). */
        const val SIT_DROP = 6

        fun resolve(style: CharacterStyle, pose: CharacterPose, sway: Int = 0, sitDrop: Int? = null): BodyLayout {
            val pr = style.artProfile.proportions
            val face = style.artProfile.face
            val sitting = pose.legs == Legs.SIT
            val lift = pose.lift.coerceIn(0, 8)
            val drop = (sitDrop ?: if (sitting) SIT_DROP else 0) + pose.bob.coerceIn(-2, 2)
            val groundY = CharacterCanvas.GROUND_Y - lift
            val ankleY = groundY - ProportionProfile.FOOT_HEIGHT + 1
            val side = pose.facing == Facing.SIDE
            val cx = CharacterCanvas.CENTER_X + sway.coerceIn(-2, 2)

            val hipY = ankleY - pr.legLength + drop
            val torsoBottom = hipY + 2
            val shoulderY = hipY - pr.torsoHeight + 2
            // De perfil o tronco mostra a profundidade (~2/3 da largura frontal).
            val shoulderW = if (side) (pr.shoulderWidth * 2 / 3).coerceAtLeast(14) else pr.shoulderWidth
            val hipW = if (side) (pr.hipWidth * 2 / 3).coerceAtLeast(13) else pr.hipWidth
            val bodyCx = if (side) cx - 1 else cx
            val shoulderLeft = bodyCx - shoulderW / 2
            val hipLeft = bodyCx - hipW / 2

            val headDy = pose.headDy.coerceIn(-3, 3)
            val headBottom = shoulderY + ProportionProfile.CHIN_OVERLAP + headDy
            // O topo nunca corta: orelhas longas reservam espaço acima da cabeça.
            val earRoom = style.species.earClearance
            val headTop = (headBottom - pr.headHeight + 1).coerceAtLeast(1 + earRoom)
            val headW = if (side) (pr.headWidth * 5 / 6) else pr.headWidth
            val headCx = (if (side) cx + 1 else cx) + pose.headTilt.coerceIn(-1, 1)
            val headLeft = headCx - headW / 2
            val headRight = headLeft + headW - 1
            val eyeY = headTop + ((headBottom - headTop) * face.eyeLine).toInt()
            return BodyLayout(
                facing = pose.facing, cx = cx, drop = drop, lift = lift, sitting = sitting,
                groundY = groundY, ankleY = ankleY,
                hipY = hipY, hipLeft = hipLeft, hipRight = hipLeft + hipW - 1, torsoBottom = torsoBottom,
                shoulderY = shoulderY, shoulderLeft = shoulderLeft, shoulderRight = shoulderLeft + shoulderW - 1,
                neckY = shoulderY - 1,
                headLeft = headLeft, headTop = headTop, headRight = headRight, headBottom = headBottom,
                headCx = headCx, eyeY = eyeY,
            )
        }
    }
}

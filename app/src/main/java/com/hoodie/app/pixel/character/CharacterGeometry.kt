package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.character.species.EarStyle
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs

/** Medidas resolvidas no canvas lógico para uma escala e pose compartilhadas. */
data class CharacterGeometry(
    val torsoOffsetY: Int,
    val lift: Int,
    val bodyLeft: Int,
    val bodyRight: Int,
    val bodyTop: Int,
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

        fun resolve(style: CharacterStyle, pose: CharacterPose): CharacterGeometry {
            val torsoOffsetY = (if (pose.legs == Legs.SIT) 5 else 0) + pose.bob.coerceIn(-2, 2)
            val lift = pose.lift.coerceIn(0, 8)
            val frontBodyWidth = style.scale.bodyWidth.coerceIn(22, 32)
            val bodyWidth = if (pose.facing == Facing.SIDE) {
                (frontBodyWidth * 2 / 3).coerceAtLeast(18)
            } else {
                frontBodyWidth
            }
            val bodyLeft = CharacterCanvas.CENTER_X - bodyWidth / 2
            val bodyRight = bodyLeft + bodyWidth - 1
            val bodyTop = 29 + torsoOffsetY - lift
            val bodyBottom = bodyTop + style.scale.bodyHeight.coerceIn(24, 31) - 1
            // A few idle/look frames move the head up; clamp only poses that
            // would otherwise clip the skull, preserving the normal head placement.
            val nominalHeadTop = (if (style.species.earStyle == EarStyle.LONG) 12 else 2) + torsoOffsetY - lift
            val headTop = nominalHeadTop.coerceAtLeast(1 - pose.headDy)
            val headWidth = (style.species.headWidth * style.scale.headScale).toInt().coerceIn(22, 36)
            return CharacterGeometry(
                torsoOffsetY, lift, bodyLeft, bodyRight, bodyTop, bodyBottom, headTop, headWidth,
            )
        }
    }
}

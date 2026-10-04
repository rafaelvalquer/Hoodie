package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.character.species.MuzzleStyle
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Mouth

/** Expressões da boca são aplicadas depois do focinho para permanecerem legíveis em cada espécie. */
internal object CharacterMouthPainter {
    fun draw(
        buffer: PixelBuffer,
        style: CharacterStyle,
        pose: CharacterPose,
        headTop: Int,
        headCenterX: Int,
    ) {
        if (pose.facing == Facing.BACK) return
        val side = pose.facing == Facing.SIDE
        val x = if (side) headCenterX + 8 else headCenterX - 2
        val y = headTop + style.species.headHeight - 5 + pose.headDy

        if (style.mouthStyle == MouthStyle.BEAK || style.species.muzzleStyle == MuzzleStyle.BEAK) {
            drawBeakExpression(buffer, style, pose, x, headTop + 20 + pose.headDy)
            return
        }

        if (style.mouthStyle == MouthStyle.MUZZLE && style.species.muzzleStyle == MuzzleStyle.BROAD) {
            drawBroadMuzzleExpression(buffer, style, pose, headTop, headCenterX, side)
            return
        }

        when (pose.mouth) {
            Mouth.OPEN -> buffer.box(x, y, x + 3, y + 3, style.palette.outline)
            Mouth.FLAT -> buffer.hline(x, x + 4, y + 1, style.palette.outline)
            Mouth.CHEW -> {
                buffer.set(x, y + 1, style.palette.outline)
                buffer.set(x + 4, y + 1, style.palette.outline)
            }
            Mouth.SMILE -> Unit
        }
    }

    private fun drawBeakExpression(buffer: PixelBuffer, style: CharacterStyle, pose: CharacterPose, x: Int, y: Int) {
        when (pose.mouth) {
            Mouth.OPEN -> {
                buffer.box(x - 2, y, x + 5, y + 1, style.palette.outline)
                buffer.box(x - 1, y + 2, x + 4, y + 4, style.palette.accent)
                buffer.hline(x, x + 3, y + 5, style.palette.outline)
            }
            Mouth.FLAT -> buffer.hline(x - 1, x + 4, y + 2, style.palette.outline)
            Mouth.CHEW -> {
                buffer.set(x, y + 2, style.palette.outline)
                buffer.set(x + 3, y + 2, style.palette.outline)
            }
            Mouth.SMILE -> Unit
        }
    }

    /** A boca ocupa a base do focinho, com proporção curta para combinar com a mandíbula do Bulldog. */
    private fun drawBroadMuzzleExpression(
        buffer: PixelBuffer,
        style: CharacterStyle,
        pose: CharacterPose,
        headTop: Int,
        headCenterX: Int,
        side: Boolean,
    ) {
        val x = if (side) headCenterX + 9 else headCenterX - 4
        val y = headTop + 24 + pose.headDy
        when (pose.mouth) {
            Mouth.OPEN -> {
                buffer.box(x, y, x + 6, y + 3, style.palette.outline)
                buffer.box(x + 1, y + 1, x + 5, y + 2, style.palette.furDark)
                buffer.box(x + 2, y + 3, x + 4, y + 3, style.palette.accent)
            }
            Mouth.FLAT -> buffer.hline(x + 1, x + 5, y + 1, style.palette.outline)
            Mouth.CHEW -> {
                buffer.set(x + 1, y + 1, style.palette.outline)
                buffer.set(x + 5, y + 1, style.palette.outline)
            }
            Mouth.SMILE -> Unit
        }
    }
}

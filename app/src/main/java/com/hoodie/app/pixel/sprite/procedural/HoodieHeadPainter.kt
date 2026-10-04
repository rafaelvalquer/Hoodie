package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.HoodiePainter.Part
import com.hoodie.app.pixel.sprite.HoodiePainter.WIDTH
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shape
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shapeUnion
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.recolor
import com.hoodie.app.pixel.character.CharacterEyePainter

/** Procedural HoodieHeadPainter; preserves drawing order and semantic part ownership. */
internal object HoodieHeadPainter {
    fun drawHeadFront(b: PixelBuffer, p: HoodiePose, up: Int) {
        val tx = if (p.headOnly) p.headTilt * 2 else 0
        part(b, Part.EARS) { drawEars(b, p.ears, up, tx) }
        val head = R(7 + tx, 9, 40 + tx, 33, 8, round = true).dy(up)
        part(b, Part.HEAD) {
        shape(b, head, HoodiePalette.FUR)
        recolor(b, head.x0 + 1, head.y1 - 2, head.x1 - 1, head.y1 - 1, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        recolor(b, 35 + tx, head.y0 + 4, 39 + tx, head.y1 - 3, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        b.hline(13 + tx, 19 + tx, head.y0 + 2, HoodiePalette.FUR_LIGHT)
        b.hline(11 + tx, 14 + tx, head.y0 + 3, HoodiePalette.FUR_LIGHT)
        b.set(head.x0, 26 + up, HoodiePalette.FUR); b.set(head.x1, 26 + up, HoodiePalette.FUR_SHADE)

        drawEyes(b, p.eyes, up, intArrayOf(15 + tx, 30 + tx))
        drawMouth(b, p.mouth, 23 + tx, up)
        if (p.blush || p.eyes == Eyes.HAPPY) {
            b.hline(11 + tx, 13 + tx, 24 + up, HoodiePalette.BLUSH); b.hline(34 + tx, 36 + tx, 24 + up, HoodiePalette.BLUSH)
        }
        }
    }

    fun drawMouth(b: PixelBuffer, mouth: Mouth, nx: Int, up: Int) {
        b.hline(nx, nx + 1, 23 + up, HoodiePalette.NOSE)
        when (mouth) {
            Mouth.SMILE -> {
                b.set(nx - 2, 24 + up, HoodiePalette.NOSE); b.set(nx - 1, 25 + up, HoodiePalette.NOSE)
                b.hline(nx, nx + 1, 24 + up, HoodiePalette.NOSE)
                b.set(nx + 2, 25 + up, HoodiePalette.NOSE); b.set(nx + 3, 24 + up, HoodiePalette.NOSE)
            }
            Mouth.OPEN -> {
                b.box(nx - 1, 24 + up, nx + 2, 27 + up, HoodiePalette.NOSE)
                b.hline(nx, nx + 1, 26 + up, HoodiePalette.TONGUE)
            }
            Mouth.FLAT -> b.hline(nx - 1, nx + 2, 25 + up, HoodiePalette.NOSE)
            Mouth.CHEW -> {
                b.hline(nx - 1, nx + 2, 25 + up, HoodiePalette.NOSE); b.set(nx - 2, 24 + up, HoodiePalette.NOSE); b.set(nx + 3, 24 + up, HoodiePalette.NOSE)
            }
        }
    }

    fun drawEars(b: PixelBuffer, ears: Ears, up: Int, tx: Int) {
        val (ldx, ldy, rdx, rdy) = when (ears) {
            Ears.NORMAL -> listOf(0, 0, 0, 0)
            Ears.ALERT -> listOf(0, -1, 0, -1)
            Ears.RELAXED -> listOf(-1, 1, 1, 1)
            Ears.DOWN -> listOf(-2, 3, 2, 3)
            Ears.TWITCH_LEFT -> listOf(-1, 1, 0, 0)
            Ears.TWITCH_RIGHT -> listOf(0, 0, 1, 1)
        }
        ear(b, mirror = false, dx = ldx + tx, dy = up + ldy)
        ear(b, mirror = true, dx = rdx + tx, dy = up + rdy)
    }

    fun ear(b: PixelBuffer, mirror: Boolean, dx: Int, dy: Int, fill: Int = HoodiePalette.FUR, inner: Int? = HoodiePalette.INNER_EAR) {
        fun mx(x: Int) = if (mirror) WIDTH - 1 - x else x
        val top = 2; val bottom = 13
        for (y in top..bottom) {
            val l = 10 - (y - top) * 2 / 11
            val r = 11 + (y - top) * 9 / 11
            for (x in l - 1..r + 1) b.set(mx(x) + dx, y + dy, HoodiePalette.OUTLINE)
        }
        b.hline(mx(10) + dx, mx(11) + dx, top - 1 + dy, HoodiePalette.OUTLINE)
        for (y in top..bottom) {
            val l = 10 - (y - top) * 2 / 11
            val r = 11 + (y - top) * 9 / 11
            for (x in l..r) b.set(mx(x) + dx, y + dy, fill)
        }
        if (inner != null) for (y in 6..12) {
            val l = 11 - (y - 6) / 6
            val r = 11 + (y - 6) * 5 / 7
            for (x in l..r) b.set(mx(x) + dx, y + dy, inner)
        }
    }

    fun drawEyes(b: PixelBuffer, eyes: Eyes, up: Int, xs: IntArray) {
        CharacterEyePainter.drawLegacyHoodie(b, eyes, up, xs, HoodiePalette.EYE, HoodiePalette.WHITE, HoodiePalette.FUR_SHADE)
    }

    fun drawHeadBack(b: PixelBuffer, p: HoodiePose, up: Int): Int {
        val hu = up + p.headDy
        val (ldy, rdy) = when (p.ears) { Ears.ALERT -> -1 to -1; Ears.DOWN -> 3 to 3; Ears.RELAXED -> 1 to 1; Ears.TWITCH_LEFT -> 1 to 0; Ears.TWITCH_RIGHT -> 0 to 1; else -> 0 to 0 }
        part(b, Part.EARS) {
            ear(b, mirror = false, dx = 0, dy = hu + ldy, inner = HoodiePalette.FUR_SHADE)
            ear(b, mirror = true, dx = 0, dy = hu + rdy, inner = HoodiePalette.FUR_SHADE)
        }
        val head = R(7, 9, 40, 33, 8, round = true).dy(hu)
        part(b, Part.HEAD) {
            shape(b, head, HoodiePalette.FUR)
            recolor(b, 8, head.y0 + 3, 12, head.y1 - 3, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
            recolor(b, head.x0 + 1, head.y1 - 3, head.x1 - 1, head.y1 - 1, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
            b.hline(22, 26, head.y0 + 3, HoodiePalette.FUR_LIGHT)
        }
        return hu
    }

    fun drawHeadSide(b: PixelBuffer, p: HoodiePose, up: Int): Int {
        val hu = up + p.headDy
        val (edx, edy) = when (p.ears) { Ears.ALERT -> 0 to -1; Ears.DOWN -> 2 to 3; Ears.RELAXED -> 1 to 1; Ears.TWITCH_LEFT, Ears.TWITCH_RIGHT -> 1 to 1; else -> 0 to 0 }
        part(b, Part.EARS) {
            ear(b, mirror = false, dx = 19 + edx, dy = hu + 1 + edy, fill = HoodiePalette.FUR_SHADE, inner = null)
            ear(b, mirror = false, dx = 3 - edx, dy = hu + edy)
        }
        part(b, Part.HEAD) {
        shapeUnion(b, listOf(R(8, 9, 37, 33, 8, round = true).dy(hu), R(4, 19, 12, 30, 3).dy(hu)), HoodiePalette.FUR)
        recolor(b, 9, 31 + hu, 36, 32 + hu, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        recolor(b, 31, 13 + hu, 36, 30 + hu, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        b.hline(14, 20, 11 + hu, HoodiePalette.FUR_LIGHT)
        drawEyes(b, p.eyes, hu, intArrayOf(12))
        b.hline(4, 5, 22 + hu, HoodiePalette.NOSE)
        when (p.mouth) {
            Mouth.OPEN -> { b.box(5, 25 + hu, 8, 27 + hu, HoodiePalette.NOSE); b.set(6, 26 + hu, HoodiePalette.TONGUE) }
            else -> { b.set(6, 25 + hu, HoodiePalette.NOSE); b.set(7, 26 + hu, HoodiePalette.NOSE); b.set(8, 25 + hu, HoodiePalette.NOSE) }
        }
        if (p.blush || p.eyes == Eyes.HAPPY) b.hline(13, 15, 25 + hu, HoodiePalette.BLUSH)
        }

        return hu
    }
}

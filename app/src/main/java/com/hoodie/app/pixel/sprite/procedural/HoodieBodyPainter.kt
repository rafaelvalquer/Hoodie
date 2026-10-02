package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.HoodiePainter.Part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shape
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shapeUnion
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.recolor

/** Procedural HoodieBodyPainter; preserves drawing order and semantic part ownership. */
internal object HoodieBodyPainter {
    fun drawBodyFront(b: PixelBuffer, up: Int, p: HoodiePose) {
        val body = R(11, 33, 36, 60, 4).dy(up)
        shape(b, body, HoodiePalette.HOOD)
        recolor(b, 31, body.y0 + 2, 35, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        recolor(b, 12, body.y0 + 3, 13, body.y1 - 5, HoodiePalette.HOOD, HoodiePalette.HOOD_LIGHT)
        // Barra canelada.
        recolor(b, 12, body.y1 - 3, 35, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        recolor(b, 12, body.y1 - 3, 35, body.y1 - 1, HoodiePalette.HOOD_LIGHT, HoodiePalette.HOOD_SHADE)
        for (x in 13..34 step 3) b.set(x, body.y1 - 2, HoodiePalette.HOOD_DARK)
        // Bolso canguru.
        shape(b, R(16, 46, 31, 55, 2).dy(up), HoodiePalette.HOOD_SHADE, HoodiePalette.HOOD_DARK)
        b.hline(18, 29, 48 + up, HoodiePalette.HOOD_DARK)
        part(b, Part.STRINGS) { drawStrings(b, intArrayOf(20, 27), up, p.stringSwing) }
        if (p.backpack) part(b, Part.BACKPACK) {
            for (x in intArrayOf(14, 32)) {
                b.box(x, 34 + up, x + 1, 47 + up, HoodiePalette.BACKPACK_DARK)
                b.set(x, 47 + up, HoodiePalette.OUTLINE); b.set(x + 1, 47 + up, HoodiePalette.OUTLINE)
            }
        }
    }

    fun drawStrings(b: PixelBuffer, xs: IntArray, up: Int, swing: Int) {
        for (x in xs) {
            b.vline(x, 36 + up, 39 + up, HoodiePalette.STRING)
            val lx = x + swing.coerceIn(-1, 1)
            b.vline(lx, 40 + up, 42 + up, HoodiePalette.STRING)
            val tip = x + swing.coerceIn(-2, 2)
            b.set(tip, 43 + up, HoodiePalette.STRING)
            b.set(tip, 44 + up, HoodiePalette.HOOD_DARK)
        }
    }

    fun drawBodyBack(b: PixelBuffer, up: Int) {
        val body = R(11, 33, 36, 60, 4).dy(up)
        part(b, Part.TORSO) {
        shape(b, body, HoodiePalette.HOOD)
        recolor(b, 12, body.y0 + 2, 15, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        recolor(b, 12, body.y1 - 3, 35, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        for (x in 13..34 step 3) b.set(x, body.y1 - 2, HoodiePalette.HOOD_DARK)
        // Capuz caído nas costas.
        shape(b, R(14, 30, 33, 45, 6, round = true).dy(up), HoodiePalette.HOOD_SHADE, HoodiePalette.HOOD_DARK)
        b.hline(18, 29, 32 + up, HoodiePalette.HOOD_DARK)
        }
    }

    fun drawBodySide(b: PixelBuffer, p: HoodiePose, up: Int) {
        val body = R(15, 33, 32, 55, 4).dy(up)
        part(b, Part.TORSO) {
        shape(b, R(25, 28, 36, 42, 4).dy(up), HoodiePalette.HOOD_SHADE)
        shapeUnion(b, listOf(body, R(13, 41, 19, 53, 3).dy(up)), HoodiePalette.HOOD)
        recolor(b, 29, body.y0 + 2, 31, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        recolor(b, 14, body.y1 - 2, 31, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        for (x in 15..30 step 3) b.set(x, body.y1 - 1, HoodiePalette.HOOD_DARK)
        }
        part(b, Part.STRINGS) { drawStrings(b, intArrayOf(16), up, p.stringSwing) }
        if (p.backpack) part(b, Part.BACKPACK) { b.box(25, 34 + up, 26, 45 + up, HoodiePalette.BACKPACK_DARK) }
    }
}

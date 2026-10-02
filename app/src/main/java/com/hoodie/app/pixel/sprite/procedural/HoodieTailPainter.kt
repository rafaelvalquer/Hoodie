package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.HoodiePainter.Part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shape
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shapeUnion
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.recolor

/** Procedural HoodieTailPainter; preserves drawing order and semantic part ownership. */
internal object HoodieTailPainter {
    fun drawTailBack(b: PixelBuffer, p: HoodiePose, up: Int) {
        val tailSway = if (p.legs == Legs.WALK) intArrayOf(0, 0, 1, 1, 0, 0, -1, -1)[p.stride.mod(8)] else 0
        part(b, Part.TAIL) {
            shape(b, R(31, 54, 35, 63, 2).dy(up), HoodiePalette.FUR)
            shape(b, R(33 + tailSway, 60 - kotlin.math.abs(tailSway), 39 + tailSway, 64 - kotlin.math.abs(tailSway), 2).dy(up), HoodiePalette.FUR)
        }
    }

    fun drawTailSide(b: PixelBuffer, p: HoodiePose, up: Int) {
        val sit = p.legs == Legs.SIT
        val walking = p.legs == Legs.WALK
        val s = p.stride.mod(8)
        if (!sit) part(b, Part.TAIL) {
            val sway = if (walking) intArrayOf(0, 0, 1, 1, 0, 0, -1, -1)[s] else 0
            shapeUnion(b, listOf(R(30, 48, 35, 54, 2), R(34 + sway, 41, 38 + sway, 52, 2), R(36 + sway * 2, 36, 40 + sway * 2, 43, 2)).map { it.dy(up) }, HoodiePalette.FUR)
            recolor(b, 37, 36 + up, 41, 52 + up, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        }
    }
}

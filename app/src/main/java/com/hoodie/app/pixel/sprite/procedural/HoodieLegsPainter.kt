package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.HoodiePainter.Part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shape
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shapeUnion
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.recolor

/** Procedural HoodieLegsPainter; preserves drawing order and semantic part ownership. */
internal object HoodieLegsPainter {
    fun drawLegsFront(b: PixelBuffer, p: HoodiePose) {
        if (p.legs == Legs.SIT) {
            part(b, Part.LEG_LEFT) { foot(b, R(12, 63, 21, 70, 3)) }
            part(b, Part.LEG_RIGHT) { foot(b, R(12, 63, 21, 70, 3).mirror()) }
            return
        }
        fun leg(lift: Int, forward: Int = 0) = R(15 + forward, 57 - lift, 21 + forward, 68 - lift, 1) to R(13 + forward, 65 - lift, 21 + forward, 70 - lift, 2)
        val (l, r) = when (p.legs) {
            Legs.STEP_LEFT -> leg(1) to leg(0)
            Legs.STEP_RIGHT -> leg(0) to leg(1)
            Legs.RUN_A -> leg(5, -1) to leg(0)
            Legs.RUN_B -> leg(0) to leg(5, -1)
            Legs.WALK -> {
                val s = p.stride.mod(8)
                leg(STRIDE_LIFT[s] * 2) to leg(STRIDE_LIFT[(s + 4) % 8] * 2)
            }
            else -> leg(0) to leg(0)
        }
        part(b, Part.LEG_LEFT) { shape(b, l.first, HoodiePalette.FUR); foot(b, l.second) }
        part(b, Part.LEG_RIGHT) { shape(b, r.first.mirror(), HoodiePalette.FUR); foot(b, r.second.mirror()) }
    }

    fun foot(b: PixelBuffer, f: R, color: Int = HoodiePalette.FUR) {
        shape(b, f, color)
        recolor(b, f.x0, f.y1 - 1, f.x1, f.y1 - 1, color, HoodiePalette.FUR_SHADE)
        val mid = (f.x0 + f.x1) / 2
        b.set(mid - 1, f.y1 - 1, HoodiePalette.OUTLINE); b.set(mid + 1, f.y1 - 1, HoodiePalette.OUTLINE)
    }

    fun sideLeg(b: PixelBuffer, offset: Int, lift: Int, color: Int) {
        val hx = 21 + offset / 2
        shapeUnion(b, listOf(R(hx, 50, hx + 5, 60 - lift / 2, 1), R(21 + offset, 58 - lift, 26 + offset, 67 - lift, 1)), color)
        foot(b, R(17 + offset, 65 - lift, 26 + offset, 70 - lift, 2), color)
    }
}

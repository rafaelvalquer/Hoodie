package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.HoodiePainter.Part

/** Drawing primitives and per-thread semantic part recording. */
internal object ProceduralDrawing {
    val recorder = ThreadLocal<IntArray?>()

    inline fun <T> part(b: PixelBuffer, p: Part, block: () -> T): T {
        val rec = recorder.get() ?: return block()
        val before = b.pixels.copyOf()
        val recBefore = rec.copyOf()
        val result = block()
        for (i in before.indices) if (b.pixels[i] != before[i] && rec[i] == recBefore[i]) rec[i] = p.ordinal
        return result
    }

    fun shape(b: PixelBuffer, r: R, fill: Int, outline: Int = HoodiePalette.OUTLINE) = shapeUnion(b, listOf(r), fill, outline)

    fun shapeUnion(b: PixelBuffer, rs: List<R>, fill: Int, outline: Int = HoodiePalette.OUTLINE) {
        fun inside(x: Int, y: Int) = rs.any { it.inside(x, y) }
        val x0 = rs.minOf { it.x0 } - 1; val x1 = rs.maxOf { it.x1 } + 1
        val y0 = rs.minOf { it.y0 } - 1; val y1 = rs.maxOf { it.y1 } + 1
        for (y in y0..y1) for (x in x0..x1) {
            if (inside(x, y)) b.set(x, y, fill)
            else if (inside(x - 1, y) || inside(x + 1, y) || inside(x, y - 1) || inside(x, y + 1)) b.set(x, y, outline)
        }
    }

    fun recolor(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, from: Int, to: Int) {
        for (y in y0..y1) for (x in x0..x1) if (b[x, y] == from) b.set(x, y, to)
    }
}

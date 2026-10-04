package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing
import com.hoodie.app.pixel.sprite.procedural.R

/** Primitivas de membros partilhadas pelos sprites compostos e pelo painter legado do Hoodie. */
internal object CharacterLimbPainter {
    fun drawSegment(buffer: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, fill: Int, outline: Int, width: Int) {
        for (offset in -(width / 2 + 1)..(width / 2 + 1)) buffer.line(x0 + offset, y0, x1 + offset, y1, outline)
        for (offset in -(width / 2 - 1)..(width / 2 - 1)) buffer.line(x0 + offset, y0, x1 + offset, y1, fill)
    }

    fun drawShape(buffer: PixelBuffer, shape: R, fill: Int, outline: Int) = ProceduralDrawing.shape(buffer, shape, fill, outline)

    fun drawShapes(buffer: PixelBuffer, shapes: List<R>, fill: Int, outline: Int) = ProceduralDrawing.shapeUnion(buffer, shapes, fill, outline)

    fun recolor(buffer: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, from: Int, to: Int) =
        ProceduralDrawing.recolor(buffer, x0, y0, x1, y1, from, to)
}

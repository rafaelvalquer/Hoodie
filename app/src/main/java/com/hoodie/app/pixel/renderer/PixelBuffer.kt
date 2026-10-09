package com.hoodie.app.pixel.renderer

/**
 * Framebuffer ARGB em resolução lógica. Todo o jogo é desenhado aqui, pixel a
 * pixel, e só no final escalado por um fator inteiro sem interpolação.
 */
class PixelBuffer(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)

    operator fun get(x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= width || y >= height) 0 else pixels[y * width + x]

    fun set(x: Int, y: Int, color: Int) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        val a = color ushr 24
        if (a == 0) return
        val i = y * width + x
        pixels[i] = if (a == 255) color else blend(pixels[i], color, a)
    }

    fun fill(color: Int) = pixels.fill(color)

    fun clear() = pixels.fill(0)

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        val x0 = x.coerceAtLeast(0); val y0 = y.coerceAtLeast(0)
        val x1 = (x + w).coerceAtMost(width); val y1 = (y + h).coerceAtMost(height)
        if ((color ushr 24) == 255) {
            for (yy in y0 until y1) pixels.fill(color, yy * width + x0, yy * width + x1.coerceAtLeast(x0))
        } else {
            for (yy in y0 until y1) for (xx in x0 until x1) set(xx, yy, color)
        }
    }

    /** Retângulo pelas coordenadas inclusivas dos cantos. */
    fun box(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) = rect(x0, y0, x1 - x0 + 1, y1 - y0 + 1, color)

    fun hline(x0: Int, x1: Int, y: Int, color: Int) = box(minOf(x0, x1), y, maxOf(x0, x1), y, color)

    fun vline(x: Int, y0: Int, y1: Int, color: Int) = box(x, minOf(y0, y1), x, maxOf(y0, y1), color)

    /** Caixa com contorno de 1px — a linguagem visual de todos os objetos. */
    fun outlined(x0: Int, y0: Int, x1: Int, y1: Int, fill: Int, outline: Int) {
        box(x0, y0, x1, y1, outline)
        if (x1 - x0 >= 2 && y1 - y0 >= 2) box(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
    }

    /** Padrão xadrez: o "degradê" do pixel art. */
    fun dither(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, phase: Int = 0) {
        for (y in y0..y1) for (x in x0..x1) if ((x + y + phase) and 1 == 0) set(x, y, color)
    }

    fun disc(cx: Int, cy: Int, r: Int, color: Int) {
        for (y in -r..r) for (x in -r..r) if (x * x + y * y <= r * r + r) set(cx + x, cy + y, color)
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        var x = x0; var y = y0
        val dx = kotlin.math.abs(x1 - x0); val dy = -kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            set(x, y, color)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    fun blit(src: PixelBuffer, dx: Int, dy: Int, flipX: Boolean = false) {
        val sourceX0 = maxOf(0, -dx)
        val sourceY0 = maxOf(0, -dy)
        val sourceX1 = minOf(src.width, width - dx)
        val sourceY1 = minOf(src.height, height - dy)
        if (sourceX0 >= sourceX1 || sourceY0 >= sourceY1) return

        val source = src.pixels
        val target = pixels
        for (sy in sourceY0 until sourceY1) {
            var sourceIndex = sy * src.width + if (flipX) src.width - 1 - sourceX0 else sourceX0
            val targetIndex = (dy + sy) * width + dx + sourceX0
            val step = if (flipX) -1 else 1
            for (sx in sourceX0 until sourceX1) {
                val color = source[sourceIndex]
                val alpha = color ushr 24
                if (alpha == 255) target[targetIndex + sx - sourceX0] = color
                else if (alpha != 0) {
                    val destinationIndex = targetIndex + sx - sourceX0
                    target[destinationIndex] = blend(target[destinationIndex], color, alpha)
                }
                sourceIndex += step
            }
        }
    }

    /** Draws only cached opaque spans for an immutable sprite frame. */
    internal fun blitOpaqueRows(src: PixelBuffer, dx: Int, dy: Int, rowBounds: IntArray) {
        require(rowBounds.size >= src.height * 2)
        val source = src.pixels
        val target = pixels
        val sourceY0 = maxOf(0, -dy)
        val sourceY1 = minOf(src.height, height - dy)
        for (sy in sourceY0 until sourceY1) {
            val boundsIndex = sy * 2
            val sourceX0 = maxOf(0, -dx, rowBounds[boundsIndex])
            val sourceX1 = minOf(src.width, width - dx, rowBounds[boundsIndex + 1] + 1)
            if (sourceX0 >= sourceX1) continue
            val sourceRow = sy * src.width
            val targetIndex = (dy + sy) * width + dx + sourceX0
            for (sx in sourceX0 until sourceX1) {
                val color = source[sourceRow + sx]
                val alpha = color ushr 24
                val destinationIndex = targetIndex + sx - sourceX0
                if (alpha == 255) target[destinationIndex] = color
                else if (alpha != 0) target[destinationIndex] = blend(target[destinationIndex], color, alpha)
            }
        }
    }

    /** Copia um trecho retangular sem criar buffers intermediários. */
    fun blitRegion(src: PixelBuffer, sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
        val x0 = maxOf(0, -sx, -dx)
        val y0 = maxOf(0, -sy, -dy)
        val x1 = minOf(w, src.width - sx, width - dx)
        val y1 = minOf(h, src.height - sy, height - dy)
        if (x0 >= x1 || y0 >= y1) return
        for (row in y0 until y1) {
            val source = (sy + row) * src.width + sx + x0
            val target = (dy + row) * width + dx + x0
            for (col in x0 until x1) {
                val c = src.pixels[source + col - x0]
                if (c ushr 24 != 0) set(dx + col, dy + row, c)
            }
        }
    }

    fun oval(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        val cx2 = x0 + x1; val cy2 = y0 + y1
        val rx2 = (x1 - x0).coerceAtLeast(1); val ry2 = (y1 - y0).coerceAtLeast(1)
        for (y in minOf(y0, y1)..maxOf(y0, y1)) for (x in minOf(x0, x1)..maxOf(x0, x1)) {
            val dx = 2 * x - cx2; val dy = 2 * y - cy2
            if (dx * dx * ry2 * ry2 + dy * dy * rx2 * rx2 <= rx2 * rx2 * ry2 * ry2) set(x, y, color)
        }
    }

    fun copyFrom(src: PixelBuffer) {
        require(src.width == width && src.height == height)
        System.arraycopy(src.pixels, 0, pixels, 0, pixels.size)
    }

    /** Desenha um glifo de string: '#' = cor, qualquer outro caractere = transparente. */
    fun glyph(rows: List<String>, x: Int, y: Int, color: Int) {
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> if (ch == '#') set(x + c, y + r, color) } }
    }

    companion object {
        fun blend(dst: Int, src: Int, alpha: Int): Int {
            val inv = 255 - alpha
            val r = (((src shr 16) and 0xFF) * alpha + ((dst shr 16) and 0xFF) * inv) / 255
            val g = (((src shr 8) and 0xFF) * alpha + ((dst shr 8) and 0xFF) * inv) / 255
            val b = ((src and 0xFF) * alpha + (dst and 0xFF) * inv) / 255
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun mix(a: Int, b: Int, t: Float): Int = blend(a, b or (0xFF shl 24), (t.coerceIn(0f, 1f) * 255).toInt())
    }
}

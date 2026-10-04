package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Silhueta como máscara: une elipses, retângulos arredondados e trapézios e pinta
 * tudo de uma vez com UM contorno externo e a luz comum ([ShadingProfile]).
 * É o que dá volume (ombro, quadril, bochecha) sem desenhar caixas sobrepostas.
 */
class CharacterMask(val width: Int = CharacterCanvas.WIDTH, val height: Int = CharacterCanvas.HEIGHT) {
    private val bits = BooleanArray(width * height)

    operator fun get(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && bits[y * width + x]

    fun set(x: Int, y: Int, on: Boolean = true) {
        if (x in 0 until width && y in 0 until height) bits[y * width + x] = on
    }

    val isEmpty: Boolean get() = bits.none { it }

    fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int) = apply {
        if (rx <= 0 || ry <= 0) return@apply
        for (dy in -ry..ry) for (dx in -rx..rx) {
            // +0.5 suaviza a borda: evita os "bicos" de um pixel no topo e nas laterais.
            if ((dx * dx).toFloat() / (rx * rx) + (dy * dy).toFloat() / (ry * ry) <= 1f + 0.5f / maxOf(rx, ry)) set(cx + dx, cy + dy)
        }
    }

    /** Elipse dentro da caixa `x0..x1`×`y0..y1` (aceita larguras pares). */
    fun oval(x0: Int, y0: Int, x1: Int, y1: Int) = apply {
        val cx = (x0 + x1) / 2f; val cy = (y0 + y1) / 2f
        val rx = (x1 - x0) / 2f + 0.5f; val ry = (y1 - y0) / 2f + 0.5f
        for (y in y0..y1) for (x in x0..x1) {
            val nx = (x - cx) / rx; val ny = (y - cy) / ry
            if (nx * nx + ny * ny <= 1.02f) set(x, y)
        }
    }

    /** Retângulo com cantos arredondados de raio [r] (mesma máscara usada pelo Hoodie). */
    fun roundRect(x0: Int, y0: Int, x1: Int, y1: Int, r: Int) = apply {
        val rr = r.coerceAtMost(minOf(x1 - x0, y1 - y0) / 2).coerceAtLeast(0)
        for (y in y0..y1) for (x in x0..x1) {
            val dx = minOf(x - x0, x1 - x); val dy = minOf(y - y0, y1 - y)
            if (dx >= rr || dy >= rr) { set(x, y); continue }
            val ex = rr - dx - 0.5; val ey = rr - dy - 0.5
            if (ex * ex + ey * ey <= rr * rr) set(x, y)
        }
    }

    /**
     * Trapézio vertical: largura `topLeft..topRight` em [top] interpolando até
     * `bottomLeft..bottomRight` em [bottom]. Ombro → quadril, saia do paletó, coxa.
     */
    fun trapezoid(top: Int, bottom: Int, topLeft: Int, topRight: Int, bottomLeft: Int, bottomRight: Int) = apply {
        val h = (bottom - top).coerceAtLeast(1)
        for (y in top..bottom) {
            val t = (y - top).toFloat() / h
            val l = Math.round(topLeft + (bottomLeft - topLeft) * t)
            val r = Math.round(topRight + (bottomRight - topRight) * t)
            for (x in l..r) set(x, y)
        }
    }

    /** Segmento grosso (braço/perna) de [x0,y0] a [x1,y1] com largura [w]. */
    fun capsule(x0: Int, y0: Int, x1: Int, y1: Int, w: Int) = apply {
        val r = w / 2f
        val minX = minOf(x0, x1) - w; val maxX = maxOf(x0, x1) + w
        val minY = minOf(y0, y1) - w; val maxY = maxOf(y0, y1) + w
        val vx = (x1 - x0).toFloat(); val vy = (y1 - y0).toFloat()
        val len2 = (vx * vx + vy * vy).coerceAtLeast(0.0001f)
        for (y in minY..maxY) for (x in minX..maxX) {
            val t = (((x - x0) * vx + (y - y0) * vy) / len2).coerceIn(0f, 1f)
            val dx = x - (x0 + vx * t); val dy = y - (y0 + vy * t)
            if (dx * dx + dy * dy <= r * r + 0.25f) set(x, y)
        }
    }

    fun box(x0: Int, y0: Int, x1: Int, y1: Int) = apply { for (y in y0..y1) for (x in x0..x1) set(x, y) }

    fun union(other: CharacterMask) = apply { for (i in bits.indices) if (other.bits[i]) bits[i] = true }

    fun subtract(other: CharacterMask) = apply { for (i in bits.indices) if (other.bits[i]) bits[i] = false }

    fun intersect(other: CharacterMask) = apply { for (i in bits.indices) bits[i] = bits[i] && other.bits[i] }

    fun copy(): CharacterMask = CharacterMask(width, height).also { bits.copyInto(it.bits) }

    /** Pixel interno (não é contorno). */
    fun interior(x: Int, y: Int) = this[x, y] && this[x - 1, y] && this[x + 1, y] && this[x, y - 1] && this[x, y + 1]

    /** Bounding box ocupada ou null. */
    fun bounds(): IntArray? {
        var x0 = width; var y0 = height; var x1 = -1; var y1 = -1
        for (y in 0 until height) for (x in 0 until width) if (bits[y * width + x]) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        return if (x1 < 0) null else intArrayOf(x0, y0, x1, y1)
    }

    /**
     * Pinta a máscara: contorno externo de 1 px, base, realce de 1 px na borda clara
     * e faixa de sombra de [ShadingProfile.shadowDepth] px na borda escura.
     */
    fun paint(b: PixelBuffer, tones: Tones, shading: ShadingProfile? = ShadingProfile(), outline: Boolean = true) {
        val lightLeft = shading?.lightDirection != LightDirection.TOP_RIGHT
        val depth = shading?.shadowDepth ?: 0
        val sx = if (lightLeft) 1 else -1
        for (y in 0 until height) for (x in 0 until width) {
            if (!this[x, y]) continue
            if (outline && !interior(x, y)) { b.set(x, y, tones.outline); continue }
            var color = tones.base
            if (shading != null) {
                // Sombra: a borda oposta à luz está a até [depth] px (abaixo ou do lado escuro).
                val shadow = (1..depth).any { k -> !this[x + sx * (k + 1), y] || !this[x, y + k + 1] }
                // Realce: logo dentro do contorno do lado da luz (só a primeira faixa).
                val light = !this[x - sx * 2, y] || !this[x, y - 2]
                color = when {
                    shadow && !light -> tones.dark
                    light && !shadow -> tones.light
                    else -> tones.base
                }
            }
            b.set(x, y, color)
        }
    }

    /** Só o contorno (para recortes internos como a dobra do focinho). */
    fun paintOutline(b: PixelBuffer, color: Int) {
        for (y in 0 until height) for (x in 0 until width) if (this[x, y] && !interior(x, y)) b.set(x, y, color)
    }

    /** Preenche sem contorno nem luz (marcas, manchas, forro). */
    fun fill(b: PixelBuffer, color: Int) {
        for (y in 0 until height) for (x in 0 until width) if (this[x, y]) b.set(x, y, color)
    }

    companion object {
        fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int) = CharacterMask().ellipse(cx, cy, rx, ry)
        fun oval(x0: Int, y0: Int, x1: Int, y1: Int) = CharacterMask().oval(x0, y0, x1, y1)
        fun roundRect(x0: Int, y0: Int, x1: Int, y1: Int, r: Int) = CharacterMask().roundRect(x0, y0, x1, y1, r)
        fun trapezoid(top: Int, bottom: Int, tl: Int, tr: Int, bl: Int, br: Int) = CharacterMask().trapezoid(top, bottom, tl, tr, bl, br)
        fun capsule(x0: Int, y0: Int, x1: Int, y1: Int, w: Int) = CharacterMask().capsule(x0, y0, x1, y1, w)
        fun box(x0: Int, y0: Int, x1: Int, y1: Int) = CharacterMask().box(x0, y0, x1, y1)
    }
}

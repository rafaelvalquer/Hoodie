package com.hoodie.app.pixel

import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** `rect` (e quem o usa: box, hline, vline, outlined) recorta nas quatro bordas e nunca estoura o array. */
class PixelBufferRectTest {
    private val red = 0xFFFF0000.toInt()
    private val half = 0x80FF0000.toInt()
    private val bg = 0xFF101010.toInt()

    private fun buf() = PixelBuffer(8, 4).apply { fill(bg) }

    private fun assertUntouched(b: PixelBuffer, label: String) = assertArrayEquals(label, buf().pixels, b.pixels)

    @Test fun aBoxEntirelyPastTheRightEdgeOnTheLastRowDrawsNothing() {
        listOf(red, half).forEach { c ->
            // Antes: ArrayIndexOutOfBoundsException na última linha.
            val b = buf(); b.rect(9, 3, 4, 1, c); assertUntouched(b, "x > width, última linha")
            val e = buf(); e.rect(8, 0, 3, 4, c); assertUntouched(e, "x == width")
            val g = buf(); g.rect(Int.MAX_VALUE - 1, 0, 10, 4, c); assertUntouched(g, "x gigante")
        }
    }

    @Test fun boxesOutsideAnyEdgeDrawNothing() {
        listOf(red, half).forEach { c ->
            listOf(
                intArrayOf(-5, 0, 3, 4), intArrayOf(0, -5, 8, 3), intArrayOf(0, 4, 8, 2), intArrayOf(0, 9, 8, 2),
                intArrayOf(2, 1, -3, 2), intArrayOf(2, 1, 3, -2), intArrayOf(2, 1, 0, 2), intArrayOf(Int.MIN_VALUE, 0, -10, 4),
            ).forEach { (x, y, w, h) -> val b = buf(); b.rect(x, y, w, h, c); assertUntouched(b, "rect($x,$y,$w,$h)") }
        }
    }

    @Test fun partiallyVisibleBoxesAreClippedToTheBuffer() {
        val b = buf()
        b.rect(6, 2, 5, 9, red)                       // sai pela direita e por baixo
        for (y in 0 until 4) for (x in 0 until 8) assertEquals("($x,$y)", if (x >= 6 && y >= 2) red else bg, b[x, y])
        val a = buf()
        a.rect(-3, -3, 5, 5, red)                     // sai pela esquerda e por cima
        for (y in 0 until 4) for (x in 0 until 8) assertEquals("($x,$y)", if (x < 2 && y < 2) red else bg, a[x, y])
    }

    @Test fun helpersInheritTheClipping() {
        val b = buf()
        b.box(7, 3, 20, 20, red); b.hline(5, 30, 3, red); b.vline(9, 0, 3, red); b.outlined(6, 2, 40, 40, red, red)
        b.box(100, 100, 120, 120, red); b.hline(50, 60, 3, red); b.vline(8, 0, 3, red)
        for (y in 0 until 4) for (x in 0 until 8) {
            val expected = (y == 3 && x >= 5) || (x >= 6 && y >= 2)
            assertEquals("($x,$y)", if (expected) red else bg, b[x, y])
        }
    }
}

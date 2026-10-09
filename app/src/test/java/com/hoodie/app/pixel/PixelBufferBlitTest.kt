package com.hoodie.app.pixel

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.character.CharacterAnchors
import com.hoodie.app.pixel.character.CharacterFrame
import com.hoodie.app.pixel.sprite.Point
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PixelBufferBlitTest {
    @Test
    fun `optimized blit matches per-pixel composition for clipping, mirroring, and alpha`() {
        val source = PixelBuffer(4, 3).apply {
            pixels[0] = 0x00000000
            pixels[1] = 0xFFFF0000.toInt()
            pixels[2] = 0x8080FF20.toInt()
            pixels[3] = 0x0100FF00
            pixels[4] = 0xFF0000FF.toInt()
            pixels[5] = 0x40FFFFFF
            pixels[6] = 0x00FFFFFF
            pixels[7] = 0xFF112233.toInt()
            pixels[8] = 0xFFABCDEF.toInt()
            pixels[9] = 0x2000FFFF
            pixels[10] = 0x7F123456
            pixels[11] = 0x00000000
        }

        listOf(
            Triple(8, 6, false),
            Triple(-2, -1, false),
            Triple(3, 2, true),
            Triple(-1, 1, true),
            Triple(20, 20, true),
        ).forEach { (x, y, flipX) ->
            val expected = PixelBuffer(7, 5).apply { fill(0xFF345678.toInt()) }
            val actual = PixelBuffer(7, 5).apply { fill(0xFF345678.toInt()) }
            for (sy in 0 until source.height) for (sx in 0 until source.width) {
                val color = source.pixels[sy * source.width + if (flipX) source.width - 1 - sx else sx]
                expected.set(x + sx, y + sy, color)
            }

            actual.blit(source, x, y, flipX)

            assertArrayEquals("position=($x,$y), flipX=$flipX", expected.pixels, actual.pixels)
        }
    }

    @Test
    fun `opaque row blit matches regular blit for cropped sprite frames`() {
        val source = PixelBuffer(8, 5).apply {
            set(2, 0, 0xFFAA1100.toInt())
            set(3, 0, 0x8066CC33.toInt())
            set(1, 1, 0xFF0000FF.toInt())
            set(6, 1, 0xFF00FFFF.toInt())
            set(4, 3, 0xFFFF00FF.toInt())
        }
        val point = Point(0, 0)
        val frame = CharacterFrame(source, CharacterAnchors(point, point, point, point, point, point))

        listOf(-3 to -1, 2 to 0, 6 to 3, 20 to 20).forEach { (x, y) ->
            val expected = PixelBuffer(9, 6).apply { fill(0xFF123456.toInt()) }
            val actual = PixelBuffer(9, 6).apply { fill(0xFF123456.toInt()) }
            expected.blit(source, x, y)
            actual.blitOpaqueRows(frame.image, x, y, frame.opaqueRowBounds)
            assertArrayEquals("position=($x,$y)", expected.pixels, actual.pixels)
        }
    }
}

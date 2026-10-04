package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterEyePainter
import com.hoodie.app.pixel.character.EyeStyle
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Eyes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterEyeStyleTest {
    @Test fun hoodieEyeStyleMatchesTheExtractedLegacyEyePainterForEveryExpression() {
        Eyes.entries.forEach { expression ->
            val legacy = PixelBuffer(48, 72)
            val shared = PixelBuffer(48, 72)
            val centers = listOf(15, 31)
            CharacterEyePainter.drawLegacyHoodie(
                legacy, expression, up = 6, xs = intArrayOf(14, 30),
                ink = INK, white = LIGHT, shade = SHADE,
            )
            CharacterEyePainter.drawCharacter(
                shared, expression, xs = centers, y = 24,
                ink = INK, light = LIGHT, brow = SHADE, style = EyeStyle.HOODIE,
            )
            assertArrayEquals("legacy eye pixels changed for $expression", legacy.pixels, shared.pixels)
        }
    }

    @Test fun everyEyeStyleHasItsOwnOpenEyeSilhouette() {
        val images = EyeStyle.entries.associateWith { style ->
            PixelBuffer(48, 72).also { buffer ->
                CharacterEyePainter.drawCharacter(
                    buffer, Eyes.OPEN, xs = listOf(15, 31), y = 24,
                    ink = INK, light = LIGHT, brow = SHADE, style = style,
                )
            }.pixels.toList()
        }

        assertEquals("each style should produce a distinct open-eye treatment", EyeStyle.entries.size, images.values.distinct().size)
        EyeStyle.entries.forEach { style ->
            assertTrue("$style should draw visible eye pixels", images.getValue(style).any { it == INK })
        }
    }

    private companion object {
        const val INK = 0xFF1A1C33.toInt()
        const val LIGHT = 0xFFF3EBD9.toInt()
        const val SHADE = 0xFF7F7568.toInt()
    }
}

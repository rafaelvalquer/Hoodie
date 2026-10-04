package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Eyes

/** Desenho de olhos compartilhado; parâmetros de forma preservam a identidade de cada personagem. */
internal object CharacterEyePainter {
    fun drawLegacyHoodie(buffer: PixelBuffer, eyes: Eyes, up: Int, xs: IntArray, ink: Int, white: Int, shade: Int) {
        for (baseX in xs) {
            var x = baseX
            var y = 18 + up
            when (eyes) {
                Eyes.LOOK_LEFT -> x -= 1
                Eyes.LOOK_RIGHT -> x += 1
                Eyes.LOOK_UP -> y -= 1
                Eyes.LOOK_DOWN -> y += 1
                else -> Unit
            }
            when (eyes) {
                Eyes.OPEN, Eyes.LOOK_LEFT, Eyes.LOOK_RIGHT, Eyes.LOOK_UP -> {
                    buffer.box(x, y, x + 2, y + 3, ink)
                    buffer.set(x, y, white)
                }
                Eyes.LOOK_DOWN -> {
                    buffer.box(x, y, x + 2, y + 2, ink)
                    buffer.set(x, y, white)
                }
                Eyes.HALF -> {
                    buffer.box(x, y + 2, x + 2, y + 3, ink)
                    buffer.hline(x - 1, x + 3, y + 1, shade)
                }
                Eyes.CLOSED -> {
                    buffer.hline(x - 1, x + 3, y + 2, ink)
                    buffer.set(x - 1, y + 1, ink)
                    buffer.set(x + 3, y + 1, ink)
                }
                Eyes.HAPPY -> {
                    buffer.set(x - 1, y + 3, ink); buffer.set(x, y + 2, ink)
                    buffer.set(x + 1, y + 1, ink); buffer.set(x + 2, y + 2, ink)
                    buffer.set(x + 3, y + 3, ink)
                }
                Eyes.WIDE -> {
                    buffer.box(x - 1, y - 1, x + 3, y + 3, ink)
                    buffer.box(x - 1, y - 1, x, y, white)
                }
                Eyes.FOCUSED -> {
                    buffer.box(x, y + 1, x + 2, y + 3, ink)
                    buffer.hline(x - 1, x + 3, y, shade)
                }
                Eyes.SLEEPY -> {
                    buffer.box(x, y + 2, x + 2, y + 3, ink)
                    buffer.hline(x - 1, x + 3, y + 1, ink)
                }
            }
        }
    }

    fun drawCharacter(
        buffer: PixelBuffer,
        eyes: Eyes,
        xs: List<Int>,
        y: Int,
        ink: Int,
        light: Int,
        brow: Int,
        style: EyeStyle,
    ) {
        if (style == EyeStyle.HOODIE) {
            drawLegacyHoodie(
                buffer, eyes, up = y - 18,
                xs = xs.map { it - 1 }.toIntArray(), ink = ink, white = light, shade = brow,
            )
            return
        }
        xs.forEach { baseX ->
            val x = baseX + when (eyes) { Eyes.LOOK_LEFT -> -1; Eyes.LOOK_RIGHT -> 1; else -> 0 }
            val pupilY = y + when (eyes) { Eyes.LOOK_UP -> -1; Eyes.LOOK_DOWN -> 1; else -> 0 }
            when (eyes) {
                Eyes.CLOSED, Eyes.SLEEPY -> {
                    val halfWidth = if (style == EyeStyle.SOFT) 1 else 2
                    buffer.hline(x - halfWidth, x + halfWidth, y + 1, ink)
                    if (style == EyeStyle.HEAVY) {
                        buffer.set(x - 2, y, ink)
                        buffer.set(x + 2, y, ink)
                    }
                }
                Eyes.HALF -> {
                    val halfWidth = if (style == EyeStyle.SOFT) 1 else 2
                    buffer.hline(x - halfWidth, x + halfWidth, y, ink)
                    buffer.hline(x - halfWidth, x + halfWidth, y + 1, ink)
                    buffer.hline(x - maxOf(0, halfWidth - 1), x + maxOf(0, halfWidth - 1), y + 2, light)
                }
                Eyes.HAPPY -> {
                    buffer.set(x - 2, y + 2, ink); buffer.set(x - 1, y + 1, ink)
                    buffer.set(x, y, ink); buffer.set(x + 1, y + 1, ink); buffer.set(x + 2, y + 2, ink)
                }
                else -> {
                    when (style) {
                        EyeStyle.SOFT -> {
                            buffer.hline(x - 1, x + 1, y - 1, ink)
                            buffer.hline(x - 2, x + 2, y, ink)
                            buffer.hline(x - 2, x + 2, y + 1, ink)
                            buffer.hline(x - 1, x + 1, y + 2, ink)
                            buffer.set(x - 1, pupilY, light)
                            buffer.set(x, pupilY + 1, ink)
                        }
                        EyeStyle.ROUND -> {
                            buffer.hline(x - 1, x + 1, y - 2, ink)
                            buffer.hline(x - 2, x + 2, y - 1, ink)
                            buffer.hline(x - 2, x + 2, y, ink)
                            buffer.hline(x - 2, x + 2, y + 1, ink)
                            buffer.hline(x - 1, x + 1, y + 2, ink)
                            buffer.set(x - 1, pupilY - 1, light)
                            buffer.box(x, pupilY + 1, x + 1, pupilY + 2, ink)
                        }
                        EyeStyle.MASKED -> {
                            buffer.hline(x - 1, x + 1, y - 2, ink)
                            buffer.hline(x - 2, x + 2, y - 1, ink)
                            buffer.hline(x - 2, x + 2, y, ink)
                            buffer.hline(x - 1, x + 1, y + 1, ink)
                            buffer.set(x - 1, pupilY - 1, light)
                            buffer.set(x, pupilY, ink)
                        }
                        EyeStyle.HEAVY -> {
                            val height = if (eyes == Eyes.WIDE) 5 else 4
                            buffer.box(x - 2, y - 1, x + 2, y + height - 2, ink)
                            buffer.set(x, pupilY, light)
                            buffer.box(x, pupilY + 1, x + 1, pupilY + 2, ink)
                        }
                        EyeStyle.HOODIE -> Unit
                    }
                }
            }
            if (style == EyeStyle.HEAVY) {
                buffer.hline(x - 3, x + 3, y - 3, brow)
                buffer.hline(x - 2, x + 2, y - 2, ink)
            }
        }
    }
}

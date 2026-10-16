package com.hoodie.app.pixel.effects

import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TapEffectsTest {
    @Test fun `cada efeito comeca visivel, fica pequeno e termina vazio`() {
        TapEffectStyle.entries.forEach { style ->
            assertTrue("$style vazio no início", TapEffects.cells(style, 0).isNotEmpty())
            assertTrue("$style ainda visível no meio", TapEffects.cells(style, style.durationMs / 2).isNotEmpty())
            assertEquals("$style não termina", emptyList<TapCell>(), TapEffects.cells(style, style.durationMs))
            assertTrue(style.durationMs <= 700)
            (0 until style.durationMs step 20).forEach { t ->
                TapEffects.cells(style, t, 3).forEach { c ->
                    assertTrue("$style grande demais em $t: $c", c.dx in -12..12 && c.dy in -16..12)
                    assertTrue(c.alpha in 0f..1f)
                }
            }
        }
    }

    @Test fun `mesma idade da sempre o mesmo quadro`() {
        TapEffectStyle.entries.forEach { s -> assertEquals(TapEffects.cells(s, 120, 7), TapEffects.cells(s, 120, 7)) }
    }

    /** Quadros para a revisão (build/pixel-preview/tap-effects/<estilo>/frame_NN.png). */
    @Test fun `exporta quadros para revisao`() {
        TapEffectStyle.entries.forEach { style ->
            var t = 0L; var i = 0
            while (t <= style.durationMs + 80) {
                val b = PixelBuffer(36, 36)
                b.box(0, 0, 35, 35, 0xFF6F7C96.toInt())
                b.box(0, 24, 35, 35, 0xFF5E697F.toInt())
                TapEffects.cells(style, t, 1).forEach { c ->
                    val x = 18 + c.dx; val y = 18 + c.dy
                    if (x in 0..35 && y in 0..35) b.set(x, y, PixelBuffer.mix(b[x, y], c.color, c.alpha))
                }
                PreviewExport.write(File(PreviewExport.dir, "tap-effects/${style.name}/frame_%02d.png".format(i)), b, 8, null)
                t += 40; i++
            }
        }
    }
}

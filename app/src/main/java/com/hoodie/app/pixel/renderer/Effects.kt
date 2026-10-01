package com.hoodie.app.pixel.renderer

/** Sistema de partículas mínimo — sempre em pixel art, nunca alpha suave demais. */
enum class EffectKind { STEAM, SLEEP_Z, MUSIC, SWEAT, SPARKLE, DUST }

object Effects {
    private val Z_SMALL = listOf("###", "  #", " # ", "#  ", "###")
    private val Z_BIG = listOf("####", "   #", "  # ", " #  ", "####")
    private val NOTE = listOf("  ##", "  # ", "  # ", "### ", "##  ")
    private val SPARK = listOf(" # ", "###", " # ")

    fun draw(b: PixelBuffer, kind: EffectKind, x: Int, y: Int, t: Long) {
        when (kind) {
            EffectKind.STEAM -> for (k in 0..2) {
                val phase = (t + k * 500) % 1500
                val rise = (phase / 100).toInt()
                val sway = if ((phase / 300) % 2 == 0L) 0 else 1
                val alpha = (200 - phase * 180 / 1500).toInt()
                val c = (alpha shl 24) or 0xFFFFFF
                b.set(x + k * 3 + sway, y - rise, c); b.set(x + k * 3 + sway, y - rise - 1, c)
            }
            EffectKind.SLEEP_Z -> {
                val phase = t % 2400
                val rise = (phase / 160).toInt()
                b.glyph(Z_SMALL, x + rise / 2, y - rise, 0xFFE9EDFF.toInt())
                val phase2 = (t + 1200) % 2400
                val rise2 = (phase2 / 160).toInt()
                b.glyph(Z_BIG, x + 6 + rise2 / 2, y - 6 - rise2, 0xFFE9EDFF.toInt())
            }
            EffectKind.MUSIC -> for (k in 0..1) {
                val phase = (t + k * 900) % 1800
                val rise = (phase / 90).toInt()
                b.glyph(NOTE, x + k * 10 + (if ((phase / 300) % 2 == 0L) 0 else 1), y - rise, if (k == 0) 0xFFF2CF5B.toInt() else 0xFF7FE0C2.toInt())
            }
            EffectKind.SWEAT -> {
                val phase = t % 900
                val fall = (phase / 90).toInt()
                val c = 0xFF8AD6F2.toInt()
                b.set(x, y + fall, c); b.set(x, y + fall + 1, c); b.set(x - 1, y + fall + 1, c); b.set(x + 1, y + fall + 1, c)
            }
            EffectKind.SPARKLE -> for (k in 0..2) {
                if (((t / 250) + k) % 3 == 0L) b.glyph(SPARK, x + k * 11 - 10, y + (k % 2) * 8, 0xFFFFF2A8.toInt())
            }
            EffectKind.DUST -> for (k in 0..3) {
                val phase = (t + k * 220) % 880
                val r = (phase / 160).toInt()
                b.set(x + k * 4 - 6 + r, y - r, 0xAAD8CBB4.toInt())
            }
        }
    }
}

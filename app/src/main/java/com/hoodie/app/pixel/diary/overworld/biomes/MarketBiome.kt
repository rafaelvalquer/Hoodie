package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Compras: feira com duas barracas de toldo listrado (o toldo balança) e caixotes. */
object MarketBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(1, 30, 30)
        val wave = BiomePainter.frame(timeMs, 500, 2)
        stall(c, 1, 8, P.AWNING_A, wave)
        stall(c, 16, 12, P.AWNING_C, 1 - wave)
        // Caixotes de frutas na frente.
        c.outlined(4, 26, 10, 30, P.WOOD)
        c.set(6, 25, P.AWNING_A); c.set(8, 25, P.FLOWER_B)
        c.outlined(21, 26, 27, 30, P.WOOD)
        c.set(23, 25, 0xFF4FB676.toInt()); c.set(25, 25, P.AWNING_A)
        if (night) { c.set(8, 16, P.WINDOW_LIT); c.set(23, 20, P.WINDOW_LIT) }
    }

    private fun stall(c: BiomeCanvas, x: Int, top: Int, color: Int, wave: Int) {
        c.vline(x + 1, top + 4, 29, P.WOOD_DARK)
        c.vline(x + 13, top + 4, 29, P.WOOD_DARK)
        c.outlined(x, top + wave, x + 14, top + 4 + wave, P.AWNING_B)
        for (k in x + 1..x + 13 step 4) c.box(k, top + 1 + wave, k + 1, top + 3 + wave, color)
        c.outlined(x + 1, top + 12, x + 13, top + 15, P.WOOD)
    }
}

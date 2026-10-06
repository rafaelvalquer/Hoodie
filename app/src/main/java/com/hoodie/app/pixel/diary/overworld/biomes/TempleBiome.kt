package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Academia: templo de colunas e frontão; tocha acesa na entrada. */
object TempleBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(2, 29, 30)
        c.roof(15, 4, 1, 30, 11, P.STONE_LIGHT, P.STONE)
        c.outlined(3, 11, 28, 13, P.STONE)
        // Quatro colunas.
        for (x in intArrayOf(4, 10, 18, 24)) {
            c.outlined(x, 13, x + 3, 27, P.STONE_LIGHT)
            c.vline(x + 2, 14, 26, P.STONE)
        }
        c.box(14, 15, 17, 27, P.OUTLINE)
        // Degraus.
        c.outlined(2, 27, 29, 29, P.STONE)
        c.outlined(0, 29, 31, 31, P.STONE_DARK)
        // Tocha com chama viva.
        c.vline(29, 20, 26, P.WOOD_DARK)
        val f = BiomePainter.frame(timeMs, 200, 3)
        c.set(29, 19, P.FIRE); c.set(29 + (f - 1), 18, P.FIRE_CORE); c.set(29, 17 - (f % 2), P.FIRE)
    }
}

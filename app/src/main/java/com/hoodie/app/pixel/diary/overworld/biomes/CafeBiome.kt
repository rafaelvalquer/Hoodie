package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Café/lanche: cabana pequena de toldo listrado e xícara fumegando. */
object CafeBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(6, 25, 30)
        c.outlined(7, 15, 24, 30, P.WALL)
        // Toldo listrado.
        c.outlined(5, 11, 26, 15, P.AWNING_B)
        for (x in 6..25 step 4) c.box(x, 12, x + 1, 14, P.AWNING_A)
        c.hline(5, 26, 16, P.OUTLINE)
        c.outlined(13, 21, 18, 30, P.DOOR)
        c.outlined(8, 18, 11, 21, if (night) P.WINDOW_LIT else P.GLASS)
        // Mesinha com xícara fumegante.
        c.outlined(21, 24, 27, 26, P.WOOD)
        c.vline(24, 27, 30, P.WOOD_DARK)
        c.outlined(23, 21, 25, 23, P.PAPER)
        val f = BiomePainter.frame(timeMs)
        c.set(24, 20 - f % 2, P.SMOKE); c.set(23 + f % 2, 18 - f % 2, P.SMOKE)
    }
}

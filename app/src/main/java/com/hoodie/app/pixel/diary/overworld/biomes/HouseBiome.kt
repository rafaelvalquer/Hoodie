package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Casinha de telhado vermelho com chaminé; fumaça sobe e a janela acende à noite. */
object HouseBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(5, 27, 30)
        // Chaminé atrás do telhado.
        c.outlined(20, 4, 24, 12, P.WALL_SHADE)
        c.roof(15, 3, 3, 28, 14, P.ROOF_RED, P.ROOF_RED_DARK)
        c.outlined(6, 14, 25, 30, P.WALL)
        c.vline(24, 15, 29, P.WALL_SHADE)
        // Porta e janela.
        c.outlined(13, 21, 18, 30, P.DOOR)
        c.set(17, 25, P.GOLD)
        c.outlined(8, 17, 11, 20, if (night) P.WINDOW_LIT else P.GLASS)
        c.outlined(20, 17, 23, 20, if (night) P.WINDOW_LIT else P.GLASS)
        // Fumaça: três baforadas subindo.
        val f = BiomePainter.frame(timeMs)
        for (k in 0..1) {
            val y = 2 - ((f + k * 2) % 4)
            val x = 22 + (if ((f + k) % 2 == 0) 0 else 1)
            if (y >= -2) { c.set(x, y, P.SMOKE); c.set(x + 1, y, P.SMOKE) }
        }
    }
}

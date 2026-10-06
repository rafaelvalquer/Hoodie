package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Trabalho: torre de vidro com ameias de castelo; bandeira tremula e as janelas acendem em sequência. */
object OfficeCastleBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(6, 26, 30)
        // Mastro e bandeira (2 quadros).
        c.vline(16, 0, 6, P.OUTLINE)
        val wave = BiomePainter.frame(timeMs, 300, 2)
        c.box(17, 0, 21, 2 + wave, P.FLAG)
        c.set(21, 0, P.OUTLINE)
        // Ameias.
        for (x in intArrayOf(6, 11, 16, 21)) c.outlined(x, 5, x + 4, 8, P.STONE)
        c.outlined(6, 8, 25, 30, P.GLASS_DARK)
        // Grade de vidro: 3 colunas × 5 andares; à noite acendem em onda.
        val step = ((timeMs / 500) % 15).toInt()
        for (row in 0 until 5) for (col in 0 until 3) {
            val x = 8 + col * 6; val y = 10 + row * 4
            val i = row * 3 + col
            val lit = night && (i <= step || (i * 7) % 5 == 0)
            c.box(x, y, x + 3, y + 2, if (lit) P.WINDOW_LIT else P.GLASS)
        }
        c.vline(24, 9, 29, P.STONE_DARK)
        // Portaria.
        c.outlined(13, 24, 18, 30, P.STONE_DARK)
        c.box(14, 25, 17, 29, if (night) P.WINDOW_LIT else P.GLASS)
    }
}

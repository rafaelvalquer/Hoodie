package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Almoço: taverna de madeira com placa de caneca balançando e vapor na porta. */
object TavernBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(3, 26, 30)
        c.roof(14, 4, 1, 27, 13, P.WOOD_DARK, P.OUTLINE)
        c.outlined(3, 13, 25, 30, P.WOOD)
        for (y in intArrayOf(17, 21, 25)) c.hline(4, 24, y, P.WOOD_DARK)
        c.outlined(11, 21, 17, 30, P.DOOR)
        c.outlined(5, 16, 9, 20, if (night) P.WINDOW_LIT else P.GLASS)
        c.outlined(19, 16, 23, 20, if (night) P.WINDOW_LIT else P.GLASS)
        // Placa pendurada (balança 1 px).
        val swing = BiomePainter.frame(timeMs, 500, 2)
        c.hline(25, 29, 14, P.OUTLINE)
        c.outlined(26 + swing, 15, 31, 21, P.SIGN)
        c.box(28 + swing, 17, 29 + swing, 19, P.GOLD)
        c.set(30 + swing, 18, P.OUTLINE)
        // Vapor saindo da porta.
        val f = BiomePainter.frame(timeMs)
        c.set(14, 19 - f, P.SMOKE); c.set(15, 18 - ((f + 2) % 4), P.SMOKE)
    }
}

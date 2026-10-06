package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Escola: torre do mago/biblioteca de telhado em cone; uma página voa em volta. */
object WizardTowerBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(9, 22, 30)
        c.roof(15, 0, 8, 23, 10, P.PURPLE, P.PURPLE_DARK)
        c.set(15, 3, P.GOLD)
        c.outlined(10, 10, 21, 30, P.STONE_LIGHT)
        for (y in intArrayOf(14, 19, 24)) for (x in intArrayOf(11, 16)) c.hline(x, x + 3, y, P.STONE)
        c.outlined(13, 12, 18, 16, if (night) P.WINDOW_LIT else P.GLASS)
        c.outlined(13, 24, 18, 30, P.DOOR)
        // Página voando num arco ao redor da torre.
        val f = BiomePainter.frame(timeMs, 250, 8)
        val px = intArrayOf(24, 26, 27, 26, 24, 25, 27, 28)[f]
        val py = intArrayOf(8, 6, 8, 10, 12, 14, 12, 10)[f]
        c.box(px, py, px + 1, py + 1, P.PAPER)
        c.set(px, py + 1, P.STONE)
    }
}

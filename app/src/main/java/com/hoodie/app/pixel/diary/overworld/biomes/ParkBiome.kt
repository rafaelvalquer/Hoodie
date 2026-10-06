package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Passeio: coreto no bosque; folhas caem e um passarinho bate asas. */
object ParkBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(4, 27, 30)
        // Árvores dos lados.
        tree(c, 3, 14); tree(c, 27, 12)
        // Coreto: cúpula, colunas e piso.
        c.roof(15, 7, 6, 25, 14, P.TREE_LIGHT, P.TREE)
        c.set(15, 6, P.GOLD)
        for (x in intArrayOf(7, 12, 19, 24)) c.vline(x, 15, 26, P.WALL)
        c.outlined(5, 26, 26, 29, P.STONE_LIGHT)
        if (night) c.set(15, 18, P.WINDOW_LIT)
        // Folha caindo + passarinho.
        val f = BiomePainter.frame(timeMs, 300, 8)
        c.set(4 + f % 3, 4 + f, P.FLOWER_B)
        val wing = BiomePainter.frame(timeMs, 200, 2)
        c.set(20, 2 + wing, P.BIRD); c.set(21, 3, P.BIRD); c.set(22, 2 + wing, P.BIRD)
    }

    private fun tree(c: BiomeCanvas, cx: Int, top: Int) {
        c.vline(cx, top + 8, top + 14, P.TRUNK)
        c.disc(cx, top + 4, 4, P.OUTLINE)
        c.disc(cx, top + 4, 3, P.TREE)
        c.set(cx - 1, top + 2, P.TREE_LIGHT)
    }
}

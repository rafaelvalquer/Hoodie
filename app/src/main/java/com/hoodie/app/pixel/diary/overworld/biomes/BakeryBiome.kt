package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Padaria/cafeteria: croissant no telhado, chaminé do forno fumegando, toldo marrom e vitrine de pães. */
object BakeryBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(3, 28, 30)
        // Chaminé do forno.
        c.outlined(22, 6, 25, 12, P.STONE_DARK)
        val f = BiomePainter.frame(timeMs)
        c.set(23, 4 - f % 2, P.SMOKE); c.set(24 + f % 2, 2 - f % 2, P.SMOKE)
        c.roof(15, 7, 3, 28, 13, P.BAKERY_ROOF, P.BAKERY_ROOF_DARK)
        // Croissant no topo do telhado, com contorno próprio para não sumir no telhado.
        c.hline(11, 19, 4, P.OUTLINE)
        c.vline(10, 5, 7, P.OUTLINE); c.vline(20, 5, 7, P.OUTLINE)
        c.hline(11, 19, 8, P.OUTLINE)
        c.box(11, 5, 19, 7, P.CRUST)
        c.vline(13, 5, 7, P.GOLD_DARK); c.vline(15, 5, 7, P.GOLD_DARK); c.vline(17, 5, 7, P.GOLD_DARK)
        c.set(11, 5, P.OUTLINE); c.set(19, 5, P.OUTLINE)
        c.outlined(4, 14, 27, 30, P.BAKERY_WALL)
        // Toldo marrom e creme.
        c.outlined(3, 14, 28, 17, P.AWNING_B)
        for (x in 4..27 step 4) c.box(x, 15, x + 1, 16, P.BAKERY_ROOF)
        // Vitrine de pães, porta e janela com um pão de forma.
        val glass = if (night) P.WINDOW_LIT else P.GLASS
        c.outlined(5, 20, 12, 26, glass)
        c.box(6, 24, 7, 25, P.CRUST); c.box(9, 24, 11, 25, P.GOLD_DARK); c.set(8, 23, P.CRUST)
        c.outlined(14, 20, 19, 30, P.DOOR)
        c.outlined(21, 20, 26, 26, glass)
        c.disc(23, 24, 1, P.CRUST)
    }
}

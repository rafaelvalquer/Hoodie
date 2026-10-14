package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Shopping: galeria larga com fachada de vidro, letreiro com sacola, porta dupla e bandeirinhas ao vento. */
object MallBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(1, 30, 30)
        c.outlined(1, 12, 30, 30, P.WALL)
        // Faixa do letreiro com a sacola no meio.
        c.outlined(0, 8, 31, 13, P.MALL_SIGN)
        c.outlined(13, 9, 18, 12, P.PAPER)
        c.hline(14, 17, 9, P.MALL_SIGN_DARK); c.set(14, 8, P.OUTLINE); c.set(17, 8, P.OUTLINE)
        // Vitrines dos dois lados (acendem à noite) com um produto exposto em cada.
        val glass = if (night) P.WINDOW_LIT else P.GLASS
        c.outlined(3, 16, 11, 25, glass); c.vline(7, 17, 24, P.GLASS_DARK)
        c.outlined(20, 16, 28, 25, glass); c.vline(24, 17, 24, P.GLASS_DARK)
        c.box(4, 23, 6, 24, P.MALL_SIGN); c.box(25, 22, 27, 24, P.AWNING_C)
        // Porta dupla de vidro, encostada na trilha.
        c.outlined(12, 19, 19, 30, glass); c.vline(15, 20, 29, P.OUTLINE)
        // Bandeirinhas no topo (balançam).
        val f = BiomePainter.frame(timeMs, 500, 2)
        c.vline(3, 3, 8, P.OUTLINE); c.box(4, 3 + f, 6, 4 + f, P.AWNING_C)
        c.vline(28, 3, 8, P.OUTLINE); c.box(25, 3 + f, 27, 4 + f, P.FLAG)
    }
}

package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Agrupamento de paradas rápidas: pilha de pedras com marco de estrada (o "×k" vem na placa). */
object MilestoneBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(7, 25, 30)
        // Marco de pedra com topo arredondado.
        c.outlined(12, 10, 19, 30, P.STONE_LIGHT)
        c.hline(13, 18, 10, P.OUTLINE)
        c.box(13, 9, 18, 9, P.OUTLINE)
        c.hline(13, 18, 14, P.GOLD)
        c.vline(18, 11, 29, P.STONE)
        // Pedras empilhadas dos lados.
        c.outlined(5, 25, 11, 30, P.STONE)
        c.outlined(7, 21, 11, 25, P.STONE_LIGHT)
        c.outlined(20, 26, 26, 30, P.STONE)
        c.set(22, 25, P.STONE_DARK)
    }
}

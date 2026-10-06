package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Outro/desconhecido: acampamento com barraca, placa "?" e bandeirinha. */
object CampBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(3, 24, 30)
        c.roof(13, 9, 2, 24, 30, P.CANVAS, P.WALL_SHADE)
        c.line(13, 13, 13, 29, P.WOOD_DARK)
        c.box(12, 24, 14, 29, P.OUTLINE)
        // Placa "?".
        c.vline(28, 20, 30, P.WOOD_DARK)
        c.outlined(24, 14, 31, 21, P.SIGN)
        c.hline(27, 28, 15, P.OUTLINE); c.set(29, 16, P.OUTLINE); c.set(28, 17, P.OUTLINE); c.set(28, 19, P.OUTLINE)
        // Bandeirinha no topo da barraca.
        c.vline(13, 4, 9, P.OUTLINE)
        val wave = BiomePainter.frame(timeMs, 300, 2)
        c.box(14, 4, 17, 5 + wave, P.FLAG)
        if (night) c.set(11, 27, P.FIRE_CORE)
    }
}

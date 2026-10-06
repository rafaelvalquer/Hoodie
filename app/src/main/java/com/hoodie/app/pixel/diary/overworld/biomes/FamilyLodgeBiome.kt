package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas
import com.hoodie.app.pixel.diary.overworld.OverworldPalette as P

/** Família: chalé de troncos com fogueira na frente soltando faíscas. */
object FamilyLodgeBiome : BiomePainter {
    override fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long) {
        c.shadow(2, 22, 30)
        c.roof(12, 2, 0, 24, 14, P.ROOF_RED_DARK, P.OUTLINE)
        c.outlined(2, 14, 22, 30, P.WOOD)
        for (y in 16..28 step 3) c.hline(3, 21, y, P.WOOD_DARK)
        c.outlined(9, 21, 14, 30, P.DOOR)
        c.outlined(16, 17, 20, 21, if (night) P.WINDOW_LIT else P.GLASS)
        // Fogueira: lenha cruzada, chama e faíscas.
        c.line(23, 30, 30, 27, P.WOOD_DARK); c.line(23, 27, 30, 30, P.WOOD_DARK)
        val f = BiomePainter.frame(timeMs, 180, 4)
        c.box(25, 25 - f % 2, 28, 28, P.FIRE)
        c.box(26, 26, 27, 28, P.FIRE_CORE)
        c.set(24 + f, 22 - f, P.FIRE_CORE)
        c.set(29 - f % 2, 21 - (f + 1) % 3, P.FIRE)
    }
}

package com.hoodie.app.pixel.diary.overworld.biomes

import com.hoodie.app.pixel.diary.overworld.BiomeCanvas

/**
 * Construção do overworld em 32×32 px lógicos (contorno de 1 px, porta embaixo no
 * centro, encostada na trilha). Procedural hoje; um sprite sheet do Aseprite pode
 * substituir pelo mesmo contrato.
 */
interface BiomePainter {
    /** [night] acende janelas/luzes; [timeMs] anima os detalhes vivos. */
    fun paint(c: BiomeCanvas, night: Boolean, timeMs: Long)

    companion object {
        const val SIZE = 32
        /** Quadro da animação lenta (~2,5 FPS no mapa parado). */
        fun frame(timeMs: Long, period: Long = 400L, frames: Int = 4) = ((timeMs / period) % frames).toInt()
    }
}

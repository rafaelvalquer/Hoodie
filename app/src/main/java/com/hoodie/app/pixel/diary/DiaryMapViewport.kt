package com.hoodie.app.pixel.diary

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Ponto em pixels de tela (px do Canvas). */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Única conversão entre o mapa lógico (240×160) e a tela. Canvas, rótulos,
 * hitbox de toque e o marcador do replay usam esta mesma instância — por isso
 * o que se vê é exatamente o que se toca.
 *
 * Escala inteira (pixel perfeito) quando cabe ao menos 1×; o mapa é centralizado.
 */
data class DiaryMapViewport(
    val screenWidth: Float,
    val screenHeight: Float,
    val logicalWidth: Int = DiaryMapTiles.WIDTH,
    val logicalHeight: Int = DiaryMapTiles.HEIGHT,
) {
    val scale: Float = run {
        val fit = min(screenWidth / logicalWidth, screenHeight / logicalHeight)
        if (fit >= 1f) floor(fit) else max(fit, 0.01f)
    }
    val offsetX: Float = (screenWidth - logicalWidth * scale) / 2f
    val offsetY: Float = (screenHeight - logicalHeight * scale) / 2f

    fun toScreen(p: MapPoint) = ScreenPoint(offsetX + p.x * scale, offsetY + p.y * scale)

    fun toLogical(s: ScreenPoint) = MapPoint((s.x - offsetX) / scale, (s.y - offsetY) / scale)

    /** null quando o toque cai fora do mapa (nas bordas de centralização). */
    fun toLogicalOrNull(s: ScreenPoint): MapPoint? = toLogical(s).takeIf { it.x >= 0 && it.y >= 0 && it.x < logicalWidth && it.y < logicalHeight }

    companion object {
        /** Altura para uma largura dada mantendo a proporção do mapa. */
        fun heightFor(width: Float) = width * DiaryMapTiles.HEIGHT / DiaryMapTiles.WIDTH
    }
}

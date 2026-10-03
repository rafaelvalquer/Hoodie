package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.ScreenPoint
import kotlin.math.floor
import kotlin.math.max

/**
 * Única conversão entre a jornada lógica (240 × altura variável) e a tela.
 * Ao contrário do mapa clássico, a altura acompanha o dia: a largura decide a
 * escala (inteira quando cabe ≥ 1×, pixel perfeito) e a altura é consequência.
 * Canvas, cartões, selos, toques e o Hoodie usam esta mesma instância.
 */
data class JourneyViewport(
    val screenWidth: Float,
    val logicalHeight: Int,
    val logicalWidth: Int = JourneyLayoutEngine.WIDTH,
) {
    val scale: Float = run {
        val fit = screenWidth / logicalWidth
        if (fit >= 1f) floor(fit) else max(fit, 0.01f)
    }
    val offsetX: Float = (screenWidth - logicalWidth * scale) / 2f
    val screenHeight: Float = logicalHeight * scale

    fun toScreen(p: MapPoint) = ScreenPoint(offsetX + p.x * scale, p.y * scale)
    fun toLogical(s: ScreenPoint) = MapPoint((s.x - offsetX) / scale, s.y / scale)
    fun toLogicalOrNull(s: ScreenPoint): MapPoint? =
        toLogical(s).takeIf { it.x >= 0 && it.y >= 0 && it.x < logicalWidth && it.y < logicalHeight }

    /** Retângulo lógico → (x, y, largura, altura) em px de tela. */
    fun toScreen(r: JourneyRect): FloatArray = floatArrayOf(offsetX + r.x * scale, r.y * scale, r.w * scale, r.h * scale)
}

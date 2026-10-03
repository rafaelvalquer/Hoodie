package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.journey.JourneyPalette as P
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.abs
import kotlin.math.roundToInt

/** Estado de um trecho no replay. */
enum class SegmentState { DONE, ACTIVE, UPCOMING }

/**
 * Trechos da jornada: a rua (estática, igual para todo meio de transporte) e,
 * por cima, o traço do meio — 🚶 pontilhado verde, 🚗 linha dupla cinza,
 * 🚌 azul contínua, 🚇 trilho roxo/ciano, 🚲 fio amarelo.
 */
object JourneySegmentRenderer {
    const val ROAD_HALF = 3

    /** Rua asfaltada com meio-fio, desenhada uma vez na camada estática. */
    fun paintRoad(b: PixelBuffer, path: JourneyPath) {
        stamp(b, path, ROAD_HALF + 1, P.ROAD_EDGE)
        stamp(b, path, ROAD_HALF, P.ROAD)
    }

    private fun stamp(b: PixelBuffer, path: JourneyPath, half: Int, color: Int) {
        walk(path) { p, _, _ -> b.box(p.x.roundToInt() - half, p.y.roundToInt() - half, p.x.roundToInt() + half, p.y.roundToInt() + half, color) }
    }

    /**
     * Traço do meio de transporte. ACTIVE: a parte já percorrida fica dourada
     * (o rastro do Hoodie); UPCOMING: o trecho aparece apagado.
     */
    fun paintRoute(b: PixelBuffer, path: JourneyPath, mode: MovementMode?, state: SegmentState, progress: Float = 1f, timeMs: Long = 0) {
        val style = P.route(mode)
        val traveled = if (state == SegmentState.ACTIVE) progress.coerceIn(0f, 1f) * path.length else Float.MAX_VALUE
        walk(path) { p, d, dir ->
            val done = d <= traveled
            var main = if (state == SegmentState.ACTIVE && done) P.GOLD else style.color
            var accent = if (state == SegmentState.ACTIVE && done) P.GOLD else style.accent
            if (state == SegmentState.UPCOMING) { main = P.dim(main) and 0x99FFFFFF.toInt(); accent = P.dim(accent) and 0x99FFFFFF.toInt() }
            val x = p.x.roundToInt(); val y = p.y.roundToInt()
            // Perpendicular ao sentido do caminho (para linhas duplas e trilhos).
            val (px, py) = if (abs(dir.first) >= abs(dir.second)) 0 to 1 else 1 to 0
            val step = d.toInt()
            when (style.stroke) {
                P.Stroke.DOTTED -> if (step % 4 < 2) { b.set(x, y, main); b.set(x + px, y + py, main) }
                P.Stroke.DOUBLE -> { b.set(x - 2 * px, y - 2 * py, main); b.set(x + 2 * px, y + 2 * py, main); if (step % 6 < 3) b.set(x, y, accent) }
                P.Stroke.SOLID -> { b.set(x, y, main); b.set(x + px, y + py, main); b.set(x - px, y - py, accent) }
                P.Stroke.SEGMENTED -> {
                    b.set(x - 2 * px, y - 2 * py, main); b.set(x + 2 * px, y + 2 * py, main)
                    if (step % 4 == 0) for (k in -2..2) b.set(x + k * px, y + k * py, accent)
                }
                P.Stroke.THIN -> b.set(x, y, main)
            }
        }
        if (state != SegmentState.UPCOMING) arrows(b, path, style.accent.takeIf { state != SegmentState.ACTIVE } ?: P.GOLD, timeMs)
    }

    /** Duas setinhas na rua horizontal mostrando o sentido do dia. No trecho ativo elas "andam". */
    private fun arrows(b: PixelBuffer, path: JourneyPath, color: Int, timeMs: Long) {
        val dirX = path.horizontalSign.toInt()
        if (dirX == 0 || path.points.size < 4) return
        val a = path.points[2]; val c = path.points[3]
        val span = abs(c.x - a.x).toInt()
        if (span < 24) return
        val shift = ((timeMs / 250) % 4).toInt()
        listOf(span / 3, 2 * span / 3).forEach { off ->
            val x = (a.x + dirX * (off + shift)).roundToInt(); val y = a.y.roundToInt()
            for (k in 0..2) { b.set(x - dirX * k, y - k, color); b.set(x - dirX * k, y + k, color) }
        }
    }

    /** Percorre o caminho de 1 em 1 px de comprimento: (ponto, distância desde o início, direção). */
    private inline fun walk(path: JourneyPath, f: (MapPoint, Float, Pair<Float, Float>) -> Unit) {
        val len = path.length
        if (len <= 0f) return
        var d = 0f
        while (d <= len) {
            val t = d / len
            f(path.pointAt(t), d, path.directionAt(t))
            d += 1f
        }
    }
}

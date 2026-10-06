package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.journey.JourneyPath
import com.hoodie.app.pixel.diary.overworld.OverworldPalette.Pattern
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Trilhas do overworld: leito por meio de transporte (terra, pedra, trilhos…) com um
 * padrão próprio além da cor, e o rastro dourado do que já foi percorrido no replay.
 */
object OverworldTrailPainter {

    /** Percorre o caminho a cada pixel: (ponto, distância, perpendicular unitária). */
    private inline fun walk(path: JourneyPath, upTo: Float = 1f, f: (MapPoint, Float, Float, Float) -> Unit) {
        val len = path.length
        if (len <= 0f || path.points.size < 2) return
        val end = (len * upTo.coerceIn(0f, 1f)).toInt()
        for (d in 0..end) {
            val t = d / len
            val p = path.pointAt(t)
            val (dx, dy) = path.directionAt(t)
            val n = hypot(dx, dy).takeIf { it > 0f } ?: 1f
            f(p, d.toFloat(), -dy / n, dx / n)
        }
    }

    private fun stamp(b: PixelBuffer, p: MapPoint, px: Float, py: Float, offset: Float, color: Int) {
        b.set((p.x + px * offset).roundToInt(), (p.y + py * offset).roundToInt(), color)
    }

    /** Camada estática: leito + bordas + padrão do meio. */
    fun paintBed(b: PixelBuffer, path: JourneyPath, mode: MovementMode?) {
        val s = OverworldPalette.trail(mode)
        val half = s.width / 2
        // Bordas primeiro, leito por cima: curvas ficam sem buracos.
        walk(path) { p, _, px, py ->
            stamp(b, p, px, py, half + 1f, s.bedEdge); stamp(b, p, px, py, -(half + 1f), s.bedEdge)
        }
        walk(path) { p, _, px, py -> for (o in -half..half) stamp(b, p, px, py, o.toFloat(), s.bed) }
        walk(path) { p, d, px, py ->
            val i = d.toInt()
            when (s.pattern) {
                Pattern.DOTTED -> if (i % 4 == 0) stamp(b, p, px, py, 0f, s.mark)
                Pattern.DASHED -> if (i % 6 < 3) stamp(b, p, px, py, 0f, s.mark)
                Pattern.DOUBLE -> { stamp(b, p, px, py, -1f, s.mark); stamp(b, p, px, py, 1f, s.mark) }
                Pattern.MARKERS -> {
                    stamp(b, p, px, py, 0f, s.mark)
                    if (i % 24 == 12) for (k in 0..2) stamp(b, MapPoint(p.x, p.y - k), px, py, half + 2f, OverworldPalette.STONE_DARK)
                }
                Pattern.SLEEPERS -> {
                    if (i % 3 == 0) for (o in -half + 1..half - 1) stamp(b, p, px, py, o.toFloat(), s.mark)
                    stamp(b, p, px, py, -2f, OverworldPalette.STONE_DARK); stamp(b, p, px, py, 2f, OverworldPalette.STONE_DARK)
                }
            }
        }
    }

    /** Pracinha de terra na porta de cada parada (as trilhas se encontram nela). */
    fun paintPlaza(b: PixelBuffer, at: MapPoint) {
        val cx = at.x.roundToInt(); val cy = at.y.roundToInt()
        b.disc(cx, cy, 5, OverworldPalette.DIRT_DARK)
        b.disc(cx, cy, 4, OverworldPalette.DIRT)
        b.set(cx - 2, cy - 1, OverworldPalette.SIGN_LIGHT)
    }

    /** Rastro dourado até [progress] do caminho (0..1). */
    fun paintGold(b: PixelBuffer, path: JourneyPath, progress: Float) {
        if (progress <= 0f) return
        walk(path, progress) { p, _, px, py ->
            stamp(b, p, px, py, -1f, OverworldPalette.GOLD_DARK)
            stamp(b, p, px, py, 0f, OverworldPalette.GOLD)
            stamp(b, p, px, py, 1f, OverworldPalette.GOLD)
        }
    }
}

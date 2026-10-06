package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.pixel.diary.MapPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * Caminho de um trecho em pontos lógicos. O Hoodie anda por ele com velocidade
 * constante (progresso → distância percorrida), então nunca "teleporta".
 */
data class JourneyPath(val segmentId: String, val points: List<MapPoint>) {
    private val cumulative: FloatArray = FloatArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + dist(points[i - 1], points[i])
    }

    val length: Float get() = cumulative.lastOrNull() ?: 0f

    /** Ponto a [progress] (0..1) do comprimento do caminho. */
    fun pointAt(progress: Float): MapPoint {
        if (points.isEmpty()) return MapPoint(0f, 0f)
        if (points.size == 1 || length <= 0f) return points.first()
        val target = progress.coerceIn(0f, 1f) * length
        val i = indexAt(target)
        val a = points[i]; val b = points[i + 1]
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen <= 0f) 0f else (target - cumulative[i]) / segLen
        return MapPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    /** Direção do trecho do caminho em [progress] (dx, dy normalizados para o eixo dominante -1/0/1). */
    fun directionAt(progress: Float): Pair<Float, Float> {
        if (points.size < 2) return 0f to 1f
        val i = indexAt(progress.coerceIn(0f, 1f) * length)
        val a = points[i]; val b = points[i + 1]
        return (b.x - a.x) to (b.y - a.y)
    }

    /** Sentido horizontal do trecho como um todo (o veículo não "vira de frente" nas descidas). */
    val horizontalSign: Float get() = sign((points.lastOrNull()?.x ?: 0f) - (points.firstOrNull()?.x ?: 0f))

    /** Busca binária no comprimento acumulado: O(log n) por quadro de replay. */
    private fun indexAt(distance: Float): Int {
        var lo = 0
        var hi = points.size - 2
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (distance <= cumulative[mid + 1]) hi = mid else lo = mid + 1
        }
        return lo.coerceIn(0, maxOf(0, points.size - 2))
    }

    private companion object {
        fun dist(a: MapPoint, b: MapPoint) = hypot(b.x - a.x, b.y - a.y)
    }
}

object JourneyPathBuilder {
    /** Chanfro das esquinas: o Hoodie faz a curva na diagonal. */
    const val CORNER = 8

    /**
     *     A ◉
     *       │            (desce da plataforma A)
     *       ╰──────────╮ (curva + rua horizontal em [crossY])
     *                  │ (desce até a plataforma B)
     *                  ◉ B
     */
    fun build(segmentId: String, from: MapPoint, to: MapPoint, crossY: Int): JourneyPath {
        val y = crossY.toFloat()
        val dir = sign(to.x - from.x)
        if (dir == 0f || abs(to.x - from.x) < 2 * CORNER) {
            // Mesma coluna (não acontece no zigue-zague, mas fica seguro): linha reta.
            return JourneyPath(segmentId, listOf(from, to))
        }
        val r = CORNER.toFloat()
        val points = buildList {
            add(from)
            add(MapPoint(from.x, (y - r).coerceAtLeast(from.y)))
            add(MapPoint(from.x + dir * r, y))
            add(MapPoint(to.x - dir * r, y))
            add(MapPoint(to.x, (y + r).coerceAtMost(to.y)))
            add(to)
        }
        return JourneyPath(segmentId, points.zipWithNext().filter { (a, b) -> a != b }.map { it.first } + points.last())
    }

    // ───── Jornada 3.0 (serpentina) ─────

    /** Paradas na mesma linha: trecho reto horizontal. */
    fun straight(id: String, from: MapPoint, to: MapPoint): JourneyPath = JourneyPath(id, listOf(from, to))

    /**
     * Virada de linha: curva em U pela borda (Bézier cúbica amostrada em pontos inteiros),
     * com o bojo para fora ([bulge] px; [outward] +1 para a direita, -1 para a esquerda).
     */
    fun uTurn(id: String, from: MapPoint, to: MapPoint, bulge: Int, outward: Int, samples: Int = 18): JourneyPath {
        val c1 = MapPoint(from.x + outward * bulge, from.y)
        val c2 = MapPoint(to.x + outward * bulge, to.y)
        val pts = (0..samples).map { i ->
            val t = i / samples.toFloat()
            val u = 1 - t
            val x = u * u * u * from.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * to.x
            val y = u * u * u * from.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * to.y
            MapPoint(kotlin.math.round(x), kotlin.math.round(y))
        }.distinct()
        return JourneyPath(id, pts)
    }

    /** Saída de capítulo: da parada até a borda de baixo (portal). */
    fun portalOut(id: String, from: MapPoint, bottomY: Int): JourneyPath =
        JourneyPath(id, listOf(from, MapPoint(from.x, bottomY.toFloat())))

    /** Entrada de capítulo: da borda de cima, ao lado do prédio, até a porta da primeira parada. */
    fun portalIn(id: String, topX: Int, to: MapPoint): JourneyPath =
        JourneyPath(id, listOf(MapPoint(topX.toFloat(), 0f), MapPoint(topX.toFloat(), to.y), to).distinct())
}

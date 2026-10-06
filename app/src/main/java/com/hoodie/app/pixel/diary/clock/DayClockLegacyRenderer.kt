package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.domain.diary.journey.ClockArc
import com.hoodie.app.domain.diary.journey.DayClockLegacyData
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.MarkerDirection
import com.hoodie.app.pixel.diary.journey.JourneyHoodieMarker
import com.hoodie.app.pixel.diary.journey.JourneyMarkerState
import com.hoodie.app.pixel.diary.journey.JourneyVehicle
import com.hoodie.app.pixel.diary.overworld.BiomeState
import com.hoodie.app.pixel.diary.overworld.OverworldBiomeCatalog
import com.hoodie.app.pixel.diary.overworld.OverworldDecorPlacer
import com.hoodie.app.pixel.diary.overworld.OverworldPalette
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Um quadro do relógio: dados do dia + onde o replay está (null = parado, dia inteiro "feito"). */
data class DayClockLegacyScene(
    val data: DayClockLegacyData,
    /** Grau do replay (0..360) ou null. */
    val replayDeg: Float? = null,
    val selectedArcId: String? = null,
    val timeMs: Long = 0L,
    val seed: Long = 0L,
)

/**
 * Relógio do dia em canvas fixo 240×240: trilha de pedra em volta de uma praça com
 * relógio de sol. Permanências = arcos grossos na cor do lugar; trechos = arcos finos
 * com o padrão do meio; paradas curtas = tiques. No replay a parte futura fica
 * dessaturada e um ponteiro + o Hoodie percorrem o anel.
 */
object DayClockLegacyRenderer {
    const val SIZE = 240
    const val CX = 120
    const val CY = 120
    const val R_OUTER = 104
    const val R_STAY_OUT = 100
    const val R_STAY_IN = 84
    const val R_INNER = 80
    const val R_LEG_OUT = 94
    const val R_LEG_IN = 90
    const val R_MID = 92

    /** Ponto do anel no grau [deg] (00 h no topo, sentido horário). */
    fun point(deg: Float, r: Float): MapPoint {
        val a = Math.toRadians(deg.toDouble() - 90.0)
        return MapPoint((CX + r * cos(a)).toFloat(), (CY + r * sin(a)).toFloat())
    }

    /** Grau sob um ponto lógico (para o toque). */
    fun degAt(p: MapPoint): Float {
        val a = Math.toDegrees(atan2((p.y - CY).toDouble(), (p.x - CX).toDouble())) + 90.0
        return ((a + 360.0) % 360.0).toFloat()
    }

    fun radiusAt(p: MapPoint) = hypot(p.x - CX, p.y - CY)

    /** Anel estático do dia (praça, pedra, horas e arcos no estado "feito"). Cacheável por dia. */
    fun staticLayer(data: DayClockLegacyData, seed: Long): PixelBuffer {
        val b = PixelBuffer(SIZE, SIZE)
        OverworldDecorPlacer.paintGround(b, seed)
        ring(b, R_OUTER + 3, R_INNER - 2) { _, _ -> OverworldPalette.OUTLINE }
        ring(b, R_OUTER + 2, R_INNER - 1) { d, r -> if ((d.toInt() / 6 + r) % 2 == 0) DayClockLegacyPalette.RING else DayClockLegacyPalette.RING_LIGHT }
        // Praça com relógio de sol.
        b.disc(CX, CY, R_INNER - 3, OverworldPalette.OUTLINE)
        b.disc(CX, CY, R_INNER - 4, DayClockLegacyPalette.PLAZA)
        for (r in 20 until R_INNER - 6 step 14) ring(b, r + 1, r) { _, _ -> DayClockLegacyPalette.PLAZA_DARK }
        // Marcas das horas na borda de fora (maiores em 0, 6, 12, 18).
        val hours = 24
        for (h in 0 until hours) {
            val deg = h * 360f / hours
            val long = h % 6 == 0
            line(b, point(deg, R_OUTER + 2f), point(deg, R_OUTER + if (long) 9f else 6f), DayClockLegacyPalette.HOUR)
        }
        data.arcs.forEach { paintArc(b, it, future = false) }
        return b
    }

    fun render(scene: DayClockLegacyScene, staticLayer: PixelBuffer? = null, out: PixelBuffer? = null): PixelBuffer {
        val b = out?.takeIf { it.width == SIZE && it.height == SIZE } ?: PixelBuffer(SIZE, SIZE)
        (staticLayer ?: staticLayer(scene.data, scene.seed)).pixels.copyInto(b.pixels)
        val replay = scene.replayDeg
        // Parte futura dessaturada: repinta por cima o que ainda não aconteceu.
        if (replay != null) scene.data.arcs.forEach { arc ->
            if (arc.endDeg > replay) paintArc(b, arc, future = true, fromDeg = maxOf(arc.startDeg, replay))
        }
        scene.data.arcs.firstOrNull { it.id == scene.selectedArcId }?.let { outlineArc(b, it) }
        scene.data.ticks.forEach { t ->
            val future = replay != null && t.deg > replay
            val color = if (future) OverworldPalette.desaturate(OverworldPalette.GOLD) else OverworldPalette.GOLD
            line(b, point(t.deg, R_STAY_IN - 2f), point(t.deg, R_OUTER + 1f), OverworldPalette.OUTLINE)
            line(b, point(t.deg, R_STAY_IN - 1f), point(t.deg, R_OUTER.toFloat()), color)
            if (t.grouped) {
                val p = point(t.deg, R_OUTER + 5f)
                b.disc(p.x.roundToInt(), p.y.roundToInt(), 3, OverworldPalette.OUTLINE)
                b.disc(p.x.roundToInt(), p.y.roundToInt(), 2, color)
            }
        }
        // Mini ícones dos arcos longos (a construção do overworld, reduzida).
        scene.data.labels.forEach { l ->
            val arc = scene.data.arcs.firstOrNull { it.id == l.arcId } ?: return@forEach
            val p = point(arc.midDeg, R_MID.toFloat())
            miniIcon(b, l.biome, p.x.roundToInt(), p.y.roundToInt(), replay != null && arc.startDeg > replay, scene.timeMs)
        }
        if (replay != null) {
            line(b, MapPoint(CX.toFloat(), CY.toFloat()), point(replay, R_INNER - 6f), OverworldPalette.OUTLINE)
            line(b, MapPoint(CX.toFloat() - 1, CY.toFloat()), point(replay, R_INNER - 7f), DayClockLegacyPalette.POINTER)
            b.disc(CX, CY, 3, OverworldPalette.OUTLINE); b.disc(CX, CY, 2, DayClockLegacyPalette.POINTER)
            val at = point(replay, R_MID.toFloat())
            JourneyHoodieMarker.paint(b, JourneyMarkerState(at, MarkerDirection.FRONT, moving = true, vehicle = JourneyVehicle.ON_FOOT, facingRight = replay < 180f), scene.timeMs)
        } else {
            // Gnômon do relógio de sol.
            for (k in 0..14) b.hline(CX - k / 3, CX + k / 3, CY - 14 + k, DayClockLegacyPalette.GNOMON)
            b.disc(CX, CY + 2, 3, OverworldPalette.OUTLINE)
        }
        return b
    }

    private val iconCache = HashMap<BiomeType, PixelBuffer>()

    /** Construção 32×32 reduzida para 14×14 (vizinho mais próximo). */
    private fun miniIcon(b: PixelBuffer, biome: BiomeType?, cx: Int, cy: Int, future: Boolean, timeMs: Long) {
        val src = iconCache.getOrPut(biome ?: BiomeType.CAMP) {
            PixelBuffer(32, 32).also { OverworldBiomeCatalog.paint(it, biome ?: BiomeType.CAMP, 0, 0, BiomeState.VISITED, false, 0L) }
        }
        val size = 14
        b.disc(cx, cy, 9, OverworldPalette.OUTLINE)
        b.disc(cx, cy, 8, if (future) OverworldPalette.desaturate(DayClockLegacyPalette.PLAZA) else DayClockLegacyPalette.PLAZA)
        for (y in 0 until size) for (x in 0 until size) {
            val c = src[x * 32 / size, y * 32 / size]
            if (c ushr 24 != 0) b.set(cx - size / 2 + x, cy - size / 2 + y, if (future) OverworldPalette.desaturate(c) else c)
        }
    }

    private fun paintArc(b: PixelBuffer, arc: ClockArc, future: Boolean, fromDeg: Float = arc.startDeg) {
        val base = if (arc.kind == ClockArc.Kind.STAY) DayClockLegacyPalette.biome(arc.biome) else OverworldPalette.trail(arc.mode).mark
        val color = if (future) OverworldPalette.desaturate(base) else base
        val (rIn, rOut) = if (arc.kind == ClockArc.Kind.STAY) R_STAY_IN to R_STAY_OUT else R_LEG_IN to R_LEG_OUT
        val pattern = arc.mode?.let { OverworldPalette.trail(it).pattern }
        ring(b, rOut, rIn, fromDeg, arc.endDeg) { d, r ->
            val edge = r == rIn || r == rOut
            when {
                edge -> OverworldPalette.OUTLINE
                arc.kind == ClockArc.Kind.LEG && pattern != null && !patternOn(pattern, d, r) -> if (future) OverworldPalette.desaturate(DayClockLegacyPalette.RING_LIGHT) else DayClockLegacyPalette.RING_LIGHT
                else -> color
            }
        }
    }

    /** Padrão do meio também no relógio (acessível sem depender só da cor). */
    private fun patternOn(p: OverworldPalette.Pattern, deg: Float, r: Int): Boolean {
        val s = (deg * 3f).toInt()
        return when (p) {
            OverworldPalette.Pattern.DOTTED -> s % 3 == 0
            OverworldPalette.Pattern.DASHED -> s % 4 < 2
            OverworldPalette.Pattern.DOUBLE -> r != R_MID
            OverworldPalette.Pattern.MARKERS -> true
            OverworldPalette.Pattern.SLEEPERS -> s % 3 != 1
        }
    }

    private fun outlineArc(b: PixelBuffer, arc: ClockArc) {
        val c = com.hoodie.app.pixel.diary.journey.JourneyPalette.SELECTED
        ring(b, R_STAY_OUT + 1, R_STAY_OUT + 1, arc.startDeg, arc.endDeg) { _, _ -> c }
        ring(b, R_STAY_IN - 1, R_STAY_IN - 1, arc.startDeg, arc.endDeg) { _, _ -> c }
    }

    /** Preenche a coroa entre [rIn] e [rOut] de [fromDeg] a [toDeg]. */
    private inline fun ring(b: PixelBuffer, rOut: Int, rIn: Int, fromDeg: Float = 0f, toDeg: Float = 360f, color: (Float, Int) -> Int) {
        for (y in CY - rOut..CY + rOut) for (x in CX - rOut..CX + rOut) {
            val dist = hypot((x - CX).toFloat(), (y - CY).toFloat())
            val r = dist.roundToInt()
            if (r < rIn || r > rOut) continue
            val d = degAt(MapPoint(x.toFloat(), y.toFloat()))
            if (d < fromDeg || d > toDeg) continue
            b.set(x, y, color(d, r))
        }
    }

    private fun line(b: PixelBuffer, a: MapPoint, c: MapPoint, color: Int) =
        b.line(a.x.roundToInt(), a.y.roundToInt(), c.x.roundToInt(), c.y.roundToInt(), color)
}

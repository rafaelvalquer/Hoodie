package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.clock.DayClockData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** Pixel lógico do mostrador. */
data class ClockPx(val x: Int, val y: Int)

sealed interface ClockHit {
    data object Center : ClockHit
    data class Segment(val id: String) : ClockHit
    data object FutureArea : ClockHit
    data object None : ClockHit
}

/**
 * Mostrador em pixel art fina: 312×312 px lógicos (base 104 × [K]), 00h no topo, sentido
 * horário. Na tela fica em escala inteira (×3 num Pixel 8 → ~1 dp por pixel). Raio e
 * fração do dia de cada pixel ficam em duas tabelas calculadas uma vez — renderer e
 * toque usam as mesmas, sem atan2 por frame.
 */
object DayClockGeometry {
    /** Fator de resolução sobre o desenho-base de 104 px. */
    const val K = 3
    const val SIZE = 104 * K
    const val CENTER = 52f * K
    val CENTER_PLATE = 0f..29.5f * K
    /** Bisel da placa (até 92.5) + contorno de 2 px. */
    val PLATE_BORDER = 29.5f * K..31.5f * K
    val ACTIVITY_RING = 31.5f * K..41.5f * K
    val SELECTION_RING = 41.5f * K..128.5f
    val TICK_RING = 41.5f * K..43.6f * K
    val SKY_RING = 43.6f * K..49.5f * K
    const val OUTER_EDGE = 50.5f * K
    /** Raio do meio do anel de atividades: ícones, Hoodie e larguras mínimas. */
    const val ACTIVITY_MID = 36.5f * K
    /** Trilha dos deslocamentos (5 px). */
    val TRAIL = 107f..112f
    /** Raio das plaquinhas 00/06/12/18. */
    const val PLAQUE_RADIUS = 46.5f * K

    /** Raio do centro de cada pixel. */
    val RADIUS: FloatArray = FloatArray(SIZE * SIZE) { i -> hypot(i % SIZE + 0.5f - CENTER, i / SIZE + 0.5f - CENTER) }

    /** Fração do dia (0..1) do centro de cada pixel. */
    val FRACTION: FloatArray = FloatArray(SIZE * SIZE) { i -> fractionAt(i % SIZE + 0.5f, i / SIZE + 0.5f) }

    fun fractionAt(x: Float, y: Float): Float {
        val a = atan2(x - CENTER, -(y - CENTER))
        val f = (a / (2 * PI)).toFloat()
        return if (f < 0f) f + 1f else f
    }

    fun minuteToAngle(minute: Int, dayLength: Int): Float = minute.toFloat() / dayLength * 360f

    fun pointAt(minute: Float, radius: Float, dayLength: Int): ClockPx {
        val a = minute / dayLength * 2 * PI
        return ClockPx(floor(CENTER + radius * sin(a)).toInt(), floor(CENTER - radius * cos(a)).toInt())
    }

    fun pointAt(minute: Int, radius: Float, dayLength: Int): ClockPx = pointAt(minute.toFloat(), radius, dayLength)

    fun minuteAt(x: Float, y: Float, dayLength: Int): Float = fractionAt(x, y) * dayLength

    /** Comprimento (px) do arco de [minutes] no raio [r]. */
    fun arcPx(minutes: Float, dayLength: Int, r: Float = ACTIVITY_MID): Float = (minutes / dayLength * 2 * PI * r).toFloat()

    /** Distância angular em px entre a fração [f] e a fração [g] no raio [r] (com volta). */
    fun angularPx(f: Float, g: Float, r: Float): Float {
        var d = abs(f - g)
        if (d > 0.5f) d = 1f - d
        return (d * 2 * PI * r).toFloat()
    }

    /**
     * Toque em px lógicos. Centro → volta ao agora; anel (com folga de toque até o céu)
     * → segmento; depois do "agora" → área futura (ignorada pela UI).
     */
    fun hitTest(x: Float, y: Float, data: DayClockData, nowMinute: Int? = data.nowMinute): ClockHit {
        val r = hypot(x - CENTER, y - CENTER)
        if (r <= CENTER_PLATE.endInclusive) return ClockHit.Center
        if (r > SKY_RING.endInclusive + K) return ClockHit.None
        val len = data.dayLengthMinutes
        val m = minuteAt(x, y, len)
        if (nowMinute != null && m > nowMinute) return ClockHit.FutureArea
        // Trechos mais estreitos que ~2 px (da base) ganham uma área de toque própria.
        val f = m / len
        data.segments.filter { it !is ClockSegment.Unknown && arcPx(it.minutes.toFloat(), len) < 2f * K }
            .map { it to angularPx(f, (it.startMinute + it.minutes / 2f) / len, ACTIVITY_MID) }
            .filter { it.second <= 1.5f * K }
            .minByOrNull { it.second }?.let { return ClockHit.Segment(it.first.id) }
        return data.segmentAt(m)?.let { ClockHit.Segment(it.id) } ?: ClockHit.None
    }
}

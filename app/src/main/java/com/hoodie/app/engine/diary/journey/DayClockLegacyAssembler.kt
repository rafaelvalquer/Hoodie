package com.hoodie.app.engine.diary.journey

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.domain.diary.journey.ClockArc
import com.hoodie.app.domain.diary.journey.ClockLabel
import com.hoodie.app.domain.diary.journey.ClockTick
import com.hoodie.app.domain.diary.journey.DayClockLegacyData
import com.hoodie.app.domain.diary.model.JourneyMapData
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI

data class DayClockLegacyConfig(
    val minArcDeg: Float = HoodieConfig.CLOCK_MIN_ARC_DEG,
    val minLabelDeg: Float = HoodieConfig.CLOCK_MIN_LABEL_DEG,
    val tickMergeDeg: Float = HoodieConfig.CLOCK_TICK_MERGE_DEG,
    /** Raio onde os rótulos ficam (px lógicos do relógio de 240). */
    val labelRadius: Float = 62f,
    /** Largura aproximada de um caractere do rótulo (px lógicos). */
    val charWidth: Float = 4.2f,
    val labelMaxChars: Int = 10,
)

/**
 * Dia → anel de 24 h. Puro. Ângulo proporcional à duração REAL do dia (ZonedDateTime):
 * em dias de 23 ou 25 h a escala acompanha. 00 h no topo, sentido horário.
 */
object DayClockLegacyAssembler {

    data class Day(val start: Long, val end: Long) {
        val lengthMs get() = (end - start).coerceAtLeast(1)
        val hours get() = lengthMs / 3_600_000f
        fun deg(t: Long): Float = ((t - start).toFloat() / lengthMs * 360f).coerceIn(0f, 360f)
        fun timeAt(deg: Float): Long = start + (deg.coerceIn(0f, 360f) / 360f * lengthMs).toLong()
    }

    fun day(date: LocalDate, zone: ZoneId) = Day(
        date.atStartOfDay(zone).toInstant().toEpochMilli(),
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    fun build(data: JourneyMapData, zone: ZoneId, date: LocalDate = JourneyChapterPlanner.dateOf(data, zone), config: DayClockLegacyConfig = DayClockLegacyConfig()): DayClockLegacyData {
        val d = day(date, zone)
        if (data.isEmpty) return DayClockLegacyData.EMPTY.copy(dayHours = d.hours, dayStart = d.start, dayEnd = d.end)
        val openEnd = maxOf(data.endAt, data.nodes.last().arrivalAt)
        val arcs = mutableListOf<ClockArc>()
        val shortStays = mutableListOf<Triple<String, Long, Long>>()
        data.nodes.forEach { n ->
            val s = maxOf(n.arrivalAt, d.start); val e = minOf(n.departureAt ?: openEnd, d.end)
            if (e <= s) return@forEach
            val sweep = d.deg(e) - d.deg(s)
            if (sweep < config.minArcDeg) shortStays += Triple(n.id, s, e)
            else arcs += ClockArc(
                id = "stay-${n.id}", kind = ClockArc.Kind.STAY, startDeg = d.deg(s), sweepDeg = sweep,
                biome = BiomeType.of(n.placeType, n.durationMs, n.hoodieActivity), mode = null, stopId = n.id,
                startAt = s, endAt = e,
            )
        }
        data.segments.forEach { seg ->
            val s = maxOf(seg.startedAt, d.start); val e = minOf(seg.endedAt, d.end)
            if (e <= s) return@forEach
            val sweep = d.deg(e) - d.deg(s)
            if (sweep < 0.5f) return@forEach
            arcs += ClockArc("leg-${seg.id}", ClockArc.Kind.LEG, d.deg(s), sweep, null, seg.movementMode, null, s, e)
        }
        val ticks = mergeTicks(shortStays.map { (id, s, e) -> ClockTick("tick-$id", d.deg(s + (e - s) / 2), listOf(id), s, e) }, config.tickMergeDeg)
        val names = data.nodes.associate { it.id to it.placeName }
        return DayClockLegacyData(arcs.sortedBy { it.startDeg }, ticks, labels(arcs, names, config), d.hours, d.start, d.end)
    }

    /** Tiques consecutivos a menos de [mergeDeg] viram um só "×k". */
    fun mergeTicks(ticks: List<ClockTick>, mergeDeg: Float): List<ClockTick> {
        val out = mutableListOf<ClockTick>()
        ticks.sortedBy { it.deg }.forEach { t ->
            val last = out.lastOrNull()
            if (last != null && t.deg - lastDeg(last) < mergeDeg) {
                out[out.lastIndex] = ClockTick(
                    id = last.id, deg = (last.deg * last.count + t.deg) / (last.count + 1),
                    stopIds = last.stopIds + t.stopIds, startAt = last.startAt, endAt = t.endAt,
                )
            } else out += t
        }
        return out
    }

    private fun lastDeg(t: ClockTick) = t.deg

    /**
     * Rótulos só nos arcos de permanência longos, no raio interno. Colisão por faixa
     * angular: os maiores arcos ficam, os menores que colidem saem.
     */
    fun labels(arcs: List<ClockArc>, names: Map<String, String>, config: DayClockLegacyConfig): List<ClockLabel> {
        val accepted = mutableListOf<Pair<ClockLabel, ClosedFloatingPointRange<Float>>>()
        arcs.filter { it.kind == ClockArc.Kind.STAY && it.sweepDeg >= config.minLabelDeg }
            .sortedByDescending { it.sweepDeg }
            .forEach { arc ->
                val name = names[arc.stopId].orEmpty()
                val text = if (name.length <= config.labelMaxChars) name else name.take(config.labelMaxChars - 1).trimEnd() + "…"
                val halfDeg = ((text.length * config.charWidth + 6f) / config.labelRadius * 180f / PI.toFloat()) / 2f
                val range = (arc.midDeg - halfDeg)..(arc.midDeg + halfDeg)
                if (accepted.none { (_, r) -> r.start < range.endInclusive && range.start < r.endInclusive }) {
                    accepted += ClockLabel(arc.id, text, arc.midDeg, arc.biome) to range
                }
            }
        return accepted.map { it.first }.sortedBy { it.deg }
    }

    /** O que o toque em [deg] acerta: tique (perto) > arco de permanência > trecho. */
    fun hit(data: DayClockLegacyData, deg: Float, tickToleranceDeg: Float = 3f): Any? =
        data.ticks.minByOrNull { kotlin.math.abs(it.deg - deg) }?.takeIf { kotlin.math.abs(it.deg - deg) <= tickToleranceDeg }
            ?: data.arcs.firstOrNull { it.kind == ClockArc.Kind.STAY && deg in it }
            ?: data.arcs.firstOrNull { deg in it }
}

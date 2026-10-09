package com.hoodie.app.engine.diary

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.daycycle.SleepConfidence
import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.domain.diary.clock.ClockHourMark
import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.clock.DayClockData
import com.hoodie.app.domain.diary.clock.SleepPhase
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.JourneyMapData
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Relógio do Dia 2.0: visitas + deslocamentos + atividades do Hoodie → segmentos do
 * mostrador de 24 h. Puro (JVM). Regras:
 * - recorta tudo no dia local e, hoje, no "agora";
 * - visitas repetidas continuam separadas (ids `journey-i`, os mesmos da Jornada);
 * - deslocamento entre duas visitas usa o meio dominante da Jornada (`journey-seg-i`);
 *   fora disso, trechos de mobilidade avulsos viram `move-k`;
 * - o que sobra vira [ClockSegment.Unknown] — nunca uma suposição.
 */
object DayClockAssembler {
    private const val MINUTE_MS = 60_000L

    fun build(
        diary: DailyDiary,
        date: LocalDate,
        zone: ZoneId,
        now: Long,
        journey: JourneyMapData = JourneyMapAssembler.build(diary, now),
    ): DayClockData {
        val empty = DayClockData.empty(date, zone)
        val dayStart = empty.dayStart.toEpochMilli()
        val dayEnd = dayStart + empty.dayLengthMinutes * MINUTE_MS
        val today = now in dayStart until dayEnd
        val cut = now.coerceIn(dayStart, dayEnd)
        fun minute(ms: Long) = ((ms - dayStart).toDouble() / MINUTE_MS).roundToInt()
        val marks = hourMarks(date, zone, dayStart)
        if (cut <= dayStart) return empty.copy(nowMinute = if (today) 0 else null, hourMarks = marks)

        // 1. Itens em ms, já recortados.
        data class Item(val s: Long, val e: Long, val stay: Boolean, val id: String, val build: (Int, Int) -> ClockSegment)
        val items = mutableListOf<Item>()
        val window = diary.activityWindow
        if (HoodieConfig.DIARY_CLOCK_SLEEP_SEGMENTS) {
            window.sleepBeforeStart?.let { sleep ->
                val s = maxOf(dayStart, sleep.startedAt ?: dayStart)
                val e = minOf(window.activeStartAt, sleep.endedAt, cut)
                if (e > s) items += Item(s, e, false, "sleep-before-wake") { a, b ->
                    ClockSegment.Sleep(
                        "sleep-before-wake", a, b, SleepPhase.BEFORE_WAKE,
                        if (window.wakeReason == com.hoodie.app.domain.daycycle.WakeReason.SCHEDULE_FALLBACK) SleepConfidence.LOW else sleep.confidence,
                    )
                }
            }
            window.sleepAfterEnd?.let { sleep ->
                val s = maxOf(dayStart, sleep.startedAt)
                val e = minOf(dayEnd, cut)
                if (e > s) items += Item(s, e, false, "sleep-after-active-day") { a, b ->
                    ClockSegment.Sleep("sleep-after-active-day", a, b, SleepPhase.AFTER_ACTIVE_DAY, sleep.confidence)
                }
            }
        }
        journey.nodes.forEachIndexed { i, n ->
            val s = maxOf(n.arrivalAt, dayStart)
            val e = minOf(n.departureAt ?: cut, cut)
            if (e <= s) return@forEachIndexed
            val context = dominant(diary.replay.contexts, s, e) ?: n.contextType ?: n.placeType.toContext()
            val activity = dominant(diary.replay.activities, s, e) ?: n.hoodieActivity
            items += Item(s, e, true, n.id) { a, b ->
                ClockSegment.Stay(
                    n.id, a, b, n.placeId, n.placeName, n.placeType,
                    ClockCategory.of(context.takeIf { it != UserContextType.COMMUTING } ?: n.placeType.toContext()),
                    activity, stopIndex = 0, visitNumber = n.revisitCount + 1,
                    ongoing = n.departureAt == null || n.departureAt > cut,
                )
            }
        }
        journey.segments.forEach { seg ->
            val s = maxOf(seg.startedAt, dayStart)
            val e = minOf(seg.endedAt, cut)
            if (e > s) items += Item(s, e, false, seg.id) { a, b -> ClockSegment.Move(seg.id, a, b, seg.movementMode, seg.fromNodeId, seg.toNodeId) }
        }
        items.sortWith(compareBy<Item> { it.s }.thenBy { if (it.stay) 0 else 1 })
        val movementsByStart = diary.movements.sortedBy { it.startedAt }
        val nextStayByItem = arrayOfNulls<String>(items.size)
        var nextStay: String? = null
        for (index in items.indices.reversed()) {
            if (items[index].stay) nextStay = items[index].id
            nextStayByItem[index] = nextStay
        }

        // 2. Varredura: sono explícito, visitas e deslocamentos vencem; os demais buracos ficam Unknown.
        val out = mutableListOf<ClockSegment>()
        var moveK = 0
        var unknownK = 0
        fun lastStayId() = out.lastOrNull { it is ClockSegment.Stay }?.id
        fun fillGap(from: Long, to: Long, nextStopId: String?) {
            var cursor = from
            movementsByStart.forEach { mv ->
                val s = maxOf(mv.startedAt, cursor)
                val e = minOf(mv.endedAt, to)
                if (e <= s) return@forEach
                if (s > cursor) addUnknown(out, minute(cursor), minute(s)) { "unknown-${unknownK++}" }
                val a = minute(s); val b = minute(e)
                out += ClockSegment.Move("move-${moveK++}", a, b, mv.mode, lastStayId(), nextStopId)
                cursor = e
            }
            if (to > cursor) addUnknown(out, minute(cursor), minute(to)) { "unknown-${unknownK++}" }
        }
        var cursor = dayStart
        items.forEachIndexed { idx, it ->
            val s = maxOf(it.s, cursor)
            if (it.e <= s) return@forEachIndexed
            if (s > cursor) fillGap(cursor, s, nextStayByItem[idx])
            out += it.build(minute(s), minute(it.e))
            cursor = it.e
        }
        if (cut > cursor) fillGap(cursor, cut, null)

        // 3. Numeração das paradas e totais.
        var stop = 0
        val segments = out.map { s -> if (s is ClockSegment.Stay) s.copy(stopIndex = ++stop) else s }
        val totals = buildMap<ClockCategory, Int> {
            segments.forEach { s ->
                val c = when (s) { is ClockSegment.Stay -> s.category; is ClockSegment.Move -> ClockCategory.COMMUTE; else -> null } ?: return@forEach
                put(c, (get(c) ?: 0) + s.minutes)
            }
        }
        return DayClockData(date, zone, empty.dayStart, empty.dayLengthMinutes, if (today) minute(now) else null, segments, totals, marks)
    }

    private fun addUnknown(out: MutableList<ClockSegment>, a: Int, b: Int, id: () -> String) {
        if (b > a) out += ClockSegment.Unknown(id(), a, b)
    }

    /** Valor com maior sobreposição em [s, e). */
    private fun <T> dominant(ranges: List<Pair<LongRange, T>>, s: Long, e: Long): T? = ranges
        .map { (r, v) -> v to (minOf(e, r.last + 1) - maxOf(s, r.first)) }
        .filter { it.second > 0 }
        .groupBy({ it.first }, { it.second })
        .maxByOrNull { (_, d) -> d.sum() }?.key

    /** Horas cheias locais → minuto do mostrador (horas que não existem no horário de verão somem). */
    fun hourMarks(date: LocalDate, zone: ZoneId, dayStart: Long = date.atStartOfDay(zone).toInstant().toEpochMilli()): List<ClockHourMark> =
        (0 until 24).mapNotNull { h ->
            val t = date.atTime(h, 0).atZone(zone)
            if (t.hour != h) null else ClockHourMark(h, ((t.toInstant().toEpochMilli() - dayStart) / MINUTE_MS).toInt())
        }
}

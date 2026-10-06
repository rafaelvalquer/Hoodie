package com.hoodie.app.engine.diary

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.data.repository.MobilityTrip
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryMovement
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.domain.daycycle.DailyActivityWindow
import java.time.ZoneId

/**
 * Deslocamentos no Diário, sem nenhuma rua ou coordenada:
 *
 *     07:47 🏠 Saiu de Casa           (já vem do contexto)
 *     07:47 🚶 Caminhou · 07:47–07:54 · 7 min
 *     07:54 🚌 Pegou ônibus · 07:54–08:25 · 31 min
 *     08:31 🏢 Chegou ao Trabalho     (já vem do contexto)
 *
 * e o total do dia por meio de deslocamento.
 */
object DiaryMobilityMerger {

    fun merge(diary: DailyDiary, trips: List<MobilityTrip>, dayStart: Long, dayEnd: Long, now: Long, zone: ZoneId): DailyDiary {
        if (trips.isEmpty()) return diary
        val activeStart = if (diary.activityWindow == DailyActivityWindow.EMPTY) dayStart else diary.activityWindow.activeStartAt
        val activeEnd = if (diary.activityWindow == DailyActivityWindow.EMPTY) minOf(dayEnd, now) else diary.activityWindow.activeEndAt
        val items = mutableListOf<DiaryTimelineItem>()
        val totals = LinkedHashMap<MovementMode, Long>()
        val movements = mutableListOf<DiaryMovement>()
        trips.forEach { trip ->
            trip.segments.forEach { seg ->
                if (seg.mode == MovementMode.NONE) return@forEach
                val rawEnd = seg.endedAt ?: trip.session.endedAt ?: now
                val civilStart = maxOf(seg.startedAt, dayStart)
                val civilEnd = minOf(rawEnd, dayEnd, now)
                if (civilEnd > civilStart) totals[seg.mode] = (totals[seg.mode] ?: 0L) + civilEnd - civilStart
                val start = maxOf(civilStart, activeStart)
                val end = minOf(civilEnd, activeEnd)
                if (end <= start) return@forEach
                val ms = end - start
                movements += DiaryMovement(seg.mode, start, end)
                items += DiaryTimelineItem(
                    id = "mobility-${seg.id}",
                    timestamp = start,
                    type = DiaryTimelineType.MOVEMENT,
                    actor = DiaryActor.USER,
                    title = seg.mode.verb,
                    subtitle = "${formatClock(start, zone)}–${formatClock(end, zone)} · ${minutes(ms)}",
                    emoji = seg.mode.emoji,
                )
            }
        }
        if (items.isEmpty()) return diary.copy(mobilityTotals = totals)
        val mergedTimeline = (diary.timeline + items).filter { it.timestamp in activeStart..activeEnd }.sortedBy { it.timestamp }
        val replayTimeline = if (diary.replay.startAt == diary.replay.endAt && diary.replay.timeline.isEmpty()) {
            (diary.replay.timeline + items).sortedBy { it.timestamp }
        } else {
            (diary.replay.timeline + items).filter { it.timestamp in diary.replay.startAt..diary.replay.endAt }.sortedBy { it.timestamp }
        }
        return diary.copy(
            timeline = mergedTimeline,
            replay = diary.replay.copy(timeline = replayTimeline),
            mobilityTotals = totals,
            movements = movements.sortedBy { it.startedAt },
        )
    }

    /** "🚶 16 min caminhando · 🚌 31 min de ônibus". */
    fun summary(totals: Map<MovementMode, Long>): String =
        totals.entries.sortedByDescending { it.value }.joinToString(" · ") { (mode, ms) -> "${mode.emoji} ${minutes(ms)} ${mode.label.lowercase()}" }

    private fun minutes(ms: Long): String {
        val m = (ms + MINUTE_MS / 2) / MINUTE_MS
        return if (m >= 60) "${m / 60}h${(m % 60).toString().padStart(2, '0')}" else "$m min"
    }
}

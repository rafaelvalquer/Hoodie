package com.hoodie.app.engine.diary

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DiaryVisitDetails
import com.hoodie.app.domain.diary.model.ReplayPhoneApp
import com.hoodie.app.domain.diary.model.ReplaySequence
import com.hoodie.app.domain.diary.model.ReplayVisualState
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.engine.deviceusage.VisitPhoneUsageCalculator
import java.time.Instant
import java.time.ZoneId

/**
 * Instante do replay → estado visual (contexto, atividade do Hoodie, app ativo,
 * nó/trecho do mapa e período do dia). O período depende só do horário: um
 * replay que termina às 22h continua noturno depois de FINISHED.
 */
object ReplayHudAssembler {

    fun assemble(sequence: ReplaySequence, phone: DailyPhoneInsights?, timestamp: Long?, zone: ZoneId): ReplayVisualState {
        if (timestamp == null) return ReplayVisualState.IDLE
        val frame = sequence.frameAt(timestamp)
        val app = phone?.let { p ->
            VisitPhoneUsageCalculator.activeAppAt(frame.timestamp, p.appSessions)?.let { s ->
                ReplayPhoneApp(s.packageName, p.labelOf(s.packageName), p.categoryOf(s.packageName), s.startedAt, s.endedAt)
            }
        }
        return ReplayVisualState(
            timestamp = frame.timestamp,
            context = frame.currentContext,
            hoodieActivity = frame.currentHoodieActivity,
            activePhoneApp = app,
            activeNodeId = frame.activeNodeId,
            activeTripId = frame.activeEdgeId,
            tripProgress = frame.progressOnEdge,
            dayPeriod = periodAt(frame.timestamp, zone),
        )
    }

    fun periodAt(timestamp: Long, zone: ZoneId): DayPeriod = DayPeriod.of(Instant.ofEpochMilli(timestamp).atZone(zone).hour)

    /** Detalhe de cada visita do dia: celular (sessões ∩ visita), atividades do Hoodie e eventos. */
    fun visitDetails(diary: DailyDiary, now: Long): List<DiaryVisitDetails> = diary.visits.mapIndexed { i, v ->
        val end = v.departureAt ?: now
        val phone = diary.phoneInsights
        DiaryVisitDetails(
            index = i,
            visit = v,
            phoneUsage = phone?.let { VisitPhoneUsageCalculator.calculate(v.arrivalAt, v.departureAt, now, it.appSessions, it::labelOf, it::categoryOf) },
            hoodieActivities = diary.replay.activities
                .filter { (range, _) -> range.first < end && range.last > v.arrivalAt }
                .sortedBy { it.first.first }.map { it.second }.distinct(),
            events = diary.timeline.filter { it.id in v.relatedTimelineIds },
        )
    }
}

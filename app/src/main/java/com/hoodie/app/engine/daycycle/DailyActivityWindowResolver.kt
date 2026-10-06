package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.domain.daycycle.DailyActivityWindow
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.daycycle.WakeReason
import com.hoodie.app.domain.phoneinsights.model.AppSession
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Deriva o relógio ativo dos registros canônicos; não persiste nem altera eventos de origem. */
object DailyActivityWindowResolver {
    private val realContextSources = setOf(
        ContextSource.GEOFENCE, ContextSource.MANUAL, ContextSource.CONFIRMATION,
        ContextSource.MOBILITY, ContextSource.LOCATION_CHECK,
    )

    fun resolve(
        date: LocalDate,
        civilStartAt: Long,
        civilEndAt: Long,
        now: Long,
        zone: ZoneId,
        sleepSchedule: SleepSchedule,
        contexts: List<ContextEventEntity>,
        activities: List<HoodieActivityEntity>,
        timeline: List<TimelineEventEntity>,
        appSessions: List<AppSession> = emptyList(),
        mobilitySessions: List<MobilitySessionEntity> = emptyList(),
        analysisEnabled: Boolean = false,
        mobilityEnabled: Boolean = true,
    ): DailyActivityWindow {
        val observationEnd = minOf(civilEndAt, now + 1)
        val phoneEvidence = if (analysisEnabled) PhoneWakeEvidenceBuilder.build(appSessions, civilStartAt, observationEnd) else emptyList()
        val mobilityEvidence = if (mobilityEnabled) MobilityWakeEvidenceBuilder.build(mobilitySessions, civilStartAt, observationEnd) else emptyList()
        val contextEvidence = ContextWakeEvidenceBuilder.build(contexts, civilStartAt, observationEnd)
        val hoodieEvidence = ContextWakeEvidenceBuilder.hoodieWake(activities, civilStartAt, observationEnd)
        val allEvidence = phoneEvidence + mobilityEvidence + contextEvidence + hoodieEvidence

        val activityMarkers = buildList {
            if (analysisEnabled) appSessions.forEach { add(it.startedAt); add(it.endedAt) }
            if (mobilityEnabled) mobilitySessions.filter { it.confirmed }.forEach { session ->
                add(session.startedAt)
                session.endedAt?.let(::add)
            }
            contexts.filter { it.source in realContextSources }.forEach { context ->
                add(context.startedAt)
                context.endedAt?.let(::add)
            }
            // Timestamps persistidos são atividade real do usuário; Hoodie é simulação e não fecha a janela de sono.
            timeline.filter { it.actor.name == "USER" }.forEach { add(it.timestamp) }
        }

        val detector = WakeDetector()
        val wake = detector.detect(allEvidence, activityMarkers) { at ->
            scheduledSleepAtBefore(at, sleepSchedule.sleepMinute, zone)
        }
        val scheduledWake = localInstant(date, sleepSchedule.wakeMinute, zone).coerceIn(civilStartAt, civilEndAt)
        val activeStart = (wake?.evidence?.timestamp ?: scheduledWake).coerceIn(civilStartAt, civilEndAt)
        val provisional = date == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val observedEnd = if (provisional) now.coerceAtMost(civilEndAt) else observedEndAt(
            civilStartAt, civilEndAt, now, activeStart, contexts, activities, timeline,
            appSessions.takeIf { analysisEnabled }.orEmpty(), mobilitySessions.takeIf { mobilityEnabled }.orEmpty(),
        )
        val activeEnd = observedEnd.coerceIn(activeStart, civilEndAt)
        val fallbackSleep = if (wake == null && activeStart < now) SleepSpanResolver().resolveBefore(
            activeStart, activityMarkers.filter { it <= activeStart }, scheduledSleepAtBefore(activeStart, sleepSchedule.sleepMinute, zone),
        ) else null

        return DailyActivityWindow(
            civilStartAt = civilStartAt,
            civilEndAt = civilEndAt,
            activeStartAt = activeStart,
            activeEndAt = activeEnd,
            sleepBeforeStart = wake?.sleepBefore ?: fallbackSleep,
            wakeReason = wake?.let { WakeDetector.reason(it.evidence.type) } ?: WakeReason.SCHEDULE_FALLBACK,
            wakeConfidence = wake?.confidence ?: WakeConfidence.LOW,
            provisional = provisional,
        )
    }

    private fun observedEndAt(
        civilStart: Long,
        civilEnd: Long,
        now: Long,
        activeStart: Long,
        contexts: List<ContextEventEntity>,
        activities: List<HoodieActivityEntity>,
        timeline: List<TimelineEventEntity>,
        appSessions: List<AppSession>,
        mobility: List<MobilitySessionEntity>,
    ): Long {
        val latest = buildList {
            contexts.filter { it.startedAt < civilEnd && (it.endedAt == null || it.endedAt > civilStart) }
                .forEach { add(minOf(it.endedAt ?: now, civilEnd, now)) }
            activities.filter { it.startedAt < civilEnd && it.endedAt > civilStart }
                .forEach { add(minOf(it.endedAt, civilEnd, now)) }
            timeline.filter { it.timestamp in civilStart until civilEnd && it.timestamp <= now }.forEach { add(it.timestamp) }
            appSessions.forEach { add(minOf(it.endedAt, civilEnd, now)) }
            mobility.forEach { add(minOf(it.endedAt ?: now, civilEnd, now)) }
        }.filter { it >= civilStart }.maxOrNull() ?: activeStart
        return latest.coerceAtMost(civilEnd).coerceAtLeast(activeStart)
    }

    private fun localInstant(date: LocalDate, minute: Int, zone: ZoneId): Long {
        val safeMinute = minute.coerceIn(0, 1_439)
        return date.atTime(LocalTime.of(safeMinute / 60, safeMinute % 60)).atZone(zone).toInstant().toEpochMilli()
    }

    private fun scheduledSleepAtBefore(timestamp: Long, sleepMinute: Int, zone: ZoneId): Long {
        val safeMinute = sleepMinute.coerceIn(0, 1_439)
        val localDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        var bedtime = localInstant(localDate, safeMinute, zone)
        if (bedtime >= timestamp) bedtime = localInstant(localDate.minusDays(1), safeMinute, zone)
        return bedtime
    }
}

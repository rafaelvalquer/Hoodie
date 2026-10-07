package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.domain.daycycle.DailyActivityWindow
import com.hoodie.app.domain.daycycle.InferredSleepSpan
import com.hoodie.app.domain.daycycle.SleepConfidence
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.daycycle.WakeReason
import com.hoodie.app.domain.phoneinsights.model.AppSession
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Deriva o relógio ativo dos registros canônicos; não persiste nem altera eventos de origem. */
object DailyActivityWindowResolver {
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
        evidenceEndAt: Long = civilEndAt,
    ): DailyActivityWindow {
        val observationEnd = minOf(evidenceEndAt, now + 1)
        // Wake timing is restricted to the selected civil day; lookahead only corroborates sleep onset.
        val wakeEvidenceEnd = minOf(civilEndAt, observationEnd)
        val phoneEvidence = if (analysisEnabled) PhoneWakeEvidenceBuilder.build(appSessions, civilStartAt, wakeEvidenceEnd) else emptyList()
        val mobilityEvidence = if (mobilityEnabled) MobilityWakeEvidenceBuilder.build(mobilitySessions, civilStartAt, wakeEvidenceEnd) else emptyList()
        val contextEvidence = ContextWakeEvidenceBuilder.build(contexts, civilStartAt, wakeEvidenceEnd)
        val hoodieEvidence = ContextWakeEvidenceBuilder.hoodieWake(activities, civilStartAt, wakeEvidenceEnd)
        val allEvidence = phoneEvidence + mobilityEvidence + contextEvidence + hoodieEvidence
        val observedEvidenceEnd = minOf(evidenceEndAt, now).coerceAtLeast(civilStartAt)
        val realActivities = RealUserActivityBuilder.build(
            from = civilStartAt,
            until = maxOf(observationEnd, observedEvidenceEnd),
            appSessions = appSessions,
            mobilitySessions = mobilitySessions,
            contexts = contexts,
            timeline = timeline,
            analysisEnabled = analysisEnabled,
            mobilityEnabled = mobilityEnabled,
        )
        val activityMarkers = realActivities.map { it.timestamp }

        val detector = WakeDetector()
        val wake = detector.detect(allEvidence, activityMarkers) { at ->
            scheduledSleepAtBefore(at, sleepSchedule.sleepMinute, zone)
        }
        val scheduledWake = localInstant(date, sleepSchedule.wakeMinute, zone).coerceIn(civilStartAt, civilEndAt)
        val activeStart = (wake?.evidence?.timestamp ?: scheduledWake).coerceIn(civilStartAt, civilEndAt)
        val provisional = date == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val sleepAfterEnd = if (HoodieConfig.ACTIVE_DAY_END_INFERENCE) SleepOnsetDetector().detect(
            date = date,
            activeStartAt = activeStart,
            civilEndAt = civilEndAt,
            observedUntil = observedEvidenceEnd,
            now = now,
            zone = zone,
            schedule = sleepSchedule,
            activities = realActivities,
            contexts = contexts,
            mobilitySessions = mobilitySessions.filter { mobilityEnabled && it.confirmed },
        ) else null
        val lastRealActivity = realActivities.asSequence()
            .filter { it.timestamp >= activeStart && it.timestamp < civilEndAt && it.timestamp <= observedEvidenceEnd }
            .maxByOrNull { it.timestamp }
        val observedEnd = when {
            sleepAfterEnd != null -> sleepAfterEnd.startedAt
            provisional -> now.coerceAtMost(civilEndAt)
            else -> lastRealActivity?.timestamp ?: activeStart
        }
        val activeEnd = observedEnd.coerceIn(activeStart, civilEndAt)
        val inferredSleepBefore = wake?.sleepBefore ?: SleepSpanResolver().resolveBefore(
            activeStart,
            activityMarkers.filter { it <= activeStart },
            scheduledSleepAtBefore(activeStart, sleepSchedule.sleepMinute, zone),
        )
        val activityBeforeScheduledWake = activityMarkers.any { it in civilStartAt until activeStart }
        val fallbackSleep = if (inferredSleepBefore == null && wake == null &&
            activeStart in (civilStartAt + 1)..now && !activityBeforeScheduledWake
        ) InferredSleepSpan(startedAt = null, endedAt = activeStart, confidence = SleepConfidence.LOW) else null

        return DailyActivityWindow(
            civilStartAt = civilStartAt,
            civilEndAt = civilEndAt,
            activeStartAt = activeStart,
            activeEndAt = activeEnd,
            sleepBeforeStart = inferredSleepBefore ?: fallbackSleep,
            wakeReason = wake?.let { WakeDetector.reason(it.evidence.type) } ?: WakeReason.SCHEDULE_FALLBACK,
            wakeConfidence = wake?.confidence ?: WakeConfidence.LOW,
            provisional = provisional,
            sleepAfterEnd = sleepAfterEnd,
        )
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

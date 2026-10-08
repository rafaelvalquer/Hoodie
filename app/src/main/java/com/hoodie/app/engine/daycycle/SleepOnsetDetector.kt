package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.daycycle.InferredSleepOnset
import com.hoodie.app.domain.daycycle.SleepConfidence
import com.hoodie.app.domain.daycycle.SleepOnsetReason
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/** Detecta o provável início do sono a partir de inatividade confirmada e sinais independentes. */
class SleepOnsetDetector {
    private val realContextSources = setOf(
        ContextSource.GEOFENCE, ContextSource.MANUAL, ContextSource.CONFIRMATION,
        ContextSource.MOBILITY, ContextSource.LOCATION_CHECK, ContextSource.USER_CORRECTION,
    )

    fun detect(
        date: LocalDate,
        activeStartAt: Long,
        civilEndAt: Long,
        observedUntil: Long,
        now: Long,
        zone: ZoneId,
        schedule: SleepSchedule,
        activities: List<RealUserActivity>,
        contexts: List<ContextEventEntity>,
        mobilitySessions: List<MobilitySessionEntity>,
    ): InferredSleepOnset? {
        val last = activities.asSequence()
            .filter { it.timestamp >= activeStartAt && it.timestamp < civilEndAt && it.timestamp <= observedUntil }
            .maxWithOrNull(compareBy<RealUserActivity> { it.timestamp }.thenBy { it.source.ordinal })
            ?: return null
        val onsetAt = last.timestamp + HoodieConfig.SLEEP_ONSET_GRACE_MS
        if (onsetAt >= civilEndAt) return null
        val inactiveMs = minOf(observedUntil, now).coerceAtLeast(onsetAt) - onsetAt
        if (inactiveMs < HoodieConfig.SLEEP_END_MIN_INACTIVITY_MS) return null

        val homeSpans = contexts.filter { it.source in realContextSources && it.type == UserContextType.HOME }
        val atHome = homeSpans.any { it.startedAt <= onsetAt && (it.endedAt == null || it.endedAt > onsetAt) }
        val mobilityEnd = mobilitySessions.asSequence().filter { it.confirmed }
            .flatMap { sequenceOf(it.startedAt, it.endedAt ?: it.startedAt) }
            .filter { it < last.timestamp }.maxOrNull()
        val latestHomeArrival = homeSpans.filter { it.startedAt <= last.timestamp }.maxOfOrNull { it.startedAt }
        val arrivedHomeAfterMobility = mobilityEnd != null && latestHomeArrival != null &&
            latestHomeArrival > mobilityEnd &&
            latestHomeArrival - mobilityEnd <= HoodieConfig.SLEEP_END_CORROBORATION_WINDOW_MS &&
            onsetAt - latestHomeArrival >= HoodieConfig.SLEEP_END_HOME_SETTLE_MS
        val nearBedtime = isNearScheduledSleep(onsetAt, schedule.sleepMinute, zone)
        val lastPhoneActivity = activities.any {
            it.timestamp == last.timestamp && it.source == RealUserActivitySource.PHONE
        }
        val nextActivity = activities.asSequence()
            .filter { it.timestamp > onsetAt && it.timestamp <= observedUntil }
            .minOfOrNull { it.timestamp }
        val corroboratingMorningActivity = nextActivity != null &&
            nextActivity - onsetAt >= HoodieConfig.SLEEP_END_MIN_INACTIVITY_MS

        val score = 25 +
            (if (atHome) 25 else 0) +
            (if (nearBedtime) 15 else 0) +
            (if (lastPhoneActivity) 15 else 0) +
            (if (arrivedHomeAfterMobility) 10 else 0) +
            (if (corroboratingMorningActivity) 10 else 0)
        // Sem 60 pontos há apenas uma lacuna: não a chamamos de sono.
        if (score < 60) return null

        val reason = when {
            score >= 75 -> SleepOnsetReason.CORROBORATED
            arrivedHomeAfterMobility -> SleepOnsetReason.HOME_ARRIVAL
            lastPhoneActivity -> SleepOnsetReason.PHONE_INACTIVITY
            atHome -> SleepOnsetReason.HOME_INACTIVITY
            else -> SleepOnsetReason.SCHEDULE_FALLBACK
        }
        val provisional = date == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return InferredSleepOnset(
            startedAt = onsetAt,
            reason = reason,
            confidence = if (score >= 75) SleepConfidence.HIGH else SleepConfidence.MEDIUM,
            provisional = provisional,
        )
    }

    private fun isNearScheduledSleep(timestamp: Long, sleepMinute: Int, zone: ZoneId): Boolean {
        val centerDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val minute = sleepMinute.coerceIn(0, 1_439)
        val targetTime = LocalTime.of(minute / 60, minute % 60)
        val closest = (-1L..1L).map { offset ->
            centerDate.plusDays(offset).atTime(targetTime).atZone(zone).toInstant().toEpochMilli()
        }.minOf { abs(it - timestamp) }
        return closest <= HoodieConfig.SLEEP_END_SCHEDULE_WINDOW_MS
    }
}

package com.hoodie.app.engine.routine

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.*
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.model.*
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.detection.*
import com.hoodie.app.domain.routine.*
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.engine.daycycle.DailyActivityWindowResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineLearningCoordinator @Inject constructor(private val db: HoodieDatabase, private val clock: ClockProvider, private val settings: SettingsRepository) {
    suspend fun recompute() {
        if (!HoodieConfig.LEARNED_ROUTINE) return
        val today = clock.today()
        val firstDate = today.minusDays(28)
        val from = firstDate.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val until = today.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val contexts = db.contextEventDao().overlapping(from, until)
        val exceptions = db.dayExceptionDao().range(firstDate.toEpochDay(), today.toEpochDay()).map { it.epochDay }.toSet()
        val s = settings.current()
        val observations = mutableListOf<RoutineObservation>()
        for (offset in 1L..28L) {
            val date = today.minusDays(offset)
            if (date.toEpochDay() in exceptions) continue
            val start = date.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
            val events = contexts.filter { it.startedAt < end && (it.endedAt ?: Long.MAX_VALUE) > start }.sortedBy { it.startedAt }
            // A final home stay can continue into today; the completed civil day is still observable.
            val unfinished = events.any { event ->
                (event.endedAt == null && (event !== events.lastOrNull() || event.type != UserContextType.HOME)) ||
                    (event.endedAt != null && event.endedAt <= event.startedAt)
            }
            if (events.isEmpty() || unfinished || events.zipWithNext().any { (a, b) -> (a.endedAt ?: end) > b.startedAt }) continue
            fun add(type: RoutineEventType, timestamp: Long?, source: ContextSource, confidence: Float) {
                if (timestamp == null || timestamp !in start until end) return
                val local = java.time.Instant.ofEpochMilli(timestamp).atZone(clock.zone())
                val detectedSource = when (source) {
                    ContextSource.USER_CORRECTION -> DetectionSource.USER_CORRECTION
                    ContextSource.CONFIRMATION, ContextSource.MANUAL -> DetectionSource.USER_CONFIRMATION
                    ContextSource.ROUTINE, ContextSource.ONBOARDING -> DetectionSource.ROUTINE
                    else -> DetectionSource.SENSOR
                }
                observations += RoutineObservation(date, type, local.hour * 60 + local.minute, ConfidenceScore(confidence.coerceIn(0f, 1f)), detectedSource)
            }
            // A complete day needs observed activity plus a later home return. Partial visits do not define a routine.
            val homeReturn = events.lastOrNull { it.type == UserContextType.HOME && it.startedAt > start }
            if (homeReturn == null || events.none { it.type != UserContextType.HOME && it.confidence >= .60f }) continue
            val work = events.filter { it.type == UserContextType.WORK }
            work.firstOrNull()?.let { add(RoutineEventType.WORK_START, it.startedAt, it.source, it.confidence) }
            work.lastOrNull()?.let { add(RoutineEventType.WORK_END, it.endedAt, it.source, it.confidence) }
            events.singleOrNull { it.type == UserContextType.LUNCH }?.let {
                add(RoutineEventType.LUNCH_START, it.startedAt, it.source, it.confidence)
                add(RoutineEventType.LUNCH_END, it.endedAt, it.source, it.confidence)
            }
            events.firstOrNull { it.type == UserContextType.GYM }?.let { add(RoutineEventType.GYM_START, it.startedAt, it.source, it.confidence) }
            add(RoutineEventType.HOME_RETURN, homeReturn.startedAt, homeReturn.source, homeReturn.confidence)
            events.firstOrNull { it.type == UserContextType.HOME }?.let { add(RoutineEventType.LEAVE_HOME, it.endedAt, it.source, it.confidence) }
            val phone = if (s.digital.analysisEnabled) db.deviceUsageDao().sessions(date.toEpochDay()).map { AppSession(it.packageName, it.startedAt, it.endedAt) } else emptyList()
            val mobility = if (s.mobility.detectionEnabled) db.mobilitySessionDao().overlapping(start, end) else emptyList()
            val window = DailyActivityWindowResolver.resolve(date, start, end, clock.nowMillis(), clock.zone(), s.sleep, events,
                db.hoodieActivityDao().overlapping(start, end), db.timelineDao().range(start, end), phone, mobility, s.digital.analysisEnabled, s.mobility.detectionEnabled)
            if (window.wakeConfidence != com.hoodie.app.domain.daycycle.WakeConfidence.LOW) add(RoutineEventType.WAKE, window.activeStartAt, ContextSource.GEOFENCE, .85f)
            window.sleepAfterEnd?.takeIf { !it.provisional }?.let { add(RoutineEventType.SLEEP, it.startedAt, ContextSource.GEOFENCE, .85f) }
        }
        val learned = RoutineLearner.learn(observations, today.minusDays(1))
        db.intelligenceDao().replaceRoutineSlots(learned.map { LearnedRoutineSlotEntity(dayGroup = it.dayGroup, type = it.type.name,
            medianMinute = it.medianMinute, deviationMinutes = it.deviationMinutes, sampleCount = it.sampleCount, confidence = it.confidence.value, updatedAt = clock.nowMillis()) })
    }
}

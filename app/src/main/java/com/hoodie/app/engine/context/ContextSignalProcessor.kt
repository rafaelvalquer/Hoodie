package com.hoodie.app.engine.context

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.ConfirmationDao
import com.hoodie.app.core.database.ContextConfirmationEntity
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.database.LocationEventDao
import com.hoodie.app.core.database.QuestionDao
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.model.ContextQuestion
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.atZone
import com.hoodie.app.core.time.minuteOfDay
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.worker.CheckScheduler
import java.time.DayOfWeek
import com.hoodie.app.domain.detection.DetectionResult
import com.hoodie.app.engine.detection.DetectionQuestion
import com.hoodie.app.engine.detection.QuestionPolicy

/** Shared scoring, questions and transition reactions. Called under the facade lock. */
internal class ContextSignalProcessor(

    val contextDao: ContextEventDao,
    val transitions: ContextTransitionService,
    val confirmationDao: ConfirmationDao,
    val questionDao: QuestionDao,
    val locationEventDao: LocationEventDao,
    val places: PlaceRepository,
    val routines: RoutineRepository,
    val settings: SettingsRepository,
    val memory: MemoryEngine,
    val hoodie: HoodieEngine,
    val notifier: Notifier,
    val scheduler: CheckScheduler,
    val location: LocationSource,
    val geofences: GeofenceRegistrar,
    val cipher: CoordinateCipher,
    val clock: ClockProvider,
    val log: DebugEventLogger,
    val intelligence: com.hoodie.app.core.database.IntelligenceDao? = null,
) {
    suspend fun input(signal: ContextSignal, previous: UserContextType?): ContextInput {
        val now = clock.now()
        val confirmations = confirmationDao.since(clock.nowMillis() - HoodieConfig.CONFIRMATION_LOOKBACK_MS).map {
            ConfirmationRecord(it.type, it.placeId, DayOfWeek.of(it.dayOfWeek), it.minuteOfDay, it.accepted, it.timestamp)
        }
        val learnedLunch = if (HoodieConfig.LEARNED_ROUTINE) intelligence?.routineSlots()?.filter { it.type == "LUNCH_START" }?.let { slots ->
            slots.firstOrNull { it.dayGroup == now.dayOfWeek.name } ?: slots.firstOrNull { it.dayGroup == com.hoodie.app.domain.routine.routineDayGroup(now.dayOfWeek) }
        }?.let { com.hoodie.app.domain.routine.LearnedRoutineSlot(it.dayGroup, com.hoodie.app.domain.routine.RoutineEventType.LUNCH_START,
            it.medianMinute, it.deviationMinutes, it.sampleCount, com.hoodie.app.domain.detection.ConfidenceScore(it.confidence)) } else null
        return ContextInput(signal, now, routines.get(), routines.isDayOff(now.toLocalDate()), previous, confirmations, learnedLunch)
    }

    suspend fun inferredContextFor(type: PlaceType, now: Long): UserContextType = when {
        type != PlaceType.RESTAURANT -> type.toContext()
        RoutineEngine.isLunchWindow(now.atZone(clock.zone()).minuteOfDay(), routines.get()) -> UserContextType.LUNCH
        else -> UserContextType.DINING
    }

    suspend fun recordConfirmation(type: UserContextType, placeId: Long?, at: Long, accepted: Boolean) {
        val z = at.atZone(clock.zone())
        confirmationDao.insert(
            ContextConfirmationEntity(type = type, placeId = placeId, dayOfWeek = z.dayOfWeek.value, minuteOfDay = z.minuteOfDay(), accepted = accepted, timestamp = at),
        )
    }

    suspend fun switchTo(
        type: UserContextType,
        at: Long,
        confidence: Float,
        placeId: Long?,
        source: ContextSource,
        reason: TransitionReason,
        note: String? = null,
        venueType: PlaceType? = null,
    ): TransitionResult {
        val r = transitions.transition(type, at, confidence, placeId, source, reason, note, venueType)
        if (r.changed) {
            memory.onContext(type, r.previous?.type, r.event.startedAt)
            if (source == ContextSource.GEOFENCE) notifyArrival(type, r.previous, r.event.startedAt)
        }
        hoodie.resolve()
        return r
    }

    suspend fun notifyArrival(type: UserContextType, previous: ContextEventEntity?, at: Long) {
        val name = settings.current().catName
        when (type) {
            UserContextType.WORK -> if (previous?.type != UserContextType.LUNCH) notifier.event("🐱 $name chegou ao trabalho.")
            UserContextType.HOME -> if (previous != null && previous.type != UserContextType.HOME && at - previous.startedAt >= HOME_RETURN_MIN_MS) {
                notifier.event("🏠 Vocês estão de volta em casa.")
            }
            UserContextType.GYM -> notifier.event("🏋 $name veio treinar junto!")
            else -> Unit
        }
    }

    suspend fun ask(kind: QuestionKind, candidate: UserContextType?, placeId: Long?, eventId: Long?, coords: String? = null, detection: DetectionResult? = null): Long? {
        if (HoodieConfig.PASSIVE_CONTEXT_CONFIRMATION && kind == QuestionKind.CONFIRM_CONTEXT) return null
        val now = clock.nowMillis()
        val history = questionDao.since(now - DAY_MS)
        val recent = history.map { AskedQuestion(it.kind, it.candidate, it.askedAt) }
        if (HoodieConfig.UNIFIED_CONFIDENCE_ENGINE && detection != null && !QuestionPolicy.canAsk(
                detection, "${kind.name}:${candidate?.name}", now,
                history.map { DetectionQuestion("${it.kind.name}:${it.candidate?.name}", it.askedAt) },
            )) return null
        if (!ConfirmationPolicy.canAsk(now, clock.zone(), kind, candidate, recent)) return null
        val q = ContextQuestionEntity(kind = kind, candidate = candidate, placeId = placeId, encryptedCoordinates = coords, contextEventId = eventId, askedAt = now)
        val id = questionDao.insert(q)
        val prompt = ContextQuestion(id, kind, candidate, placeId, null, eventId, now, null, null).prompt
        if (kind == QuestionKind.CONFIRM_CONTEXT) notifier.askYesNo(id, prompt) else notifier.askInApp(id, prompt)
        return id
    }

    companion object { private const val HOME_RETURN_MIN_MS = 30 * MINUTE_MS }
}

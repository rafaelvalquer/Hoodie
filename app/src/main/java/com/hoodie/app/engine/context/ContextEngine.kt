package com.hoodie.app.engine.context

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.ConfirmationDao
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.LocationEventDao
import com.hoodie.app.core.database.QuestionDao
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.worker.CheckScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class GeofenceTransition { ENTER, EXIT, DWELL }

/**
 * Context Engine: sensor → candidato → score → confiança → contexto.
 *
 * Recebe eventos de geofence, checagens agendadas, respostas e o modo manual e
 * decide o contexto. Toda escrita em context_events passa pelo
 * [ContextTransitionService] (manter, ou fechar + abrir). Depois de cada
 * mudança, pede ao HoodieEngine para reagir na hora.
 */
@Singleton
class ContextEngine @Inject constructor(
    private val contextDao: ContextEventDao,
    transitions: ContextTransitionService,
    confirmationDao: ConfirmationDao,
    questionDao: QuestionDao,
    locationEventDao: LocationEventDao,
    private val places: PlaceRepository,
    routines: RoutineRepository,
    settings: SettingsRepository,
    memory: MemoryEngine,
    private val hoodie: HoodieEngine,
    notifier: Notifier,
    scheduler: CheckScheduler,
    location: LocationSource,
    geofences: GeofenceRegistrar,
    cipher: CoordinateCipher,
    private val clock: ClockProvider,
    log: DebugEventLogger,
    intelligence: com.hoodie.app.core.database.IntelligenceDao? = null,
    private val corrections: dagger.Lazy<com.hoodie.app.engine.correction.DiaryCorrectionService>? = null,
    effectiveRoutine: com.hoodie.app.data.repository.EffectiveRoutineRepository? = null,
    mobilityRepository: com.hoodie.app.data.repository.MobilityRepository? = null,
    private val undo: dagger.Lazy<com.hoodie.app.engine.correction.CorrectionUndoService>? = null,
) {
    private val mutex = Mutex()

    private val processor = ContextSignalProcessor(contextDao, transitions, confirmationDao, questionDao, locationEventDao, places, routines, settings, memory, hoodie, notifier, scheduler, location, geofences, cipher, clock, log, intelligence, effectiveRoutine, mobilityRepository)
    private val geofenceContextHandler = GeofenceContextHandler(processor)
    private val contextQuestionHandler = ContextQuestionHandler(processor)
    private val routineFallbackHandler = RoutineFallbackHandler(processor)
    private val manualContextHandler = ManualContextHandler(processor)
    private val placeLearningHandler = PlaceLearningHandler(processor)

    suspend fun onGeofence(placeId: Long, transition: GeofenceTransition, at: Long = clock.nowMillis()): Unit = mutex.withLock { geofenceContextHandler.onGeofence(placeId, transition, at) }

    suspend fun onArrivalGeofences(placeIds: List<Long>, transition: GeofenceTransition, at: Long,
        position: com.hoodie.app.core.geofence.PlaceArrivalPosition? = null): Long? = mutex.withLock {
        geofenceContextHandler.onArrivalGeofences(placeIds, transition, at, position)
    }

    suspend fun onLunchCheck(exitAt: Long, placeId: Long): Unit = mutex.withLock { geofenceContextHandler.onLunchCheck(exitAt, placeId) }

    suspend fun onCommuteCheck(eventId: Long): Unit = mutex.withLock { geofenceContextHandler.onCommuteCheck(eventId) }

    suspend fun answerYesNo(questionId: Long, yes: Boolean): Unit = mutex.withLock { contextQuestionHandler.answerYesNo(questionId, yes) }

    suspend fun answerNewPlace(questionId: Long, type: PlaceType): Unit = mutex.withLock { contextQuestionHandler.answerNewPlace(questionId, type) }

    suspend fun dismissQuestion(questionId: Long): Unit = mutex.withLock { contextQuestionHandler.dismissQuestion(questionId) }

    /** Retires legacy location confirmations and their notifications without touching mobility questions. */
    suspend fun dismissPendingContextConfirmations(): Unit = mutex.withLock {
        if (HoodieConfig.PASSIVE_CONTEXT_CONFIRMATION) contextQuestionHandler.dismissPendingContextConfirmations()
    }

    /** Confirms only the still-current event; repeated taps are safe. */
    suspend fun confirmCurrentContext(expectedEventId: Long, confirmedAt: Long = clock.nowMillis()): Boolean =
        mutex.withLock { contextQuestionHandler.confirmCurrent(expectedEventId, confirmedAt) }

    suspend fun applyRoutineFallbackIfNeeded(): Unit = mutex.withLock { routineFallbackHandler.applyRoutineFallbackIfNeeded() }

    suspend fun setManual(type: UserContextType): Unit = mutex.withLock { manualContextHandler.setManual(type) }

    suspend fun setManualPlace(type: PlaceType): Unit = mutex.withLock { manualContextHandler.setManualPlace(type) }

    suspend fun correctPlaceNow(type: PlaceType): Unit = mutex.withLock { manualContextHandler.correctPlaceNow(type) }

    /** Validates the snapshot and applies a requested current or whole-event correction under the engine lock. */
    suspend fun correctCurrentContext(expectedEventId: Long?, type: PlaceType, sinceActivityStart: Boolean, placeId: Long? = null): Boolean =
        mutex.withLock { correctLocked(expectedEventId, type, sinceActivityStart, placeId) }

    private suspend fun correctLocked(expectedEventId: Long?, type: PlaceType, sinceActivityStart: Boolean, placeId: Long?): Boolean {
        val current = contextDao.current()
        if (current?.id != expectedEventId) return false
        if (!sinceActivityStart) return manualContextHandler.correctCurrentPlace(expectedEventId, type, placeId)
        val event = current ?: return false
        requireNotNull(corrections) { "Correções do Diário indisponíveis." }.get().save(
            com.hoodie.app.domain.correction.DiaryCorrection(
                com.hoodie.app.domain.correction.CorrectionTargetType.CONTEXT, event.id,
                type.toContext(), placeId ?: places.firstOfType(type)?.id, event.startedAt, event.endedAt, venueType = type,
            ),
        )
        return true
    }

    /**
     * Correção rápida da Home com o lugar exato e opção de Desfazer. Confere o evento esperado e
     * fotografa os registros canônicos antes de aplicar; o Desfazer só existe se tudo foi gravado.
     */
    suspend fun correctWithUndo(request: com.hoodie.app.domain.correction.QuickContextCorrection): com.hoodie.app.domain.correction.QuickCorrectionResult = mutex.withLock {
        val current = contextDao.current()
        if (current?.id != request.expectedEventId) return@withLock com.hoodie.app.domain.correction.QuickCorrectionResult.Stale
        val service = undo?.get()
        val pending = service?.begin(current.startedAt)
        val since = request.scope == com.hoodie.app.domain.correction.CorrectionScope.SINCE_CURRENT_ARRIVAL
        if (!correctLocked(request.expectedEventId, request.placeType, since, request.placeId)) {
            return@withLock com.hoodie.app.domain.correction.QuickCorrectionResult.Stale
        }
        val operationId = if (service != null && pending != null) {
            runCatching { service.commit(pending, request.scope.name) }.getOrNull()
        } else null
        com.hoodie.app.domain.correction.QuickCorrectionResult.Applied(operationId)
    }

    /** Reverte uma correção recente se nada mudou depois; senão devolve o motivo do bloqueio. */
    suspend fun undoCorrection(operationId: String): com.hoodie.app.domain.correction.UndoResult = mutex.withLock {
        val service = undo?.get() ?: return@withLock com.hoodie.app.domain.correction.UndoResult.NotFound
        val result = service.undo(operationId)
        if (result == com.hoodie.app.domain.correction.UndoResult.Restored) hoodie.resolve()
        result
    }

    suspend fun savePlaceHere(type: PlaceType, name: String, lat: Double, lng: Double): Place = mutex.withLock { placeLearningHandler.savePlaceHere(type, name, lat, lng) }

    suspend fun newPlaceSaveOptions(questionId: Long, manualPosition: Pair<Double, Double>? = null): NewPlaceSaveOptions? =
        mutex.withLock { placeLearningHandler.saveOptions(questionId, manualPosition) }

    suspend fun answerSavePlace(questionId: Long, save: Boolean, name: String? = null,
        reusePlaceId: Long? = null, manualPosition: Pair<Double, Double>? = null): Unit = mutex.withLock {
        placeLearningHandler.answerSavePlace(questionId, save, name, reusePlaceId, manualPosition)
    }

    // ── Mobilidade: APIs explícitas; o ContextEngine segue dono do UserContextType ──

    /** Início de deslocamento confirmado pela mobilidade. true quando o contexto mudou. */
    suspend fun beginCommute(fromPlaceId: Long?, at: Long, confidence: Float): Boolean =
        mutex.withLock { geofenceContextHandler.beginCommute(fromPlaceId, at, confidence) }

    suspend fun arriveAt(placeId: Long, at: Long): Unit = mutex.withLock { geofenceContextHandler.arriveAt(placeId, at) }

    suspend fun arriveAtPosition(lat: Double, lng: Double, askNewPlace: Boolean): Unit =
        mutex.withLock { geofenceContextHandler.arriveAtPosition(lat, lng, askNewPlace) }

    suspend fun rejectArrival(placeId: Long, at: Long): Boolean = mutex.withLock { geofenceContextHandler.rejectArrival(placeId, at) }

    companion object {
        const val FLAP_MS = HoodieConfig.GPS_FLAP_MS
        const val MANUAL_HOLD_MS = HoodieConfig.MANUAL_HOLD_MS
    }
}

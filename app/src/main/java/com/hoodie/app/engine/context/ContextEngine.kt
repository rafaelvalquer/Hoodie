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
    contextDao: ContextEventDao,
    transitions: ContextTransitionService,
    confirmationDao: ConfirmationDao,
    questionDao: QuestionDao,
    locationEventDao: LocationEventDao,
    places: PlaceRepository,
    routines: RoutineRepository,
    settings: SettingsRepository,
    memory: MemoryEngine,
    hoodie: HoodieEngine,
    notifier: Notifier,
    scheduler: CheckScheduler,
    location: LocationSource,
    geofences: GeofenceRegistrar,
    cipher: CoordinateCipher,
    private val clock: ClockProvider,
    log: DebugEventLogger,
) {
    private val mutex = Mutex()

    private val processor = ContextSignalProcessor(contextDao, transitions, confirmationDao, questionDao, locationEventDao, places, routines, settings, memory, hoodie, notifier, scheduler, location, geofences, cipher, clock, log)
    private val geofenceContextHandler = GeofenceContextHandler(processor)
    private val contextQuestionHandler = ContextQuestionHandler(processor)
    private val routineFallbackHandler = RoutineFallbackHandler(processor)
    private val manualContextHandler = ManualContextHandler(processor)
    private val placeLearningHandler = PlaceLearningHandler(processor)

    suspend fun onGeofence(placeId: Long, transition: GeofenceTransition, at: Long = clock.nowMillis()): Unit = mutex.withLock { geofenceContextHandler.onGeofence(placeId, transition, at) }

    suspend fun onLunchCheck(exitAt: Long, placeId: Long): Unit = mutex.withLock { geofenceContextHandler.onLunchCheck(exitAt, placeId) }

    suspend fun onCommuteCheck(eventId: Long): Unit = mutex.withLock { geofenceContextHandler.onCommuteCheck(eventId) }

    suspend fun answerYesNo(questionId: Long, yes: Boolean): Unit = mutex.withLock { contextQuestionHandler.answerYesNo(questionId, yes) }

    suspend fun answerNewPlace(questionId: Long, type: PlaceType): Unit = mutex.withLock { contextQuestionHandler.answerNewPlace(questionId, type) }

    suspend fun dismissQuestion(questionId: Long): Unit = mutex.withLock { contextQuestionHandler.dismissQuestion(questionId) }

    suspend fun applyRoutineFallbackIfNeeded(): Unit = mutex.withLock { routineFallbackHandler.applyRoutineFallbackIfNeeded() }

    suspend fun setManual(type: UserContextType): Unit = mutex.withLock { manualContextHandler.setManual(type) }

    suspend fun savePlaceHere(type: PlaceType, name: String, lat: Double, lng: Double): Place = mutex.withLock { placeLearningHandler.savePlaceHere(type, name, lat, lng) }

    suspend fun answerSavePlace(questionId: Long, save: Boolean): Unit = mutex.withLock { placeLearningHandler.answerSavePlace(questionId, save) }

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

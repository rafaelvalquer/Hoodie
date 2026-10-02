package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.RoomTransactionRunner
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.geofence.GeofenceRegistrationResult
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.time.FixedClock
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.data.repository.TimelineRepository
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.context.ContextTransitionService
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.worker.CheckScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/** Coordenadas em texto puro: nos testes não há Android Keystore. */
object PlainCipher : CoordinateCipher {
    override fun encrypt(latitude: Double, longitude: Double) = "$latitude,$longitude"
    override fun decrypt(payload: String): Pair<Double, Double>? =
        payload.split(",").takeIf { it.size == 2 }?.let { it[0].toDouble() to it[1].toDouble() }
}

class FakeNotifier : Notifier {
    val events = mutableListOf<String>()
    val questions = mutableListOf<Pair<Long, String>>()
    val cancelled = mutableListOf<Long>()
    var cancelledAll = false
    override suspend fun event(text: String) { events += text }
    override suspend fun askYesNo(questionId: Long, text: String) { questions += questionId to text }
    override suspend fun askInApp(questionId: Long, text: String) { questions += questionId to text }
    override fun cancelQuestion(questionId: Long) { cancelled += questionId }
    override fun cancelAll() { cancelledAll = true }
}

class FakeScheduler : CheckScheduler {
    var lunchCheck: Pair<Long, Long>? = null
    var commuteCheck: Long? = null
    var reconciles = 0
    override fun scheduleLunchCheck(exitAt: Long, placeId: Long) { lunchCheck = exitAt to placeId }
    override fun scheduleCommuteCheck(eventId: Long) { commuteCheck = eventId }
    override fun cancelChecks() { lunchCheck = null; commuteCheck = null }
    override fun reconcileNow() { reconciles++ }
}

class FakeLocation(var state: LocationPermissionState = LocationPermissionState.BACKGROUND) : LocationSource {
    var position: Pair<Double, Double>? = null
    override fun permissionState() = state
    override suspend fun current() = position
}

class FakeGeofences : GeofenceRegistrar {
    override val lastResult = MutableStateFlow<GeofenceRegistrationResult?>(null)
    var registrations = 0
    var cleared = false
    var beforeRegister: (suspend () -> Unit)? = null
    override suspend fun registerAll(): GeofenceRegistrationResult {
        beforeRegister?.invoke()
        registrations++
        return GeofenceRegistrationResult(0, 0, 0, null).also { lastResult.value = it }
    }
    override suspend fun clear() { cleared = true }
}

/**
 * Grafo real (Room em memória + engines reais) com fakes só nas bordas do
 * Android: relógio, localização, notificações, WorkManager e geofences.
 */
class TestGraph(startAt: Long) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: HoodieDatabase = Room.inMemoryDatabaseBuilder(context, HoodieDatabase::class.java).allowMainThreadQueries().build()
    val clock = FixedClock(startAt)
    val log = DebugEventLogger(clock, enabled = true)
    val places = PlaceRepository(db.placeDao(), PlainCipher)
    val routines = RoutineRepository(db.routineDao(), db.dayExceptionDao())
    val settings = SettingsRepository(context)
    val timeline = TimelineRepository(db.timelineDao())
    val memory = MemoryEngine(db.memoryDao(), settings, timeline, clock)
    val hoodie = HoodieEngine(db.hoodieStateDao(), db.hoodieActivityDao(), db.contextEventDao(), routines, settings, timeline, clock, log)
    val transitions = ContextTransitionService(db.contextEventDao(), timeline, places, RoomTransactionRunner(db), log)
    val notifier = FakeNotifier()
    val scheduler = FakeScheduler()
    val location = FakeLocation()
    val geofences = FakeGeofences()
    val engine = ContextEngine(
        db.contextEventDao(), transitions, db.confirmationDao(), db.questionDao(), db.locationEventDao(),
        places, routines, settings, memory, hoodie, notifier, scheduler, location, geofences, PlainCipher, clock, log,
    )

    val contextDao get() = db.contextEventDao()

    fun setRoutine(r: Routine) = runBlocking { routines.save(r, clock.millis) }

    fun addPlace(type: PlaceType, lat: Double, lng: Double = -46.63): Place =
        runBlocking { places.add(type.label, type, lat, lng, 150f, clock.millis) }

    /** Timeline inteira (ordem cronológica). */
    fun timelineTexts(): List<String> = runBlocking { db.timelineDao().range(0, Long.MAX_VALUE).map { it.text } }

    fun close() {
        db.close()
        runBlocking { settings.clear() }
    }
}

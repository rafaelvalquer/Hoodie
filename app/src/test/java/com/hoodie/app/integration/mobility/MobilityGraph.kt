package com.hoodie.app.integration.mobility

import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.mobility.ActivityRecognitionPermissionState
import com.hoodie.app.core.mobility.ActivityRecognitionPermissions
import com.hoodie.app.core.mobility.ActivityRecognitionRegistrar
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MovementObservation
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.data.repository.MobilityRepository
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.SUNDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.mobility.MobilityEngine
import com.hoodie.app.engine.mobility.MobilityEventBus
import com.hoodie.app.engine.mobility.QuestionRouter
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
import com.hoodie.app.integration.TestGraph
import com.hoodie.app.worker.MobilityScheduler
import kotlinx.coroutines.runBlocking

class FakeMobilityScheduler : MobilityScheduler {
    val scheduled = mutableListOf<Long>()
    var cancelled = 0
    override fun scheduleMobilityCheck(delayMs: Long) { scheduled += delayMs }
    override fun cancelMobilityCheck() { cancelled++ }
}

class FakeArPermissions(var granted: Boolean = true) : ActivityRecognitionPermissions {
    override fun state() = if (granted) ActivityRecognitionPermissionState.GRANTED else ActivityRecognitionPermissionState.DENIED
}

class FakeArRegistrar : ActivityRecognitionRegistrar {
    var registered = false
    var registerCalls = 0
    override suspend fun register(): Boolean { registerCalls++; registered = true; return true }
    override suspend fun unregister() { registered = false }
}

/** Conta leituras de posição: a mobilidade não pode virar GPS contínuo. */
class CountingLocation(private val inner: LocationSource) : LocationSource {
    var reads = 0
    override fun permissionState(): LocationPermissionState = inner.permissionState()
    override suspend fun current(): Pair<Double, Double>? { reads++; return inner.current() }
}

/**
 * TestGraph + MobilityEngine real. Uma "nova instância" do engine simula um processo novo
 * (o estado tem de vir do banco, não da memória).
 */
class MobilityGraph(startDay: Int = SUNDAY - 7, private val intelligenceEnabled: Boolean = false) {
    val g = TestGraph(com.hoodie.app.engine.at(startDay, 20).ms())
    val scheduler = FakeMobilityScheduler()
    val arPermissions = FakeArPermissions()
    val location = CountingLocation(g.location)
    val repo = MobilityRepository(g.db.mobilitySessionDao(), g.db.mobilitySegmentDao())
    val bus = MobilityEventBus()
    var engine = newEngine()
    val router get() = QuestionRouter(g.db.questionDao(), g.engine, engine)
    val home: Place
    val work: Place
    val restaurant: Place

    init {
        g.setRoutine(officeRoutine)
        g.clock.millis = com.hoodie.app.engine.at(startDay, 21).ms()
        home = runBlocking { g.engine.savePlaceHere(PlaceType.HOME, "Casa", -23.55, -46.63) }
        work = g.addPlace(PlaceType.WORK, -23.60)
        restaurant = g.addPlace(PlaceType.RESTAURANT, -23.62)
    }

    fun newEngine() = MobilityEngine(
        repo, g.engine, g.contextDao, g.places, g.routines, g.db.questionDao(), g.notifier, g.settings,
        location, scheduler, arPermissions, bus, g.clock, g.log,
        transportFeatures = if (intelligenceEnabled) com.hoodie.app.engine.mobility.TransportFeatureBuilder() else null,
        transportPatterns = if (intelligenceEnabled) com.hoodie.app.engine.mobility.TransportPatternLearner(g.db, com.hoodie.app.core.database.RoomTransactionRunner(g.db), g.clock) else null,
        intelligence = if (intelligenceEnabled) g.db.intelligenceDao() else null,
    )

    /** "Reinicia o processo": engine novo, mesmo banco. */
    fun restart() { engine = newEngine() }

    fun at(day: Int, h: Int, m: Int = 0) { g.clock.millis = com.hoodie.app.engine.at(day, h, m).ms() }
    fun now() = g.clock.millis

    fun move(activity: DetectedMovement, entering: Boolean = true) = runBlocking { engine.onMovement(MovementObservation(activity, 100, now(), entering)) }

    /** Geofence como o receiver faz: contexto primeiro, mobilidade depois. */
    fun geofence(place: Place, t: GeofenceTransition) = runBlocking {
        g.engine.onGeofence(place.id, t, now())
        engine.onGeofence(place.id, t, now())
    }

    fun check() = runBlocking { engine.onCheck() }

    fun open() = runBlocking { repo.open() }
    fun sessions() = runBlocking { repo.tripsBetween(0, Long.MAX_VALUE).map { it.session }.filter { it.endedAt != null }.sortedByDescending { it.startedAt } }
    fun segmentsOf(id: Long) = runBlocking { repo.segmentsOf(id) }
    fun context() = runBlocking { g.contextDao.current() }

    fun pending(kind: QuestionKind) = runBlocking { g.db.questionDao().since(0).lastOrNull { it.kind == kind && it.answeredAt == null } }
    fun asked(kind: QuestionKind) = runBlocking { g.db.questionDao().since(0).count { it.kind == kind } }
    fun answer(kind: QuestionKind, yes: Boolean) = runBlocking { router.answerYesNo(pending(kind)!!.id, yes) }

    fun close() = g.close()
}

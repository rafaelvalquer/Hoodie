package com.hoodie.app.data.repository

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.MobilitySegmentDao
import com.hoodie.app.core.database.MobilitySegmentEntity
import com.hoodie.app.core.database.MobilitySessionDao
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.ModeCertainty
import com.hoodie.app.core.mobility.MobilityPhase
import com.hoodie.app.core.mobility.MobilityVisualSnapshot
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.engine.mobility.TripRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Um deslocamento com seus trechos (Diário, aprendizado). */
data class MobilityTrip(val session: MobilitySessionEntity, val segments: List<MobilitySegmentEntity>)

/** Acesso às sessões/trechos de mobilidade. Só lugares conhecidos, horários e modos. */
@Singleton
class MobilityRepository @Inject constructor(
    private val sessions: MobilitySessionDao,
    private val segments: MobilitySegmentDao,
) {
    suspend fun open(): MobilitySessionEntity? = sessions.open()
    suspend fun session(id: Long): MobilitySessionEntity? = sessions.getById(id)
    suspend fun insert(s: MobilitySessionEntity): MobilitySessionEntity = s.copy(id = sessions.insert(s))
    suspend fun update(s: MobilitySessionEntity) = sessions.update(s)
    suspend fun delete(id: Long) = sessions.delete(id)

    suspend fun segmentsOf(sessionId: Long): List<MobilitySegmentEntity> = segments.forSession(sessionId)
    suspend fun openSegment(sessionId: Long): MobilitySegmentEntity? = segments.openFor(sessionId)
    suspend fun insertSegment(s: MobilitySegmentEntity): MobilitySegmentEntity = s.copy(id = segments.insert(s))
    suspend fun updateSegment(s: MobilitySegmentEntity) = segments.update(s)

    /**
     * Modo exibido agora: só de um deslocamento confirmado e em andamento.
     * Candidatos (ainda sem confirmação) não mudam a cena.
     */
    val activeMode: Flow<MovementMode?> = sessions.observeOpen().map { s ->
        s?.takeIf { (it.confirmed || HoodieConfig.UNIFIED_CONFIDENCE_ENGINE && it.confidence >= .60f) && it.state in ACTIVE_STATES }?.currentMode
    }.distinctUntilChanged()

    /** Visual evidence includes candidates, but `activeMode` keeps its confirmed-mode contract. */
    val visualObservation: Flow<MobilityVisualSnapshot> = sessions.observeOpenVisual().map { row ->
        val s = row?.session ?: return@map MobilityVisualSnapshot()
        val mode = row.openSegmentMode ?: s.currentMode
        val source = row.openSegmentSource ?: s.source
        val certainty = when {
            source == com.hoodie.app.core.mobility.MobilitySource.USER_CORRECTION ||
                source == com.hoodie.app.core.mobility.MobilitySource.CONFIRMATION -> ModeCertainty.USER_SELECTED
            source == com.hoodie.app.core.mobility.MobilitySource.PREFERENCE -> ModeCertainty.PREFERRED
            s.state == MobilityState.MOVEMENT_CANDIDATE -> ModeCertainty.PROVISIONAL
            row.openSegmentConfirmed == true || s.confirmed -> ModeCertainty.CONFIRMED
            else -> ModeCertainty.PROVISIONAL
        }
        val phase = when (s.state) {
            MobilityState.MOVEMENT_CANDIDATE -> MobilityPhase.CANDIDATE
            MobilityState.ARRIVING -> MobilityPhase.ARRIVING
            MobilityState.ARRIVED -> MobilityPhase.IDLE
            MobilityState.STATIONARY -> MobilityPhase.IDLE
            MobilityState.WALKING, MobilityState.IN_VEHICLE -> MobilityPhase.ACTIVE
        }
        MobilityVisualSnapshot(
            sessionId = s.id, mode = mode.takeIf { it != MovementMode.NONE },
            observedMovement = s.lastObservedMovement, confidence = row.openSegmentConfidence ?: s.confidence,
            certainty = certainty, source = source, observedAt = s.lastObservationAt,
            lastVehicleAt = s.lastVehicleAt, vehicleExitAt = s.vehicleExitAt, phase = phase,
            speedSampleAttempts = s.speedSampleAttempts, speedSampleCount = s.speedSampleCount,
            meanSpeedKmh = s.meanSpeedKmh, maxSpeedKmh = s.maxSpeedKmh,
        )
    }.distinctUntilChanged()

    /** Histórico para o aprendizado (deslocamentos confirmados e encerrados). */
    suspend fun trips(now: Long, lookbackDays: Long = LEARNING_LOOKBACK_DAYS): List<TripRecord> {
        val list = sessions.finishedSince(now - lookbackDays * DAY_MS)
        if (list.isEmpty()) return emptyList()
        val bySession = segments.forSessions(list.map { it.id }).groupBy { it.sessionId }
        return list.filter { s -> bySession[s.id].orEmpty().any { it.mode != MovementMode.NONE } }.map { s ->
            val segs = bySession[s.id].orEmpty()
            TripRecord(
                startedAt = s.startedAt,
                originPlaceId = s.originPlaceId,
                destinationPlaceId = s.destinationPlaceId,
                modes = segs.map { it.mode },
                confirmedVehicleModes = segs.filter { it.mode.isVehicle && it.confirmed }.map { it.mode },
                arrivalConfirmed = s.arrivalConfirmed,
            )
        }
    }

    /** Último deslocamento encerrado (correção de chegada). */
    suspend fun lastFinished(): MobilitySessionEntity? = sessions.lastFinished()

    /** Deslocamentos confirmados que tocam o intervalo (Diário). */
    suspend fun tripsBetween(from: Long, to: Long): List<MobilityTrip> {
        val list = sessions.overlapping(from, to)
        if (list.isEmpty()) return emptyList()
        val bySession = segments.forSessions(list.map { it.id }).groupBy { it.sessionId }
        return list.map { MobilityTrip(it, bySession[it.id].orEmpty()) }
    }

    /** "Apagar histórico de deslocamentos" (trechos vão junto: ON DELETE CASCADE). */
    suspend fun clearHistory() = sessions.clear()

    suspend fun cleanup(now: Long): Int = sessions.deleteBefore(now - HoodieConfig.MOBILITY_RETENTION_DAYS * DAY_MS)

    suspend fun count(): Int = sessions.count()

    companion object {
        val ACTIVE_STATES = setOf(MobilityState.WALKING, MobilityState.IN_VEHICLE, MobilityState.ARRIVING)
        const val LEARNING_LOOKBACK_DAYS = 60L
    }
}

package com.hoodie.app.data.repository

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.MobilitySegmentDao
import com.hoodie.app.core.database.MobilitySegmentEntity
import com.hoodie.app.core.database.MobilitySessionDao
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.HoodieDatabase
import androidx.room.withTransaction
import com.hoodie.app.core.location.SpeedObservation
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.ModeCertainty
import com.hoodie.app.core.mobility.MobilityPhase
import com.hoodie.app.core.mobility.MobilityVisualSnapshot
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.mobility.SpeedSampleReservation
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
    private val db: HoodieDatabase,
) {
    private val sessions: MobilitySessionDao get() = db.mobilitySessionDao()
    private val segments: MobilitySegmentDao get() = db.mobilitySegmentDao()
    suspend fun open(): MobilitySessionEntity? = sessions.open()
    suspend fun session(id: Long): MobilitySessionEntity? = sessions.getById(id)
    suspend fun insert(s: MobilitySessionEntity): MobilitySessionEntity = s.copy(id = sessions.insert(s))
    suspend fun update(s: MobilitySessionEntity): Boolean = sessions.updatePreservingSpeed(s)
    suspend fun updateConfidence(sessionId: Long, confidence: Float): Boolean = sessions.updateConfidence(sessionId, confidence) == 1
    suspend fun updateMovementEvidence(sessionId: Long, movement: DetectedMovement, observedAt: Long,
        recordVehicle: Boolean, recordVehicleExit: Boolean, clearPending: Boolean): Boolean =
        sessions.updateMovementEvidence(sessionId, movement.name, observedAt, recordVehicle, recordVehicleExit, clearPending) == 1

    suspend fun setPendingMovement(sessionId: Long, mode: MovementMode, at: Long): Boolean =
        sessions.setPendingMovement(sessionId, mode.name, at) == 1

    suspend fun clearPendingMovement(sessionId: Long): Boolean = sessions.clearPendingMovement(sessionId) == 1

    suspend fun reserveSpeedSample(sessionId: Long, now: Long, intervalMs: Long, maxAttempts: Int): SpeedSampleReservation? {
        val earliest = now - intervalMs
        if (sessions.reserveSpeedAttempt(sessionId, now, earliest, maxAttempts) != 1) return null
        val reserved = sessions.getById(sessionId) ?: return null
        if (reserved.endedAt != null || !reserved.currentMode.isVehicle || reserved.stillSince != null || reserved.lastSpeedSampleAt != now) return null
        return SpeedSampleReservation(reserved.id, reserved.startedAt, now, reserved.speedSampleCount, reserved.speedSampleAttempts)
    }

    suspend fun applySpeedSample(reservation: SpeedSampleReservation, sample: SpeedObservation, now: Long, maxAgeMs: Long): MobilitySessionEntity? =
        sessions.applySpeedSample(reservation, sample, now, maxAgeMs)

    /** Session and first segment are one atomic promotion. */
    suspend fun promoteWithSegment(expected: MobilitySessionEntity, promoted: MobilitySessionEntity, segment: MobilitySegmentEntity): Boolean = db.withTransaction {
        val current = sessions.getById(expected.id) ?: return@withTransaction false
        if (current.endedAt != null || current.state != MobilityState.MOVEMENT_CANDIDATE) return@withTransaction false
        if (current.startedAt != expected.startedAt) return@withTransaction false
        sessions.update(promoted.mergeSpeedAggregatesFrom(current))
        segments.insert(segment)
        true
    }

    /** Close the current segment, update the session, and insert one replacement atomically. */
    suspend fun switchSegment(expectedMode: MovementMode, updated: MobilitySessionEntity, next: MobilitySegmentEntity): Boolean = db.withTransaction {
        val current = sessions.getById(updated.id) ?: return@withTransaction false
        if (current.endedAt != null || current.currentMode != expectedMode) return@withTransaction false
        val open = segments.openFor(updated.id)
        if (open?.mode == next.mode) return@withTransaction false
        open?.let { segments.update(it.copy(endedAt = maxOf(next.startedAt, it.startedAt))) }
        sessions.update(updated.mergeSpeedAggregatesFrom(current))
        segments.insert(next)
        true
    }

    /** Atomic end of a trip and its open segment. */
    suspend fun finishWithSegment(ended: MobilitySessionEntity, at: Long): Boolean = db.withTransaction {
        val current = sessions.getById(ended.id) ?: return@withTransaction false
        if (current.endedAt != null) return@withTransaction false
        segments.openFor(ended.id)?.let { segments.update(it.copy(endedAt = maxOf(at, it.startedAt))) }
        sessions.update(ended.mergeSpeedAggregatesFrom(current))
        true
    }
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

    private fun MobilitySessionEntity.mergeSpeedAggregatesFrom(current: MobilitySessionEntity) = copy(
        speedSampleAttempts = current.speedSampleAttempts,
        lastSpeedSampleAt = current.lastSpeedSampleAt,
        speedSampleCount = current.speedSampleCount,
        meanSpeedKmh = current.meanSpeedKmh,
        maxSpeedKmh = current.maxSpeedKmh,
        speedVariation = current.speedVariation,
    )
}

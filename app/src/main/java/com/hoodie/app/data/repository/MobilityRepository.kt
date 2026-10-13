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
import kotlinx.coroutines.flow.combine
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
    private val evidence get() = db.transportEvidenceDao()
    val currentEvidence = evidence.observeCurrent()
    suspend fun evidenceFor(sessionId: Long) = evidence.current(sessionId)
    suspend fun ensureEvidence(sessionId: Long, at: Long): com.hoodie.app.core.database.TransportEvidenceEntity? = db.withTransaction {
        val session = sessions.getById(sessionId)?.takeIf { it.endedAt == null } ?: return@withTransaction null
        evidence.current(sessionId) ?: com.hoodie.app.core.database.TransportEvidenceEntity(sessionId = sessionId,
            startedAt = maxOf(at, session.startedAt)).let { it.copy(id = evidence.insert(it)) }
    }
    suspend fun invalidateSpeedReservations(sessionId: Long) = evidence.invalidate(sessionId)
    suspend fun invalidateAllSpeedReservations() = evidence.invalidateAll()
    suspend fun releaseSpeedReservation(r: SpeedSampleReservation) = db.withTransaction {
        evidence.get(r.evidenceId)?.takeIf { it.reservationId == r.workRequestId }?.let { evidence.update(it.copy(reservationId = null)) }
    }

    suspend fun recordTransportMovement(sessionId: Long, movement: DetectedMovement, at: Long) = db.withTransaction {
        val e = evidence.current(sessionId) ?: return@withTransaction
        if (at < e.startedAt) return@withTransaction
        if (movement == DetectedMovement.STILL) evidence.update(e.copy(stopStart = e.stopStart ?: at, reservationId = null))
        else if (movement.isMoving && e.stopStart != null) {
            val duration = (at - e.stopStart).coerceAtLeast(0)
            evidence.update(e.copy(stopStart = null, stopCount = e.stopCount + if (duration >= 10_000) 1 else 0,
                stoppedMs = e.stoppedMs + if (duration >= 10_000) duration else 0,
                stopSquareSum = e.stopSquareSum + if (duration >= 10_000) duration.toDouble() * duration else 0.0))
        }
    }

    /** Both the budget and the single-use reservation belong to the vehicle period, never to the trip. */
    suspend fun reserveEvidenceSample(sessionId: Long, evidenceId: Long, workRequestId: String, now: Long): SpeedSampleReservation? = db.withTransaction {
        val s = sessions.getById(sessionId) ?: return@withTransaction null
        val e = evidence.current(sessionId)?.takeIf { it.id == evidenceId } ?: return@withTransaction null
        if (s.endedAt != null || !s.currentMode.isVehicle || s.stillSince != null || s.pendingMovementAt != null ||
            e.attempts >= 5 || workRequestId in e.consumedWorkRequestIds.split(",") ||
            e.lastAttemptAt?.let { now - it < 60_000 } == true) return@withTransaction null
        evidence.update(e.copy(attempts = e.attempts + 1, lastAttemptAt = now,
            lastWorkRequestId = workRequestId, consumedWorkRequestIds = (e.consumedWorkRequestIds.split(",").filter { it.isNotEmpty() } + workRequestId).joinToString(","), reservationId = workRequestId))
        SpeedSampleReservation(s.id, s.startedAt, now, e.sampleCount, e.attempts + 1, e.id, workRequestId)
    }

    suspend fun applyEvidenceSample(r: SpeedSampleReservation, sample: SpeedObservation, now: Long): Boolean = db.withTransaction {
        val s = sessions.getById(r.sessionId) ?: return@withTransaction false
        val e = evidence.current(s.id) ?: return@withTransaction false
        if (s.endedAt != null || !s.currentMode.isVehicle || s.stillSince != null || s.pendingMovementAt != null ||
            e.id != r.evidenceId || e.reservationId != r.workRequestId || e.sampleCount != r.sampleCountAtReservation ||
            now - r.reservedAt !in 0..120_000 || sample.observedAt < e.startedAt ||
            sample.observedAt > now || now - sample.observedAt > 120_000 ||
            !sample.metersPerSecond.isFinite() || sample.metersPerSecond !in 0f..100f) return@withTransaction false
        val speed = sample.metersPerSecond * 3.6
        val mean = e.meanSpeedKmh?.toDouble() ?: 0.0
        val n = e.sampleCount + 1
        val nextMean = mean + (speed - mean) / n
        val m2 = e.sampleCount * (mean * e.speedVariation) * (mean * e.speedVariation) + (speed - mean) * (speed - nextMean)
        val variation = if (nextMean > 0) kotlin.math.sqrt((m2 / n).coerceAtLeast(0.0)) / nextMean else 0.0
        evidence.update(e.copy(reservationId = null, sampleCount = n, meanSpeedKmh = nextMean.toFloat(),
            maxSpeedKmh = maxOf(e.maxSpeedKmh ?: 0f, speed.toFloat()), speedVariation = variation.toFloat()))
        true
    }
    suspend fun open(): MobilitySessionEntity? = sessions.open()
    suspend fun session(id: Long): MobilitySessionEntity? = sessions.getById(id)
    suspend fun insert(s: MobilitySessionEntity): MobilitySessionEntity = s.copy(id = sessions.insert(s))
    suspend fun update(s: MobilitySessionEntity): Boolean = sessions.updatePreservingSpeed(s)
    suspend fun updateConfidence(sessionId: Long, confidence: Float): Boolean = sessions.updateConfidence(sessionId, confidence) == 1
    suspend fun updateMovementEvidence(sessionId: Long, movement: DetectedMovement, observedAt: Long,
        recordVehicle: Boolean, recordVehicleExit: Boolean, clearPending: Boolean): Boolean =
        sessions.updateMovementEvidence(sessionId, movement.name, observedAt, recordVehicle, recordVehicleExit, clearPending) == 1

    suspend fun setPendingMovement(sessionId: Long, mode: MovementMode, at: Long): Boolean = db.withTransaction {
        val changed = sessions.setPendingMovement(sessionId, mode.name, at) == 1
        if (changed) evidence.invalidate(sessionId)
        changed
    }

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
        val e = if (segment.mode.isVehicle) ensureEvidence(current.id, current.startedAt) else null
        segments.insert(segment.copy(evidenceId = e?.id))
        true
    }

    /** Close the current segment, update the session, and insert one replacement atomically. */
    suspend fun switchSegment(expectedMode: MovementMode, updated: MobilitySessionEntity, next: MobilitySegmentEntity): Boolean = db.withTransaction {
        val current = sessions.getById(updated.id) ?: return@withTransaction false
        if (current.endedAt != null || current.currentMode != expectedMode) return@withTransaction false
        val open = segments.openFor(updated.id)
        if (open?.mode == next.mode) return@withTransaction false
        val boundary = maxOf(next.startedAt, current.startedAt, open?.startedAt ?: current.startedAt)
        if (next.confirmedAt != null && (current.pendingMovementAt == null || current.pendingMovementAt > next.startedAt ||
                current.pendingMovementMode != next.mode || open?.source == com.hoodie.app.core.mobility.MobilitySource.USER_CORRECTION)) return@withTransaction false
        open?.let { segments.update(it.copy(endedAt = boundary)) }
        val e = if (next.mode.isVehicle) ensureEvidence(current.id, boundary) else {
            evidence.current(current.id)?.let { evidence.update(it.copy(endedAt = boundary, reservationId = null)) }
            null
        }
        sessions.update(updated.mergeSpeedAggregatesFrom(current))
        segments.insert(next.copy(startedAt = boundary, evidenceId = e?.id))
        true
    }

    /** Atomic end of a trip and its open segment. */
    suspend fun finishWithSegment(ended: MobilitySessionEntity, at: Long): Boolean = db.withTransaction {
        val current = sessions.getById(ended.id) ?: return@withTransaction false
        if (current.endedAt != null) return@withTransaction false
        segments.openFor(ended.id)?.let { segments.update(it.copy(endedAt = maxOf(at, it.startedAt))) }
        sessions.update(ended.mergeSpeedAggregatesFrom(current))
        evidence.current(ended.id)?.let { evidence.update(it.copy(endedAt = at, reservationId = null)) }
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
    val visualObservation: Flow<MobilityVisualSnapshot> = combine(sessions.observeOpenVisual(), evidence.observeCurrent()) { row, e ->
        val s = row?.session ?: return@combine MobilityVisualSnapshot()
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
            speedSampleAttempts = e?.takeIf { it.sessionId == s.id }?.attempts ?: 0, speedSampleCount = e?.takeIf { it.sessionId == s.id }?.sampleCount ?: 0,
            meanSpeedKmh = e?.takeIf { it.sessionId == s.id }?.meanSpeedKmh, maxSpeedKmh = e?.takeIf { it.sessionId == s.id }?.maxSpeedKmh,
            evidenceId = e?.takeIf { it.sessionId == s.id }?.id,
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

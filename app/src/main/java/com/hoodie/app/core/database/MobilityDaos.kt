package com.hoodie.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Update
import androidx.room.Transaction
import com.hoodie.app.core.location.SpeedObservation
import com.hoodie.app.core.mobility.SpeedSampleReservation
import kotlinx.coroutines.flow.Flow
import kotlin.math.sqrt

@Dao
interface MobilitySessionDao {
    @Insert
    suspend fun insert(s: MobilitySessionEntity): Long

    @Update
    suspend fun update(s: MobilitySessionEntity)

    /** Merge asynchronous speed writes into an engine state change in the same transaction. */
    @Transaction
    suspend fun updatePreservingSpeed(s: MobilitySessionEntity): Boolean {
        val current = getById(s.id) ?: return false
        if (current.endedAt != null && s.endedAt == null) return false
        update(s.copy(
            speedSampleAttempts = current.speedSampleAttempts,
            lastSpeedSampleAt = current.lastSpeedSampleAt,
            speedSampleCount = current.speedSampleCount,
            meanSpeedKmh = current.meanSpeedKmh,
            maxSpeedKmh = current.maxSpeedKmh,
            speedVariation = current.speedVariation,
        ))
        return true
    }

    @Query("SELECT * FROM mobility_sessions WHERE id = :id")
    suspend fun getById(id: Long): MobilitySessionEntity?

    @Query("UPDATE mobility_sessions SET confidence = :confidence WHERE id = :id AND endedAt IS NULL AND state = 'MOVEMENT_CANDIDATE'")
    suspend fun updateConfidence(id: Long, confidence: Float): Int

    @Query("""
        UPDATE mobility_sessions
        SET lastObservedMovement = :movement, lastObservationAt = :observedAt,
            lastVehicleAt = CASE WHEN :recordVehicle = 1 THEN :observedAt ELSE lastVehicleAt END,
            vehicleExitAt = CASE WHEN :recordVehicleExit = 1 THEN :observedAt ELSE vehicleExitAt END,
            pendingMovementMode = CASE WHEN :clearPending = 1 THEN NULL ELSE pendingMovementMode END,
            pendingMovementAt = CASE WHEN :clearPending = 1 THEN NULL ELSE pendingMovementAt END
        WHERE id = :id AND endedAt IS NULL AND (lastObservationAt IS NULL OR lastObservationAt <= :observedAt)
    """)
    suspend fun updateMovementEvidence(id: Long, movement: String, observedAt: Long, recordVehicle: Boolean,
        recordVehicleExit: Boolean, clearPending: Boolean): Int

    @Query("""
        UPDATE mobility_sessions SET pendingMovementMode = :mode, pendingMovementAt = :at
        WHERE id = :id AND endedAt IS NULL
          AND currentMode IN ('VEHICLE_UNKNOWN', 'CAR', 'BUS', 'TRAIN', 'METRO', 'OTHER', 'PUBLIC_TRANSPORT')
          AND (lastObservationAt IS NULL OR lastObservationAt <= :at)
    """)
    suspend fun setPendingMovement(id: Long, mode: String, at: Long): Int

    @Query("UPDATE mobility_sessions SET pendingMovementMode = NULL, pendingMovementAt = NULL WHERE id = :id AND endedAt IS NULL")
    suspend fun clearPendingMovement(id: Long): Int

    /** Atomic throttle and reservation. Concurrent callers cannot reserve the same window. */
    @Query("""
        UPDATE mobility_sessions
        SET speedSampleAttempts = speedSampleAttempts + 1, lastSpeedSampleAt = :now
        WHERE id = :id AND endedAt IS NULL AND stillSince IS NULL
          AND currentMode IN ('VEHICLE_UNKNOWN', 'CAR', 'BUS', 'TRAIN', 'METRO', 'OTHER', 'PUBLIC_TRANSPORT')
          AND speedSampleAttempts < :maxAttempts
          AND (lastSpeedSampleAt IS NULL OR lastSpeedSampleAt <= :earliestAllowed)
    """)
    suspend fun reserveSpeedAttempt(id: Long, now: Long, earliestAllowed: Long, maxAttempts: Int): Int

    @Transaction
    suspend fun applySpeedSample(reservation: SpeedSampleReservation, sample: SpeedObservation, now: Long, maxAgeMs: Long): MobilitySessionEntity? {
        val current = getById(reservation.sessionId) ?: return null
        if (current.endedAt != null || current.startedAt != reservation.startedAt || current.stillSince != null ||
            !current.currentMode.isVehicle || current.lastSpeedSampleAt != reservation.reservedAt ||
            current.speedSampleCount != reservation.sampleCountAtReservation) return null
        if (sample.observedAt < current.startedAt || sample.observedAt > now || now - sample.observedAt > maxAgeMs ||
            !sample.metersPerSecond.isFinite() || sample.metersPerSecond !in 0f..100f) return null

        val speed = sample.metersPerSecond * 3.6f
        val oldCount = current.speedSampleCount.coerceAtLeast(0)
        val oldMean = current.meanSpeedKmh?.takeIf { it.isFinite() }?.toDouble() ?: 0.0
        val oldVariation = current.speedVariation.takeIf { it.isFinite() }?.coerceAtLeast(0f)?.toDouble() ?: 0.0
        val oldM2 = if (oldCount > 0) oldCount * (oldMean * oldVariation) * (oldMean * oldVariation) else 0.0
        val newCount = oldCount + 1
        val delta = speed - oldMean
        val newMean = if (oldCount == 0) speed.toDouble() else oldMean + delta / newCount
        val newM2 = if (oldCount == 0) 0.0 else oldM2 + delta * (speed - newMean)
        val newVariation = if (newMean > 0.0) sqrt((newM2 / newCount).coerceAtLeast(0.0)) / newMean else 0.0
        val changed = applySpeedAggregates(
            reservation.sessionId, reservation.reservedAt, reservation.sampleCountAtReservation,
            newCount, newMean.toFloat(), maxOf(current.maxSpeedKmh ?: speed, speed), newVariation.toFloat(),
        )
        return if (changed == 1) getById(reservation.sessionId) else null
    }

    @Query("""
        UPDATE mobility_sessions
        SET speedSampleCount = :count, meanSpeedKmh = :meanKmh, maxSpeedKmh = :maxKmh, speedVariation = :variation
        WHERE id = :id AND endedAt IS NULL AND stillSince IS NULL AND lastSpeedSampleAt = :reservedAt
          AND speedSampleCount = :expectedCount
          AND currentMode IN ('VEHICLE_UNKNOWN', 'CAR', 'BUS', 'TRAIN', 'METRO', 'OTHER', 'PUBLIC_TRANSPORT')
    """)
    suspend fun applySpeedAggregates(id: Long, reservedAt: Long, expectedCount: Int, count: Int, meanKmh: Float,
        maxKmh: Float, variation: Float): Int

    /** Sessão aberta (candidata ou em andamento). Só existe uma por vez. */
    @Query("SELECT * FROM mobility_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun open(): MobilitySessionEntity?

    @Query("SELECT * FROM mobility_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpen(): Flow<MobilitySessionEntity?>

    /** Session and its open segment are read from one Room query snapshot. */
    @Query("""
        SELECT s.*, g.mode AS openSegmentMode, g.confidence AS openSegmentConfidence,
               g.confirmed AS openSegmentConfirmed, g.source AS openSegmentSource
        FROM mobility_sessions AS s
        LEFT JOIN mobility_segments AS g ON g.sessionId = s.id AND g.endedAt IS NULL
        WHERE s.endedAt IS NULL
        ORDER BY s.startedAt DESC LIMIT 1
    """)
    fun observeOpenVisual(): Flow<MobilityVisualRow?>

    /** Deslocamentos encerrados (para aprendizado), mais recentes primeiro. */
    @Query("SELECT * FROM mobility_sessions WHERE endedAt IS NOT NULL AND confirmed = 1 AND startedAt >= :since ORDER BY startedAt DESC")
    suspend fun finishedSince(since: Long): List<MobilitySessionEntity>

    @Query("SELECT * FROM mobility_sessions WHERE endedAt IS NOT NULL ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastFinished(): MobilitySessionEntity?

    @Query("SELECT * FROM mobility_sessions WHERE (confirmed = 1 OR (confidence >= 0.6 AND state != 'MOVEMENT_CANDIDATE')) AND startedAt < :to AND (endedAt IS NULL OR endedAt > :from) ORDER BY startedAt")
    suspend fun overlapping(from: Long, to: Long): List<MobilitySessionEntity>

    @Query("DELETE FROM mobility_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM mobility_sessions WHERE startedAt < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("DELETE FROM mobility_sessions")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM mobility_sessions")
    suspend fun count(): Int
}

data class MobilityVisualRow(
    @Embedded val session: MobilitySessionEntity,
    @ColumnInfo(name = "openSegmentMode") val openSegmentMode: com.hoodie.app.core.mobility.MovementMode?,
    @ColumnInfo(name = "openSegmentConfidence") val openSegmentConfidence: Float?,
    @ColumnInfo(name = "openSegmentConfirmed") val openSegmentConfirmed: Boolean?,
    @ColumnInfo(name = "openSegmentSource") val openSegmentSource: com.hoodie.app.core.mobility.MobilitySource?,
)

@Dao
interface MobilitySegmentDao {
    @Query("SELECT * FROM mobility_segments WHERE id = :id")
    suspend fun getById(id: Long): MobilitySegmentEntity?
    @Insert
    suspend fun insert(s: MobilitySegmentEntity): Long

    @Update
    suspend fun update(s: MobilitySegmentEntity)

    @Query("SELECT * FROM mobility_segments WHERE sessionId = :sessionId ORDER BY startedAt")
    suspend fun forSession(sessionId: Long): List<MobilitySegmentEntity>

    @Query("SELECT * FROM mobility_segments WHERE sessionId = :sessionId AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openFor(sessionId: Long): MobilitySegmentEntity?

    @Query("SELECT * FROM mobility_segments WHERE sessionId IN (:sessionIds) ORDER BY startedAt")
    suspend fun forSessions(sessionIds: List<Long>): List<MobilitySegmentEntity>
}

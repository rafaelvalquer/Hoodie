package com.hoodie.app.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.hoodie.app.engine.mobility.TransportFeatures
import kotlin.math.sqrt

/** Aggregate evidence for one uninterrupted vehicle period. Never stores positions or individual samples. */
@Entity(tableName = "transport_evidence", foreignKeys = [ForeignKey(entity = MobilitySessionEntity::class,
    parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("sessionId")])
data class TransportEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastWorkRequestId: String? = null,
    val consumedWorkRequestIds: String = "",
    val reservationId: String? = null,
    val sampleCount: Int = 0,
    val meanSpeedKmh: Float? = null,
    val maxSpeedKmh: Float? = null,
    val speedVariation: Float = 0f,
    val stopStart: Long? = null,
    val stopCount: Int = 0,
    val stoppedMs: Long = 0,
    val stopSquareSum: Double = 0.0,
) {
    fun features(now: Long): TransportFeatures {
        val meanStop = if (stopCount > 0) stoppedMs / stopCount else 0
        val deviation = if (stopCount > 0) sqrt((stopSquareSum / stopCount - meanStop.toDouble() * meanStop).coerceAtLeast(0.0)) else 0.0
        return TransportFeatures((now - startedAt).coerceAtLeast(0), true, meanSpeedKmh, maxSpeedKmh,
            speedVariation, stopCount, stoppedMs + (stopStart?.let { (now - it).coerceAtLeast(0) } ?: 0),
            meanStop, stopCount >= 3 && meanStop > 0 && deviation / meanStop < .30)
    }
}

@Dao
interface TransportEvidenceDao {
    @Query("""SELECT COALESCE(SUM(attempts), 0) AS attempts, MAX(lastAttemptAt) AS lastAttemptAt
        FROM transport_evidence WHERE sessionId = :sessionId""")
    suspend fun sampleBudget(sessionId: Long): TransportSampleBudget
    @Insert suspend fun insert(value: TransportEvidenceEntity): Long
    @Update suspend fun update(value: TransportEvidenceEntity)
    @Query("SELECT * FROM transport_evidence WHERE id = :id") suspend fun get(id: Long): TransportEvidenceEntity?
    @Query("SELECT * FROM transport_evidence WHERE sessionId = :sessionId AND endedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun current(sessionId: Long): TransportEvidenceEntity?
    @Query("SELECT * FROM transport_evidence WHERE endedAt IS NULL ORDER BY id DESC LIMIT 1")
    fun observeCurrent(): Flow<TransportEvidenceEntity?>
    @Query("UPDATE transport_evidence SET reservationId = NULL WHERE sessionId = :sessionId")
    suspend fun invalidate(sessionId: Long)
    @Query("UPDATE transport_evidence SET reservationId = NULL") suspend fun invalidateAll()
}

data class TransportSampleBudget(val attempts: Int, val lastAttemptAt: Long?)

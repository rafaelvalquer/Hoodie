package com.hoodie.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MobilitySessionDao {
    @Insert
    suspend fun insert(s: MobilitySessionEntity): Long

    @Update
    suspend fun update(s: MobilitySessionEntity)

    @Query("SELECT * FROM mobility_sessions WHERE id = :id")
    suspend fun getById(id: Long): MobilitySessionEntity?

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

package com.hoodie.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    /** Deslocamentos encerrados (para aprendizado), mais recentes primeiro. */
    @Query("SELECT * FROM mobility_sessions WHERE endedAt IS NOT NULL AND confirmed = 1 AND startedAt >= :since ORDER BY startedAt DESC")
    suspend fun finishedSince(since: Long): List<MobilitySessionEntity>

    @Query("SELECT * FROM mobility_sessions WHERE confirmed = 1 AND startedAt < :to AND (endedAt IS NULL OR endedAt > :from) ORDER BY startedAt")
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

@Dao
interface MobilitySegmentDao {
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

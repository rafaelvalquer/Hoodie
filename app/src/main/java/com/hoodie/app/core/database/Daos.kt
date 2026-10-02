package com.hoodie.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.hoodie.app.core.model.TimelineSourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY type, name")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places")
    suspend fun getAll(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: Long): PlaceEntity?

    @Insert
    suspend fun insert(place: PlaceEntity): Long

    @Update
    suspend fun update(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE places SET confirmationCount = confirmationCount + 1, lastVisitedAt = :at WHERE id = :id")
    suspend fun markVisited(id: Long, at: Long)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE id = 1")
    fun observe(): Flow<RoutineEntity?>

    @Query("SELECT * FROM routines WHERE id = 1")
    suspend fun get(): RoutineEntity?

    @Upsert
    suspend fun upsert(routine: RoutineEntity)
}

@Dao
interface DayExceptionDao {
    @Query("SELECT * FROM day_exceptions WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DayExceptionEntity?

    @Query("SELECT * FROM day_exceptions WHERE epochDay = :epochDay")
    fun observe(epochDay: Long): Flow<DayExceptionEntity?>

    @Query("SELECT * FROM day_exceptions WHERE epochDay BETWEEN :from AND :to")
    suspend fun range(from: Long, to: Long): List<DayExceptionEntity>

    @Upsert
    suspend fun upsert(e: DayExceptionEntity)

    @Query("DELETE FROM day_exceptions WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)
}

@Dao
interface LocationEventDao {
    @Insert
    suspend fun insert(e: LocationEventEntity): Long

    @Query("SELECT * FROM location_events WHERE placeId = :placeId ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastForPlace(placeId: Long): LocationEventEntity?

    @Query("DELETE FROM location_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface ContextEventDao {
    @Query("SELECT * FROM context_events WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun current(): ContextEventEntity?

    @Query("SELECT * FROM context_events WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeCurrent(): Flow<ContextEventEntity?>

    @Query("SELECT * FROM context_events WHERE id = :id")
    suspend fun getById(id: Long): ContextEventEntity?

    @Query("SELECT * FROM context_events ORDER BY startedAt DESC LIMIT 1 OFFSET 1")
    suspend fun previous(): ContextEventEntity?

    @Insert
    suspend fun insert(e: ContextEventEntity): Long

    @Update
    suspend fun update(e: ContextEventEntity)

    @Query("UPDATE context_events SET endedAt = :endedAt WHERE endedAt IS NULL")
    suspend fun closeOpen(endedAt: Long)

    @Query("DELETE FROM context_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM context_events WHERE startedAt < :to AND (endedAt IS NULL OR endedAt > :from) ORDER BY startedAt")
    suspend fun overlapping(from: Long, to: Long): List<ContextEventEntity>

    @Query("SELECT * FROM context_events WHERE startedAt < :to AND (endedAt IS NULL OR endedAt > :from) ORDER BY startedAt")
    fun observeOverlapping(from: Long, to: Long): Flow<List<ContextEventEntity>>

    @Query("SELECT * FROM context_events WHERE startedAt > :after ORDER BY startedAt LIMIT 1")
    suspend fun firstStartedAfter(after: Long): ContextEventEntity?

    @Query("SELECT * FROM context_events ORDER BY startedAt")
    suspend fun all(): List<ContextEventEntity>

    @Query("SELECT COUNT(*) FROM context_events")
    suspend fun count(): Int
}

@Dao
interface ConfirmationDao {
    @Insert
    suspend fun insert(c: ContextConfirmationEntity)

    @Query("SELECT * FROM context_confirmations WHERE timestamp >= :since")
    suspend fun since(since: Long): List<ContextConfirmationEntity>
}

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(q: ContextQuestionEntity): Long

    @Query("SELECT * FROM context_questions WHERE id = :id")
    suspend fun getById(id: Long): ContextQuestionEntity?

    @Query("SELECT * FROM context_questions WHERE answeredAt IS NULL AND askedAt >= :since ORDER BY askedAt DESC")
    fun observePending(since: Long): Flow<List<ContextQuestionEntity>>

    @Query("SELECT * FROM context_questions WHERE askedAt >= :since")
    suspend fun since(since: Long): List<ContextQuestionEntity>

    @Update
    suspend fun update(q: ContextQuestionEntity)
}

@Dao
interface HoodieStateDao {
    @Query("SELECT * FROM hoodie_state WHERE id = 1")
    suspend fun get(): HoodieStateEntity?

    @Upsert
    suspend fun upsert(s: HoodieStateEntity)
}

@Dao
interface HoodieActivityDao {
    @Insert
    suspend fun insertAll(items: List<HoodieActivityEntity>)

    @Query("SELECT * FROM hoodie_activities WHERE startedAt < :to AND endedAt > :from ORDER BY startedAt")
    suspend fun overlapping(from: Long, to: Long): List<HoodieActivityEntity>

    @Query("SELECT * FROM hoodie_activities WHERE startedAt < :to AND endedAt > :from ORDER BY startedAt")
    fun observeOverlapping(from: Long, to: Long): Flow<List<HoodieActivityEntity>>

    /** Atividade concluída exatamente em [at] (a que foi interrompida por um boundary). */
    @Query("SELECT * FROM hoodie_activities WHERE endedAt = :at ORDER BY startedAt DESC LIMIT 1")
    suspend fun endedAt(at: Long): HoodieActivityEntity?

    @Query("DELETE FROM hoodie_activities WHERE startedAt >= :from")
    suspend fun deleteStartedFrom(from: Long): Int

    @Query("DELETE FROM hoodie_activities WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM hoodie_activities")
    suspend fun count(): Int
}

@Dao
interface TimelineDao {
    @Insert
    suspend fun insert(e: TimelineEventEntity): Long

    @Insert
    suspend fun insertAll(e: List<TimelineEventEntity>)

    @Query("SELECT * FROM timeline_events WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun range(from: Long, to: Long): List<TimelineEventEntity>

    @Query("SELECT * FROM timeline_events WHERE sourceType = :type AND sourceId = :id")
    suspend fun bySource(type: TimelineSourceType, id: Long): List<TimelineEventEntity>

    @Query("DELETE FROM timeline_events WHERE sourceType = :type AND sourceId = :id")
    suspend fun deleteBySource(type: TimelineSourceType, id: Long): Int

    @Query("DELETE FROM timeline_events WHERE sourceType = :type AND timestamp >= :from AND timestamp <= :to")
    suspend fun deleteSourceRange(type: TimelineSourceType, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM timeline_events")
    suspend fun count(): Int
}

@Dao
interface MemoryDao {
    /** -1 quando a memória já existia (memórias são desbloqueadas uma única vez). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: MemoryEntity): Long

    @Query("SELECT * FROM memories ORDER BY unlockedAt")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM memories WHERE `key` = :key)")
    suspend fun exists(key: String): Boolean
}

@Dao
interface DeviceUsageDao {
    @Query("SELECT * FROM daily_device_usage WHERE date = :date")
    suspend fun day(date: String): DailyDeviceUsageEntity?

    @Query("SELECT * FROM daily_app_usage WHERE date = :date ORDER BY foregroundMs DESC")
    suspend fun apps(date: String): List<DailyAppUsageEntity>

    @Query("SELECT * FROM daily_context_app_usage WHERE date = :date ORDER BY foregroundMs DESC")
    suspend fun contextApps(date: String): List<DailyContextAppUsageEntity>

    @Upsert
    suspend fun upsertDay(day: DailyDeviceUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<DailyAppUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContextApps(items: List<DailyContextAppUsageEntity>)

    @Query("DELETE FROM daily_app_usage WHERE date = :date")
    suspend fun deleteApps(date: String)

    @Query("DELETE FROM daily_context_app_usage WHERE date = :date")
    suspend fun deleteContextApps(date: String)

    @Query("SELECT * FROM phone_app_sessions WHERE epochDay = :epochDay ORDER BY startedAt")
    suspend fun sessions(epochDay: Long): List<PhoneAppSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<PhoneAppSessionEntity>)

    @Query("DELETE FROM phone_app_sessions WHERE epochDay = :epochDay")
    suspend fun deleteSessions(epochDay: Long)

    @Query("DELETE FROM phone_app_sessions")
    suspend fun clearSessions()

    @Query("SELECT COUNT(*) FROM phone_app_sessions")
    suspend fun sessionCount(): Int

    /** Troca o dia inteiro de uma vez: o Diário nunca vê metade de um recálculo. */
    @Transaction
    suspend fun replaceDay(
        day: DailyDeviceUsageEntity,
        apps: List<DailyAppUsageEntity>,
        contexts: List<DailyContextAppUsageEntity>,
        sessions: List<PhoneAppSessionEntity> = emptyList(),
        epochDay: Long? = null,
    ) {
        deleteApps(day.date)
        deleteContextApps(day.date)
        upsertDay(day)
        insertApps(apps)
        insertContextApps(contexts)
        if (epochDay != null) {
            deleteSessions(epochDay)
            insertSessions(sessions)
        }
    }

    @Query("SELECT * FROM app_category_overrides")
    suspend fun overrides(): List<AppCategoryOverrideEntity>

    @Upsert
    suspend fun upsertOverride(o: AppCategoryOverrideEntity)

    @Query("DELETE FROM app_category_overrides WHERE packageName = :packageName")
    suspend fun deleteOverride(packageName: String)

    @Query("DELETE FROM daily_device_usage")
    suspend fun clearDays()

    @Query("DELETE FROM daily_app_usage")
    suspend fun clearApps()

    @Query("DELETE FROM daily_context_app_usage")
    suspend fun clearContextApps()

    /** "Apagar histórico digital": some tudo que foi medido; as categorias escolhidas ficam. */
    @Transaction
    suspend fun clearHistory() {
        clearDays()
        clearApps()
        clearContextApps()
        clearSessions()
    }

    @Query("SELECT COUNT(*) FROM daily_device_usage")
    suspend fun dayCount(): Int
}

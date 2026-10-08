package com.hoodie.app.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IntelligenceDao {
    @Query("SELECT * FROM day_state WHERE id = 1") suspend fun dayState(): DayStateEntity?
    @Query("SELECT * FROM day_state WHERE id = 1") fun observeDayState(): Flow<DayStateEntity?>
    @Upsert suspend fun saveDayState(state: DayStateEntity)
    @Insert suspend fun insertCorrection(correction: DiaryCorrectionEntity): Long
    @Query("SELECT * FROM diary_corrections WHERE createdAt >= :since ORDER BY createdAt, id") suspend fun correctionsSince(since: Long): List<DiaryCorrectionEntity>
    @Query("SELECT * FROM learned_routine_slots ORDER BY dayGroup, type") suspend fun routineSlots(): List<LearnedRoutineSlotEntity>
    @Query("SELECT * FROM learned_routine_slots ORDER BY dayGroup, type") fun observeRoutineSlots(): Flow<List<LearnedRoutineSlotEntity>>
    @Query("DELETE FROM learned_routine_slots") suspend fun clearRoutineSlots()
    @Insert suspend fun insertRoutineSlots(slots: List<LearnedRoutineSlotEntity>)
    @Transaction suspend fun replaceRoutineSlots(slots: List<LearnedRoutineSlotEntity>) {
        clearRoutineSlots()
        insertRoutineSlots(slots)
    }
    @Query("SELECT * FROM transport_patterns WHERE originPlaceId = :origin AND destinationPlaceId = :destination AND dayGroup = :dayGroup AND timeBucket = :bucket")
    suspend fun transportPatterns(origin: Long, destination: Long, dayGroup: String, bucket: Int): List<TransportPatternEntity>
    @Upsert suspend fun saveTransportPattern(pattern: TransportPatternEntity)
    @Query("DELETE FROM transport_patterns") suspend fun clearTransportPatterns()
}

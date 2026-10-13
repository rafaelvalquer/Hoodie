package com.hoodie.app.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Immutable in force; future versions may be replaced before their effectiveFrom. */
@Entity(tableName = "effective_routine_versions", indices = [Index(value = ["epochDay", "effectiveFrom"], unique = true)])
data class EffectiveRoutineVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val effectiveFrom: Long,
    val inputKey: String,
    val snapshot: String,
)
@Dao
interface EffectiveRoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: EffectiveRoutineVersionEntity): Long
    @Query("SELECT * FROM effective_routine_versions WHERE epochDay = :day AND effectiveFrom <= :asOf ORDER BY effectiveFrom DESC LIMIT 1")
    suspend fun at(day: Long, asOf: Long): EffectiveRoutineVersionEntity?
    @Query("SELECT * FROM effective_routine_versions ORDER BY effectiveFrom") suspend fun all(): List<EffectiveRoutineVersionEntity>
    @Query("SELECT * FROM effective_routine_versions ORDER BY effectiveFrom") fun observe(): Flow<List<EffectiveRoutineVersionEntity>>
}

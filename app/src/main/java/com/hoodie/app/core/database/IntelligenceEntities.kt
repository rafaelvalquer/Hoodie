package com.hoodie.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "day_state")
data class DayStateEntity(
    @PrimaryKey val id: Int = 1,
    val state: String,
    val startedAt: Long,
    val confidence: Float,
    val reason: String,
    val provisional: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "diary_corrections", indices = [Index("createdAt"), Index(value = ["targetType", "targetId"])])
data class DiaryCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,
    val targetId: Long,
    val originalContext: String?,
    val correctedContext: String?,
    val originalPlaceId: Long?,
    val correctedPlaceId: Long?,
    val originalStartAt: Long?,
    val correctedStartAt: Long?,
    val originalEndAt: Long?,
    val correctedEndAt: Long?,
    val originalMode: String?,
    val correctedMode: String?,
    val originalSource: String,
    val originalConfidence: Float,
    val createdAt: Long,
    val originalVenueType: String? = null,
    val correctedVenueType: String? = null,
)

@Entity(tableName = "learned_routine_slots", indices = [Index(value = ["dayGroup", "type"], unique = true)])
data class LearnedRoutineSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayGroup: String,
    val type: String,
    val medianMinute: Int,
    val deviationMinutes: Int,
    val sampleCount: Int,
    val confidence: Float,
    val updatedAt: Long,
)

@Entity(tableName = "transport_patterns", primaryKeys = ["originPlaceId", "destinationPlaceId", "dayGroup", "timeBucket", "mode"], indices = [Index("updatedAt")])
data class TransportPatternEntity(
    val originPlaceId: Long,
    val destinationPlaceId: Long,
    val dayGroup: String,
    val timeBucket: Int,
    val mode: String,
    val confirmations: Int,
    val rejections: Int,
    val confidence: Float,
    val updatedAt: Long,
)

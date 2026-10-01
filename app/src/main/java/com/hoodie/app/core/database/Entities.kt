package com.hoodie.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.model.WorkMode

/**
 * Lugar conhecido. As coordenadas NUNCA ficam em texto puro: [encryptedCoordinates]
 * é "lat,lng" cifrado com AES-GCM usando uma chave do Android Keystore.
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaceType,
    val encryptedCoordinates: String,
    val radiusMeters: Float,
    val confidence: Float,
    val isFavorite: Boolean = false,
    val confirmationCount: Int = 0,
    val createdAt: Long,
    val lastVisitedAt: Long? = null,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: Long = 1,
    val workMode: WorkMode,
    /** Bitmask dos dias: bit 0 = segunda ... bit 6 = domingo. */
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val lunchStartMinute: Int,
    val lunchEndMinute: Int,
    val updatedAt: Long,
)

/** "Hoje não vou trabalhar": feriado, folga, férias. */
@Entity(tableName = "day_exceptions")
data class DayExceptionEntity(
    @PrimaryKey val epochDay: Long,
    val kind: String,
    val createdAt: Long,
)

/** Evento de geofence. Guarda o lugar e a transição — nunca latitude/longitude. */
@Entity(tableName = "location_events", indices = [Index("timestamp")])
data class LocationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val transition: String,
    val timestamp: Long,
)

@Entity(tableName = "context_events", indices = [Index("startedAt"), Index("endedAt")])
data class ContextEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: UserContextType,
    val startedAt: Long,
    val endedAt: Long?,
    val confidence: Float,
    val placeId: Long?,
    val source: ContextSource,
)

@Entity(tableName = "context_confirmations", indices = [Index("timestamp")])
data class ContextConfirmationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: UserContextType,
    val placeId: Long?,
    val dayOfWeek: Int,
    val minuteOfDay: Int,
    val accepted: Boolean,
    val timestamp: Long,
)

@Entity(tableName = "context_questions", indices = [Index("askedAt")])
data class ContextQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: QuestionKind,
    val candidate: UserContextType?,
    val placeId: Long?,
    /** Só para NEW_PLACE: posição pontual (cifrada) caso o usuário queira salvar o lugar. */
    val encryptedCoordinates: String?,
    val chosenPlaceType: PlaceType? = null,
    val contextEventId: Long?,
    val askedAt: Long,
    val answeredAt: Long? = null,
    val answer: String? = null,
)

@Entity(tableName = "hoodie_state")
data class HoodieStateEntity(
    @PrimaryKey val id: Int = 1,
    val activity: HoodieActivity,
    val startedAt: Long,
    val expectedEndAt: Long?,
    val energy: Int,
    val hunger: Int,
    val mood: Int,
    val social: Int,
    val focus: Int,
    val needsAt: Long,
    val userContext: UserContextType,
)

@Entity(tableName = "hoodie_activities", indices = [Index("startedAt")])
data class HoodieActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activity: HoodieActivity,
    val startedAt: Long,
    val endedAt: Long,
    val userContext: UserContextType,
)

/**
 * Linha da timeline ligada ao registro que a originou ([sourceType] + [sourceId]),
 * para que correções (ex.: oscilação de GPS desfeita) apaguem também o que foi contado.
 */
@Entity(tableName = "timeline_events", indices = [Index("timestamp"), Index("sourceType", "sourceId")])
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val actor: TimelineActor,
    val emoji: String,
    val text: String,
    val sourceType: TimelineSourceType? = null,
    /** CONTEXT → id do context_event; HOODIE_ACTIVITY → startedAt da atividade. */
    val sourceId: Long? = null,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val key: String,
    val emoji: String,
    val title: String,
    val unlockedAt: Long,
)

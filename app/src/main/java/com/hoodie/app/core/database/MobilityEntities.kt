package com.hoodie.app.core.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode

/**
 * Um deslocamento: origem, destino, horários e como foi — NUNCA o trajeto.
 * Não há latitude/longitude aqui: só ids de lugares conhecidos.
 *
 * O estado da máquina fica persistido ([state]) porque cada evento do sistema pode
 * chegar num processo novo (app fechado, receiver acordado sozinho).
 */
@Entity(tableName = "mobility_sessions", indices = [Index("startedAt"), Index("endedAt")])
data class MobilitySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val originPlaceId: Long? = null,
    val destinationPlaceId: Long? = null,
    val initialMode: MovementMode,
    val currentMode: MovementMode,
    val state: MobilityState,
    val confidence: Float,
    /** Usuário confirmou que estava se deslocando (ou o padrão aprendido confirmou). */
    val confirmed: Boolean = false,
    val source: MobilitySource,
    /** Saída do lugar de origem já observada (geofence EXIT). */
    val leftOrigin: Boolean = false,
    /** null = chegada ainda não decidida; true confirmada; false o usuário disse que não chegou. */
    val arrivalConfirmed: Boolean? = null,
    val arrivalAutoConfirmed: Boolean = false,
    /** Desde quando se move sem parar (mede o movimento "sustentado"). */
    val movingSince: Long? = null,
    /** Desde quando está parado (para resolver chegada sem geofence). */
    val stillSince: Long? = null,
    /** Pergunta do meio de transporte adiada porque o veículo estava em movimento. */
    val pendingModeQuestion: Boolean = false,
    val questionsAsked: Int = 0,
    val lastObservedMovement: com.hoodie.app.core.mobility.DetectedMovement? = null,
    val lastObservationAt: Long? = null,
    val lastVehicleAt: Long? = null,
    val vehicleExitAt: Long? = null,
    val pendingMovementMode: MovementMode? = null,
    val pendingMovementAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val speedSampleAttempts: Int = 0,
    val lastSpeedSampleAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val speedSampleCount: Int = 0,
    val meanSpeedKmh: Float? = null,
    val maxSpeedKmh: Float? = null,
    @ColumnInfo(defaultValue = "0") val speedVariation: Float = 0f,
)

/** Trecho de um deslocamento com um único modo (caminhada → ônibus → caminhada). */
@Entity(
    tableName = "mobility_segments",
    foreignKeys = [ForeignKey(entity = MobilitySessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("startedAt")],
)
data class MobilitySegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val mode: MovementMode,
    val startedAt: Long,
    val endedAt: Long? = null,
    val confidence: Float,
    val confirmed: Boolean = false,
    val source: MobilitySource,
)

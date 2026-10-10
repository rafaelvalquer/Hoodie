package com.hoodie.app.core.mobility

/** Nível de evidência apresentado à Home; não altera a confirmação do Diário. */
enum class ModeCertainty { UNKNOWN, PROVISIONAL, CONFIRMED, USER_SELECTED, PREFERRED }

enum class MobilityPhase { IDLE, CANDIDATE, ACTIVE, ARRIVING }

/** Estado mínimo e sem coordenadas para escolher a cena do deslocamento atual. */
data class MobilityVisualSnapshot(
    val sessionId: Long? = null,
    val mode: MovementMode? = null,
    val observedMovement: DetectedMovement? = null,
    val confidence: Float = 0f,
    val certainty: ModeCertainty = ModeCertainty.UNKNOWN,
    val source: MobilitySource? = null,
    val observedAt: Long? = null,
    val lastVehicleAt: Long? = null,
    val vehicleExitAt: Long? = null,
    val phase: MobilityPhase = MobilityPhase.IDLE,
    val speedSampleAttempts: Int = 0,
    val speedSampleCount: Int = 0,
    val meanSpeedKmh: Float? = null,
    val maxSpeedKmh: Float? = null,
)

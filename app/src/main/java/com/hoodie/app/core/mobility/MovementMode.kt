package com.hoodie.app.core.mobility

/**
 * Como a pessoa está se deslocando. Fica separado de UserContextType: o contexto
 * continua COMMUTING e o modo diz se é caminhada, ônibus, carro…
 */
enum class MovementMode(val emoji: String, val label: String, val verb: String) {
    NONE("📍", "Parado", "Parado"),
    WALKING("🚶", "Caminhada", "Caminhou"),
    RUNNING("🏃", "Corrida", "Correu"),
    BICYCLE("🚲", "Bicicleta", "Pedalou"),
    VEHICLE_UNKNOWN("🚘", "Veículo", "Em um veículo"),
    CAR("🚗", "Carro", "Foi de carro"),
    BUS("🚌", "Ônibus", "Pegou ônibus"),
    TRAIN("🚆", "Trem", "Pegou trem"),
    METRO("🚇", "Metrô", "Pegou metrô"),
    PUBLIC_TRANSPORT("🚍", "Transporte público", "Pegou transporte"),
    OTHER("🛴", "Outro", "Outro meio");

    /** Algum tipo de veículo (com ou sem classificação). */
    val isVehicle: Boolean get() = this in VEHICLES

    /** Deslocamento a pé ou de bicicleta (o próprio corpo se move). */
    val isActive: Boolean get() = this == WALKING || this == RUNNING || this == BICYCLE

    companion object {
        val VEHICLES = setOf(VEHICLE_UNKNOWN, CAR, BUS, TRAIN, METRO, PUBLIC_TRANSPORT)

        /** Opções da pergunta "como está se deslocando?". */
        val TRANSPORT_CHOICES = listOf(CAR, BUS, TRAIN, METRO, BICYCLE, OTHER)

        fun parse(name: String?): MovementMode? = entries.firstOrNull { it.name == name }
    }
}

/** Estado da máquina de mobilidade (núcleo do MobilityEngine). */
enum class MobilityState {
    STATIONARY,
    MOVEMENT_CANDIDATE,
    WALKING,
    IN_VEHICLE,
    ARRIVING,
    ARRIVED,
}

/** O que o Android reconhece (Activity Recognition / Activity Transition API). */
enum class DetectedMovement {
    STILL,
    WALKING,
    RUNNING,
    ON_BICYCLE,
    IN_VEHICLE,
    UNKNOWN;

    /** Modo inicial correspondente (veículo ainda sem classificação). */
    fun toMode(): MovementMode = when (this) {
        WALKING -> MovementMode.WALKING
        RUNNING -> MovementMode.RUNNING
        ON_BICYCLE -> MovementMode.BICYCLE
        IN_VEHICLE -> MovementMode.VEHICLE_UNKNOWN
        STILL, UNKNOWN -> MovementMode.NONE
    }

    val isMoving: Boolean get() = this != STILL && this != UNKNOWN
}

/** De onde veio a informação de uma sessão/segmento. */
enum class MobilitySource { ACTIVITY_RECOGNITION, GEOFENCE, CONFIRMATION, LEARNED, PREFERENCE, LOCATION_CHECK }

/**
 * Uma leitura do Activity Recognition. [entering] = começou (ENTER); false = terminou (EXIT).
 * Só o tipo de movimento e a hora — nunca posição.
 */
data class MovementObservation(
    val activity: DetectedMovement,
    val confidence: Int,
    val timestamp: Long,
    val entering: Boolean = true,
)

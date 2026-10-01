package com.hoodie.app.core.geofence

import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.location.GeofenceStatusCodes
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType

/**
 * Quais lugares viram geofence. O sistema aceita 100 por app; usamos
 * [HoodieConfig.MAX_ACTIVE_GEOFENCES] e escolhemos os mais importantes.
 */
object GeofenceSelectionPolicy {

    /** 1. Casa  2. Trabalho  3. Academia  4. favoritos  5. mais confirmados  6. visitados há menos tempo. */
    val priority: Comparator<Place> = compareBy<Place> { typeRank(it.type) }
        .thenByDescending { it.isFavorite }
        .thenByDescending { it.confirmationCount }
        .thenByDescending { it.lastVisitedAt ?: Long.MIN_VALUE }
        .thenBy { it.id }

    fun select(places: List<Place>, max: Int = HoodieConfig.MAX_ACTIVE_GEOFENCES): List<Place> =
        places.sortedWith(priority).take(max)

    private fun typeRank(type: PlaceType): Int = when (type) {
        PlaceType.HOME -> 0
        PlaceType.WORK -> 1
        PlaceType.GYM -> 2
        else -> 3
    }
}

enum class GeofenceRegistrationError {
    GEOFENCE_NOT_AVAILABLE,
    GEOFENCE_TOO_MANY_GEOFENCES,
    GEOFENCE_TOO_MANY_PENDING_INTENTS,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    PLAY_SERVICES_ERROR,
    UNKNOWN;

    companion object {
        /** Códigos de status do Play Services → erro do Hoodie. */
        fun fromStatusCode(code: Int): GeofenceRegistrationError = when (code) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> GEOFENCE_NOT_AVAILABLE
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> GEOFENCE_TOO_MANY_GEOFENCES
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> GEOFENCE_TOO_MANY_PENDING_INTENTS
            GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION -> PERMISSION_DENIED
            CommonStatusCodes.API_NOT_CONNECTED, CommonStatusCodes.SERVICE_DISABLED,
            CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED, CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.INTERNAL_ERROR, CommonStatusCodes.DEVELOPER_ERROR,
            -> PLAY_SERVICES_ERROR
            else -> UNKNOWN
        }
    }
}

/** Resultado observável de um registro (Ajustes e Developer Lab mostram isso). */
data class GeofenceRegistrationResult(
    val requested: Int,
    val registered: Int,
    val skipped: Int,
    val error: GeofenceRegistrationError?,
    val activePlaceIds: Set<Long> = emptySet(),
    val at: Long = 0,
) {
    val ok: Boolean get() = error == null

    companion object {
        fun failure(requested: Int, error: GeofenceRegistrationError, at: Long) =
            GeofenceRegistrationResult(requested, 0, requested, error, emptySet(), at)
    }
}

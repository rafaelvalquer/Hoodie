package com.hoodie.app.engine.mobility

import com.hoodie.app.core.model.Place

/** Onde o deslocamento terminou e como isso foi descoberto. */
sealed interface ArrivalResolution {
    /** Geofence ENTER (ou leitura pontual dentro de um lugar conhecido). */
    data class KnownPlace(val place: Place, val byGeofence: Boolean) : ArrivalResolution
    /** Sem posição: o lugar provável pela rotina/histórico (pede confirmação, não muda o contexto). */
    data class Probable(val placeId: Long) : ArrivalResolution
    /** Parou num lugar que o Hoodie não conhece: segue o fluxo atual de lugar novo. */
    data class Unknown(val latitude: Double, val longitude: Double) : ArrivalResolution
    /** Sem como saber agora. */
    data object Unresolved : ArrivalResolution
}

/**
 * Prioridade de resolução da chegada, sem GPS contínuo:
 *
 *     1. geofence ENTER
 *     2. UMA leitura pontual de posição + PlaceRepository.containing()
 *     3. lugar provável pelo histórico/rotina
 *     4. lugar desconhecido
 */
object ArrivalResolver {

    suspend fun resolve(
        geofencePlace: Place?,
        readPosition: suspend () -> Pair<Double, Double>?,
        containing: suspend (Double, Double) -> Place?,
        probablePlaceId: () -> Long?,
    ): ArrivalResolution {
        if (geofencePlace != null) return ArrivalResolution.KnownPlace(geofencePlace, byGeofence = true)
        val pos = readPosition()
        if (pos != null) {
            val known = containing(pos.first, pos.second)
            return if (known != null) ArrivalResolution.KnownPlace(known, byGeofence = false)
            else ArrivalResolution.Unknown(pos.first, pos.second)
        }
        return probablePlaceId()?.let { ArrivalResolution.Probable(it) } ?: ArrivalResolution.Unresolved
    }
}

package com.hoodie.app.core.geofence

import com.hoodie.app.core.model.Place
import com.hoodie.app.core.util.Geo

/** Ephemeral evidence from one location reading; never a stored coordinate history. */
data class PlaceArrivalPosition(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null)

object PlaceArrivalPolicy {
    private const val UNKNOWN_ACCURACY_METERS = 25.0
    private const val MIN_DISTANCE_ADVANTAGE_METERS = 10.0

    /** Ambiguous evidence may retain the current place, but cannot select a new neighbor. */
    fun select(candidates: List<Place>, position: PlaceArrivalPosition?, currentPlaceId: Long? = null): Place? {
        val distinct = candidates.distinctBy { it.id }
        if (position == null) return distinct.firstOrNull { it.id == currentPlaceId } ?: distinct.singleOrNull()
        if (!position.latitude.isFinite() || position.latitude !in -90.0..90.0 ||
            !position.longitude.isFinite() || position.longitude !in -180.0..180.0) return null
        val accuracy = position.accuracyMeters?.takeIf { it.isFinite() && it > 0f }?.toDouble()
            ?: UNKNOWN_ACCURACY_METERS
        val ranked = distinct.map { it to Geo.distanceMeters(position.latitude, position.longitude, it.latitude, it.longitude) }
            .filter { (place, distance) -> distance <= place.radiusMeters }
            .sortedWith(compareBy<Pair<Place, Double>> { it.second }.thenBy { it.first.id })
        if (ranked.isEmpty()) return null
        val current = ranked.firstOrNull { it.first.id == currentPlaceId }?.first
        val nearest = ranked.first()
        if (accuracy > nearest.first.radiusMeters) return current
        if (ranked.size == 1) return nearest.first
        return if (ranked[1].second - nearest.second > 2 * accuracy + MIN_DISTANCE_ADVANTAGE_METERS) nearest.first else current
    }
}

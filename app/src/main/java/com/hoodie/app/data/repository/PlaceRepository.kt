package com.hoodie.app.data.repository

import com.hoodie.app.core.database.PlaceDao
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.error.PlaceException
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.util.Geo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepository @Inject constructor(
    private val dao: PlaceDao,
    private val cipher: CoordinateCipher,
) {
    val places: Flow<List<Place>> = dao.observeAll().map { list -> list.mapNotNull(::toDomain) }

    suspend fun all(): List<Place> = dao.getAll().mapNotNull(::toDomain)

    suspend fun byId(id: Long): Place? = dao.getById(id)?.let(::toDomain)

    suspend fun firstOfType(type: PlaceType): Place? = all().firstOrNull { it.type == type }

    /** Proximity suggests candidates only; callers must ask before reusing a place. */
    suspend fun findNearby(latitude: Double, longitude: Double, maxDistanceMeters: Float): List<Place> {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(maxDistanceMeters.isFinite() && maxDistanceMeters >= 0f)
        return all().map { it to Geo.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
            .filter { it.second <= maxDistanceMeters }
            .sortedWith(compareBy<Pair<Place, Double>> { it.second }.thenBy { it.first.id })
            .map { it.first }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun add(name: String, type: PlaceType, lat: Double, lng: Double, radius: Float, now: Long,
        visitedAt: Long? = now): Place {
        val id = dao.insert(
            PlaceEntity(
                name = name, type = type, encryptedCoordinates = cipher.encrypt(lat, lng),
                radiusMeters = radius, confidence = 1f, createdAt = now, lastVisitedAt = visitedAt,
            ),
        )
        return requireNotNull(byId(id))
    }

    suspend fun update(place: Place) {
        val changed = dao.updateDetails(place.id, place.name, place.type, place.radiusMeters,
            cipher.encrypt(place.latitude, place.longitude))
        if (changed == 0) throw PlaceException.NotFound(place.id)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun markVisited(id: Long, at: Long) = dao.markVisited(id, at)

    /** Lugar conhecido que contém o ponto (para reconciliar um geofence perdido). */
    suspend fun containing(lat: Double, lng: Double, currentPlaceId: Long? = null): Place? = com.hoodie.app.core.geofence.PlaceArrivalPolicy.select(
        all(), com.hoodie.app.core.geofence.PlaceArrivalPosition(lat, lng), currentPlaceId,
    )

    private fun toDomain(e: PlaceEntity): Place? {
        val (lat, lng) = cipher.decrypt(e.encryptedCoordinates) ?: return null
        return Place(e.id, e.name, e.type, lat, lng, e.radiusMeters, e.confirmationCount, e.createdAt, e.lastVisitedAt, e.isFavorite)
    }
}

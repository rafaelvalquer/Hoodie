package com.hoodie.app.data.repository

import com.hoodie.app.core.database.PlaceDao
import com.hoodie.app.core.database.PlaceEntity
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

    suspend fun add(name: String, type: PlaceType, lat: Double, lng: Double, radius: Float, now: Long): Place {
        val id = dao.insert(
            PlaceEntity(
                name = name, type = type, encryptedCoordinates = cipher.encrypt(lat, lng),
                radiusMeters = radius, confidence = 1f, createdAt = now, lastVisitedAt = now,
            ),
        )
        return requireNotNull(byId(id))
    }

    suspend fun update(place: Place) {
        val existing = dao.getById(place.id) ?: return
        dao.update(
            existing.copy(
                name = place.name, type = place.type, radiusMeters = place.radiusMeters,
                encryptedCoordinates = cipher.encrypt(place.latitude, place.longitude),
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun markVisited(id: Long, at: Long) = dao.markVisited(id, at)

    /** Lugar conhecido que contém o ponto (para reconciliar um geofence perdido). */
    suspend fun containing(lat: Double, lng: Double): Place? = all()
        .map { it to Geo.distanceMeters(lat, lng, it.latitude, it.longitude) }
        .filter { (p, d) -> d <= p.radiusMeters }
        .minByOrNull { it.second }?.first

    private fun toDomain(e: PlaceEntity): Place? {
        val (lat, lng) = cipher.decrypt(e.encryptedCoordinates) ?: return null
        return Place(e.id, e.name, e.type, lat, lng, e.radiusMeters, e.confirmationCount, e.createdAt, e.lastVisitedAt, e.isFavorite)
    }
}

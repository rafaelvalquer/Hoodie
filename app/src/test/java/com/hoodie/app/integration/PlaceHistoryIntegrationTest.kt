package com.hoodie.app.integration

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.diary.DiaryAssembler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaceHistoryIntegrationTest {
    @Test fun editingDetailsCannotOverwriteAnArrivalOrFavoriteSavedDuringTheEdit() = runBlocking {
        val g = TestGraph(30_000)
        try {
            val place = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1_000, visitedAt = null)
            g.places.markVisited(place.id, 10_000)
            val before = requireNotNull(g.places.byId(place.id))
            val cipher = object : com.hoodie.app.core.security.CoordinateCipher {
                override fun encrypt(latitude: Double, longitude: Double): String {
                    runBlocking {
                        g.places.markVisited(place.id, 20_000)
                        g.places.setFavorite(place.id, true)
                    }
                    return PlainCipher.encrypt(latitude, longitude)
                }
                override fun decrypt(payload: String) = PlainCipher.decrypt(payload)
            }
            val editor = com.hoodie.app.data.repository.PlaceRepository(g.db.placeDao(), cipher)
            editor.update(before.copy(name = "Cantina da Praça", radiusMeters = 100f))
            assertEquals(before.copy(name = "Cantina da Praça", radiusMeters = 100f,
                lastVisitedAt = 20_000, confirmationCount = 2, isFavorite = true), g.places.byId(place.id))
        } finally { g.close() }
    }

    @Test fun renamingAndDeletingAStorePreservesTheHistoricalIntervalIdentityAndCategory() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val store = g.places.add("Loja original", PlaceType.STORE, 0.0, 0.0, 150f, 1_000, visitedAt = null)
            g.engine.onGeofence(store.id, GeofenceTransition.ENTER, 2_000)
            val eventId = requireNotNull(g.contextDao.current()).id
            g.engine.onGeofence(store.id, GeofenceTransition.EXIT, 8_000)
            val canonical = requireNotNull(g.contextDao.getById(eventId))
            assertEquals(PlaceType.STORE, canonical.venueType)
            val beforeRename = requireNotNull(g.places.byId(store.id))
            g.places.update(beforeRename.copy(name = "Loja renomeada"))
            assertEquals(beforeRename.copy(name = "Loja renomeada"), g.places.byId(store.id))

            val renamed = DiaryAssembler.build(LocalDate.ofEpochDay(0), listOf(canonical), emptyList(),
                emptyList(), g.db.placeDao().getAll(), 0, 86_400_000, 10_000).visits.single()
            assertEquals("Loja renomeada", renamed.placeName)
            assertEquals(store.id, renamed.placeId)
            assertEquals(PlaceType.STORE, renamed.placeType)

            g.places.delete(store.id)
            assertNull(g.places.byId(store.id))
            assertEquals(canonical, g.contextDao.getById(eventId))
            val deleted = DiaryAssembler.build(LocalDate.ofEpochDay(0), listOf(canonical), emptyList(),
                emptyList(), g.db.placeDao().getAll(), 0, 86_400_000, 10_000).visits.single()
            assertEquals(store.id, deleted.placeId)
            assertEquals(PlaceType.STORE, deleted.placeType)
            assertEquals("Loja", deleted.placeName)
            assertEquals(2_000L, deleted.arrivalAt)
            assertEquals(8_000L, deleted.departureAt)
            assertEquals(6_000L, deleted.durationMs)
            assertEquals(canonical.source, deleted.source)
            assertEquals(canonical.confidence, deleted.confidence)
        } finally { g.close() }
    }
}

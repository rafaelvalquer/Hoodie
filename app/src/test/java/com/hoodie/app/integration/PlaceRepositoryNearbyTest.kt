package com.hoodie.app.integration

import com.hoodie.app.core.model.PlaceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaceRepositoryNearbyTest {
    @Test fun delayedArrivalCannotMoveTheLastVisitBackwardsAndManualRegistrationIsNotAVisit() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val place = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 10_000, visitedAt = null)
            assertNull(place.lastVisitedAt)
            assertEquals(0, place.confirmationCount)
            g.places.markVisited(place.id, 8_000)
            g.places.markVisited(place.id, 2_000)
            val saved = g.places.byId(place.id)!!
            assertEquals(10_000L, saved.createdAt)
            assertEquals(8_000L, saved.lastVisitedAt)
            assertEquals(2, saved.confirmationCount)
        } finally { g.close() }
    }

    @Test fun savedRestaurantIsReusedAndRepeatedEnterDwellDoNotCountExtraArrivals() = runBlocking {
        val g = TestGraph(1_000)
        try {
            val place = g.places.add("Cantina da Praça", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1_000)
            g.engine.onGeofence(place.id, com.hoodie.app.engine.context.GeofenceTransition.ENTER, 2_000)
            g.engine.onGeofence(place.id, com.hoodie.app.engine.context.GeofenceTransition.DWELL, 3_000)
            g.engine.onGeofence(place.id, com.hoodie.app.engine.context.GeofenceTransition.ENTER, 4_000)
            assertEquals(1, g.places.byId(place.id)!!.confirmationCount)
            assertEquals(place.id, g.contextDao.current()!!.placeId)

            g.engine.onGeofence(place.id, com.hoodie.app.engine.context.GeofenceTransition.EXIT, 600_000)
            g.engine.onGeofence(place.id, com.hoodie.app.engine.context.GeofenceTransition.ENTER, 1_200_000)
            g.engine.onGeofence(place.id, com.hoodie.app.engine.context.GeofenceTransition.DWELL, 1_201_000)

            assertEquals(2, g.places.byId(place.id)!!.confirmationCount)
            assertEquals(1_200_000L, g.places.byId(place.id)!!.lastVisitedAt)
            assertEquals(place.id, g.contextDao.current()!!.placeId)
            assertEquals(1, g.places.all().size)
            assertTrue(g.db.questionDao().since(0).none { it.kind == com.hoodie.app.core.model.QuestionKind.NEW_PLACE })
        } finally { g.close() }
    }

    @Test fun nearbyKeepsNeighboringRestaurantsDistinctAndSortsByDistance() = runBlocking {
        val g = TestGraph(1_000)
        try {
            val near = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0001, 150f, 1_000)
            val neighbor = g.places.add("Padaria", PlaceType.RESTAURANT, 0.0, 0.0002, 150f, 1_000)
            g.places.add("Distante", PlaceType.RESTAURANT, 0.0, 0.001, 150f, 1_000)
            assertEquals(listOf(near.id, neighbor.id), g.places.findNearby(0.0, 0.0, 30f).map { it.id })
            assertEquals(3, g.places.all().size)
            assertTrue(g.places.findNearby(0.0, 0.0, 5f).isEmpty())
        } finally { g.close() }
    }

    @Test fun favoriteOnlyUpdatesFavoriteAndSurvivesRepositoryRecreation() = runBlocking {
        val g = TestGraph(1_000)
        try {
            val place = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1_000)
            g.places.markVisited(place.id, 2_000)
            val before = g.places.byId(place.id)!!
            g.places.setFavorite(place.id, true)
            val recreated = com.hoodie.app.data.repository.PlaceRepository(g.db.placeDao(), PlainCipher)
            assertEquals(before.copy(isFavorite = true), recreated.byId(place.id))
            recreated.setFavorite(place.id, false)
            assertEquals(before, g.places.byId(place.id))
        } finally { g.close() }
    }

    @Test fun proximityRejectsInvalidCoordinatesAndRadius() = runBlocking {
        val g = TestGraph(1_000)
        try {
            for ((lat, lng, radius) in listOf(Triple(Double.NaN, 0.0, 30f), Triple(91.0, 0.0, 30f),
                    Triple(0.0, 181.0, 30f), Triple(0.0, 0.0, -1f), Triple(0.0, 0.0, Float.NaN))) {
                try {
                    g.places.findNearby(lat, lng, radius)
                    fail("Invalid proximity input accepted")
                } catch (_: IllegalArgumentException) { }
            }
        } finally { g.close() }
    }
}

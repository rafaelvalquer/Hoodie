package com.hoodie.app.integration

import com.hoodie.app.core.geofence.PlaceArrivalPosition
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.engine.context.GeofenceTransition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverlappingPlacesIntegrationTest {
    @Test fun anAmbiguousPointCheckRetainsTheCurrentRestaurantWithoutAskingAgain() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val cantina = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1_000, visitedAt = null)
            g.places.add("Padaria", PlaceType.RESTAURANT, 0.0, .0002, 150f, 1_000, visitedAt = null)
            g.engine.onArrivalGeofences(listOf(cantina.id), GeofenceTransition.ENTER, 2_000,
                PlaceArrivalPosition(0.0, 0.0, 3f))
            val before = g.contextDao.current()
            g.engine.arriveAtPosition(0.0, .0001, askNewPlace = true)
            assertEquals(before, g.contextDao.current())
            assertEquals(1, g.places.byId(cantina.id)!!.confirmationCount)
            assertTrue(g.db.questionDao().since(0).none { it.kind == com.hoodie.app.core.model.QuestionKind.NEW_PLACE })
        } finally { g.close() }
    }

    @Test fun aBatchChoosesOneRestaurantAndRepeatedEnterDwellKeepOneVisit() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val cantina = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1_000, visitedAt = null)
            val padaria = g.places.add("Padaria", PlaceType.RESTAURANT, 0.0, .0002, 150f, 1_000, visitedAt = null)
            val position = PlaceArrivalPosition(0.0, 0.0, 3f)
            assertEquals(cantina.id, g.engine.onArrivalGeofences(listOf(padaria.id, cantina.id), GeofenceTransition.ENTER, 2_000, position))
            g.engine.onArrivalGeofences(listOf(cantina.id, padaria.id), GeofenceTransition.DWELL, 3_000, position)
            assertNull(g.engine.onArrivalGeofences(listOf(padaria.id), GeofenceTransition.ENTER, 4_000))
            assertEquals(cantina.id, g.contextDao.current()!!.placeId)
            assertEquals(1, g.places.byId(cantina.id)!!.confirmationCount)
            assertEquals(0, g.places.byId(padaria.id)!!.confirmationCount)
            assertEquals(2, g.places.all().size)
        } finally { g.close() }
    }

    @Test fun ambiguousArrivalDoesNotInventAPlaceOrAVisit() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val cantina = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1_000, visitedAt = null)
            val padaria = g.places.add("Padaria", PlaceType.RESTAURANT, 0.0, .0002, 150f, 1_000, visitedAt = null)
            assertNull(g.engine.onArrivalGeofences(listOf(cantina.id, padaria.id), GeofenceTransition.ENTER,
                2_000, PlaceArrivalPosition(0.0, .0001, 30f)))
            assertNull(g.contextDao.current()?.placeId)
            assertTrue(g.places.all().all { it.confirmationCount == 0 })
            assertEquals(1, g.scheduler.reconciles)
            assertNull(g.places.containing(0.0, .0001))
        } finally { g.close() }
    }

    @Test fun aSavedPlaceWithoutRegisteredGeofenceRemainsAvailableToAPointCheck() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val cantina = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1_000, visitedAt = null)
            assertEquals(0, g.geofences.registrations)
            g.engine.arriveAtPosition(0.0, 0.0, askNewPlace = true)
            assertEquals(cantina.id, g.contextDao.current()!!.placeId)
            assertEquals(1, g.places.all().size)
            assertTrue(g.db.questionDao().since(0).none { it.kind == com.hoodie.app.core.model.QuestionKind.NEW_PLACE })
        } finally { g.close() }
    }
}

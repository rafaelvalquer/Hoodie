package com.hoodie.app.core.geofence

import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import org.junit.Assert.*
import org.junit.Test

class PlaceArrivalPolicyTest {
    private val cantina = Place(1, "Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 0, 0, null)
    private val neighbor = cantina.copy(id = 2, name = "Padaria", longitude = .0002)

    @Test fun inaccurateOrMissingPositionCannotPickBetweenNeighbors() {
        assertNull(PlaceArrivalPolicy.select(listOf(cantina, neighbor), PlaceArrivalPosition(0.0, 0.0, 25f)))
        assertNull(PlaceArrivalPolicy.select(listOf(neighbor, cantina), PlaceArrivalPosition(0.0, 0.0)))
        assertNull(PlaceArrivalPolicy.select(listOf(cantina, neighbor), null))
        assertEquals(cantina, PlaceArrivalPolicy.select(listOf(cantina), null))
    }

    @Test fun ambiguousEvidenceRetainsTheCurrentPlaceWithoutFollowingListOrder() {
        assertEquals(neighbor, PlaceArrivalPolicy.select(listOf(cantina, neighbor), PlaceArrivalPosition(0.0, .0001, 20f), neighbor.id))
        assertEquals(neighbor, PlaceArrivalPolicy.select(listOf(cantina, neighbor), null, neighbor.id))
    }

    @Test fun reliablePositionCanIdentifyADifferentNeighbor() {
        assertEquals(cantina, PlaceArrivalPolicy.select(listOf(neighbor, cantina), PlaceArrivalPosition(0.0, 0.0, 3f), neighbor.id))
        assertEquals(neighbor, PlaceArrivalPolicy.select(listOf(cantina, neighbor), PlaceArrivalPosition(0.0, .0002, 3f), cantina.id))
    }

    @Test fun aLargeAccuracyRadiusOrInvalidPointCannotSelectANewPlace() {
        assertNull(PlaceArrivalPolicy.select(listOf(cantina), PlaceArrivalPosition(0.0, 0.0, 500f)))
        assertNull(PlaceArrivalPolicy.select(listOf(cantina), PlaceArrivalPosition(Double.NaN, 0.0, 3f)))
        assertNull(PlaceArrivalPolicy.select(listOf(cantina), PlaceArrivalPosition(91.0, 0.0, 3f)))
        assertNull(PlaceArrivalPolicy.select(listOf(cantina), PlaceArrivalPosition(0.1, 0.0, 3f)))
    }

    @Test fun twoDifferentPlacesAtTheSamePointAreNeverMergedOrSelectedArbitrarily() {
        val samePosition = neighbor.copy(longitude = cantina.longitude)
        assertNull(PlaceArrivalPolicy.select(listOf(cantina, samePosition), PlaceArrivalPosition(0.0, 0.0, 1f)))
        assertEquals(cantina, PlaceArrivalPolicy.select(listOf(cantina, samePosition), null, cantina.id))
    }
}

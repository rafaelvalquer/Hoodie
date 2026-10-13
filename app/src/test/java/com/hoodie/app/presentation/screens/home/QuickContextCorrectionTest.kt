package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickContextCorrectionTest {
    private fun place(id: Long, type: PlaceType) = Place(id, "P$id", type, 0.0, 0.0, 150f, 0, 0, null)

    @Test fun `tipos comuns cobrem o basico e deixam o resto em ver todos`() {
        assertEquals(listOf(PlaceType.HOME, PlaceType.WORK, PlaceType.GYM, PlaceType.RESTAURANT, PlaceType.OTHER), COMMON_CORRECTION_TYPES)
        assertTrue(PlaceType.physicalPlaceOptions.containsAll(COMMON_CORRECTION_TYPES))
    }

    @Test fun `varios lugares do mesmo tipo exigem escolher o exato`() {
        val places = listOf(place(1, PlaceType.RESTAURANT), place(2, PlaceType.RESTAURANT), place(3, PlaceType.HOME))
        assertEquals(listOf(1L, 2L), placesOfType(places, PlaceType.RESTAURANT).map { it.id })
        assertEquals(listOf(3L), placesOfType(places, PlaceType.HOME).map { it.id })
        assertTrue(placesOfType(places, PlaceType.GYM).isEmpty())
    }
}
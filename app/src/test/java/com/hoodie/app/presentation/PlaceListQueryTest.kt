package com.hoodie.app.presentation

import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.screens.places.PlaceCategory
import com.hoodie.app.presentation.screens.places.PlaceSort
import com.hoodie.app.presentation.screens.places.queryPlaces
import org.junit.Assert.*
import org.junit.Test

class PlaceListQueryTest {
    private fun place(id: Long, name: String, type: PlaceType, visits: Int = 0,
        at: Long? = null, favorite: Boolean = false) =
        Place(id, name, type, 0.0, 0.0, 150f, visits, 0, at, favorite)

    private val cantina = place(1, "Cantina da Praça", PlaceType.RESTAURANT, 5, 100)
    private val shop = place(2, "Roupas", PlaceType.STORE, 2, 200, true)
    private val home = place(3, "Casa", PlaceType.HOME)
    private val all = listOf(cantina, shop, home)

    @Test fun searchIgnoresCaseAccentsAndSurroundingWhitespace() {
        assertEquals(listOf(cantina), queryPlaces(all, PlaceCategory.ALL, " PRAca ", PlaceSort.NAME))
    }

    @Test fun categoryAndSearchAreAppliedTogether() {
        assertEquals(listOf(cantina), queryPlaces(all, PlaceCategory.RESTAURANTS, "cantina", PlaceSort.NAME))
        assertTrue(queryPlaces(all, PlaceCategory.HOME, "cantina", PlaceSort.NAME).isEmpty())
        assertEquals(all.size, queryPlaces(all, PlaceCategory.ALL, "", PlaceSort.NAME).size)
    }

    @Test fun orderingSupportsFavoritesRecentVisitsAndName() {
        assertEquals(shop, queryPlaces(all, PlaceCategory.ALL, "", PlaceSort.FAVORITES).first())
        assertEquals(listOf(shop, cantina, home), queryPlaces(all, PlaceCategory.ALL, "", PlaceSort.RECENT))
        assertEquals(listOf(cantina, shop, home), queryPlaces(all, PlaceCategory.ALL, "", PlaceSort.MOST_VISITED))
        assertEquals(listOf(cantina, home, shop), queryPlaces(all, PlaceCategory.ALL, "", PlaceSort.NAME))
    }

    @Test fun categoriesCoverEveryPhysicalTypeExactlyOnce() {
        PlaceType.entries.forEach { type ->
            assertEquals("$type", 1, PlaceCategory.entries.count { it != PlaceCategory.ALL && it.contains(type) })
        }
    }
}

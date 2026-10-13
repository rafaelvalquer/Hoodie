package com.hoodie.app.presentation.screens.places

import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import java.text.Normalizer
import java.util.Locale

internal enum class PlaceCategory(val label: String, private val types: Set<PlaceType>?) {
    ALL("Todos", null),
    RESTAURANTS("Restaurantes", setOf(PlaceType.RESTAURANT)),
    STORES("Lojas", setOf(PlaceType.STORE, PlaceType.MARKET)),
    GYMS("Academias", setOf(PlaceType.GYM)),
    WORK("Trabalho", setOf(PlaceType.WORK)),
    HOME("Casa", setOf(PlaceType.HOME)),
    OTHER("Outros", setOf(PlaceType.SCHOOL, PlaceType.FAMILY, PlaceType.LEISURE, PlaceType.OTHER));

    fun contains(type: PlaceType): Boolean = types == null || type in types
}

internal enum class PlaceSort(val label: String) {
    FAVORITES("Favoritos"), RECENT("Visitados recentemente"), MOST_VISITED("Mais visitados"), NAME("Nome")
}

/** Only changes presentation; filtering never changes the saved places or geofences. */
internal fun queryPlaces(
    places: List<Place>, category: PlaceCategory, query: String, sort: PlaceSort,
): List<Place> {
    val term = searchableName(query.trim())
    val byName = compareBy<Place> { searchableName(it.name) }.thenBy { it.id }
    val order = when (sort) {
        PlaceSort.FAVORITES -> compareByDescending<Place> { it.isFavorite }.then(byName)
        PlaceSort.RECENT -> compareByDescending<Place> { it.lastVisitedAt ?: Long.MIN_VALUE }.then(byName)
        PlaceSort.MOST_VISITED -> compareByDescending<Place> { it.confirmationCount }.then(byName)
        PlaceSort.NAME -> byName
    }
    return places.filter { category.contains(it.type) && searchableName(it.name).contains(term) }.sortedWith(order)
}

private fun searchableName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

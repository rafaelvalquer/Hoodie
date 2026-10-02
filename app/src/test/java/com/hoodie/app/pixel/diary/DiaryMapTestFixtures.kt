package com.hoodie.app.pixel.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.PlaceVisit

/** Dia típico: casa → trabalho → restaurante → trabalho → academia → casa. */
object DiaryMapTestFixtures {
    private const val H = 3_600_000L

    fun visit(id: Long?, name: String, type: PlaceType, startH: Int, endH: Int) =
        PlaceVisit(id, name, type, startH * H, endH * H, (endH - startH) * H, 1)

    val typicalDay = listOf(
        visit(1, "Casa", PlaceType.HOME, 6, 8),
        visit(2, "Trabalho", PlaceType.WORK, 9, 12),
        visit(3, "Café", PlaceType.RESTAURANT, 12, 13),
        visit(2, "Trabalho", PlaceType.WORK, 13, 17),
        visit(4, "Academia", PlaceType.GYM, 18, 19),
        visit(1, "Casa", PlaceType.HOME, 20, 23),
    )

    /** Muitos lugares diferentes (mais que terrenos). */
    val crowdedDay = (0 until 20).map { i -> visit(100L + i, "Lugar $i", PlaceType.entries[i % PlaceType.entries.size], i, i + 1) }
}

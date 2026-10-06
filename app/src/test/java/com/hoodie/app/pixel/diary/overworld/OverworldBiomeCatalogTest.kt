package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.domain.diary.model.DiaryMapNodeType
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverworldBiomeCatalogTest {
    @Test fun everyPlaceTypeAndNodeTypeHasABiome() {
        PlaceType.entries.forEach { assertTrue("$it", OverworldBiomeCatalog.biomeFor(it) in BiomeType.entries) }
        DiaryMapNodeType.entries.forEach { assertTrue("$it", OverworldBiomeCatalog.biomeFor(it) in BiomeType.entries) }
        assertEquals("desconhecido vira acampamento", BiomeType.CAMP, OverworldBiomeCatalog.biomeFor(null as PlaceType?))
        assertEquals(BiomeType.CAMP, OverworldBiomeCatalog.biomeFor(PlaceType.OTHER))
        assertEquals(BiomeType.HOUSE, OverworldBiomeCatalog.biomeFor(PlaceType.HOME))
        assertEquals(BiomeType.OFFICE_CASTLE, OverworldBiomeCatalog.biomeFor(PlaceType.WORK))
    }

    @Test fun shortRestaurantOrCoffeeIsTheCafeAndRealMealIsTheTavern() {
        assertEquals(BiomeType.CAFE, BiomeType.of(PlaceType.RESTAURANT, 15 * 60_000L))
        assertEquals(BiomeType.CAFE, BiomeType.of(PlaceType.RESTAURANT, 90 * 60_000L, HoodieActivity.COFFEE))
        assertEquals(BiomeType.TAVERN, BiomeType.of(PlaceType.RESTAURANT, 50 * 60_000L))
    }

    @Test fun everyBiomeHasItsOwnBuildingInAllStatesDayAndNight() {
        val renders = BiomeType.entries.map { b ->
            BiomeState.entries.flatMap { s ->
                listOf(false, true).map { night ->
                    PixelBuffer(32, 32).also { OverworldBiomeCatalog.paint(it, b, 0, 0, s, night, 1_000) }.pixels.toList()
                }
            }
        }
        renders.forEach { states -> states.forEach { assertTrue("pinta algo", it.count { p -> p ushr 24 != 0 } > 60) } }
        // Construções diferentes entre si…
        assertEquals(BiomeType.entries.size, renders.map { it[2] }.toSet().size)
        // …e estados diferentes (futura ≠ visitada ≠ fantasma).
        renders.forEach { s -> assertNotEquals(s[0], s[2]); assertNotEquals(s[2], s[6]) }
    }

    @Test fun ghostAndFutureAreTransformsOfThePaletteNotNewColors() {
        val c = OverworldPalette.ROOF_RED
        assertNotEquals(c, OverworldPalette.state(c, BiomeState.FUTURE))
        assertEquals(c, OverworldPalette.state(c, BiomeState.VISITED))
        val g = OverworldPalette.state(c, BiomeState.GHOST)
        assertEquals(0xFF, g ushr 24)
        assertFalse(g == c)
    }

    @Test fun everyMovementModeHasColorAndPattern() {
        val styles = com.hoodie.app.core.mobility.MovementMode.entries.map { OverworldPalette.trail(it) } + OverworldPalette.trail(null)
        assertTrue(styles.all { it.width >= 5 })
        val walk = OverworldPalette.trail(com.hoodie.app.core.mobility.MovementMode.WALKING)
        val bike = OverworldPalette.trail(com.hoodie.app.core.mobility.MovementMode.BICYCLE)
        val car = OverworldPalette.trail(com.hoodie.app.core.mobility.MovementMode.CAR)
        val bus = OverworldPalette.trail(com.hoodie.app.core.mobility.MovementMode.BUS)
        val train = OverworldPalette.trail(com.hoodie.app.core.mobility.MovementMode.TRAIN)
        // Padrão distinto (acessível sem depender só da cor).
        assertEquals(5, setOf(walk.pattern, bike.pattern, car.pattern, bus.pattern, train.pattern).size)
    }
}

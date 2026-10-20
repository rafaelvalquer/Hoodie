package com.hoodie.app.pixel.venue

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada tipo de lugar abre a cena certa — só no contexto compatível e com a cena liberada. */
class VenueSceneRoutingTest {
    private val all = setOf(SceneId.BAKERY, SceneId.MALL, SceneId.BEACH, SceneId.CINEMA, SceneId.PHARMACY, SceneId.HOTEL)

    private fun scene(type: PlaceType?, context: UserContextType = type?.toContext() ?: UserContextType.UNKNOWN, venues: Set<SceneId> = all, activity: HoodieActivity = HoodieActivity.IDLE) =
        VisualDirector.resolve(activity, context, shoppingVenue = type, venueScenes = venues).scene

    @Test fun eachPlaceTypeOpensItsScene() {
        mapOf(
            PlaceType.BAKERY to SceneId.BAKERY, PlaceType.MALL to SceneId.MALL, PlaceType.BEACH to SceneId.BEACH,
            PlaceType.CINEMA to SceneId.CINEMA, PlaceType.PHARMACY to SceneId.PHARMACY, PlaceType.HOTEL to SceneId.HOTEL,
        ).forEach { (type, expected) ->
            HoodieActivity.entries.forEach { a -> assertEquals("$type $a", expected, scene(type, activity = a)) }
            assertEquals(expected, VisualDirector.specificScene(type))
        }
    }

    @Test fun mappingFollowsThePlan() {
        assertEquals(UserContextType.DINING, PlaceType.BAKERY.toContext())
        assertEquals(UserContextType.SHOPPING, PlaceType.MALL.toContext())
        assertEquals(UserContextType.LEISURE, PlaceType.BEACH.toContext())
        assertEquals(UserContextType.LEISURE, PlaceType.CINEMA.toContext())
        assertEquals(UserContextType.SHOPPING, PlaceType.PHARMACY.toContext())
        assertEquals(UserContextType.TRAVEL, PlaceType.HOTEL.toContext())
        // A padaria também serve o almoço; o shopping também é passeio.
        assertEquals(SceneId.BAKERY, scene(PlaceType.BAKERY, UserContextType.LUNCH))
        assertEquals(SceneId.MALL, scene(PlaceType.MALL, UserContextType.LEISURE))
    }

    /** Desligada, cada cena cai na do contexto: nada quebra para quem já cadastrou o lugar. */
    @Test fun disabledScenesFallBackToTheContextScene() {
        assertEquals(SceneId.RESTAURANT, scene(PlaceType.BAKERY, venues = emptySet()))
        assertEquals(SceneId.SHOPPING, scene(PlaceType.MALL, venues = emptySet()))
        assertEquals(SceneId.SHOPPING, scene(PlaceType.PHARMACY, venues = emptySet()))
        assertEquals(SceneId.LEISURE, scene(PlaceType.BEACH, venues = emptySet()))
        assertEquals(SceneId.LEISURE, scene(PlaceType.CINEMA, venues = emptySet()))
        assertEquals(SceneId.GENERIC_OUTDOOR, scene(PlaceType.HOTEL, venues = emptySet()))
        // Só uma liberada: as outras continuam no fallback.
        assertEquals(SceneId.BAKERY, scene(PlaceType.BAKERY, venues = setOf(SceneId.BAKERY)))
        assertEquals(SceneId.LEISURE, scene(PlaceType.BEACH, venues = setOf(SceneId.BAKERY)))
    }

    @Test fun incompatibleContextKeepsTheContextScene() {
        assertEquals(SceneId.OFFICE, scene(PlaceType.BAKERY, UserContextType.WORK))
        assertEquals(SceneId.HOME, scene(PlaceType.HOTEL, UserContextType.HOME))
        assertEquals(SceneId.SCHOOL, scene(PlaceType.CINEMA, UserContextType.STUDY))
        assertNull(VisualDirector.venueScene(UserContextType.GYM, PlaceType.BEACH, all))
    }

    /** "Outro" e lugar desconhecido nunca escolhem uma das cenas novas ao acaso. */
    @Test fun otherNeverPicksAVenue() {
        UserContextType.entries.forEach { ctx ->
            assertNull(VisualDirector.venueScene(ctx, PlaceType.OTHER, all))
            assertNull(VisualDirector.venueScene(ctx, null, all))
        }
        // Mercado e Loja seguem como antes.
        assertEquals(SceneId.STORE, scene(PlaceType.STORE))
        assertEquals(SceneId.SHOPPING, scene(PlaceType.MARKET))
    }

    /** Um deslocamento real tem prioridade sobre a visita anterior. */
    @Test fun commuteWinsOverThePreviousVisit() {
        val s = VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, mobilityMode = MovementMode.BUS, shoppingVenue = PlaceType.BAKERY, venueScenes = all).scene
        assertNotEquals(SceneId.BAKERY, s)
    }

    /** O tipo do lugar só fica guardado no evento quando escolhe uma cena. */
    @Test fun venueTypeIsKeptOnlyWhereItChoosesAScene() {
        assertEquals(PlaceType.BAKERY, PlaceType.venueFor(UserContextType.DINING, PlaceType.BAKERY))
        assertEquals(PlaceType.HOTEL, PlaceType.venueFor(UserContextType.TRAVEL, PlaceType.HOTEL))
        assertNull(PlaceType.venueFor(UserContextType.WORK, PlaceType.BAKERY))
        assertNull(PlaceType.venueFor(UserContextType.LEISURE, PlaceType.LEISURE))
        assertNull(PlaceType.venueFor(UserContextType.DINING, PlaceType.RESTAURANT))
        // Compras guardam qualquer tipo, como antes (Mercado/Loja).
        assertEquals(PlaceType.MARKET, PlaceType.venueFor(UserContextType.SHOPPING, PlaceType.MARKET))
    }

    @Test fun everyVenueSceneIsRegisteredWithItsSpots() {
        // A sala da família usa o motor de venues, mas não é um estabelecimento (vem do contexto VISITING).
        VenueTestFixtures.scenes.filter { it != SceneId.FAMILY }.forEach { id ->
            val scene = SceneRegistry[id]
            assertEquals(id, scene.id)
            HoodieActivity.entries.forEach { a ->
                val v = VisualDirector.resolve(a, UserContextType.UNKNOWN).copy(scene = id)
                val state = VisualDirector.resolve(a, contextOf(id), shoppingVenue = typeOf(id), venueScenes = all)
                assertTrue("$id $a: spot ${state.spot}", state.spot in scene.spots)
                state.actions.mapNotNull { it.spot }.forEach { assertTrue("$id $a: ação em ${it}", it in scene.spots) }
                state.approachSpot?.let { assertTrue("$id $a: antecipação em $it", it in scene.spots) }
                assertTrue(v.actions.isNotEmpty())
            }
        }
    }

    private fun typeOf(id: SceneId) = PlaceType.entries.first { VisualDirector.specificScene(it) == id }
    private fun contextOf(id: SceneId) = typeOf(id).toContext()
}

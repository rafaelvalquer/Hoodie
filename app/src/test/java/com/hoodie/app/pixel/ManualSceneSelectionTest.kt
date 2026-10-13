package com.hoodie.app.pixel

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Escolha manual em "O que estou fazendo?": cada opção do catálogo abre a cena do que foi escolhido,
 * pelo mesmo caminho da Home (contexto do tipo + tipo guardado no evento + escolha explícita).
 */
class ManualSceneSelectionTest {
    private val expected = mapOf(
        PlaceType.HOME to SceneId.HOME,
        PlaceType.WORK to SceneId.OFFICE,
        PlaceType.GYM to SceneId.GYM,
        PlaceType.SCHOOL to SceneId.SCHOOL,
        PlaceType.RESTAURANT to SceneId.RESTAURANT,
        PlaceType.MARKET to SceneId.SHOPPING,
        PlaceType.STORE to SceneId.STORE,
        PlaceType.FAMILY to SceneId.FAMILY,
        PlaceType.LEISURE to SceneId.LEISURE,
        PlaceType.OTHER to SceneId.UNKNOWN,
        PlaceType.BAKERY to SceneId.BAKERY,
        PlaceType.MALL to SceneId.MALL,
        PlaceType.BEACH to SceneId.BEACH,
        PlaceType.CINEMA to SceneId.CINEMA,
        PlaceType.PHARMACY to SceneId.PHARMACY,
        PlaceType.HOTEL to SceneId.HOTEL,
    )

    /** O que a Home vê depois de ManualContextHandler.setManualPlace(type). */
    private fun manualScene(type: PlaceType, explicit: Boolean = true): SceneId {
        val context = type.toContext()
        val stored = PlaceType.venueFor(context, type)
        val activity = if (context == UserContextType.WORK) HoodieActivity.WORKING else HoodieActivity.IDLE
        return VisualDirector.resolve(activity, context, shoppingVenue = stored, manualTransition = explicit).scene
    }

    @Test
    fun `toda opcao do catalogo tem cena esperada`() {
        assertEquals(PlaceType.physicalPlaceOptions.toSet(), expected.keys)
    }

    @Test
    fun `escolha manual abre a cena real do lugar escolhido`() {
        PlaceType.physicalPlaceOptions.forEach { type ->
            assertEquals("escolheu $type", expected.getValue(type), manualScene(type))
        }
    }

    @Test
    fun `lugares com cena propria guardam o tipo no evento escolhido`() {
        listOf(PlaceType.BAKERY, PlaceType.MALL, PlaceType.BEACH, PlaceType.CINEMA, PlaceType.PHARMACY, PlaceType.HOTEL, PlaceType.STORE).forEach { type ->
            assertEquals(type, PlaceType.venueFor(type.toContext(), type))
        }
    }

    @Test
    fun `deteccao automatica continua respeitando as flags de liberacao`() {
        val fallback = mapOf(
            PlaceType.BAKERY to SceneId.RESTAURANT, PlaceType.MALL to SceneId.SHOPPING, PlaceType.BEACH to SceneId.LEISURE,
            PlaceType.CINEMA to SceneId.LEISURE, PlaceType.PHARMACY to SceneId.SHOPPING, PlaceType.HOTEL to SceneId.GENERIC_OUTDOOR,
        )
        fallback.forEach { (type, generic) ->
            val scene = expected.getValue(type)
            val automatic = manualScene(type, explicit = false)
            if (scene in VisualDirector.enabledVenueScenes) assertEquals(scene, automatic)
            else assertEquals("$type sem flag", generic, automatic)
        }
        // Um deslocamento real continua valendo mais que a visita (nada muda fora do estabelecimento).
        assertTrue(VisualDirector.venueScenesFor(false).all { it in VisualDirector.allVenueScenes })
    }
}

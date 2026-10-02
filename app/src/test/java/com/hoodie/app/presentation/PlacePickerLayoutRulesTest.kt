package com.hoodie.app.presentation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.screens.places.picker.PlacePickerDimensions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerFocus
import com.hoodie.app.presentation.screens.places.picker.focusAfter
import com.hoodie.app.presentation.screens.places.picker.selectedAddressText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras puras da tela Novo Local: altura do mapa, foco e texto do local selecionado. */
class PlacePickerLayoutRulesTest {

    private fun map(h: Dp, font: Float = 1f, focus: PlacePickerFocus = PlacePickerFocus.NONE) =
        PlacePickerDimensions.forHeight(h, font, focus).mapHeight

    @Test
    fun `mapa nunca domina a tela`() {
        assertEquals(190.dp, map(640.dp))
        assertEquals(220.dp, map(700.dp))
        assertEquals(240.dp, map(800.dp))
        assertEquals(240.dp, map(873.dp))
        assertEquals(240.dp, map(891.dp))
        assertEquals(260.dp, map(960.dp))
        listOf(560.dp, 640.dp, 800.dp, 873.dp, 1200.dp).forEach { h ->
            listOf(1f, 1.3f).forEach { f ->
                val m = map(h, f)
                assertTrue("$h/$f: $m", m >= PlacePickerDimensions.MAP_MIN_HEIGHT && m <= PlacePickerDimensions.MAP_MAX_HEIGHT)
            }
        }
    }

    @Test
    fun `fonte grande cede espaco do mapa para o formulario`() {
        assertEquals(220.dp, map(873.dp, 1.3f))
        assertEquals(PlacePickerDimensions.MAP_MIN_HEIGHT, map(640.dp, 1.3f))
    }

    @Test
    fun `digitando o mapa reduz e some em telas pequenas`() {
        assertEquals(0.dp, map(640.dp, focus = PlacePickerFocus.SEARCH))
        assertEquals(0.dp, map(640.dp, focus = PlacePickerFocus.NAME))
        assertEquals(PlacePickerDimensions.MAP_TYPING_MAX, map(873.dp, focus = PlacePickerFocus.SEARCH))
        assertEquals(PlacePickerDimensions.MAP_TYPING_MAX, map(891.dp, focus = PlacePickerFocus.NAME))
    }

    @Test
    fun `teclado aberto encolhe o mapa ate sumir`() {
        assertEquals(0.dp, map(380.dp))
        assertEquals(120.dp, map(440.dp))
        assertEquals(PlacePickerDimensions.MAP_TYPING_MAX, map(520.dp))
    }

    @Test
    fun `foco de um campo nao apaga o do outro`() {
        var f = PlacePickerFocus.NONE
        f = focusAfter(f, PlacePickerFocus.SEARCH, true)
        assertEquals(PlacePickerFocus.SEARCH, f)
        // Toque no nome: o nome ganha o foco antes de a busca avisar que perdeu.
        f = focusAfter(f, PlacePickerFocus.NAME, true)
        f = focusAfter(f, PlacePickerFocus.SEARCH, false)
        assertEquals(PlacePickerFocus.NAME, f)
        f = focusAfter(f, PlacePickerFocus.NAME, false)
        assertEquals(PlacePickerFocus.NONE, f)
    }

    @Test
    fun `estado inicial explica como escolher o local`() {
        assertEquals(
            "Busque um endereço, use sua localização ou ajuste o pino no mapa.",
            selectedAddressText(null, -23.5, -46.6, hasPoint = false),
        )
        assertEquals("-23.50000, -46.60000", selectedAddressText(null, -23.5, -46.6, hasPoint = true))
        assertEquals("Rua Santa Teresa, 123 - Sé", selectedAddressText("Rua Santa Teresa, 123 - Sé", 0.0, 0.0, true))
    }
}

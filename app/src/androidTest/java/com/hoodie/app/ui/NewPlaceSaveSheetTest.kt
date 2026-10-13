package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.model.ContextQuestion
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.engine.context.NewPlaceSaveOptions
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelRenderFrame
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.presentation.screens.home.NewPlaceSaveSheet
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class NewPlaceSaveSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val cantina = Place(1, "Cantina da Praça", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1, 0, 0)
    private val padaria = cantina.copy(id = 2, name = "Padaria")

    @Test fun nameIsOptionalAndTypedNameIsForwardedToSave() {
        var saved: String? = null
        rule.setContent {
            HoodieTheme {
                NewPlaceSaveSheet(1, PlaceType.RESTAURANT, NewPlaceSaveOptions(1, PlaceType.RESTAURANT, true, emptyList()),
                    false, onSave = { name, id, position -> saved = name; assertNull(id); assertNull(position) },
                    onToday = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("Salvar", ignoreCase = true).assertIsEnabled()
        rule.onNodeWithText("Nome (opcional)").performTextInput("Cantina da Praça")
        rule.onNodeWithText("Salvar", ignoreCase = true).performScrollTo().performClick()
        rule.runOnIdle { assertEquals("Cantina da Praça", saved) }
    }

    @Test fun nearbyPlacesRequireExplicitSelectionAndAllowCreatingADistinctPlace() {
        var reused: Long? = null
        rule.setContent {
            HoodieTheme {
                NewPlaceSaveSheet(1, PlaceType.RESTAURANT, NewPlaceSaveOptions(1, PlaceType.RESTAURANT, true, listOf(cantina, padaria)),
                    false, onSave = { _, id, _ -> reused = id }, onToday = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("Salvar", ignoreCase = true).assertExists()
        rule.onNodeWithText("Padaria · Restaurante").performScrollTo().performClick()
        rule.onNodeWithText("Reutilizar Padaria", ignoreCase = true).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(2L, reused) }
        rule.onNodeWithText("É outro lugar — criar novo cadastro").performScrollTo().performClick()
        rule.onNodeWithText("Salvar", ignoreCase = true).assertExists()
    }

    @Test fun onlyTodayDoesNotRequireLocationAndDoesNotCallSave() {
        var today = 0
        var saves = 0
        rule.setContent {
            HoodieTheme {
                NewPlaceSaveSheet(1, PlaceType.RESTAURANT, NewPlaceSaveOptions(1, PlaceType.RESTAURANT, false, emptyList()),
                    false, onSave = { _, _, _ -> saves++ }, onToday = { today++ }, onDismiss = {})
            }
        }
        rule.onNodeWithText("Salvar", ignoreCase = true).assertIsNotEnabled()
        rule.onNodeWithText("Somente hoje", ignoreCase = true).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, today); assertEquals(0, saves) }
    }

    @Test fun manualLocationEnablesSaveOnlyAfterValidCoordinates() {
        var saved: Pair<Double, Double>? = null
        var searched: Pair<Double, Double>? = null
        rule.setContent {
            HoodieTheme {
                NewPlaceSaveSheet(1, PlaceType.RESTAURANT, NewPlaceSaveOptions(1, PlaceType.RESTAURANT, false, emptyList()),
                    false, onSave = { _, _, position -> saved = position }, onToday = {},
                    onPositionChange = { searched = it }, onDismiss = {})
            }
        }
        rule.onNodeWithText("Salvar", ignoreCase = true).assertIsNotEnabled()
        rule.onNodeWithText("Coordenadas: latitude, longitude").performTextInput("-23.55, -46.63")
        rule.onNodeWithText("Salvar", ignoreCase = true).performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(-23.55 to -46.63, saved); assertEquals(saved, searched) }
    }

    @Test fun savingDisablesActionsAtLargeFontScale() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                HoodieTheme {
                    NewPlaceSaveSheet(1, PlaceType.RESTAURANT, NewPlaceSaveOptions(1, PlaceType.RESTAURANT, true, emptyList()),
                        true, onSave = { _, _, _ -> fail("Saving twice") }, onToday = { fail("Busy") }, onDismiss = {})
                }
            }
        }
        rule.onNodeWithText("Salvar", ignoreCase = true).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText("Somente hoje", ignoreCase = true).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun movingVehicleHidesPlaceDiscoveryAndSavePrompts() {
        rule.setContent {
            HoodieTheme {
                CompositionLocalProvider(LocalPixelRenderFrame provides PixelRenderFrame()) {
                    HomeContent(HomeUiState(loading = false, vehicleMoving = true,
                        question = ContextQuestion(1, QuestionKind.SAVE_PLACE, null, null, PlaceType.RESTAURANT, null, 0, null, null)),
                        false, ZoneId.of("UTC"), onOpen = {})
                }
            }
        }
        rule.onNodeWithTag("new_place_save_sheet").assertDoesNotExist()
        rule.onNodeWithText("Guardar lugar").assertDoesNotExist()
        rule.onNodeWithText("Somente hoje", ignoreCase = true).assertDoesNotExist()
    }
}

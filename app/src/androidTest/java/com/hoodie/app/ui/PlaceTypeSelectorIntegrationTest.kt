package com.hoodie.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.presentation.screens.home.HomeActions
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.presentation.screens.home.ManualPlaceOptions
import com.hoodie.app.presentation.screens.places.AddPlaceDialog
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Confirma que as opções reais de seleção manual e cadastro vêm do mesmo catálogo físico. */
@RunWith(AndroidJUnit4::class)
class PlaceTypeSelectorIntegrationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun selecao_manual_da_home_exibe_todos_os_tipos_fisicos() {
        rule.setContent {
            HoodieTheme {
                ManualPlaceOptions(onSelect = {})
            }
        }
        PlaceType.physicalPlaceOptions.forEach { type ->
            val label = "${type.emoji} ${type.label}".uppercase()
            assertTrue("Opção ausente na Home: $label", rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test fun dialogo_novo_lugar_exibe_o_mesmo_catalogo_fisico() {
        rule.setContent {
            HoodieTheme {
                AddPlaceDialog(onDismiss = {}, onHere = { _, _ -> }, onManual = { _, _, _ -> }, onMap = {})
            }
        }
        PlaceType.physicalPlaceOptions.forEach { type ->
            val label = "${type.emoji} ${type.label}"
            assertTrue("Opção ausente em Novo lugar: $label", rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test fun selecionar_restaurante_fecha_seletor_e_mostra_contexto_e_cena_corretos() {
        rule.mainClock.autoAdvance = false
        var selected: PlaceType? = null
        var selectedContext: UserContextType? = null
        var selectedScene: SceneId? = null
        rule.setContent {
            var state by remember { mutableStateOf(
                HomeUiState(
                    loading = false,
                    now = 1L,
                    context = ContextEvent(type = UserContextType.HOME, startedAt = 1L, endedAt = null, confidence = 1f, placeId = null, source = ContextSource.MANUAL),
                    visual = VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.HOME),
                ),
            ) }
            HoodieTheme {
                HomeContent(
                    state = state,
                    busy = false,
                    zone = java.time.ZoneId.of("America/Sao_Paulo"),
                    onOpen = {},
                    actions = HomeActions(setManual = { place ->
                        selected = place
                        val context = place.toContext()
                        selectedContext = context
                        val visual = VisualDirector.resolve(HoodieActivity.EATING, context)
                        selectedScene = visual.scene
                        state = state.copy(
                            context = state.context!!.copy(type = context),
                            visual = visual,
                        )
                    }),
                )
            }
        }

        rule.mainClock.advanceTimeBy(100L)
        rule.scrollWithPausedClock(rule.onNodeWithText(rule.activity.getString(com.hoodie.app.R.string.ui_home_screen_4).uppercase())).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000L)
        rule.scrollWithPausedClock(rule.onNodeWithText("🍽 RESTAURANTE")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000L)
        rule.runOnIdle {
            assertEquals(PlaceType.RESTAURANT, selected)
            assertEquals(UserContextType.DINING, selectedContext)
            assertEquals(SceneId.RESTAURANT, selectedScene)
        }
        rule.onNodeWithText("🍽 Restaurante").assertExists()
        rule.onAllNodesWithText("🍽 RESTAURANTE").assertCountEquals(0)
    }
}

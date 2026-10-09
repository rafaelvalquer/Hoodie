package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.home.HomeNowAssembler
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.presentation.screens.routine.RoutineDayExceptionError
import com.hoodie.app.presentation.screens.routine.RoutineDayExceptionSection
import com.hoodie.app.presentation.screens.routine.RoutineDayExceptionUiState
import com.hoodie.app.presentation.screens.routine.TAG_DAY_OFF_SWITCH
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RoutineDayExceptionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 9)

    private fun show(
        state: RoutineDayExceptionUiState,
        onChange: (Boolean) -> Unit = {},
        onConfirm: () -> Unit = {},
        onCancel: () -> Unit = {},
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                HoodieTheme {
                    Column {
                        RoutineDayExceptionSection(state, onChange, onConfirm, onCancel)
                    }
                }
            }
        }
    }

    @Test fun initialStateShowsPersistedNoAndAllowsChangingIt() {
        var changed: Boolean? = null
        show(RoutineDayExceptionUiState(date, isDayOff = false, loading = false), onChange = { changed = it })
        rule.onNodeWithText("EXCEÇÕES DA ROTINA").assertIsDisplayed()
        rule.onNodeWithText("Hoje é dia de folga: NÃO").assertIsDisplayed()
        rule.onNodeWithText("Informe aqui quando não for seguir sua rotina habitual de trabalho.").assertIsDisplayed()
        rule.onNodeWithTag(TAG_DAY_OFF_SWITCH).performClick()
        assertEquals(true, changed)
    }

    @Test fun savingDisablesTheSwitchAndShowsProgress() {
        show(RoutineDayExceptionUiState(date, loading = false, saving = true))
        rule.onNodeWithTag(TAG_DAY_OFF_SWITCH).assertIsNotEnabled()
        rule.onNodeWithText("Salvando…").assertIsDisplayed()
    }

    @Test fun workWarningRequiresAnExplicitChoice() {
        var confirmed = false
        var cancelled = false
        show(
            RoutineDayExceptionUiState(date, loading = false, confirmationRequired = true),
            onConfirm = { confirmed = true },
            onCancel = { cancelled = true },
        )
        rule.onNodeWithText("Já há trabalho registrado hoje").assertIsDisplayed()
        rule.onNodeWithText("Marcar folga altera apenas a rotina prevista e mantém esses registros. Caso estejam incorretos, utilize a correção do Diário.").assertIsDisplayed()
        rule.onNodeWithText("Marcar folga").performClick()
        assertTrue(confirmed)
        assertFalse(cancelled)
    }

    @Test fun saveErrorKeepsThePersistedOffValueAndDisplaysAnError() {
        show(RoutineDayExceptionUiState(date, isDayOff = false, loading = false, error = RoutineDayExceptionError.SAVE))
        rule.onNodeWithText("Não foi possível salvar a folga. A configuração anterior foi restaurada.").assertIsDisplayed()
        rule.onNodeWithTag(TAG_DAY_OFF_SWITCH).assertIsOff()
    }

    @Test fun exceptionTextRemainsAvailableAtLargeFontScale() {
        show(RoutineDayExceptionUiState(date, isDayOff = true, loading = false), fontScale = 1.3f)
        rule.onNodeWithText("Hoje é dia de folga: SIM").assertIsDisplayed()
        rule.onNodeWithText("Informe aqui quando não for seguir sua rotina habitual de trabalho.").assertIsDisplayed()
    }

    @Test fun homeNeverOffersDayOffActionsWhileTheUserIsAtWork() {
        // The live Hoodie scene animates continuously; freeze Compose's test clock so
        // this assertion observes the Home controls without waiting for the scene.
        rule.mainClock.autoAdvance = false
        val context = ContextEvent(10, UserContextType.WORK, 1, null, 1f, 2, ContextSource.USER_CORRECTION)
        val snapshot = HomeNowAssembler.assemble(null, context, "Escritório", null, null, 2)
        rule.setContent {
            HoodieTheme {
                HomeContent(
                    HomeUiState(loading = false, now = 2, context = context),
                    busy = false,
                    zone = ZoneId.of("America/Sao_Paulo"),
                    onOpen = {},
                    homeNow = snapshot,
                )
            }
        }
        rule.onNodeWithText("Trabalhando").assertIsDisplayed()
        rule.onAllNodesWithText("Hoje não vou trabalhar").assertCountEquals(0)
        rule.onAllNodesWithText("Voltar a trabalhar hoje").assertCountEquals(0)
    }
}

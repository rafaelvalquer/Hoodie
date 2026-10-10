package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.domain.daystate.DayStateReason
import com.hoodie.app.domain.daystate.DayStateSnapshot
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.dayreport.DailyReport
import com.hoodie.app.domain.dayreport.DayReportStatus
import com.hoodie.app.domain.dayreport.DayReportStop
import com.hoodie.app.presentation.screens.home.HomeNowCard
import com.hoodie.app.presentation.screens.home.HomeNowCorrectionSheet
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.engine.home.HomeNowAssembler
import com.hoodie.app.presentation.screens.diary.DayReportSection
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelRenderFrame
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DayPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun homeNowShowsEvidenceConfidenceWithoutConfirmationActionsAtLargeFontScale() {
        val day = DayStateSnapshot(DayState.ACTIVE, 100L, ConfidenceScore(.9f), DayStateReason.ACTIVE_CONTEXT, false)
        val snapshot = HomeNowAssembler.assemble(day,
            ContextEvent(42, UserContextType.WORK, 1_000, null, .63f, 8, ContextSource.GEOFENCE),
            "Escritório", 2_000, WakeConfidence.MEDIUM, 3_000)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.35f)) {
                HoodieTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HomeNowCard(snapshot, 4_000, ZoneId.of("America/Sao_Paulo"))
                    }
                }
            }
        }
        rule.onNodeWithText("Provavelmente trabalhando").assertExists()
        rule.onNodeWithText("Confiança: 63%").assertExists()
        rule.onNodeWithContentDescription("HOODIE · AGORA: Provavelmente trabalhando").assertExists()
        rule.onAllNodesWithText("Confirmar", ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Corrigir", ignoreCase = true).assertCountEquals(0)
    }

    @Test fun userConfirmedContextDoesNotDisplayCalculatedOneHundredPercentOrEditActions() {
        val snapshot = HomeNowAssembler.assemble(null,
            ContextEvent(42, UserContextType.WORK, 1_000, null, 1f, 8, ContextSource.USER_CORRECTION),
            "Escritório", null, null, 3_000)
        rule.setContent { HoodieTheme { HomeNowCard(snapshot, 4_000, ZoneId.of("UTC")) } }
        rule.onNodeWithText("Trabalhando").assertIsDisplayed()
        rule.onNodeWithText("Confirmado por você").assertIsDisplayed()
        rule.onAllNodesWithText("100%").assertCountEquals(0)
        rule.onAllNodesWithText("Confirmar", ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Corrigir", ignoreCase = true).assertCountEquals(0)
    }

    @Test fun highAutomaticConfidenceIsIdentifiedAndLowConfidenceStaysUnknown() {
        val high = HomeNowAssembler.assemble(null,
            ContextEvent(42, UserContextType.WORK, 1_000, null, .95f, 8, ContextSource.GEOFENCE),
            "Escritório", null, null, 3_000)
        rule.setContent { HoodieTheme { HomeNowCard(high, 4_000, ZoneId.of("UTC")) } }
        rule.onNodeWithText("Identificado automaticamente").assertIsDisplayed()
        rule.onNodeWithText("Confiança: 95%").assertIsDisplayed()
        rule.onAllNodesWithText("Confirmar", ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Corrigir", ignoreCase = true).assertCountEquals(0)
    }

    @Test fun lowConfidenceStaysUnknownWithoutDisplayingItsScoreOrActions() {
        val low = HomeNowAssembler.assemble(null,
            ContextEvent(43, UserContextType.WORK, 1_000, null, .40f, 8, ContextSource.GEOFENCE),
            "Escritório", null, null, 3_000)
        rule.setContent { HoodieTheme { HomeNowCard(low, 4_000, ZoneId.of("UTC")) } }
        rule.onNodeWithText("Ainda estou entendendo seu dia.").assertIsDisplayed()
        rule.onAllNodesWithText("Confiança: 40%").assertCountEquals(0)
        rule.onAllNodesWithText("Confirmar", ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Corrigir", ignoreCase = true).assertCountEquals(0)
    }

    @Test fun secondaryMenuOpensOnDemandCorrectionWhileSceneRemainsVisible() {
        val snapshot = HomeNowAssembler.assemble(null,
            ContextEvent(42, UserContextType.WORK, 1_000, null, .78f, 8, ContextSource.GEOFENCE),
            "Escritório", null, null, 3_000)
        rule.setContent {
            HoodieTheme {
                CompositionLocalProvider(LocalPixelRenderFrame provides PixelRenderFrame()) {
                    HomeContent(
                        state = HomeUiState(loading = false, now = 4_000, visual = com.hoodie.app.pixel.scene.VisualDirector.resolve(
                            com.hoodie.app.core.model.HoodieActivity.IDLE, UserContextType.WORK, false,
                            commute = com.hoodie.app.core.model.CommuteStyle.WALK, variant = 1,
                        )),
                        busy = false, zone = ZoneId.of("UTC"), onOpen = {}, homeNow = snapshot,
                    )
                }
            }
        }
        rule.onNodeWithTag("home_live_scene").assertIsDisplayed()
        rule.onAllNodesWithText("O que estou fazendo?", ignoreCase = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("Mais ações").performClick()
        rule.onNodeWithText("Ajustar atividade").performClick()
        rule.onNodeWithText("O que você está fazendo agora?").assertIsDisplayed()
        rule.onNodeWithText("Atividade exibida: Trabalho").assertIsDisplayed()
        rule.onNodeWithText("Evento: 42").assertIsDisplayed()
    }

    @Test fun dayReportShowsConciseMetricsWithoutRepeatingJourney() {
        val report = DailyReport(LocalDate.of(2026, 10, 7), DayReportStatus.PARTIAL, null, true,
            8 * 60 * 60_000L, null, null, false, 0, null,
            listOf(
                DayReportStop(1, "Casa", PlaceType.HOME, 1, 2, null),
                DayReportStop(2, "Trabalho", PlaceType.WORK, 3, 4, null),
                DayReportStop(1, "Casa", PlaceType.HOME, 5, 6, null),
            ), null, 7)
        rule.setContent {
            HoodieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DayReportSection(report, ZoneId.of("UTC"))
                }
            }
        }
        rule.onNodeWithText("Resumo parcial").assertExists()
        rule.onAllNodesWithText("Dados indisponíveis").assertCountEquals(2)
        rule.onAllNodesWithText("Casa → Trabalho → Casa").assertCountEquals(0)
        rule.onAllNodesWithText("Ver Jornada Completa").assertCountEquals(0)
        rule.onAllNodesWithText("0h00").assertCountEquals(0)
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun correctionRequiresExplicitPlaceAndDefaultsToFromNow() {
        var saved: Pair<PlaceType, Boolean>? = null
        rule.setContent {
            HoodieTheme {
                HomeNowCorrectionSheet(null, onDismiss = {}, onSave = { type, historical -> saved = type to historical; true }, saving = false)
            }
        }
        rule.onNodeWithText("A partir de agora").assertIsDisplayed()
        rule.onNodeWithText("Desde o início").assertIsNotEnabled()
        rule.onNodeWithText("Salvar correção", ignoreCase = true).assertIsNotEnabled()
        rule.onNodeWithText("Restaurante").performClick()
        rule.onNodeWithText("Salvar correção", ignoreCase = true).assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(PlaceType.RESTAURANT to false, saved) }
    }
}

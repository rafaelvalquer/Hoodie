package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
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
import com.hoodie.app.engine.home.HomeNowAssembler
import com.hoodie.app.presentation.screens.diary.DayReportSection
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

    @Test fun homeNowShowsEvidenceConfidenceAndActionsAtLargeFontScale() {
        val day = DayStateSnapshot(DayState.ACTIVE, 100L, ConfidenceScore(.9f), DayStateReason.ACTIVE_CONTEXT, false)
        val snapshot = HomeNowAssembler.assemble(day,
            ContextEvent(42, UserContextType.WORK, 1_000, null, .63f, 8, ContextSource.GEOFENCE),
            "Escritório", 2_000, WakeConfidence.MEDIUM, 3_000)
        var confirmed: Long? = null
        var corrected = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.35f)) {
                HoodieTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HomeNowCard(snapshot, 4_000, ZoneId.of("America/Sao_Paulo"), { confirmed = it }, { corrected = true })
                    }
                }
            }
        }
        rule.onNodeWithText("Provavelmente trabalhando").assertExists()
        rule.onNodeWithText("63%").assertExists()
        rule.onNodeWithContentDescription("HOODIE · AGORA: Provavelmente trabalhando").assertExists()
        rule.onNodeWithText("Confirmar", ignoreCase = true).performClick()
        rule.onNodeWithText("Corrigir", ignoreCase = true).performClick()
        rule.runOnIdle { assertEquals(42L, confirmed); assertTrue(corrected) }
    }

    @Test fun dayReportDistinguishesUnavailableDataAndPreservesJourney() {
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
                    DayReportSection(report, ZoneId.of("UTC"), {})
                }
            }
        }
        rule.onNodeWithText("Resumo parcial").assertExists()
        rule.onAllNodesWithText("Dados indisponíveis").assertCountEquals(2)
        rule.onNodeWithText("Casa → Trabalho → Casa").assertIsDisplayed()
        rule.onNodeWithText("0h00").assertDoesNotExist()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun correctionRequiresExplicitPlaceAndDefaultsToFromNow() {
        var saved: Pair<PlaceType, Boolean>? = null
        rule.setContent {
            HoodieTheme {
                HomeNowCorrectionSheet(onDismiss = {}, onSave = { type, historical -> saved = type to historical }, saving = false)
            }
        }
        rule.onNodeWithText("A partir de agora").assertIsDisplayed()
        rule.onNodeWithText("Salvar correção", ignoreCase = true).assertIsNotEnabled()
        rule.onNodeWithText("Restaurante").performClick()
        rule.onNodeWithText("Salvar correção", ignoreCase = true).assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(PlaceType.RESTAURANT to false, saved) }
    }
}

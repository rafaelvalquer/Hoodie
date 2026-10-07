package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.R
import com.hoodie.app.core.datastore.AppSettings
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.deviceusage.UsagePermissionState
import com.hoodie.app.core.mobility.ActivityRecognitionPermissionState
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.pixel.phoneinsights.HudStatTile
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelRenderFrame
import com.hoodie.app.presentation.screens.diary.SummarySection
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.presentation.screens.settings.SettingsContent
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate

/** Rótulo e valor de um mesmo bloco nunca se sobrepõem, em 360 dp e fonte 1.0/1.3. */
@RunWith(Parameterized::class)
class LabelSpacingTest(private val fontScale: Float) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale), LocalPixelRenderFrame provides PixelRenderFrame()) {
            HoodieTheme { Column(Modifier.requiredWidth(360.dp)) { block() } }
        }
    }

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    private fun assertStacked(name: String, label: SemanticsNodeInteraction, value: SemanticsNodeInteraction) {
        val l = bounds(label)
        val v = bounds(value)
        assertTrue("$name (f$fontScale): rótulo $l encosta no valor $v", l.bottom <= v.top + 0.5f)
    }

    @Test fun hudStatTile() {
        val label = context.getString(R.string.phone_unlock_count)
        content { HudStatTile("20", label, Color.Yellow, Modifier.fillMaxWidth()) }
        assertStacked("HudStatTile", rule.onNodeWithText(label.uppercase(), useUnmergedTree = true), rule.onNodeWithText("20", useUnmergedTree = true))
    }

    @Test fun diarySummaryTile() {
        content { SummarySection(DailySummary(LocalDate.of(2026, 10, 2), homeMs = 4 * 3_600_000L + 30 * 60_000L)) }
        assertStacked("Resumo do Diário", rule.onNodeWithText(context.getString(R.string.ui_extra_diary_screen_3).uppercase(), useUnmergedTree = true), rule.onNodeWithText("4h30", useUnmergedTree = true))
    }

    @Test fun settingsCatName() {
        content {
            SettingsContent(
                AppSettings(catName = "Hoodie"), permission = LocationPermissionState.NONE, geofenceResult = null,
                usagePermission = UsagePermissionState.DENIED, activityPermission = ActivityRecognitionPermissionState.GRANTED, onOpen = {},
            )
        }
        assertStacked("Nome do gato", rule.onNodeWithText(context.getString(R.string.ui_settings_screen_2).uppercase(), useUnmergedTree = true), rule.onNodeWithText("Hoodie", useUnmergedTree = true))
    }

    @Test fun homeYouPanel() {
        content {
            HomeContent(
                HomeUiState(loading = false, now = 1L, context = ContextEvent(type = UserContextType.HOME, startedAt = 1L, endedAt = null, confidence = 1f, placeId = null, source = ContextSource.MANUAL)),
                busy = false, zone = java.time.ZoneId.of("America/Sao_Paulo"), onOpen = {},
            )
        }
        assertStacked("Você", rule.onNodeWithText(context.getString(R.string.ui_home_screen_2).uppercase(), useUnmergedTree = true), rule.onNodeWithText("Casa", useUnmergedTree = true))
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "f{0}")
        fun cases() = listOf(1f, 1.3f)
    }
}

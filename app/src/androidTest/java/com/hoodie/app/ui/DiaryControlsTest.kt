package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.hoodie.app.presentation.screens.diary.DateSelector
import com.hoodie.app.presentation.screens.diary.ReplaySpeed
import java.time.LocalDate
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.presentation.screens.diary.ReplayControls
import com.hoodie.app.presentation.screens.diary.ReplayState
import com.hoodie.app.presentation.screens.diary.ReplayUiState
import com.hoodie.app.presentation.screens.diary.TimelineSection
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import java.time.ZoneId
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryControlsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun dateSelectorChangesBetweenTodayAndYesterday() {
        val today = LocalDate.of(2026, 10, 2)
        var chosen by mutableStateOf(today)
        rule.setContent {
            HoodieTheme { DateSelector(chosen, today, LocalContext.current, ZoneId.of("UTC")) { chosen = it } }
        }
        rule.onNodeWithText("Hoje").assertIsSelected()
        rule.onNodeWithText("Ontem").performClick().assertIsSelected()
        rule.runOnIdle { assertEquals(today.minusDays(1), chosen) }
        rule.onNodeWithText("Hoje").performClick().assertIsSelected()
        rule.runOnIdle { assertEquals(today, chosen) }
    }

    @Test fun replaySpeedControlsSelectAllSupportedSpeeds() {
        var speed by mutableStateOf(ReplaySpeed.NORMAL)
        rule.setContent {
            HoodieTheme { Column { ReplayControls(ReplayUiState(speed = speed), {}, {}, { speed = it }) } }
        }
        ReplaySpeed.entries.forEach { expected ->
            rule.onNodeWithText(expected.label).performClick().assertIsSelected()
            rule.runOnIdle { assertEquals(expected, speed) }
        }
    }

    @Test fun pausedReplayOffersResumeAndAccessibleReset() {
        var toggles = 0
        var resets = 0
        rule.setContent {
            HoodieTheme {
                Column {
                    ReplayControls(ReplayUiState(state = ReplayState.PAUSED),
                        onToggle = { toggles++ }, onReset = { resets++ }, onSpeed = {})
                }
            }
        }
        rule.onNodeWithText("▶ REPRODUZIR MEU DIA").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Reiniciar replay").performClick()
        rule.runOnIdle {
            assertEquals(1, toggles)
            assertEquals(1, resets)
        }
    }

    @Test fun completedReplayOffersReplayAgain() {
        rule.setContent {
            HoodieTheme {
                Column {
                    ReplayControls(ReplayUiState(state = ReplayState.FINISHED), {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("REVER DIA").assertIsDisplayed()
    }

    @Test fun timelineScrollsToCurrentReplayEvent() {
        val items = (0 until 30).map {
            DiaryTimelineItem("event-$it", it * 60_000L, DiaryTimelineType.NOTE, DiaryActor.USER, "Evento $it")
        }
        rule.setContent {
            HoodieTheme {
                TimelineSection(items, 29 * 60_000L, ZoneId.of("UTC"), setOf("event-29"))
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Evento 29").assertIsDisplayed()
    }
}

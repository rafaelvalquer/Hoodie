package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.R
import com.hoodie.app.engine.demo.DemoReplayMode
import com.hoodie.app.presentation.screens.demo.DemoReplayScreen
import com.hoodie.app.presentation.screens.demo.DemoReplayViewModel
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoReplayUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun banner_modes_navigation_playback_and_optional_preview_are_wired() {
        val vm = DemoReplayViewModel(SavedStateHandle())
        // Exercise the real play/pause actions without racing a wall-clock-driven replay.
        // Progression is covered by controller/ViewModel clock tests; this test checks UI wiring.
        vm.monotonicMillis = { 1_000L }
        rule.setContent { HoodieTheme { DemoReplayScreen(vm, onExit = {}) } }
        rule.onNodeWithTag("demo_banner").assertIsDisplayed()
        rule.onNodeWithTag("demo_replay_story").assertIsDisplayed()

        rule.runOnIdle { assertEquals(DemoReplayMode.QUICK_TOUR, vm.state.value.presentation.source.mode) }
        rule.onNodeWithTag("demo_mode_full_day").performClick()
        rule.runOnIdle { assertEquals(DemoReplayMode.FULL_DAY, vm.state.value.presentation.source.mode) }
        rule.onNodeWithTag("journey_play").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(com.hoodie.app.presentation.screens.diary.ReplayState.PLAYING,
                vm.state.value.presentation.source.state)
        }
        rule.onNodeWithTag("journey_play").performClick()
        rule.runOnIdle {
            assertEquals(com.hoodie.app.presentation.screens.diary.ReplayState.PAUSED,
                vm.state.value.presentation.source.state)
        }

        rule.onNodeWithTag("demo_preview_switch").performClick()
        rule.runOnIdle { assertEquals(true, vm.state.value.previewEnabled) }
        rule.onNodeWithTag("demo_contextual_preview").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("demo_replay_story").performScrollTo()
        rule.onNodeWithTag("demo_preview_switch").performClick()

        val beforeNext = vm.state.value.presentation.source.timestamp
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.journey_replay_next))
            .performScrollTo().performClick()
        rule.runOnIdle { check(vm.state.value.presentation.source.timestamp > beforeNext) }
        rule.onNodeWithTag("demo_exit").performClick()
        rule.runOnIdle { assertEquals(false, vm.state.value.visible) }
    }
}

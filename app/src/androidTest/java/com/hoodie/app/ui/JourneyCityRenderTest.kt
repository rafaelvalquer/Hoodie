package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.engine.diary.city.CityPlan
import com.hoodie.app.pixel.diary.city.CityScene
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelRenderFrame
import com.hoodie.app.presentation.screens.diary.JourneyCityView
import com.hoodie.app.presentation.screens.diary.ReplayUiState
import com.hoodie.app.presentation.theme.HoodieTheme
import java.time.ZoneId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyCityRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test(timeout = 60_000)
    fun fixedFrameCanScrollAndStaysIdenticalWhileTheComposeClockAdvances() {
        val start = 1_790_914_800_000L
        val node = JourneyNode("home", 0, 1L, "Casa", PlaceType.HOME,
            start, null, 3_600_000L, null, null)
        val scene = CityScene.build(requireNotNull(CityPlan.build(
            JourneyMapData(listOf(node), emptyList(), start, start + 3_600_000L))))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalPixelRenderFrame provides PixelRenderFrame()) {
                HoodieTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(700.dp))
                        JourneyCityView(scene, ReplayUiState(currentTimestamp = start), ZoneId.of("UTC"), {}, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(700.dp))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        // Same auto-advance transition used to scroll the full Diary golden.
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("journey_city").performScrollTo()
        rule.mainClock.autoAdvance = false
        val first = pixels()
        rule.mainClock.advanceTimeBy(2_000)
        assertArrayEquals("A fixed preview must not follow the Compose clock", first, pixels())
        rule.onNodeWithTag("journey_city_mode").performTouchInput { click() }
        rule.mainClock.advanceTimeByFrame()
        assertFalse("Overview interaction must still repaint a fixed preview", first.contentEquals(pixels()))
    }

    private fun pixels(): IntArray {
        val map = rule.onNodeWithTag("journey_city").captureToImage().toPixelMap()
        return IntArray(map.width * map.height) { i -> map[i % map.width, i / map.width].toArgb() }
    }
}

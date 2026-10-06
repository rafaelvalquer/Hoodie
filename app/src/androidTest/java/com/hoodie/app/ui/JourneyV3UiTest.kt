package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.domain.diary.journey.ClockArc
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.engine.diary.journey.DayClockLegacyAssembler
import com.hoodie.app.engine.diary.journey.JourneyChapterPlanner
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.engine.diary.journey.SyntheticJourneyDays
import com.hoodie.app.engine.diary.journey.SyntheticJourneyDays.Kind
import com.hoodie.app.presentation.screens.diary.DayClockLegacyView
import com.hoodie.app.presentation.screens.diary.JourneyChaptersView
import com.hoodie.app.presentation.screens.diary.JourneyOverworldMapView
import com.hoodie.app.presentation.screens.diary.ReplayState
import com.hoodie.app.presentation.screens.diary.ReplayUiState
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Jornada 3.0 na tela (plano §8.3): capítulos, replay seguindo o capítulo, toque na
 * construção e no arco do relógio. Rode com `am instrument` num emulador descartável —
 * `connectedDebugAndroidTest` desinstala o app (e apaga os dados) do aparelho.
 */
@RunWith(AndroidJUnit4::class)
class JourneyV3UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 10, 5)
    private fun model(kind: Kind) = JourneyOverworldModel.build(SyntheticJourneyDays.build(kind, date, zone), zone)
    private val hasDescription = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    private fun chapters(model: JourneyOverworldModel, replay: ReplayUiState = ReplayUiState(), manual: DayChapter? = null): () -> DayChapter? {
        var manualChapter by mutableStateOf(manual)
        rule.setContent {
            HoodieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    JourneyChaptersView(model, replay, zone, manualChapter, isToday = false, nowMillis = 0L, selectedStopId = null,
                        onOpenChapter = { manualChapter = it }, onStop = {})
                }
            }
        }
        return { manualChapter }
    }

    @Test fun tappingAClosedChapterOpensIt() {
        val m = model(Kind.TEN)
        val plan = m.plan as JourneyPlan.Chapters
        val active = JourneyChapterPlanner.activeChapter(plan, null, false, null, false, 0L)
        val other = plan.chapters.first { it.chapter != active && !it.isEmpty }.chapter
        val manual = chapters(m)
        rule.onNodeWithTag("journey_chapter_open_${active.name.lowercase()}").assertExists()
        rule.onNodeWithTag("journey_chapter_${other.name.lowercase()}").performScrollTo().assert(hasDescription).performClick()
        rule.waitForIdle()
        assertEquals(other, manual())
        rule.onNodeWithTag("journey_chapter_open_${other.name.lowercase()}").assertExists()
        rule.onNodeWithTag("journey_chapter_${active.name.lowercase()}").assertExists()
    }

    @Test fun playingReplayOpensTheChapterOfTheReplayTime() {
        val m = model(Kind.TEN)
        val night = date.atTime(20, 30).atZone(zone).toInstant().toEpochMilli()
        chapters(m, ReplayUiState(state = ReplayState.PLAYING, currentTimestamp = night), manual = DayChapter.MORNING)
        rule.onNodeWithTag("journey_chapter_open_night").assertExists()
        rule.onNodeWithTag("journey_chapter_morning").assertExists()
    }

    @Test fun tappingABuildingOpensItsStop() {
        val m = model(Kind.THREE)
        var tapped: JourneyStop? = null
        rule.setContent {
            HoodieTheme {
                JourneyOverworldMapView(m, m.single!!, ReplayUiState(), zone, null, onStop = { tapped = it }, animate = false)
            }
        }
        rule.onNodeWithTag("journey_overworld").assertIsDisplayed()
        rule.onNodeWithTag("journey_stop_journey-1").assert(hasDescription).performClick()
        rule.waitForIdle()
        assertEquals("journey-1", tapped?.id)
    }

    @Test fun tappingAClockArcOpensItsStop() {
        val data = SyntheticJourneyDays.build(Kind.NINE, date, zone)
        val clock = DayClockLegacyAssembler.build(data, zone, date)
        val arc = clock.arcs.first { it.kind == ClockArc.Kind.STAY && it.stopId != null }
        var tapped: String? = null
        rule.setContent {
            HoodieTheme {
                DayClockLegacyView(clock, data, ReplayUiState(), zone, date.toEpochDay(), null, onStop = { tapped = it }, onTick = {})
            }
        }
        rule.onNodeWithTag("day_clock_legacy").assertIsDisplayed()
        rule.onNodeWithTag("clock_arc_${arc.stopId}").assert(hasDescription).performClick()
        rule.waitForIdle()
        assertEquals(arc.stopId, tapped)
    }
}

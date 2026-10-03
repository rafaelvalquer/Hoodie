package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.domain.diary.model.DiaryMapData
import com.hoodie.app.domain.diary.model.DiaryMovement
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.diary.model.ReplaySequence
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.presentation.screens.diary.DiaryMapMode
import com.hoodie.app.presentation.screens.diary.DiaryMapModeToggle
import com.hoodie.app.presentation.screens.diary.JourneyMapModel
import com.hoodie.app.presentation.screens.diary.JourneyMapView
import com.hoodie.app.presentation.screens.diary.JourneyNodeDetailsSheet
import com.hoodie.app.presentation.screens.diary.JourneyReplayControls
import com.hoodie.app.presentation.screens.diary.ReplaySpeed
import com.hoodie.app.presentation.screens.diary.ReplayState
import com.hoodie.app.presentation.screens.diary.ReplayUiState
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Jornada Pixel na tela (plano §18.3): mapa, controles do replay 2.0, detalhe da
 * parada e seletor [JORNADA] [MAPA ANTIGO]. Rode num emulador descartável —
 * `connectedDebugAndroidTest` desinstala o app (e apaga os dados) do aparelho.
 */
@RunWith(AndroidJUnit4::class)
class JourneyMapUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("UTC")
    private fun t(h: Int, m: Int = 0) = (h * 60L + m) * 60_000L

    private val visits = listOf(
        PlaceVisit(1, "Casa", PlaceType.HOME, t(6), t(7, 47), t(1, 47), 1),
        PlaceVisit(2, "Trabalho", PlaceType.WORK, t(8, 15), t(12, 8), t(3, 53), 1),
        PlaceVisit(3, "Almoço", PlaceType.RESTAURANT, t(12, 16), t(13), t(0, 44), 1),
        PlaceVisit(2, "Trabalho", PlaceType.WORK, t(13, 5), t(17, 40), t(4, 35), 1),
    )
    private val diary = DailyDiary(
        DailySummary(LocalDate.of(1970, 1, 1)), emptyList(), visits, DiaryMapData(),
        ReplaySequence(t(6), t(18), visits, emptyList()),
        movements = listOf(DiaryMovement(MovementMode.BUS, t(7, 50), t(8, 15)), DiaryMovement(MovementMode.WALKING, t(12, 8), t(12, 16))),
    )
    private val model = JourneyMapModel.from(diary, t(18))

    @Test fun journeyShowsEveryVisitInOrderIncludingReturns() {
        rule.setContent { HoodieTheme { Column(Modifier.verticalScroll(rememberScrollState())) { JourneyMapView(model, ReplayUiState(), zone, null, onNode = {}) } } }
        (0..3).forEach { rule.onNodeWithTag("journey_card_$it").performScrollTo().assertIsDisplayed() }
        rule.onNodeWithText("Retorno #2").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("journey_segment_0").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("🚌 28min").assertIsDisplayed()
    }

    @Test fun tappingACardOpensThatVisit() {
        var tapped: JourneyNode? = null
        rule.setContent { HoodieTheme { Column(Modifier.verticalScroll(rememberScrollState())) { JourneyMapView(model, ReplayUiState(), zone, null, onNode = { tapped = it }) } } }
        rule.onNodeWithTag("journey_card_3").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(3, tapped?.visitIndex); assertTrue(tapped!!.isReturn) }
    }

    @Test fun replayControlsPlayNextPreviousAndSpeed() {
        var seeks = mutableListOf<Long>()
        var toggles = 0
        var speed = ReplaySpeed.NORMAL
        var replay by mutableStateOf(ReplayUiState(state = ReplayState.PAUSED, currentTimestamp = t(10)))
        rule.setContent {
            HoodieTheme {
                JourneyReplayControls(replay, model.data, zone, onToggle = { toggles++ }, onSeek = { seeks += it; replay = replay.copy(currentTimestamp = it) }, onReset = {}, onSpeed = { speed = it })
            }
        }
        rule.onNodeWithTag("journey_play").performClick()
        rule.onNodeWithContentDescription("Próximo evento").performClick()
        rule.onNodeWithContentDescription("Evento anterior").performClick()
        rule.onNodeWithContentDescription(ReplaySpeed.FAST.label).performClick()
        rule.runOnIdle {
            assertEquals(1, toggles)
            assertEquals(JourneyReplayAssembler.next(model.data, t(10)), seeks[0])
            assertEquals(JourneyReplayAssembler.previous(model.data, seeks[0]), seeks[1])
            assertEquals(ReplaySpeed.FAST, speed)
        }
    }

    @Test fun detailsSheetShowsReturnTotalsAndSeeAll() {
        var seeAll = false
        val node = model.data.nodes[3]
        rule.setContent { HoodieTheme { JourneyNodeDetailsSheet(node, model.data.segments[2], null, zone, onSeeAll = { seeAll = true }, onDismiss = {}) } }
        rule.onNodeWithText("Retorno #2").assertIsDisplayed()
        rule.onNodeWithText("13:05").assertIsDisplayed()
        rule.onNodeWithText("8h28").assertIsDisplayed()
        rule.onNodeWithTag("journey_details_see_all").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(seeAll) }
    }

    @Test fun mapModeToggleSwitchesBetweenJourneyAndClassic() {
        var mode by mutableStateOf(DiaryMapMode.JOURNEY)
        rule.setContent { HoodieTheme { DiaryMapModeToggle(mode, onSelect = { mode = it }) } }
        rule.onNodeWithTag("map_mode_journey").assertIsSelected()
        rule.onNodeWithTag("map_mode_classic").performClick().assertIsSelected()
        rule.runOnIdle { assertEquals(DiaryMapMode.CLASSIC, mode) }
    }
}

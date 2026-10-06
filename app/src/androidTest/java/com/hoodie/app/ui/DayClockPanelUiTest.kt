package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.clock.DayClockData
import com.hoodie.app.domain.diary.journey.DiaryMapMode
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.engine.diary.DayClockAssembler
import com.hoodie.app.engine.diary.SyntheticClockDays
import com.hoodie.app.pixel.diary.clock.DayClockGeometry
import com.hoodie.app.presentation.screens.diary.DiaryActions
import com.hoodie.app.presentation.screens.diary.DiaryContent
import com.hoodie.app.presentation.screens.diary.DiaryUiState
import com.hoodie.app.presentation.screens.diary.ReplayUiState
import com.hoodie.app.presentation.screens.diary.clock.DayClockPanel
import com.hoodie.app.presentation.screens.diary.clock.DayClockUiState
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Relógio do Dia 2.0 na tela (plano §6): toque no anel ↔ lista, centro, AGORA, troca
 * JORNADA/RELÓGIO e fonte grande. Rode com `am instrument` num emulador descartável —
 * `connectedDebugAndroidTest` desinstala o app (e apaga os dados) do aparelho.
 */
@RunWith(AndroidJUnit4::class)
class DayClockPanelUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 10, 5)
    private val day = SyntheticClockDays.build(SyntheticClockDays.Kind.FULL, date, zone, LocalTime.of(21, 40))
    private val data: DayClockData = DayClockAssembler.build(day.diary, date, zone, day.now)
    private val work get() = data.stays.first { it.placeName == "Trabalho" }

    private fun panel(fontScale: Float = 1f): () -> String? {
        var selected by mutableStateOf<String?>(null)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                HoodieTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DayClockPanel(DayClockUiState.of(data, selected, ReplayUiState()), onSelect = { selected = it }, animate = false)
                    }
                }
            }
        }
        return { selected }
    }

    /** Toca no minuto [minute] do anel de atividades, com a mesma escala inteira do mostrador. */
    private fun tapRing(minute: Float) {
        rule.onNodeWithTag("day_clock_dial").performTouchInput {
            val scale = floor(width / DayClockGeometry.SIZE.toFloat()).coerceAtLeast(1f)
            val offX = (width - DayClockGeometry.SIZE * scale) / 2f
            val a = minute / data.dayLengthMinutes * 2 * Math.PI
            val x = DayClockGeometry.CENTER + DayClockGeometry.ACTIVITY_MID * sin(a).toFloat()
            val y = DayClockGeometry.CENTER - DayClockGeometry.ACTIVITY_MID * cos(a).toFloat()
            click(Offset(offX + x * scale, y * scale))
        }
        rule.waitForIdle()
    }

    @Test fun tappingTheRingSelectsTheSegmentAndSyncsTheList() {
        val selected = panel()
        tapRing(work.startMinute + work.minutes / 2f)
        assertEquals(work.id, selected())
        rule.onNodeWithTag("clock_item_${work.id}").performScrollTo().assertIsSelected()
        rule.onNodeWithText("PARADA ${work.stopIndex} DE ${data.stopCount}").assertExists()
    }

    @Test fun tappingTheListUpdatesTheCenter() {
        val selected = panel()
        val bus = data.segments.first { it is ClockSegment.Move && it.mode == com.hoodie.app.core.mobility.MovementMode.BUS }
        rule.onNodeWithTag("clock_item_${bus.id}").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(bus.id, selected())
        rule.onNodeWithText("DESLOCAMENTO").assertExists()
        rule.onNodeWithText("🚌 Ônibus").assertExists()
    }

    @Test fun nowButtonAndCenterTapReturnToTheCurrentMoment() {
        val selected = panel()
        rule.onNodeWithTag("clock_item_${work.id}").performScrollTo().performClick()
        rule.onNodeWithTag("clock_now").performScrollTo().performClick()
        rule.waitForIdle()
        assertNull(selected())
        rule.onNodeWithText("AGORA").assertExists()
        rule.onNodeWithTag("clock_item_${work.id}").assertIsNotSelected()
        // Toque na área futura é ignorado.
        tapRing(23f * 60)
        assertNull(selected())
    }

    @Test fun largeFontDoesNotOverflowTheCenter() {
        panel(fontScale = 1.3f)
        rule.onNodeWithTag("clock_item_${work.id}").performScrollTo().performClick()
        rule.waitForIdle()
        val dial = rule.onNodeWithTag("day_clock_dial").fetchSemanticsNode().boundsInRoot
        val center = rule.onNodeWithTag("clock_center", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val scale = floor(dial.width / DayClockGeometry.SIZE)
        val plate = DayClockGeometry.CENTER_PLATE.endInclusive * 2 * scale
        assertTrue("centro ${center.height} > placa $plate", center.height <= plate)
        assertTrue(center.width <= plate)
    }

    @Test fun switchingBetweenJourneyAndClock() {
        var state by mutableStateOf(
            DiaryUiState(date, diary = day.diary.copy(summary = DailySummary(date, homeMs = 3_600_000L)), dayClock = data),
        )
        rule.setContent {
            HoodieTheme {
                DiaryContent(
                    state, zone, day.now,
                    actions = DiaryActions(
                        setMapMode = { state = state.copy(mapMode = it) },
                        selectClockSegment = { state = state.copy(clockSelectedId = it) },
                    ),
                    digitalContent = {},
                )
            }
        }
        rule.onNodeWithTag("map_mode_clock").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(DiaryMapMode.CLOCK, state.mapMode)
        rule.onNodeWithTag("day_clock").assertExists()
        rule.onNodeWithTag("map_mode_journey").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("day_clock").assertDoesNotExist()
    }

    /** Revisão visual: manhã, tarde, noite, seleção e fonte 1,3× → files/day-clock-review/ (PNG). */
    @Test fun exportReviewScreenshots() {
        val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "day-clock-review").apply { mkdirs() }
        var case by mutableStateOf(Triple(LocalTime.of(9, 15), null as String?, 1f))
        rule.setContent {
            val (now, sel, font) = case
            val d = LocalDensity.current
            val dd = SyntheticClockDays.build(SyntheticClockDays.Kind.FULL, date, zone, now)
            val data = DayClockAssembler.build(dd.diary, date, zone, dd.now)
            CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                HoodieTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DayClockPanel(DayClockUiState.of(data, sel, ReplayUiState()), onSelect = {}, animate = false)
                    }
                }
            }
        }
        listOf(
            "morning" to Triple(LocalTime.of(9, 15), null, 1f),
            "afternoon" to Triple(LocalTime.of(15, 30), null, 1f),
            "night" to Triple(LocalTime.of(21, 40), null, 1f),
            "selected_work_font130" to Triple(LocalTime.of(21, 40), work.id, 1.3f),
        ).forEach { (name, c) ->
            case = c
            rule.waitForIdle()
            Thread.sleep(300)
            rule.waitForIdle()
            val bmp = rule.onNodeWithTag("day_clock").captureToImage().asAndroidBitmap()
            java.io.File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

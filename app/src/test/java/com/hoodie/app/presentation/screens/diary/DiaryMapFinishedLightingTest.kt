package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.diary.DiaryMapLayoutEngine
import com.hoodie.app.pixel.diary.DiaryMapRenderer
import com.hoodie.app.pixel.diary.DiaryMapTestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bug da V0.2: FINISHED voltava para DAY. A luz depende do horário do replay, não do estado. */
class DiaryMapFinishedLightingTest {
    private val layout = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay)

    @Test
    fun `FINISHED mantem o periodo do horario final`() {
        val finished = ReplayUiState(state = ReplayState.FINISHED, currentTimestamp = 1L, dayPeriod = DayPeriod.NIGHT)
        assertTrue(finished.hasReplayTimestamp)
        assertFalse("FINISHED não é replay em andamento", finished.replaying)
        val scene = diaryMapScene(layout, finished, 0)
        assertEquals(DayPeriod.NIGHT, scene.period)
        // Dia completo: prédios voltam a "visitado", mas a luz continua noturna.
        assertFalse(scene.replaying)
        assertFalse(DiaryMapRenderer.render(scene).pixels.contentEquals(DiaryMapRenderer.render(scene.copy(period = DayPeriod.DAY)).pixels))
    }

    @Test
    fun `PLAYING e PAUSED usam o horario e sem replay e dia`() {
        for (state in listOf(ReplayState.PLAYING, ReplayState.PAUSED)) {
            assertEquals(DayPeriod.EVENING, diaryMapScene(layout, ReplayUiState(state = state, currentTimestamp = 1L, dayPeriod = DayPeriod.EVENING), 0).period)
        }
        assertEquals(DayPeriod.DAY, diaryMapScene(layout, ReplayUiState(dayPeriod = DayPeriod.NIGHT), 0).period)
    }

    @Test
    fun `velocidades dizem minutos por segundo`() {
        assertEquals(listOf("1 min/s", "5 min/s", "10 min/s"), ReplaySpeed.entries.map { it.label })
        // 1 s real a 5 min/s = 5 minutos do dia.
        assertEquals(5 * 60_000L, advanceReplay(0, 1_000, ReplaySpeed.FAST, Long.MAX_VALUE))
        assertEquals(100L, advanceReplay(0, 1_000, ReplaySpeed.VERY_FAST, 100))
    }
}

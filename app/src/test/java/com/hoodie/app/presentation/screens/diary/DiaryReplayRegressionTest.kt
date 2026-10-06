package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.engine.diary.DiaryRegressionScenario
import com.hoodie.app.engine.diary.DiaryRegressionScenario.CHROME
import com.hoodie.app.engine.diary.DiaryRegressionScenario.MAPS
import com.hoodie.app.engine.diary.DiaryRegressionScenario.SPOTIFY
import com.hoodie.app.engine.diary.DiaryRegressionScenario.TEAMS
import com.hoodie.app.engine.diary.DiaryRegressionScenario.WHATSAPP
import com.hoodie.app.engine.diary.DiaryRegressionScenario.YOUTUBE
import com.hoodie.app.pixel.diary.DiaryMapLayoutEngine
import com.hoodie.app.pixel.diary.DiaryMapRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cenário principal da V0.2: o replay reproduz Casa → Maps no transporte →
 * Trabalho + Teams → Restaurante + YouTube → Trabalho + Chrome → Academia + Spotify →
 * Casa + YouTube, e termina às 22h com o mapa noturno.
 */
class DiaryReplayRegressionTest {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val date = LocalDate.of(2026, 10, 5)
    private val diary = DiaryRegressionScenario.create(date, zone)
    private val layout = DiaryMapLayoutEngine.layout(diary.visits)
    private fun at(h: Int, m: Int = 0) = startOfDay(date, zone) + (h * 60L + m) * 60_000L
    private fun stateAt(h: Int, m: Int = 0) = replayAt(ReplayUiState(state = ReplayState.PLAYING), diary, at(h, m), zone)
    private fun placeOf(s: ReplayUiState) = DiaryMapLabelPolicy.activeNodeId(layout, s.activeNodeId)?.let { layout.node(it)!!.label }

    @Test
    fun `o dia inteiro na ordem certa`() {
        data class Step(val h: Int, val m: Int, val place: String?, val ctx: UserContextType, val app: String?, val trip: Boolean = false)
        val steps = listOf(
            Step(7, 10, "Casa", UserContextType.HOME, WHATSAPP),
            Step(8, 20, null, UserContextType.COMMUTING, MAPS, trip = true),
            Step(9, 30, "Trabalho", UserContextType.WORK, TEAMS),
            Step(12, 20, "Restaurante", UserContextType.LUNCH, YOUTUBE),
            Step(14, 30, "Trabalho", UserContextType.WORK, CHROME),
            Step(18, 30, "Academia", UserContextType.GYM, SPOTIFY),
            Step(20, 30, "Casa", UserContextType.HOME, YOUTUBE),
        )
        steps.forEach { s ->
            val st = stateAt(s.h, s.m)
            val label = "%02d:%02d".format(s.h, s.m)
            assertEquals("$label lugar", s.place, placeOf(st))
            assertEquals("$label contexto", s.ctx, st.currentContext)
            assertEquals("$label app", s.app, st.activePhoneApp?.packageName)
            assertEquals("$label trecho", s.trip, st.activeEdgeId != null)
        }
        // Hoodie acompanha: trabalhando no trabalho, treinando na academia, jogando em casa.
        assertEquals(HoodieActivity.WORKING, stateAt(9, 30).currentHoodieActivity)
        assertEquals(HoodieActivity.TRAINING, stateAt(18, 30).currentHoodieActivity)
        assertEquals(HoodieActivity.GAMING, stateAt(20, 30).currentHoodieActivity)
        // Sem app aberto, nada no HUD.
        assertNull(stateAt(11, 0).activePhoneApp)
    }

    @Test
    fun `no transporte o Hoodie anda pela rua entre casa e trabalho`() {
        val st = stateAt(8, 20)
        val scene = diaryMapScene(layout, st, 0)
        val marker = DiaryMapRenderer.marker(scene)!!
        assertTrue("andando", marker.walking)
        assertEquals(20f / 45f, st.edgeProgress, 0.01f)
    }

    @Test
    fun `termina as 22h e o mapa continua noturno`() {
        val finished = replayAt(ReplayUiState(state = ReplayState.FINISHED), diary, diary.replay.endAt, zone)
        assertEquals(at(22), finished.currentTimestamp)
        assertEquals(DayPeriod.NIGHT, finished.dayPeriod)
        assertEquals(DayPeriod.NIGHT, diaryMapScene(layout, finished, 0).period)
        assertEquals(1f, finished.progress)
    }

    @Test
    fun `reset returns replay to the active journey start`() {
        val reset = resetReplayAt(
            diary,
            ReplayUiState(state = ReplayState.FINISHED, currentTimestamp = at(22), wakeTransition = true),
            zone,
        )

        assertEquals(ReplayState.IDLE, reset.state)
        assertEquals(diary.replay.startAt, reset.currentTimestamp)
        assertEquals(0f, reset.progress)
        assertFalse(reset.wakeTransition)
    }

    @Test
    fun `wake transition changes only the presentation while replay time stays fixed`() {
        val timestamp = at(7, 10)
        val waking = replayAt(
            ReplayUiState(state = ReplayState.PLAYING, wakeTransition = true),
            diary,
            timestamp,
            zone,
        )

        assertEquals(timestamp, waking.currentTimestamp)
        assertEquals(HoodieActivity.WAKING_UP, waking.currentHoodieActivity)
    }
}

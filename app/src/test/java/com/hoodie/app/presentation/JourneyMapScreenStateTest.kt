package com.hoodie.app.presentation

import com.hoodie.app.R
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.diary.journey.JourneyLightingRenderer
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures.ZONE
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures.t
import com.hoodie.app.presentation.screens.diary.JourneyDetails
import com.hoodie.app.presentation.screens.diary.JourneyMapModel
import com.hoodie.app.presentation.screens.diary.JourneyText
import com.hoodie.app.presentation.screens.diary.JourneyTimeline
import com.hoodie.app.presentation.screens.diary.ReplayState
import com.hoodie.app.presentation.screens.diary.ReplayUiState
import com.hoodie.app.presentation.screens.diary.journeyScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyMapScreenStateTest {
    private val model = JourneyMapModel.from(JourneyTestFixtures.diary(), JourneyTestFixtures.NOW)

    @Test
    fun `replay da tela vira cena - luz pelo horario, dia claro parado`() {
        val idle = journeyScene(model, ReplayUiState(), null, ZONE, 0)
        assertEquals(JourneyLightingRenderer.DAYLIGHT, idle.light)
        assertFalse(idle.replay.replaying)
        val playing = journeyScene(model, ReplayUiState(state = ReplayState.PLAYING, currentTimestamp = t(22)), "journey-1", ZONE, 0)
        assertEquals(DayPeriod.NIGHT, playing.light.period)
        assertTrue(playing.replay.replaying)
        assertEquals("journey-1", playing.selectedNodeId)
        // FINISHED às 23h continua noturno, mas mostra o dia inteiro visitado.
        val finished = journeyScene(model, ReplayUiState(state = ReplayState.FINISHED, currentTimestamp = t(23)), null, ZONE, 0)
        assertEquals(DayPeriod.NIGHT, finished.light.period)
        assertFalse(finished.replay.replaying)
    }

    @Test
    fun `textos dos cartoes e dos selos`() {
        val work = model.data.nodes[1]
        assertEquals("08:15–12:08", JourneyText.times(work, ZONE))
        assertEquals("19:50–agora", JourneyText.times(model.data.nodes.last(), ZONE))
        val bus = model.data.segments[0]
        assertEquals("🚌 28min", JourneyText.segmentChip(bus))
        assertEquals("07:47 → 08:15", JourneyText.segmentTimes(bus, ZONE))
        assertEquals("🧭 28min", JourneyText.segmentChip(bus.copy(movementMode = null)))
        assertEquals("Supermercado…", JourneyText.shortName("Supermercado do bairro"))
    }

    @Test
    fun `barra temporal - fracao e instante sao inversos`() {
        val d = model.data
        assertEquals(0f, JourneyTimeline.fraction(d, null))
        assertEquals(t(6), JourneyTimeline.timestamp(d, 0f))
        assertEquals(t(23), JourneyTimeline.timestamp(d, 1f))
        val mid = JourneyTimeline.timestamp(d, 0.5f)
        assertEquals(0.5f, JourneyTimeline.fraction(d, mid), 0.0001f)
    }

    @Test
    fun `detalhe da parada - retorno, total no lugar e como chegou`() {
        val back = model.data.nodes[3] // segundo Trabalho
        val lines = JourneyDetails.lines(back, model.data.segments[2], ZONE, "em andamento").associate { it.labelRes to it.value }
        assertEquals("13:05", lines[R.string.journey_details_arrival])
        assertEquals("2", lines[R.string.journey_details_appearances])
        assertEquals("8h28", lines[R.string.journey_details_place_total])
        assertTrue(lines.getValue(R.string.journey_details_arrived_by).startsWith(MovementMode.WALKING.emoji))
        val open = JourneyDetails.lines(model.data.nodes.last(), null, ZONE, "em andamento").associate { it.labelRes to it.value }
        assertEquals("em andamento", open[R.string.journey_details_departure])
        assertFalse(R.string.journey_details_arrived_by in open)
    }
}

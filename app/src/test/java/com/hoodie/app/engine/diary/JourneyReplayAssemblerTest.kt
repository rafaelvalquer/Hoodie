package com.hoodie.app.engine.diary

import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures.t
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyReplayAssemblerTest {
    private val data = JourneyTestFixtures.data

    @Test
    fun `sem replay o dia inteiro esta alcancado`() {
        val idle = JourneyReplayAssembler.stateAt(data, null, replaying = false)
        assertEquals(5, idle.reachedIndex)
        assertNull(idle.activeNodeIndex); assertNull(idle.activeSegmentIndex)
        assertFalse(idle.replaying)
    }

    @Test
    fun `parado numa visita`() {
        val s = JourneyReplayAssembler.stateAt(data, t(10), replaying = true)
        assertEquals(1, s.activeNodeIndex)
        assertNull(s.activeSegmentIndex)
        assertEquals(1, s.reachedIndex)
    }

    @Test
    fun `no meio do trecho, com progresso pelo tempo`() {
        // Trecho 0: 07:47 → 08:15 (28 min). 08:01 = metade.
        val s = JourneyReplayAssembler.stateAt(data, t(8, 1), replaying = true)
        assertEquals(0, s.activeSegmentIndex)
        assertNull(s.activeNodeIndex)
        assertEquals(0.5f, s.segmentProgress, 0.001f)
        assertEquals(0, s.reachedIndex)
    }

    @Test
    fun `chegada exata ja e a parada seguinte`() {
        val s = JourneyReplayAssembler.stateAt(data, t(8, 15), replaying = true)
        assertEquals(1, s.activeNodeIndex)
        assertNull(s.activeSegmentIndex)
    }

    @Test
    fun `mesmo instante sempre da o mesmo estado (replay pode pular)`() {
        val a = JourneyReplayAssembler.stateAt(data, t(15, 33), true)
        JourneyReplayAssembler.stateAt(data, t(19), true)
        assertEquals(a, JourneyReplayAssembler.stateAt(data, t(15, 33), true))
    }

    @Test
    fun `eventos sao chegadas e saidas em ordem`() {
        val events = JourneyReplayAssembler.events(data)
        assertEquals(6 + 5, events.size)
        assertTrue(events.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp })
        assertEquals(JourneyEventKind.ARRIVE, events.first().kind)
        assertEquals(t(6), events.first().timestamp)
    }

    @Test
    fun `proximo e anterior navegam entre eventos`() {
        assertEquals(t(7, 47), JourneyReplayAssembler.next(data, t(6)))
        assertEquals(t(8, 15), JourneyReplayAssembler.next(data, t(7, 47)))
        // Sem mais eventos: fim do dia.
        assertEquals(t(23), JourneyReplayAssembler.next(data, t(21)))
        // Anterior a partir do meio do trabalho volta para a chegada ao trabalho.
        assertEquals(t(8, 15), JourneyReplayAssembler.previous(data, t(10)))
        // Logo depois de um evento, ⏮ pula para o anterior (não para o mesmo).
        assertEquals(t(7, 47), JourneyReplayAssembler.previous(data, t(8, 15) + 500))
        assertEquals(t(6), JourneyReplayAssembler.previous(data, t(6, 1)))
        assertEquals(data.startAt, JourneyReplayAssembler.previous(data, null))
    }
}

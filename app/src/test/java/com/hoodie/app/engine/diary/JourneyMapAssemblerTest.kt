package com.hoodie.app.engine.diary

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DiaryMovement
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneSummary
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures.MIN
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures.t
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JourneyMapAssemblerTest {
    private val data = JourneyTestFixtures.data

    @Test
    fun `cada visita vira um no proprio, em ordem cronologica`() {
        assertEquals(6, data.nodes.size)
        assertEquals(listOf("Casa", "Trabalho", "Almoço", "Trabalho", "Academia", "Casa"), data.nodes.map { it.placeName })
        assertTrue(data.nodes.zipWithNext().all { (a, b) -> a.arrivalAt < b.arrivalAt })
        assertEquals((0..5).map { "journey-$it" }, data.nodes.map { it.id })
    }

    @Test
    fun `lugares repetidos aparecem repetidos e sabem que sao retorno`() {
        val work = data.nodes.filter { it.placeName == "Trabalho" }
        assertEquals(listOf(0, 1), work.map { it.revisitCount })
        assertFalse(work[0].isReturn)
        assertTrue(work[1].isReturn)
        assertEquals(2, work[1].returnNumber)
        assertTrue(work.all { it.placeOccurrences == 2 })
        // Total acumulado no dia: 3h53 + 4h35.
        assertEquals((3 * 60 + 53 + 4 * 60 + 35) * MIN, work[0].placeTotalMs)
        assertEquals(1, data.nodes.first { it.placeName == "Academia" }.placeOccurrences)
    }

    @Test
    fun `trechos ligam visitas consecutivas da saida ate a chegada`() {
        assertEquals(5, data.segments.size)
        val first = data.segments.first()
        assertEquals("journey-0", first.fromNodeId); assertEquals("journey-1", first.toNodeId)
        assertEquals(t(7, 47), first.startedAt); assertEquals(t(8, 15), first.endedAt)
        assertEquals(28 * MIN, first.durationMs)
        assertEquals(data.segments.sumOf { it.durationMs }, data.travelMs)
    }

    @Test
    fun `meio dominante de cada trecho`() {
        // 7 min a pé + 21 min de ônibus → ônibus.
        assertEquals(
            listOf(MovementMode.BUS, MovementMode.WALKING, MovementMode.WALKING, MovementMode.CAR, MovementMode.BICYCLE),
            data.segments.map { it.movementMode },
        )
    }

    @Test
    fun `sem mobilidade detectada o trecho fica sem meio, e NONE nunca conta`() {
        val noMoves = JourneyMapAssembler.build(JourneyTestFixtures.diary(movements = emptyList()), JourneyTestFixtures.NOW)
        assertTrue(noMoves.segments.all { it.movementMode == null })
        assertNull(JourneyMapAssembler.dominantMode(listOf(DiaryMovement(MovementMode.NONE, t(8), t(9))), t(8), t(9)))
        // Folga de 2 min entre relógios: um trecho que termina 1 min antes ainda casa.
        assertEquals(MovementMode.CAR, JourneyMapAssembler.dominantMode(listOf(DiaryMovement(MovementMode.CAR, t(7), t(7, 59))), t(8), t(8, 30)))
    }

    @Test
    fun `contexto, atividade do Hoodie e celular dentro da visita`() {
        assertEquals(UserContextType.LUNCH, data.nodes[2].contextType)
        assertEquals(JourneyTestFixtures.visits[0].dominantHoodieActivity, data.nodes[0].hoodieActivity)
        val phone = DailyPhoneInsights(
            DailyPhoneSummary.empty(LocalDate.of(1970, 1, 1)), emptyList(), emptyList(), emptyList(), emptyList(),
            appSessions = listOf(
                AppSession("com.spotify.music", t(12, 20), t(12, 35)), // dentro do almoço
                AppSession("com.spotify.music", t(12, 10), t(12, 14)), // no caminho: não conta
            ),
        )
        val withPhone = JourneyMapAssembler.build(JourneyTestFixtures.diary().copy(phoneInsights = phone), JourneyTestFixtures.NOW)
        assertEquals(15 * MIN, withPhone.nodes[2].phoneUsageMs)
        assertEquals(0L, withPhone.nodes[1].phoneUsageMs)
    }

    @Test
    fun `dia vazio mantem a janela do replay`() {
        val empty = JourneyMapAssembler.build(JourneyTestFixtures.diary(visits = emptyList()), JourneyTestFixtures.NOW)
        assertTrue(empty.isEmpty)
        assertEquals(t(6), empty.startAt)
        assertEquals(t(23), empty.endAt)
    }

    @Test
    fun `lugar sem id se reconhece pelo nome e tipo`() {
        val vs = listOf(
            JourneyTestFixtures.visit(null, "Praça", PlaceType.LEISURE, t(9), t(10)),
            JourneyTestFixtures.visit(null, "Praça", PlaceType.LEISURE, t(11), t(12)),
        )
        val d = JourneyMapAssembler.build(JourneyTestFixtures.diary(vs, emptyList()), JourneyTestFixtures.NOW)
        assertEquals(1, d.nodes[1].revisitCount)
    }
}

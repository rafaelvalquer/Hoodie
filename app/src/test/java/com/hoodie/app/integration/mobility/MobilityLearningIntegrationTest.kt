package com.hoodie.app.integration.mobility

import com.hoodie.app.core.datastore.MobilitySettings
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.context.GeofenceTransition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Depois de algumas repetições: 🏠 → 🚶 → 🚌 → 🚶 → 🏢 acontece praticamente sozinho."
 * Cada dia útil o mesmo trajeto; conta as perguntas que o Hoodie ainda faz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobilityLearningIntegrationTest {
    private lateinit var m: MobilityGraph

    @Before fun setUp() { m = MobilityGraph() }
    @After fun tearDown() = m.close()

    /** Dias úteis consecutivos a partir de segunda (a fixture usa dia do mês). */
    private val workdays = listOf(MONDAY, MONDAY + 1, MONDAY + 2, MONDAY + 3, MONDAY + 4, MONDAY + 7, MONDAY + 8)

    private fun questionsSince(t: Long) = runBlocking { m.g.db.questionDao().since(t).filter { it.kind.isMobility } }

    /** Um dia de trabalho: anda, ônibus, anda, chega. Responde o que for perguntado. */
    private fun commute(day: Int, transport: MovementMode = MovementMode.BUS, arrivalYes: Boolean = true): List<QuestionKind> {
        m.at(day, 7, 30)
        runBlocking { m.g.engine.arriveAt(m.home.id, m.now()) } // começa o dia em casa
        val start = m.now()
        m.at(day, 7, 47); m.move(DetectedMovement.WALKING)
        m.at(day, 7, 48); m.geofence(m.home, GeofenceTransition.EXIT)
        m.pending(QuestionKind.CONFIRM_MOVEMENT)?.let { m.answer(QuestionKind.CONFIRM_MOVEMENT, yes = true) }
        m.at(day, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
        m.at(day, 8, 20); m.move(DetectedMovement.STILL)
        m.pending(QuestionKind.SELECT_TRANSPORT_MODE)?.let { runBlocking { m.router.answerTransportMode(it.id, transport) } }
        m.at(day, 8, 21); m.move(DetectedMovement.WALKING)
        m.at(day, 8, 30); m.geofence(m.work, GeofenceTransition.ENTER)
        m.pending(QuestionKind.CONFIRM_ARRIVAL)?.let { m.answer(QuestionKind.CONFIRM_ARRIVAL, yes = arrivalYes) }
        m.pending(QuestionKind.CONFIRM_TRIP_PATTERN)?.let { m.answer(QuestionKind.CONFIRM_TRIP_PATTERN, yes = true) }
        return questionsSince(start).map { it.kind }
    }

    @Test
    fun `perguntas diminuem ate o trajeto ficar automatico`() {
        val asked = workdays.take(6).map { commute(it) }
        // Dia 1: tudo é novo — movimento, transporte e chegada.
        assertEquals(setOf(QuestionKind.CONFIRM_MOVEMENT, QuestionKind.SELECT_TRANSPORT_MODE, QuestionKind.CONFIRM_ARRIVAL), asked[0].toSet())
        // O total de perguntas nunca cresce de um dia para o outro…
        val counts = asked.map { it.size }
        assertTrue("perguntas por dia: $asked", counts.zipWithNext().all { (a, b) -> b <= a })
        // …o padrão é proposto uma vez…
        assertEquals(1, asked.flatten().count { it == QuestionKind.CONFIRM_TRIP_PATTERN })
        // …e no fim nada é perguntado.
        assertEquals("perguntas por dia: $asked", emptyList<QuestionKind>(), asked.last())
        assertEquals(UserContextType.WORK, m.context()!!.type)
        // O deslocamento aprendido continua completo: ônibus classificado sozinho, chegada automática.
        val last = m.sessions().first()
        assertEquals(listOf(MovementMode.WALKING, MovementMode.BUS, MovementMode.WALKING), m.segmentsOf(last.id).map { it.mode })
        assertTrue(last.arrivalAutoConfirmed)
        assertEquals(m.home.id, last.originPlaceId)
        assertEquals(m.work.id, last.destinationPlaceId)
    }

    @Test
    fun `3 chegadas confirmadas viram automaticas e uma correcao volta a perguntar`() {
        workdays.take(3).forEach { commute(it) }
        assertFalse(QuestionKind.CONFIRM_ARRIVAL in commute(workdays[3]))
        assertTrue(m.sessions().first().arrivalAutoConfirmed)
        // "Não estou no trabalho": o usuário troca o contexto à mão logo depois.
        m.at(workdays[3], 8, 35)
        runBlocking {
            m.g.engine.setManual(UserContextType.LEISURE)
            m.engine.onManualContext(UserContextType.LEISURE, m.now())
        }
        assertEquals(false, m.sessions().first().arrivalConfirmed)
        assertTrue("depois da correção a chegada volta a ser perguntada", QuestionKind.CONFIRM_ARRIVAL in commute(workdays[4]))
    }

    @Test
    fun `aprendizado desligado - continua perguntando`() {
        runBlocking { m.g.settings.setMobility(MobilitySettings(learnTrips = false)) }
        workdays.take(3).forEach { commute(it) }
        val day4 = commute(workdays[3])
        assertTrue(QuestionKind.CONFIRM_ARRIVAL in day4)
        assertTrue(QuestionKind.SELECT_TRANSPORT_MODE in day4)
        assertFalse(QuestionKind.CONFIRM_TRIP_PATTERN in day4)
    }

    @Test
    fun `transporte preferido nos ajustes classifica sem perguntar`() {
        runBlocking { m.g.settings.setMobility(MobilitySettings(preferredMode = MovementMode.CAR)) }
        val asked = commute(MONDAY)
        assertFalse(QuestionKind.SELECT_TRANSPORT_MODE in asked)
        assertEquals(MovementMode.CAR, m.segmentsOf(m.sessions().first().id)[1].mode)
    }

    @Test
    fun `usuario escolhe outro transporte - sem maioria volta a perguntar`() {
        // Ônibus, ônibus, carro, carro: nenhum modo tem 3 escolhas e 2/3.
        listOf(MovementMode.BUS, MovementMode.BUS, MovementMode.CAR, MovementMode.CAR).forEachIndexed { i, mode -> commute(workdays[i], mode) }
        assertTrue(QuestionKind.SELECT_TRANSPORT_MODE in commute(workdays[4], MovementMode.CAR))
    }
}

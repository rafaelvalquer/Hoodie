package com.hoodie.app.integration.mobility

import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.diary.DiaryMobilityMerger
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Critério de pronto da Mobilidade Contextual, de ponta a ponta, com Room e engines reais:
 *
 *     07:45 Casa → 07:47 anda → sai de casa ("Você saiu de Casa?" Sim) → 07:54 veículo
 *     → app aberto ("Como está se deslocando?" Ônibus) → 08:25 desce e anda
 *     → 08:31 geofence do Trabalho ("Chegou ao Trabalho?" Sim)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobilityEngineIntegrationTest {
    private lateinit var m: MobilityGraph

    @Before fun setUp() { m = MobilityGraph() }
    @After fun tearDown() = m.close()

    private fun scene(): SceneId = runBlocking {
        val ctx = m.context()!!.type
        val mode = m.repo.activeMode.first()
        VisualDirector.resolve(HoodieActivity.COMMUTING, ctx, commute = com.hoodie.app.core.model.CommuteStyle.RANDOM, mobilityMode = mode, variant = 1).scene
    }

    @Test
    fun `primeira viagem completa - casa, caminhada, onibus, caminhada, trabalho`() {
        m.at(MONDAY, 7, 45)
        assertEquals(UserContextType.HOME, m.context()!!.type)

        // 07:47 começa a andar — ainda dentro da geofence de casa: nada muda.
        m.at(MONDAY, 7, 47); m.move(DetectedMovement.WALKING)
        assertEquals(UserContextType.HOME, m.context()!!.type)
        assertEquals(0, m.asked(QuestionKind.CONFIRM_MOVEMENT))

        // Sai de casa: primeiras vezes → pergunta.
        m.at(MONDAY, 7, 48); m.geofence(m.home, GeofenceTransition.EXIT)
        assertEquals(UserContextType.COMMUTING, m.context()!!.type)
        assertTrue(m.g.notifier.questions.any { "Casa" in it.second && "saiu" in it.second.lowercase() })
        m.answer(QuestionKind.CONFIRM_MOVEMENT, yes = true)
        assertEquals(MobilityState.WALKING, m.open()!!.state)
        assertEquals(SceneId.STREET, scene())

        // 07:54 veículo: a escolha do transporte NÃO aparece com o veículo andando.
        m.at(MONDAY, 7, 54); m.move(DetectedMovement.IN_VEHICLE)
        assertEquals(MobilityState.IN_VEHICLE, m.open()!!.state)
        assertEquals(0, m.asked(QuestionKind.SELECT_TRANSPORT_MODE))
        assertTrue(m.open()!!.pendingModeQuestion)
        assertEquals("veículo sem classificação: transporte genérico", SceneId.TRANSIT, scene())

        // App aberto (passageiro olhando): agora pergunta, dentro do app.
        m.at(MONDAY, 8, 0); runBlocking { m.engine.onAppOpened() }
        val q = m.pending(QuestionKind.SELECT_TRANSPORT_MODE)
        assertNotNull(q)
        runBlocking { m.router.answerTransportMode(q!!.id, MovementMode.BUS) }
        assertEquals(MovementMode.BUS, m.open()!!.currentMode)
        assertEquals(SceneId.BUS, scene())

        // 08:25 desce e anda.
        m.at(MONDAY, 8, 25); m.move(DetectedMovement.WALKING)
        assertEquals(SceneId.STREET, scene())

        // 08:31 geofence do Trabalho.
        m.at(MONDAY, 8, 31); m.geofence(m.work, GeofenceTransition.ENTER)
        assertEquals(UserContextType.WORK, m.context()!!.type)
        assertEquals(SceneId.OFFICE, VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK).scene)
        assertNull("deslocamento encerrado", m.open())
        m.answer(QuestionKind.CONFIRM_ARRIVAL, yes = true)

        val sessions = m.sessions()
        assertEquals(1, sessions.size)
        val s = sessions.single()
        assertEquals(m.home.id, s.originPlaceId)
        assertEquals(m.work.id, s.destinationPlaceId)
        assertEquals(true, s.arrivalConfirmed)
        val segs = m.segmentsOf(s.id)
        assertEquals(listOf(MovementMode.WALKING, MovementMode.BUS, MovementMode.WALKING), segs.map { it.mode })
        assertTrue("todos os trechos fechados", segs.all { it.endedAt != null })

        // Diário: 3 trechos com horários, sem nenhum lugar intermediário.
        val day = runBlocking { m.repo.tripsBetween(com.hoodie.app.engine.at(MONDAY, 0).toInstant().toEpochMilli(), com.hoodie.app.engine.at(MONDAY, 23).toInstant().toEpochMilli()) }
        val items = DiaryMobilityMerger.merge(
            com.hoodie.app.domain.diary.model.DailyDiary(
                com.hoodie.app.domain.diary.model.DailySummary(java.time.LocalDate.of(2025, 1, 1)), emptyList(), emptyList(),
                com.hoodie.app.domain.diary.model.DiaryMapData(), com.hoodie.app.domain.diary.model.ReplaySequence.EMPTY,
            ),
            day, 0, Long.MAX_VALUE, m.now(), m.g.clock.zone(),
        ).timeline.map { "${it.emoji} ${it.subtitle}" }
        assertEquals(listOf("🚶 07:47–07:54 · 7 min", "🚌 07:54–08:25 · 31 min", "🚶 08:25–08:31 · 6 min"), items)
    }

    @Test
    fun `andar dentro de casa nao inicia deslocamento`() {
        m.at(MONDAY, 10, 0); m.move(DetectedMovement.WALKING)
        m.at(MONDAY, 10, 5); m.check()
        m.at(MONDAY, 10, 6); m.move(DetectedMovement.STILL)
        assertNull(m.open())
        assertEquals(UserContextType.HOME, m.context()!!.type)
        assertTrue(m.sessions().isEmpty())
        assertEquals(0, m.asked(QuestionKind.CONFIRM_MOVEMENT))
    }

    @Test
    fun `veiculo depois a pe cria novo trecho`() {
        startConfirmedWalk()
        m.at(MONDAY, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
        m.at(MONDAY, 8, 20); m.move(DetectedMovement.WALKING)
        val modes = m.segmentsOf(m.open()!!.id).map { it.mode }
        assertEquals(listOf(MovementMode.WALKING, MovementMode.VEHICLE_UNKNOWN, MovementMode.WALKING), modes)
        // Veículo nunca classificado: a pergunta aparece ao chegar (parado = seguro).
        m.at(MONDAY, 8, 30); m.geofence(m.work, GeofenceTransition.ENTER)
        assertEquals(1, m.asked(QuestionKind.SELECT_TRANSPORT_MODE))
    }

    @Test
    fun `oscilacao de GPS na borda nao cria viagem falsa`() {
        m.at(MONDAY, 9, 0); m.geofence(m.home, GeofenceTransition.EXIT)
        m.at(MONDAY, 9, 1); m.geofence(m.home, GeofenceTransition.ENTER)
        m.at(MONDAY, 9, 20); m.geofence(m.home, GeofenceTransition.EXIT)
        m.at(MONDAY, 9, 21); m.geofence(m.home, GeofenceTransition.ENTER)
        assertNull(m.open())
        assertTrue(m.sessions().isEmpty())
        assertEquals(UserContextType.HOME, m.context()!!.type)
    }

    @Test
    fun `saida e volta para casa em 2 min com caminhada e revertida`() {
        m.at(MONDAY, 9, 0); m.move(DetectedMovement.WALKING)
        m.at(MONDAY, 9, 0); m.geofence(m.home, GeofenceTransition.EXIT)
        assertNotNull(m.pending(QuestionKind.CONFIRM_MOVEMENT))
        m.at(MONDAY, 9, 2); m.geofence(m.home, GeofenceTransition.ENTER)
        assertNull(m.open())
        assertTrue(m.sessions().isEmpty())
        assertNull("a pergunta some junto", m.pending(QuestionKind.CONFIRM_MOVEMENT))
        assertEquals(UserContextType.HOME, m.context()!!.type)
    }

    @Test
    fun `usuario diz que nao saiu - candidato descartado e nada no diario`() {
        m.at(MONDAY, 9, 0); m.move(DetectedMovement.WALKING)
        m.geofence(m.home, GeofenceTransition.EXIT)
        m.answer(QuestionKind.CONFIRM_MOVEMENT, yes = false)
        assertNull(m.open())
        assertTrue(m.sessions().isEmpty())
    }

    @Test
    fun `app fechado - um processo novo continua do banco`() {
        startConfirmedWalk()
        m.restart()
        m.at(MONDAY, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
        m.restart()
        m.at(MONDAY, 8, 30); m.geofence(m.work, GeofenceTransition.ENTER)
        val s = m.sessions().single()
        assertEquals(listOf(MovementMode.WALKING, MovementMode.VEHICLE_UNKNOWN), m.segmentsOf(s.id).map { it.mode })
        assertEquals(m.work.id, s.destinationPlaceId)
    }

    @Test
    fun `bateria - deslocamento com geofence nao le posicao nenhuma vez`() {
        startConfirmedWalk()
        m.at(MONDAY, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
        m.at(MONDAY, 8, 20); m.move(DetectedMovement.STILL)
        m.at(MONDAY, 8, 21); m.move(DetectedMovement.WALKING)
        m.at(MONDAY, 8, 30); m.geofence(m.work, GeofenceTransition.ENTER)
        assertEquals(0, m.location.reads)
        assertFalse("checagem agendada só por tempo, nunca periódica de GPS", m.scheduler.scheduled.any { it < 60_000 })
    }

    /** Casa → caminhada confirmada às 07:47. */
    private fun startConfirmedWalk() {
        m.at(MONDAY, 7, 47); m.move(DetectedMovement.WALKING)
        m.geofence(m.home, GeofenceTransition.EXIT)
        m.answer(QuestionKind.CONFIRM_MOVEMENT, yes = true)
        assertEquals(MobilityState.WALKING, m.open()!!.state)
    }
}

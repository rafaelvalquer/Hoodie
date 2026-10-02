package com.hoodie.app.integration.mobility

import com.hoodie.app.core.datastore.MobilitySettings
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.context.GeofenceTransition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Chegada sem geofence: parado 3 min → uma leitura pontual → lugar conhecido ou novo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobilityArrivalIntegrationTest {
    private lateinit var m: MobilityGraph

    @Before fun setUp() { m = MobilityGraph() }
    @After fun tearDown() = m.close()

    private fun walkFromHome() {
        m.at(MONDAY, 7, 47); m.move(DetectedMovement.WALKING)
        m.geofence(m.home, GeofenceTransition.EXIT)
        m.answer(QuestionKind.CONFIRM_MOVEMENT, yes = true)
    }

    @Test
    fun `geofence falhou - parado 3 min e posicao dentro do trabalho e chegada`() {
        walkFromHome()
        m.at(MONDAY, 8, 30); m.move(DetectedMovement.STILL)
        m.at(MONDAY, 8, 31); m.check()
        assertTrue("antes de 3 min não resolve", m.open() != null)
        assertEquals(0, m.location.reads)
        m.g.location.position = -23.60 to -46.63 // dentro do Trabalho
        m.at(MONDAY, 8, 34); m.check()
        assertEquals(1, m.location.reads)
        assertNull(m.open())
        val s = m.sessions().single()
        assertEquals(m.work.id, s.destinationPlaceId)
        assertEquals("chegada vale desde quando parou", com.hoodie.app.engine.at(MONDAY, 8, 30).toInstant().toEpochMilli(), s.endedAt)
        assertEquals(UserContextType.WORK, m.context()!!.type)
    }

    @Test
    fun `local desconhecido - segue o fluxo atual de lugar novo`() {
        walkFromHome()
        m.at(MONDAY, 9, 0); m.move(DetectedMovement.STILL)
        m.g.location.position = -22.0 to -43.0 // longe de tudo
        m.at(MONDAY, 9, 4); m.check()
        assertNull(m.open())
        assertNull(m.sessions().single().destinationPlaceId)
        assertEquals(UserContextType.UNKNOWN, m.context()!!.type)
        assertEquals(1, m.asked(QuestionKind.NEW_PLACE))
    }

    @Test
    fun `confirmar locais novos desligado - nao pergunta`() {
        runBlocking { m.g.settings.setMobility(MobilitySettings(confirmNewPlaces = false)) }
        walkFromHome()
        m.at(MONDAY, 9, 0); m.move(DetectedMovement.STILL)
        m.g.location.position = -22.0 to -43.0
        m.at(MONDAY, 9, 4); m.check()
        assertEquals(UserContextType.UNKNOWN, m.context()!!.type)
        assertEquals(0, m.asked(QuestionKind.NEW_PLACE))
    }

    @Test
    fun `sem posicao e sem historico - espera a geofence`() {
        walkFromHome()
        m.at(MONDAY, 9, 0); m.move(DetectedMovement.STILL)
        m.g.location.position = null
        m.at(MONDAY, 9, 4); m.check()
        assertEquals(com.hoodie.app.core.mobility.MobilityState.ARRIVING, m.open()!!.state)
        m.at(MONDAY, 9, 6); m.geofence(m.work, GeofenceTransition.ENTER)
        assertEquals(m.work.id, m.sessions().single().destinationPlaceId)
    }

    @Test
    fun `veiculo andando - a pergunta do transporte espera o STILL`() {
        walkFromHome()
        m.at(MONDAY, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
        assertEquals("nada com o veículo andando", 0, m.asked(QuestionKind.SELECT_TRANSPORT_MODE))
        assertTrue(m.g.notifier.questions.none { "veículo" in it.second })
        m.at(MONDAY, 8, 10); m.move(DetectedMovement.STILL)
        assertEquals(1, m.asked(QuestionKind.SELECT_TRANSPORT_MODE))
    }
}

package com.hoodie.app.integration.mobility

import com.hoodie.app.core.datastore.MobilitySettings
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
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

/** Mobilidade ↔ ContextEngine: quem manda no contexto continua sendo o ContextEngine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobilityContextIntegrationTest {
    private lateinit var m: MobilityGraph

    @Before fun setUp() { m = MobilityGraph() }
    @After fun tearDown() = m.close()

    @Test
    fun `sem permissao - fallback atual identico, nenhuma sessao`() {
        m.arPermissions.granted = false
        m.at(MONDAY, 8, 0); m.move(DetectedMovement.WALKING)
        m.geofence(m.home, GeofenceTransition.EXIT)
        assertEquals(UserContextType.COMMUTING, m.context()!!.type)
        m.at(MONDAY, 8, 40); m.geofence(m.work, GeofenceTransition.ENTER)
        assertEquals(UserContextType.WORK, m.context()!!.type)
        assertNull(m.open())
        assertTrue(m.sessions().isEmpty())
        assertEquals(0, m.asked(QuestionKind.CONFIRM_MOVEMENT) + m.asked(QuestionKind.CONFIRM_ARRIVAL))
    }

    @Test
    fun `deteccao desligada nos ajustes - mesmo fallback`() {
        runBlocking { m.g.settings.setMobility(MobilitySettings(detectionEnabled = false)) }
        m.at(MONDAY, 8, 0); m.move(DetectedMovement.WALKING)
        m.geofence(m.home, GeofenceTransition.EXIT)
        assertNull(m.open())
        assertEquals(UserContextType.COMMUTING, m.context()!!.type)
    }

    @Test
    fun `geofence sem activity recognition - comportamento atual`() {
        // Permissão existe, mas nenhum movimento foi reconhecido (sensor calado).
        m.at(MONDAY, 8, 0); m.geofence(m.home, GeofenceTransition.EXIT)
        assertEquals(UserContextType.COMMUTING, m.context()!!.type)
        m.at(MONDAY, 8, 40); m.geofence(m.work, GeofenceTransition.ENTER)
        assertEquals(UserContextType.WORK, m.context()!!.type)
        assertTrue("sem movimento não há deslocamento registrado", m.sessions().isEmpty())
        assertNull(m.open())
    }

    @Test
    fun `correcao manual em restaurante concorda com contexto automatico DINING`() = runBlocking {
        m.at(MONDAY, 20, 0)
        val endedAt = m.now()
        m.repo.insert(
            MobilitySessionEntity(
                startedAt = endedAt - 10 * 60_000,
                endedAt = endedAt - 60_000,
                destinationPlaceId = m.restaurant.id,
                initialMode = MovementMode.WALKING,
                currentMode = MovementMode.WALKING,
                state = MobilityState.ARRIVED,
                confidence = 1f,
                confirmed = true,
                source = MobilitySource.GEOFENCE,
                arrivalConfirmed = true,
                arrivalAutoConfirmed = true,
            ),
        )
        m.g.engine.setManualPlace(PlaceType.RESTAURANT)
        m.engine.onManualContext(PlaceType.RESTAURANT.toContext(), m.now())

        assertEquals(UserContextType.DINING, m.context()!!.type)
        assertEquals(UserContextType.DINING, m.g.db.hoodieStateDao().get()!!.userContext)
        assertEquals(true, m.sessions().single().arrivalConfirmed)
    }

    @Test
    fun `movimento confirmado antes da geofence sair - ContextEngine passa a deslocamento`() {
        // Origem sem lugar conhecido (contexto manual de passeio): sem geofence para esperar.
        runBlocking { m.g.engine.setManual(UserContextType.LEISURE) }
        m.at(MONDAY, 15, 0); m.move(DetectedMovement.WALKING)
        assertEquals(UserContextType.LEISURE, m.context()!!.type)
        // Sustentado, mas sem geofence: confiança menor → pergunta (não aplica sozinho).
        m.at(MONDAY, 15, 3); m.check()
        assertEquals(1, m.asked(QuestionKind.CONFIRM_MOVEMENT))
        m.answer(QuestionKind.CONFIRM_MOVEMENT, yes = true)
        val ctx = m.context()!!
        assertEquals(UserContextType.COMMUTING, ctx.type)
        assertEquals(ContextSource.MOBILITY, ctx.source)
        assertTrue(m.g.scheduler.commuteCheck != null)
    }

    @Test
    fun `nao estou no trabalho - volta para deslocamento e a viagem continua`() {
        m.at(MONDAY, 7, 47); m.move(DetectedMovement.WALKING)
        m.geofence(m.home, GeofenceTransition.EXIT)
        m.answer(QuestionKind.CONFIRM_MOVEMENT, yes = true)
        m.at(MONDAY, 8, 30); m.geofence(m.work, GeofenceTransition.ENTER)
        assertEquals(UserContextType.WORK, m.context()!!.type)
        m.answer(QuestionKind.CONFIRM_ARRIVAL, yes = false)
        assertEquals(UserContextType.COMMUTING, m.context()!!.type)
        // A correção fica no histórico (aprendizado) e um deslocamento novo segue aberto.
        assertEquals(false, m.sessions().single().arrivalConfirmed)
        assertTrue(m.open() != null && m.open()!!.confirmed)
    }
}

package com.hoodie.app.integration

import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.SUNDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
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
 * Context Engine real + Room em memória: o dia de referência (CT-001…CT-008) e
 * as regras de transição (CT-TRANSITION-*).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContextEngineIntegrationTest {

    @Test fun facadeSerializesPlaceLearningAndManualContextWithoutNestedLocks() = runBlocking {
        g.clock.millis = at(MONDAY, 9).ms()
        val registering = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        g.geofences.beforeRegister = { registering.complete(Unit); release.await() }
        val save = async { g.engine.savePlaceHere(PlaceType.GYM, "Academia", -23.7, -46.63) }
        withTimeout(10_000) { registering.await() }
        val manual = async(start = CoroutineStart.UNDISPATCHED) { g.engine.setManual(UserContextType.WORK) }
        assertFalse("Manual context must wait for the facade's in-flight operation", manual.isCompleted)
        release.complete(Unit)
        withTimeout(10_000) { save.await(); manual.await() }
        assertHoodieFollows(UserContextType.WORK)
        assertEquals(1, events().count { it.endedAt == null })
        assertEquals(1, runBlocking { g.places.all().count { it.name == "Academia" } })
    }

    private lateinit var g: TestGraph
    private var homeId = 0L
    private var workId = 0L

    @Before
    fun setUp() = runBlocking {
        g = TestGraph(at(SUNDAY - 7, 20).ms())
        g.setRoutine(officeRoutine)
        // Domingo à noite (dia 4): a pessoa salva a casa estando nela.
        g.clock.millis = at(MONDAY - 1, 20).ms()
        homeId = g.engine.savePlaceHere(PlaceType.HOME, "Casa", -23.55, -46.63).id
        workId = g.addPlace(PlaceType.WORK, -23.60).id
    }

    @After
    fun tearDown() = g.close()

    private fun step(day: Int, h: Int, m: Int, block: suspend () -> Unit) = runBlocking {
        g.clock.millis = at(day, h, m).ms()
        block()
    }

    private fun current() = runBlocking { g.contextDao.current() }
    private fun hoodieState() = runBlocking { g.db.hoodieStateDao().get()!! }
    private fun events() = runBlocking { g.contextDao.all() }

    /** CT-TRANSITION-006: depois de qualquer boundary, o Hoodie já reflete o contexto novo. */
    private fun assertHoodieFollows(type: UserContextType) {
        assertEquals(type, current()!!.type)
        assertEquals("Hoodie deve reagir na mesma reconciliação", type, hoodieState().userContext)
    }

    @Test
    fun `restaurante por geofence separa almoco de refeicao fora da rotina`() {
        val restaurantId = runBlocking { g.addPlace(PlaceType.RESTAURANT, -23.62).id }
        listOf(
            Triple(12, 0, UserContextType.LUNCH),
            Triple(12, 45, UserContextType.LUNCH),
            Triple(16, 0, UserContextType.DINING),
            Triple(20, 0, UserContextType.DINING),
        ).forEach { (hour, minute, expected) ->
            // Simula um novo evento de entrada depois de sair do geofence entre as amostras.
            step(MONDAY, hour, minute) { g.engine.setManual(UserContextType.HOME) }
            step(MONDAY, hour, minute) { g.engine.onGeofence(restaurantId, GeofenceTransition.ENTER) }
            assertHoodieFollows(expected)
        }
    }

    @Test
    fun `CT-001 a CT-008 dia de referencia`() {
        step(MONDAY, 7, 0) { g.hoodie.resolve() }
        assertHoodieFollows(UserContextType.HOME) // CT-001

        step(MONDAY, 8, 3) { g.engine.onGeofence(homeId, GeofenceTransition.EXIT) }
        assertHoodieFollows(UserContextType.COMMUTING) // CT-002
        assertEquals(HoodieActivity.COMMUTING, hoodieState().activity)
        assertNotNull(g.scheduler.commuteCheck)

        step(MONDAY, 8, 41) { g.engine.onGeofence(workId, GeofenceTransition.ENTER) }
        assertHoodieFollows(UserContextType.WORK) // CT-003
        assertTrue(g.notifier.events.any { "chegou ao trabalho" in it })

        step(MONDAY, 12, 5) { g.engine.onGeofence(workId, GeofenceTransition.EXIT) }
        assertHoodieFollows(UserContextType.COMMUTING) // CT-004
        assertEquals(at(MONDAY, 12, 5).ms() to workId, g.scheduler.lunchCheck)

        step(MONDAY, 12, 20) { g.engine.onLunchCheck(at(MONDAY, 12, 5).ms(), workId) }
        assertHoodieFollows(UserContextType.LUNCH) // CT-005
        val lunch = current()!!
        assertEquals(at(MONDAY, 12, 20).ms(), lunch.startedAt)

        step(MONDAY, 12, 55) { g.engine.onGeofence(workId, GeofenceTransition.ENTER) }
        assertHoodieFollows(UserContextType.WORK) // CT-006
        assertEquals("volta do almoço não avisa chegada", 1, g.notifier.events.count { "chegou ao trabalho" in it })

        step(MONDAY, 17, 40) { g.engine.onGeofence(workId, GeofenceTransition.EXIT) }
        assertHoodieFollows(UserContextType.COMMUTING) // CT-007

        step(MONDAY, 18, 20) { g.engine.onGeofence(homeId, GeofenceTransition.ENTER) }
        assertHoodieFollows(UserContextType.HOME) // CT-008
        assertTrue(g.notifier.events.any { "de volta em casa" in it })

        step(MONDAY + 1, 1, 0) { g.hoodie.resolve() }
        assertEquals(HoodieActivity.SLEEPING, hoodieState().activity)

        val types = events().filter { it.startedAt >= at(MONDAY, 0).ms() }.map { it.type }
        assertEquals(
            listOf(
                UserContextType.COMMUTING, UserContextType.WORK, UserContextType.COMMUTING, UserContextType.LUNCH,
                UserContextType.WORK, UserContextType.COMMUTING, UserContextType.HOME,
            ),
            types,
        )
        // Eventos contíguos: cada um termina exatamente onde o próximo começa.
        events().zipWithNext().forEach { (a, b) -> assertEquals(a.endedAt, b.startedAt) }
    }

    @Test
    fun `CT-TRANSITION-001 COMMUTING para LUNCH cria dois eventos e nunca persiste COMMUTING`() {
        step(MONDAY, 8, 41) { g.engine.onGeofence(workId, GeofenceTransition.ENTER) }
        step(MONDAY, 12, 5) { g.engine.onGeofence(workId, GeofenceTransition.EXIT) }
        val commuting = current()!!
        step(MONDAY, 12, 20) { g.engine.onLunchCheck(at(MONDAY, 12, 5).ms(), workId) }

        val closed = runBlocking { g.contextDao.getById(commuting.id)!! }
        assertEquals(UserContextType.COMMUTING, closed.type)
        assertEquals(at(MONDAY, 12, 5).ms(), closed.startedAt)
        assertEquals(at(MONDAY, 12, 20).ms(), closed.endedAt)
        val lunch = current()!!
        assertEquals(UserContextType.LUNCH, lunch.type)
        assertEquals(at(MONDAY, 12, 20).ms(), lunch.startedAt)
        assertNull(lunch.endedAt)
        // Aceite: impossível o estado persistido continuar COMMUTING.
        assertEquals(UserContextType.LUNCH, hoodieState().userContext)
        assertTrue(g.timelineTexts().contains("Almoço"))
    }

    @Test
    fun `CT-TRANSITION-002 WORK para LEISURE apos Nao cria boundary`() {
        // Domingo no trabalho → aplica e pergunta.
        g.clock.millis = at(SUNDAY, 9).ms()
        step(SUNDAY, 9, 0) { g.engine.onGeofence(workId, GeofenceTransition.ENTER) }
        val work = current()!!
        assertEquals(UserContextType.WORK, work.type)
        val questionId = g.notifier.questions.single().first

        step(SUNDAY, 9, 30) { g.engine.answerYesNo(questionId, yes = false) }
        val leisure = current()!!
        assertEquals(UserContextType.LEISURE, leisure.type)
        assertTrue(leisure.id != work.id)
        assertEquals(at(SUNDAY, 9, 30).ms(), leisure.startedAt)
        assertEquals(UserContextType.WORK, runBlocking { g.contextDao.getById(work.id)!!.type })
        assertEquals(at(SUNDAY, 9, 30).ms(), runBlocking { g.contextDao.getById(work.id)!!.endedAt })
        assertHoodieFollows(UserContextType.LEISURE)
        assertTrue(g.timelineTexts().contains("Passeio (corrigido)"))
        assertTrue(questionId in g.notifier.cancelled)
    }

    @Test
    fun `resposta Sim so confirma o evento atual sem boundary`() {
        step(SUNDAY, 9, 0) { g.engine.onGeofence(workId, GeofenceTransition.ENTER) }
        val work = current()!!
        val questionId = g.notifier.questions.single().first
        step(SUNDAY, 9, 10) { g.engine.answerYesNo(questionId, yes = true) }
        val after = current()!!
        assertEquals(work.id, after.id)
        assertEquals(1f, after.confidence)
        assertEquals(ContextSource.CONFIRMATION, after.source)
    }

    @Test
    fun `CT-TRANSITION-003 HOME para manual GYM cria boundary`() {
        val home = current()!!
        step(MONDAY - 1, 21, 0) { g.engine.setManual(UserContextType.GYM) }
        val gym = current()!!
        assertEquals(UserContextType.GYM, gym.type)
        assertEquals(at(MONDAY - 1, 21).ms(), runBlocking { g.contextDao.getById(home.id)!!.endedAt })
        assertHoodieFollows(UserContextType.GYM)
    }

    @Test
    fun `CT-TRANSITION-004 mesmo contexto nao duplica evento`() {
        val before = events().size
        step(MONDAY - 1, 21, 0) { g.engine.setManual(UserContextType.HOME) }
        step(MONDAY - 1, 21, 30) { g.engine.setManual(UserContextType.HOME) }
        assertEquals(before, events().size)
    }

    @Test
    fun `CT-TRANSITION-005 mesmo lugar e mesmo contexto preserva o evento`() {
        val home = current()!!
        step(MONDAY - 1, 22, 0) { g.engine.onGeofence(homeId, GeofenceTransition.ENTER) }
        step(MONDAY - 1, 22, 5) { g.engine.onGeofence(homeId, GeofenceTransition.DWELL) }
        assertEquals(home.id, current()!!.id)
        assertEquals(1, events().size)
    }

    @Test
    fun `rotina descobre o lugar depois sem criar boundary`() = runBlocking {
        val g2 = TestGraph(at(MONDAY, 20).ms())
        try {
            g2.engine.applyRoutineFallbackIfNeeded()
            val routineHome = g2.contextDao.current()!!
            assertEquals(ContextSource.ROUTINE, routineHome.source)
            val place = g2.engine.savePlaceHere(PlaceType.HOME, "Casa", -23.5, -46.6)
            val enriched = g2.contextDao.current()!!
            assertEquals(routineHome.id, enriched.id)
            assertEquals(place.id, enriched.placeId)
        } finally {
            g2.close()
        }
    }

    @Test
    fun `evento atrasado nunca fecha o atual antes dele comecar`() {
        step(MONDAY, 8, 3) { g.engine.onGeofence(homeId, GeofenceTransition.EXIT) }
        // ENTER do trabalho "carimbado" antes da saída (entrega atrasada/fora de ordem).
        step(MONDAY, 8, 50) { g.engine.onGeofence(workId, GeofenceTransition.ENTER, at(MONDAY, 8, 0).ms()) }
        val all = events()
        all.forEach { e -> e.endedAt?.let { assertTrue("endedAt >= startedAt", it >= e.startedAt) } }
        assertEquals(UserContextType.WORK, current()!!.type)
    }

    @Test
    fun `saida atrasada de lugar onde nao estamos e ignorada`() {
        step(MONDAY, 9, 0) { g.engine.onGeofence(workId, GeofenceTransition.EXIT) }
        assertEquals(UserContextType.HOME, current()!!.type)
    }

    @Test
    fun `lugar novo vira contexto respondido com boundary e oferece salvar`() {
        step(MONDAY, 18, 0) { g.engine.onGeofence(homeId, GeofenceTransition.EXIT) }
        val commutingId = current()!!.id
        g.location.position = -23.9 to -46.9
        step(MONDAY, 18, 40) { g.engine.onCommuteCheck(commutingId) }
        assertEquals(UserContextType.UNKNOWN, current()!!.type)
        val newPlaceQ = g.notifier.questions.last().first

        step(MONDAY, 18, 45) { g.engine.answerNewPlace(newPlaceQ, PlaceType.GYM) }
        val gym = current()!!
        assertEquals(UserContextType.GYM, gym.type)
        assertEquals(at(MONDAY, 18, 45).ms(), gym.startedAt)
        assertHoodieFollows(UserContextType.GYM)

        val saveQ = runBlocking { g.db.questionDao().since(0).last() }
        step(MONDAY, 18, 46) { g.engine.answerSavePlace(saveQ.id, save = true) }
        val withPlace = current()!!
        assertEquals(gym.id, withPlace.id)
        assertNotNull(withPlace.placeId)
        assertTrue(g.geofences.registrations > 0)
    }

    @Test
    fun `sem localizacao segue a rotina provavel`() {
        g.location.state = com.hoodie.app.core.location.LocationPermissionState.NONE
        step(MONDAY, 10, 0) { g.engine.setManual(UserContextType.HOME) }
        // Modo manual segura por 4h; depois a rotina assume.
        step(MONDAY, 14, 30) { g.engine.applyRoutineFallbackIfNeeded() }
        assertEquals(UserContextType.WORK, current()!!.type)
        assertEquals(ContextSource.ROUTINE, current()!!.source)
        assertHoodieFollows(UserContextType.WORK)
        assertFalse(g.timelineTexts().isEmpty())
    }
}

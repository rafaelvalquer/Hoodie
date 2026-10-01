package com.hoodie.app.integration

import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
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

/** Jornadas 3, 4 e 5 + oscilação de GPS (CT-FLAP) e recuperação após ausência longa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EndToEndJourneysTest {

    private lateinit var g: TestGraph
    private var homeId = 0L
    private var workId = 0L

    @Before
    fun setUp() = runBlocking {
        g = TestGraph(at(MONDAY - 1, 20).ms())
        g.setRoutine(officeRoutine)
        homeId = g.engine.savePlaceHere(PlaceType.HOME, "Casa", -23.55, -46.63).id
        workId = g.addPlace(PlaceType.WORK, -23.60).id
    }

    @After
    fun tearDown() = g.close()

    private fun at(day: Int, h: Int, m: Int = 0, block: suspend () -> Unit) = runBlocking {
        g.clock.millis = at(day, h, m).ms()
        block()
    }

    private fun current() = runBlocking { g.contextDao.current()!! }
    private fun state() = runBlocking { g.db.hoodieStateDao().get()!! }

    @Test
    fun `CT-FLAP-001 e 002 saida e volta em 3 min nao deixa rastro`() {
        at(MONDAY, 7, 30) { g.hoodie.resolve() }
        val home = current()
        val timelineBefore = g.timelineTexts()

        at(MONDAY, 8, 15) { g.engine.onGeofence(homeId, GeofenceTransition.EXIT) }
        assertEquals(UserContextType.COMMUTING, current().type)
        assertTrue(g.timelineTexts().any { it.startsWith("Saiu de") })

        at(MONDAY, 8, 18) { g.engine.onGeofence(homeId, GeofenceTransition.ENTER) }
        // Continua HOME, o mesmo evento reaberto.
        assertEquals(home.id, current().id)
        assertEquals(null, current().endedAt)
        assertEquals(1, runBlocking { g.contextDao.all() }.size)
        // Sem timeline falsa.
        assertFalse(g.timelineTexts().any { it.startsWith("Saiu de") })
        assertFalse(g.timelineTexts().any { "deslocamento" in it.lowercase() || "saiu de casa" in it.lowercase() })
        assertTrue(g.timelineTexts().containsAll(timelineBefore))
        // Hoodie: nada vivido no contexto desfeito.
        val acts = runBlocking { g.db.hoodieActivityDao().overlapping(0, Long.MAX_VALUE) }
        assertFalse(acts.any { it.userContext == UserContextType.COMMUTING })
        assertEquals(UserContextType.HOME, state().userContext)
        assertTrue(state().activity != HoodieActivity.COMMUTING)
        // Sem notificação falsa nem checagem pendente.
        assertTrue(g.notifier.events.isEmpty())
        assertEquals(null, g.scheduler.commuteCheck)
    }

    @Test
    fun `CT-FLAP-003 voltar depois de 5 min e deslocamento real`() {
        at(MONDAY, 8, 15) { g.engine.onGeofence(homeId, GeofenceTransition.EXIT) }
        at(MONDAY, 8, 25) { g.engine.onGeofence(homeId, GeofenceTransition.ENTER) }
        val types = runBlocking { g.contextDao.all() }.map { it.type }
        assertEquals(listOf(UserContextType.HOME, UserContextType.COMMUTING, UserContextType.HOME), types)
    }

    @Test
    fun `timeline guarda a origem de cada linha`() = runBlocking {
        g.clock.millis = at(MONDAY, 8, 3).ms()
        g.engine.onGeofence(homeId, GeofenceTransition.EXIT)
        val ctx = g.contextDao.current()!!
        val rows = g.db.timelineDao().bySource(TimelineSourceType.CONTEXT, ctx.id)
        assertEquals(1, rows.size)
        assertEquals("Saiu de: Casa", rows.single().text)
        val all = g.db.timelineDao().range(0, Long.MAX_VALUE)
        assertTrue(all.any { it.sourceType == TimelineSourceType.HOODIE_ACTIVITY })
        assertTrue(all.any { it.sourceType == TimelineSourceType.MEMORY })
        assertTrue(all.all { it.sourceType != null })
    }

    @Test
    fun `Jornada 2 dia completo com Hoodie timeline e memorias`() {
        at(MONDAY, 8, 3) { g.engine.onGeofence(homeId, GeofenceTransition.EXIT) }
        at(MONDAY, 8, 41) { g.engine.onGeofence(workId, GeofenceTransition.ENTER) }
        at(MONDAY, 12, 5) { g.engine.onGeofence(workId, GeofenceTransition.EXIT) }
        at(MONDAY, 12, 20) { g.engine.onLunchCheck(at(MONDAY, 12, 5).ms(), workId) }
        at(MONDAY, 12, 55) { g.engine.onGeofence(workId, GeofenceTransition.ENTER) }
        at(MONDAY, 17, 40) { g.engine.onGeofence(workId, GeofenceTransition.EXIT) }
        at(MONDAY, 18, 20) { g.engine.onGeofence(homeId, GeofenceTransition.ENTER) }
        at(MONDAY + 1, 1) { g.hoodie.resolve() }

        assertEquals(HoodieActivity.SLEEPING, state().activity)
        val texts = g.timelineTexts()
        listOf("Saiu de: Casa", "Trabalho", "Almoço", "Casa").forEach { assertTrue("timeline tem $it", texts.any { t -> t.startsWith(it) }) }
        val memories = runBlocking { g.db.memoryDao().exists("FIRST_WORK") to g.db.memoryDao().exists("FIRST_LUNCH") }
        assertEquals(true to true, memories)
        val acts = runBlocking { g.db.hoodieActivityDao().overlapping(at(MONDAY, 0).ms(), at(MONDAY + 1, 2).ms()) }
        assertTrue(acts.any { it.activity == HoodieActivity.COMMUTING })
        assertTrue(acts.any { it.userContext == UserContextType.WORK })
        assertTrue(acts.any { it.userContext == UserContextType.LUNCH })
    }

    @Test
    fun `Jornada 3 app fechado mostra trabalho desde 9h e Hoodie trabalhando`() {
        // Processo morto: só os receivers rodam (geofence → engine). Ninguém abre a UI.
        at(MONDAY, 8, 30) { g.engine.onGeofence(homeId, GeofenceTransition.EXIT) }
        at(MONDAY, 9, 0) { g.engine.onGeofence(workId, GeofenceTransition.ENTER) }
        // Abre o app às 10:30.
        at(MONDAY, 10, 30) { g.hoodie.resolve() }
        val ctx = current()
        assertEquals(UserContextType.WORK, ctx.type)
        assertEquals(at(MONDAY, 9).ms(), ctx.startedAt)
        assertEquals(UserContextType.WORK, state().userContext)
        assertTrue(state().activity in setOf(HoodieActivity.WORKING, HoodieActivity.COFFEE, HoodieActivity.RESTING, HoodieActivity.PHONE, HoodieActivity.IDLE))
    }

    @Test
    fun `Jornada 5 sem localizacao o Hoodie continua vivendo pela rotina`() {
        g.location.state = LocationPermissionState.NONE
        at(MONDAY, 10) { g.engine.applyRoutineFallbackIfNeeded() }
        assertEquals(UserContextType.WORK, current().type)
        at(MONDAY, 12, 30) { g.engine.applyRoutineFallbackIfNeeded() }
        assertEquals(UserContextType.LUNCH, current().type)
        assertEquals(UserContextType.LUNCH, state().userContext)
        at(MONDAY, 20) { g.engine.applyRoutineFallbackIfNeeded() }
        assertEquals(UserContextType.HOME, current().type)
    }

    @Test
    fun `CT-RECOVERY-001 ausencia longa recomeca sem inventar historico`() {
        at(MONDAY, 9) { g.hoodie.resolve() }
        val activitiesBefore = runBlocking { g.db.hoodieActivityDao().count() }
        runBlocking {
            g.clock.millis = at(MONDAY, 9).ms() + 5 * DAY_MS
            g.hoodie.resolve()
        }
        assertTrue(g.timelineTexts().any { it.endsWith("retomou a rotina.") })
        assertEquals(activitiesBefore, runBlocking { g.db.hoodieActivityDao().count() })
        assertEquals(g.clock.millis, state().startedAt)
    }

    @Test
    fun `CT-DATE-004 relogio voltando nao quebra o estado`() {
        at(MONDAY, 10) { g.hoodie.resolve() }
        val snapshot = runBlocking {
            g.clock.millis = at(MONDAY, 9).ms()
            g.hoodie.resolve()
        }
        assertTrue(snapshot.liveNeeds.energy in 0..100)
        assertTrue(snapshot.liveNeeds.hunger in 0..100)
    }
}

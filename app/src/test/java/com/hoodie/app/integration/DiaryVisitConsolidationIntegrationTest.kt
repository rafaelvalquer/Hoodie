package com.hoodie.app.integration

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.RoomTransactionRunner
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.data.repository.DiaryRepository
import com.hoodie.app.domain.correction.CorrectionTargetType
import com.hoodie.app.domain.correction.DiaryCorrection
import com.hoodie.app.domain.correction.UndoResult
import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.engine.correction.DiaryCorrectionService
import com.hoodie.app.engine.correction.PhoneContextRecalculator
import com.hoodie.app.engine.diary.DayClockAssembler
import com.hoodie.app.engine.diary.JourneyMapAssembler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Casa → Academia → Casa → Trabalho no Room; o usuário corrige a Academia para Casa e a Jornada
 * passa a ter uma Casa só (3 eventos de origem). Desfazer devolve os quatro nós; nenhum registro
 * canônico é apagado e a projeção não é gravada em lugar nenhum.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiaryVisitConsolidationIntegrationTest {
    private val date = LocalDate.of(2026, 10, 5)
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun at(h: Int, m: Int = 0) = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private lateinit var g: TestGraph
    private var home = 0L
    private var gym = 0L
    private var work = 0L
    private var morning = 0L
    private var gymVisit = 0L
    private var lateMorning = 0L

    @Before fun setup() = runBlocking {
        g = TestGraph(at(20))
        home = g.addPlace(PlaceType.HOME, -23.50).id
        gym = g.addPlace(PlaceType.GYM, -23.55).id
        work = g.addPlace(PlaceType.WORK, -23.60).id
        morning = g.contextDao.insert(ContextEventEntity(type = UserContextType.HOME, startedAt = at(8), endedAt = at(9), confidence = .9f, placeId = home, source = ContextSource.GEOFENCE))
        gymVisit = g.contextDao.insert(ContextEventEntity(type = UserContextType.GYM, startedAt = at(9), endedAt = at(10), confidence = .5f, placeId = gym, source = ContextSource.GEOFENCE))
        lateMorning = g.contextDao.insert(ContextEventEntity(type = UserContextType.HOME, startedAt = at(10), endedAt = at(11), confidence = .9f, placeId = home, source = ContextSource.GEOFENCE))
        // Sem COMMUTING de propósito: o Desfazer existente não cobre janelas com deslocamento.
        g.contextDao.insert(ContextEventEntity(type = UserContextType.WORK, startedAt = at(11, 30), endedAt = at(17), confidence = .9f, placeId = work, source = ContextSource.GEOFENCE))
        Unit
    }

    @After fun close() = g.close()

    private fun repo(graph: TestGraph = g) = DiaryRepository(graph.db.contextEventDao(), graph.db.timelineDao(), graph.db.hoodieActivityDao(), graph.db.placeDao(), graph.clock)
    private fun load(graph: TestGraph = g): DailyDiary = runBlocking { repo(graph).loadDiary(date) }
    private fun places(d: DailyDiary) = JourneyMapAssembler.build(d, at(20)).nodes.map { it.placeId }

    @Test fun correctingTheGymMergesHomeAndUndoRestoresIt() = runBlocking {
        assertEquals(listOf(home, gym, home, work), places(load()))

        val service = DiaryCorrectionService(g.db, RoomTransactionRunner(g.db), PhoneContextRecalculator(g.db, g.clock), g.clock, notifier = g.notifier)
        val pending = g.undoService.begin(at(0))
        service.save(DiaryCorrection(CorrectionTargetType.CONTEXT, gymVisit, UserContextType.HOME, home, at(9), at(10)))
        val operation = requireNotNull(g.undoService.commit(pending, "diary"))

        val corrected = load()
        val journey = JourneyMapAssembler.build(corrected, at(20))
        assertEquals(listOf(home, work), journey.nodes.map { it.placeId })
        val stay = journey.nodes.first()
        assertEquals(at(8), stay.arrivalAt)
        assertEquals(at(11), stay.departureAt)
        assertEquals(3 * 3_600_000L, stay.durationMs)
        assertEquals(listOf(morning, gymVisit, lateMorning), stay.sourceIntervals.map { it.contextEventId })
        assertTrue(stay.sourceIntervals.single { it.contextEventId == gymVisit }.corrected)
        assertEquals(1, journey.segments.size)
        // Relógio e replay concordam com o mapa.
        val clock = DayClockAssembler.build(corrected, date, zone, at(20))
        assertEquals(1, clock.segments.filterIsInstance<ClockSegment.Stay>().count { it.id == "journey-0" })
        assertEquals("visit-0", corrected.replay.frameAt(at(9, 30)).activeNodeId)
        // Originais intactas: o Diário continua com as três visitas à Casa e o Room com todos os eventos.
        assertEquals(4, corrected.visits.size)
        assertEquals(4, g.contextDao.all().size)

        assertEquals(UndoResult.Restored, g.undoService.undo(operation))
        assertEquals(listOf(home, gym, home, work), places(load()))
        assertEquals(UserContextType.GYM, g.contextDao.getById(gymVisit)!!.type)
    }

    @Test fun restoredCanonicalRowsRebuildTheSameJourney() = runBlocking {
        g.contextDao.update(g.contextDao.getById(gymVisit)!!.copy(type = UserContextType.HOME, placeId = home, source = ContextSource.USER_CORRECTION))
        val original = JourneyMapAssembler.build(load(), at(20))
        // "Restaurar": só os registros canônicos vão para um banco novo — o agrupamento não é um dado gravado.
        val restored = TestGraph(at(20))
        try {
            g.db.placeDao().getAll().forEach { restored.db.placeDao().insert(it) }
            g.contextDao.all().forEach { restored.contextDao.insert(it) }
            assertEquals(original, JourneyMapAssembler.build(load(restored), at(20)))
        } finally { restored.close() }
    }
}

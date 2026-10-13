package com.hoodie.app.integration

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.correction.CorrectionScope
import com.hoodie.app.domain.correction.QuickContextCorrection
import com.hoodie.app.domain.correction.QuickCorrectionResult
import com.hoodie.app.domain.correction.UndoBlockReason
import com.hoodie.app.domain.correction.UndoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** COR-02, COR-03, COR-04, COR-05 e COR-06: correção rápida com lugar exato e Desfazer seguro. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickCorrectionUndoIntegrationTest {
    private lateinit var g: TestGraph
    private var homeId = 0L
    private var restaurantA = 0L
    private var restaurantB = 0L
    private var eventId = 0L
    private val start get() = g.clock.millis - 2 * 3_600_000L

    @Before fun setUp() = runBlocking {
        g = TestGraph(1_790_000_000_000L)
        homeId = g.addPlace(PlaceType.HOME, -23.50).id
        restaurantA = g.addPlace(PlaceType.RESTAURANT, -23.51).id
        restaurantB = g.addPlace(PlaceType.RESTAURANT, -23.52).id
        eventId = g.contextDao.insert(ContextEventEntity(type = UserContextType.HOME, startedAt = start, endedAt = null, confidence = .8f, placeId = homeId, source = ContextSource.GEOFENCE))
    }

    @After fun tearDown() = g.close()

    private fun request(scope: CorrectionScope, placeId: Long?) = QuickContextCorrection(eventId, PlaceType.RESTAURANT, placeId, scope)

    @Test fun `desde a chegada usa o lugar exato e nao o primeiro do tipo`() = runBlocking {
        val result = g.engine.correctWithUndo(request(CorrectionScope.SINCE_CURRENT_ARRIVAL, restaurantB))
        assertTrue(result is QuickCorrectionResult.Applied)
        val event = g.contextDao.getById(eventId)!!
        assertEquals(restaurantB, event.placeId)
        assertEquals(UserContextType.DINING, event.type)
        assertEquals(ContextSource.USER_CORRECTION, event.source)
    }

    @Test fun `a partir de agora preserva o historico anterior`() = runBlocking {
        val result = g.engine.correctWithUndo(request(CorrectionScope.FROM_NOW, restaurantB)) as QuickCorrectionResult.Applied
        assertNotNull(result.operationId)
        val old = g.contextDao.getById(eventId)!!
        assertEquals(UserContextType.HOME, old.type)
        assertEquals(g.clock.millis, old.endedAt)
        val current = g.contextDao.current()!!
        assertEquals(restaurantB, current.placeId)
        assertEquals(UserContextType.DINING, current.type)
    }

    @Test fun `desfazer imediatamente restaura o estado canonico anterior`() = runBlocking {
        val original = g.contextDao.getById(eventId)!!
        val result = g.engine.correctWithUndo(request(CorrectionScope.SINCE_CURRENT_ARRIVAL, restaurantA)) as QuickCorrectionResult.Applied
        assertEquals(UndoResult.Restored, g.engine.undoCorrection(result.operationId!!))
        assertEquals(original, g.contextDao.getById(eventId))
        assertEquals(eventId, g.contextDao.current()!!.id)
    }

    @Test fun `desfazer a partir de agora remove o novo evento e reabre o anterior`() = runBlocking {
        val original = g.contextDao.getById(eventId)!!
        val result = g.engine.correctWithUndo(request(CorrectionScope.FROM_NOW, restaurantA)) as QuickCorrectionResult.Applied
        assertEquals(UndoResult.Restored, g.engine.undoCorrection(result.operationId!!))
        assertEquals(original, g.contextDao.getById(eventId))
        assertEquals(1, g.contextDao.count())
        assertEquals(0, g.db.confirmationDao().idsAfter(0).size)
    }

    @Test fun `nova mudanca de contexto bloqueia o desfazer sem tocar nos dados`() = runBlocking {
        val result = g.engine.correctWithUndo(request(CorrectionScope.SINCE_CURRENT_ARRIVAL, restaurantA)) as QuickCorrectionResult.Applied
        g.clock.millis += 60_000
        g.engine.setManualPlace(PlaceType.GYM)
        val before = g.contextDao.all()
        assertEquals(UndoResult.Blocked(UndoBlockReason.CONFLICT), g.engine.undoCorrection(result.operationId!!))
        assertEquals(before, g.contextDao.all())
    }

    @Test fun `desfazer duas vezes e seguro`() = runBlocking {
        val result = g.engine.correctWithUndo(request(CorrectionScope.SINCE_CURRENT_ARRIVAL, restaurantA)) as QuickCorrectionResult.Applied
        assertEquals(UndoResult.Restored, g.engine.undoCorrection(result.operationId!!))
        assertEquals(UndoResult.Blocked(UndoBlockReason.ALREADY_UNDONE), g.engine.undoCorrection(result.operationId!!))
    }

    @Test fun `desfazer expira`() = runBlocking {
        val result = g.engine.correctWithUndo(request(CorrectionScope.SINCE_CURRENT_ARRIVAL, restaurantA)) as QuickCorrectionResult.Applied
        g.clock.millis += 11 * 60_000L
        assertEquals(UndoResult.Blocked(UndoBlockReason.EXPIRED), g.engine.undoCorrection(result.operationId!!))
        assertEquals(restaurantA, g.contextDao.getById(eventId)!!.placeId)
    }

    @Test fun `evento esperado diferente pede nova conferencia`() = runBlocking {
        val result = g.engine.correctWithUndo(request(CorrectionScope.SINCE_CURRENT_ARRIVAL, restaurantA).copy(expectedEventId = eventId + 99))
        assertEquals(QuickCorrectionResult.Stale, result)
        assertEquals(UserContextType.HOME, g.contextDao.getById(eventId)!!.type)
    }

    @Test fun `operacao inexistente`() = runBlocking {
        assertEquals(UndoResult.NotFound, g.engine.undoCorrection("nao-existe"))
        assertNull(g.db.correctionUndoDao().byOperation("nao-existe"))
    }
}
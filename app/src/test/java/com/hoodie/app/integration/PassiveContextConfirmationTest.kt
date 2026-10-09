package com.hoodie.app.integration

import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.SUNDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PassiveContextConfirmationTest {
    private lateinit var graph: TestGraph
    private var homeId = 0L
    private var workId = 0L

    @Before fun setUp() = runBlocking {
        graph = TestGraph(at(SUNDAY - 7, 20).ms())
        graph.setRoutine(officeRoutine)
        graph.clock.millis = at(MONDAY - 1, 20).ms()
        homeId = graph.engine.savePlaceHere(PlaceType.HOME, "Casa", -23.55, -46.63).id
        workId = graph.addPlace(PlaceType.WORK, -23.60).id
    }

    @After fun tearDown() = graph.close()

    @Test fun lowConfidenceGeofenceStillRecordsUnknownContextWithoutCreatingQuestion() = runBlocking {
        graph.db.placeDao().let { dao -> dao.update(dao.getById(workId)!!.copy(confirmationCount = 1)) }
        graph.clock.millis = at(SUNDAY, 9).ms()

        graph.engine.onGeofence(workId, GeofenceTransition.ENTER)

        val event = graph.contextDao.current()
        assertNotNull(event)
        assertEquals(UserContextType.UNKNOWN, event!!.type)
        assertEquals(ContextSource.GEOFENCE, event.source)
        assertTrue("the automatic score remains an estimate", event.confidence < 1f)
        assertTrue(graph.db.questionDao().since(0).none { it.kind == QuestionKind.CONFIRM_CONTEXT })
        assertTrue(graph.notifier.questions.isEmpty())
    }

    @Test fun legacyContextConfirmationIsRetiredButTransportQuestionRemainsPending() = runBlocking {
        val contextQuestion = graph.db.questionDao().insert(ContextQuestionEntity(
            kind = QuestionKind.CONFIRM_CONTEXT, candidate = UserContextType.WORK, placeId = workId,
            encryptedCoordinates = null, contextEventId = null, askedAt = graph.clock.millis,
        ))
        val transportQuestion = graph.db.questionDao().insert(ContextQuestionEntity(
            kind = QuestionKind.SELECT_TRANSPORT_MODE, candidate = UserContextType.COMMUTING, placeId = null,
            encryptedCoordinates = null, contextEventId = null, askedAt = graph.clock.millis,
        ))

        graph.engine.dismissPendingContextConfirmations()

        assertEquals("PASSIVE_POLICY", graph.db.questionDao().getById(contextQuestion)!!.answer)
        assertNotNull(graph.db.questionDao().getById(contextQuestion)!!.answeredAt)
        assertNull(graph.db.questionDao().getById(transportQuestion)!!.answeredAt)
        assertTrue(contextQuestion in graph.notifier.cancelled)
        assertFalse(transportQuestion in graph.notifier.cancelled)
    }

    @Test fun currentCorrectionChecksExpectedEventInsideContextEngineAndUpdatesDiary() = runBlocking {
        graph.engine.setManualPlace(PlaceType.WORK)
        val openedOn = graph.contextDao.current()!!.id
        graph.engine.setManualPlace(PlaceType.HOME)

        assertFalse(graph.engine.correctCurrentContext(openedOn, PlaceType.RESTAURANT, sinceActivityStart = false))
        assertEquals(UserContextType.HOME, graph.contextDao.current()!!.type)

        val currentId = graph.contextDao.current()!!.id
        assertTrue(graph.engine.correctCurrentContext(currentId, PlaceType.RESTAURANT, sinceActivityStart = true))
        val corrected = graph.contextDao.current()!!
        assertEquals(currentId, corrected.id)
        assertEquals(UserContextType.DINING, corrected.type)
        assertEquals(ContextSource.USER_CORRECTION, corrected.source)
        assertEquals("HOME", graph.db.intelligenceDao().correctionsSince(0).single().originalContext)
        assertEquals("DINING", graph.db.intelligenceDao().correctionsSince(0).single().correctedContext)
        assertTrue(graph.timelineTexts().any { "Restaurante" in it || "Almoço" in it })
    }

    @Test fun fromNowCorrectionWithNoPriorContextIsAllowedOnlyWhileStillUnknown() = runBlocking {
        val prior = graph.contextDao.current()!!
        graph.contextDao.update(prior.copy(endedAt = graph.clock.millis))
        assertTrue(graph.engine.correctCurrentContext(null, PlaceType.HOME, sinceActivityStart = false))
        val created = graph.contextDao.current()!!
        assertEquals(UserContextType.HOME, created.type)
        assertEquals(ContextSource.USER_CORRECTION, created.source)
        assertFalse(graph.engine.correctCurrentContext(null, PlaceType.WORK, sinceActivityStart = false))
        assertEquals(created.id, graph.contextDao.current()!!.id)
        assertEquals(homeId, graph.contextDao.current()!!.placeId)
    }
}

package com.hoodie.app.integration

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.context.ContextEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewPlaceSaveIntegrationTest {
    @Test fun aLocationCheckWhileDrivingDoesNotAskForAPlaceOrReplaceTheCommute() = runBlocking {
        val m = com.hoodie.app.integration.mobility.MobilityGraph()
        try {
            m.at(com.hoodie.app.engine.MONDAY, 7, 47)
            m.move(com.hoodie.app.core.mobility.DetectedMovement.WALKING)
            m.geofence(m.home, com.hoodie.app.engine.context.GeofenceTransition.EXIT)
            m.at(com.hoodie.app.engine.MONDAY, 7, 55)
            m.move(com.hoodie.app.core.mobility.DetectedMovement.IN_VEHICLE)
            val commute = m.context()
            m.g.engine.arriveAtPosition(0.0, 0.0, askNewPlace = true)
            assertEquals(commute, m.context())
            assertEquals(0, m.asked(QuestionKind.NEW_PLACE))
        } finally { m.close() }
    }

    private suspend fun unknownVisit(g: TestGraph, coordinates: String? = "0.0,0.0", endedAt: Long? = null): Pair<Long, Long> {
        val event = g.contextDao.insert(ContextEventEntity(type = UserContextType.UNKNOWN, startedAt = 1_000,
            endedAt = endedAt, confidence = .5f, placeId = null, source = ContextSource.LOCATION_CHECK))
        val question = g.db.questionDao().insert(ContextQuestionEntity(kind = QuestionKind.NEW_PLACE, candidate = null,
            placeId = null, encryptedCoordinates = coordinates, contextEventId = event, askedAt = 1_000))
        return event to question
    }

    private suspend fun saveQuestion(g: TestGraph) = g.db.questionDao().since(0).last { it.kind == QuestionKind.SAVE_PLACE }

    private fun secondEngine(g: TestGraph) = ContextEngine(g.contextDao, g.transitions, g.db.confirmationDao(),
        g.db.questionDao(), g.db.locationEventDao(), g.places, g.routines, g.settings, g.memory, g.hoodie,
        g.notifier, g.scheduler, g.location, g.geofences, PlainCipher, g.clock, g.log)

    @Test fun nameAndPlaceArePersistedOnceAcrossConcurrentFacades() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val (eventId, questionId) = unknownVisit(g)
            g.engine.answerNewPlace(questionId, PlaceType.RESTAURANT)
            val q = saveQuestion(g)
            val otherEngine = secondEngine(g)
            listOf(async { g.engine.answerSavePlace(q.id, true, "  Cantina da Praça  ") },
                async { otherEngine.answerSavePlace(q.id, true, "Cantina da Praça") }).awaitAll()
            val place = g.places.all().single()
            assertEquals("Cantina da Praça", place.name)
            assertEquals(PlaceType.RESTAURANT, place.type)
            assertEquals(0.0, place.latitude, 0.0)
            assertEquals(place.id, g.contextDao.getById(eventId)!!.placeId)
            assertEquals(place.id, g.db.questionDao().getById(q.id)!!.placeId)
            assertNotNull(g.db.questionDao().getById(q.id)!!.answeredAt)
            otherEngine.answerSavePlace(q.id, true, "Outro nome")
            assertEquals(place, g.places.byId(place.id))
        } finally { g.close() }
    }

    @Test fun onlyTodayClassifiesTheVisitWithoutCreatingAPlace() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val (eventId, questionId) = unknownVisit(g, coordinates = null)
            g.engine.answerNewPlace(questionId, PlaceType.RESTAURANT)
            val q = saveQuestion(g)
            assertFalse(g.engine.newPlaceSaveOptions(q.id)!!.hasLocation)
            g.engine.answerSavePlace(q.id, false)
            assertEquals(UserContextType.DINING, g.contextDao.getById(eventId)!!.type)
            assertEquals(1_000L, g.contextDao.getById(eventId)!!.startedAt)
            assertEquals(ContextSource.CONFIRMATION, g.contextDao.getById(eventId)!!.source)
            assertNull(g.contextDao.getById(eventId)!!.placeId)
            assertTrue(g.places.all().isEmpty())
            assertEquals("TODAY", g.db.questionDao().getById(q.id)!!.answer)
        } finally { g.close() }
    }

    @Test fun lateAnswerClassifiesAndLinksOnlyTheOriginalHistoricalInterval() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val (eventId, questionId) = unknownVisit(g, endedAt = 2_000)
            val presentId = g.contextDao.insert(ContextEventEntity(type = UserContextType.HOME, startedAt = 2_000,
                endedAt = null, confidence = 1f, placeId = null, source = ContextSource.MANUAL))
            val present = g.contextDao.getById(presentId)
            g.engine.answerNewPlace(questionId, PlaceType.RESTAURANT)
            g.engine.answerSavePlace(saveQuestion(g).id, true, "Cantina da Praça")
            val historical = g.contextDao.getById(eventId)!!
            assertEquals(1_000L, historical.startedAt)
            assertEquals(2_000L, historical.endedAt)
            assertEquals(UserContextType.DINING, historical.type)
            assertNotNull(historical.placeId)
            val saved = g.places.byId(historical.placeId!!)!!
            assertEquals(1_000L, saved.lastVisitedAt)
            assertEquals(10_000L, saved.createdAt)
            assertEquals(1, saved.confirmationCount)
            assertEquals(present, g.contextDao.current())
            assertTrue(g.timelineTexts().any { "Cantina da Praça" in it })
        } finally { g.close() }
    }

    @Test fun nearbyIsOnlyASuggestionAndExplicitReuseNeverRenamesTheExistingPlace() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val first = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0001, 150f, 1_000)
            val neighbor = g.places.add("Padaria", PlaceType.RESTAURANT, 0.0, 0.0002, 150f, 1_000)
            val (_, questionId) = unknownVisit(g)
            g.engine.answerNewPlace(questionId, PlaceType.RESTAURANT)
            val q = saveQuestion(g)
            assertEquals(listOf(first.id, neighbor.id), g.engine.newPlaceSaveOptions(q.id)!!.nearby.map { it.id })
            g.engine.answerSavePlace(q.id, true, "Nome que não deve substituir", reusePlaceId = neighbor.id)
            assertEquals(listOf(first, neighbor.copy(confirmationCount = neighbor.confirmationCount + 1)),
                g.places.all().sortedBy { it.id })
            assertEquals(neighbor.id, g.db.questionDao().getById(q.id)!!.placeId)
            assertEquals("REUSE", g.db.questionDao().getById(q.id)!!.answer)
        } finally { g.close() }
    }

    @Test fun aFailedQuestionWriteRollsBackThePlaceAndTheHistoricalLinkThenCanRetry() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val (eventId, questionId) = unknownVisit(g)
            g.engine.answerNewPlace(questionId, PlaceType.RESTAURANT)
            val q = saveQuestion(g)
            g.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_save BEFORE UPDATE ON context_questions " +
                "WHEN NEW.kind = 'SAVE_PLACE' AND NEW.answeredAt IS NOT NULL " +
                "BEGIN SELECT RAISE(ABORT, 'injected write failure'); END")
            try {
                g.engine.answerSavePlace(q.id, true, "Cantina")
                fail("Expected injected persistence failure")
            } catch (expected: android.database.sqlite.SQLiteException) { }
            assertTrue(g.places.all().isEmpty())
            assertNull(g.contextDao.getById(eventId)!!.placeId)
            assertNull(g.db.questionDao().getById(q.id)!!.answeredAt)
            g.db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_save")
            g.engine.answerSavePlace(q.id, true, "Cantina")
            assertEquals(1, g.places.all().size)
        } finally { g.close() }
    }

    @Test fun missingLocationCanBeProvidedManuallyAndInvalidReuseLeavesTheQuestionPending() = runBlocking {
        val g = TestGraph(10_000)
        try {
            val restaurant = g.places.add("Cantina", PlaceType.RESTAURANT, 0.0, 0.0, 150f, 1_000)
            val shop = g.places.add("Loja", PlaceType.STORE, 0.0, 0.0, 150f, 1_000)
            val (_, questionId) = unknownVisit(g, coordinates = null)
            g.engine.answerNewPlace(questionId, PlaceType.RESTAURANT)
            val q = saveQuestion(g)
            val options = g.engine.newPlaceSaveOptions(q.id, 0.0 to 0.0)!!
            assertFalse(options.hasLocation)
            assertEquals(restaurant.id, options.nearby.first().id)
            try {
                g.engine.answerSavePlace(q.id, true, reusePlaceId = shop.id, manualPosition = 0.0 to 0.0)
                fail("An incompatible place must not be reused")
            } catch (expected: IllegalStateException) { }
            assertNull(g.db.questionDao().getById(q.id)!!.answeredAt)
            g.engine.answerSavePlace(q.id, true, reusePlaceId = restaurant.id, manualPosition = 0.0 to 0.0)
            assertEquals(restaurant.id, g.db.questionDao().getById(q.id)!!.placeId)
            assertEquals(2, g.places.all().size)
        } finally { g.close() }
    }
}

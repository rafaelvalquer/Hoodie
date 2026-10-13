package com.hoodie.app.integration

import com.hoodie.app.core.database.RoomTransactionRunner
import com.hoodie.app.core.model.*
import com.hoodie.app.core.mobility.*
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.data.repository.DiaryRepository
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.integration.mobility.MobilityGraph
import com.hoodie.app.pixel.npc.NpcDirector
import com.hoodie.app.pixel.npc.shopping.*
import com.hoodie.app.pixel.scene.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Section 6 of the stabilization plan, using real engines, Room and the Diary repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StabilizationDayIntegrationTest {
    @Test fun homeCarDiscoveredCantinaStoreHomeAndNextDayCantinaKeepOneHistory() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val g = m.g
            g.settings.setMobility(g.settings.current().mobility.copy(preferredMode = MovementMode.CAR))
            val store = g.places.add("Loja da Praça", PlaceType.STORE, -23.68, -46.63, 150f, m.now(), visitedAt = null)
            fun scene(activity: HoodieActivity) = VisualDirector.resolve(activity, m.context()!!.type,
                shoppingVenue = m.context()!!.placeId?.let { id -> runBlocking { g.places.byId(id)?.type } }).scene
            m.at(MONDAY, 8)
            // End the constructor's old onboarding snapshot, then observe Casa at 08:00.
            g.contextDao.closeOpen(m.now())
            m.geofence(m.home, GeofenceTransition.ENTER)
            assertEquals(m.home.id, m.context()!!.placeId)

            m.at(MONDAY, 12); m.move(DetectedMovement.WALKING)
            m.geofence(m.home, GeofenceTransition.EXIT)
            m.at(MONDAY, 12, 2); m.move(DetectedMovement.IN_VEHICLE)
            assertEquals(MovementMode.CAR, m.open()!!.currentMode)
            assertEquals(SceneId.CAR, VisualDirector.resolve(HoodieActivity.COMMUTING,
                m.context()!!.type, mobilityMode = m.open()!!.currentMode).scene)
            assertNull(m.pending(QuestionKind.NEW_PLACE))

            // A single position check after sustained stillness discovers the unknown restaurant.
            m.at(MONDAY, 12, 15); m.move(DetectedMovement.STILL)
            g.location.position = -23.65 to -46.63
            m.restart()
            m.at(MONDAY, 12, 18); m.check()
            assertNull(m.open())
            val discovery = m.pending(QuestionKind.NEW_PLACE)
            assertNotNull("Unknown arrival must offer classification", discovery)
            val originalVisit = g.contextDao.getById(discovery!!.contextEventId!!)!!
            assertEquals(UserContextType.UNKNOWN, originalVisit.type)
            g.engine.answerNewPlace(discovery.id, PlaceType.RESTAURANT)
            val save = m.pending(QuestionKind.SAVE_PLACE)!!
            g.engine.answerSavePlace(save.id, true, "Cantina da Praça")
            val cantina = g.places.all().single { it.name == "Cantina da Praça" }
            assertEquals(PlaceType.RESTAURANT, cantina.type)
            assertEquals(-23.65, cantina.latitude, 0.0)
            assertEquals(-46.63, cantina.longitude, 0.0)
            assertEquals(cantina.id, m.context()!!.placeId)
            assertEquals(originalVisit.id, m.context()!!.id)
            assertEquals(originalVisit.startedAt, m.context()!!.startedAt)
            assertEquals(ContextSource.CONFIRMATION, m.context()!!.source)
            assertEquals(SceneId.RESTAURANT, scene(HoodieActivity.EATING))
            val newPlaceQuestions = m.asked(QuestionKind.NEW_PLACE)
            val saveQuestions = m.asked(QuestionKind.SAVE_PLACE)

            m.at(MONDAY, 13, 10); m.move(DetectedMovement.WALKING)
            m.geofence(cantina, GeofenceTransition.EXIT)
            assertEquals(UserContextType.COMMUTING, m.context()!!.type)
            m.at(MONDAY, 13, 12); m.move(DetectedMovement.IN_VEHICLE)
            m.at(MONDAY, 13, 25); m.geofence(store, GeofenceTransition.ENTER)
            assertNull(m.open())
            assertEquals(store.id, m.context()!!.placeId)
            assertEquals(SceneId.STORE, scene(HoodieActivity.SHOPPING))
            val brain = NpcDirector.plan(SceneId.STORE, SceneEnv(DayPeriod.DAY, 13 * 60 + 25,
                daySeed = MONDAY, shoppingVenue = PlaceType.STORE)).single().shoppingBrain!!
            assertSame(StoreNavigationGraph, brain.floor)
            assertSame(StoreSpeechLibrary, brain.speech)
            val intents = (0L..300_000L step 250).map { brain.stateAt(it).currentIntent }.toSet()
            assertTrue(intents.containsAll(setOf(ShoppingNpcIntent.ENTER_STORE, ShoppingNpcIntent.PAY, ShoppingNpcIntent.EXIT_STORE)))

            m.at(MONDAY, 13, 55); m.move(DetectedMovement.WALKING)
            m.geofence(store, GeofenceTransition.EXIT)
            m.at(MONDAY, 13, 57); m.move(DetectedMovement.IN_VEHICLE)
            m.restart()
            m.at(MONDAY, 14, 10); m.geofence(m.home, GeofenceTransition.ENTER)
            assertNull(m.open())
            assertEquals(m.home.id, m.context()!!.placeId)
            assertEquals(UserContextType.HOME, m.context()!!.type)
            assertEquals(3, m.sessions().size)
            assertTrue(m.sessions().all { s -> m.segmentsOf(s.id).all { it.endedAt != null } })

            m.at(MONDAY, 15)
            val date = g.clock.today()
            val diaryRepository = DiaryRepository(g.contextDao, g.db.timelineDao(), g.db.hoodieActivityDao(),
                g.db.placeDao(), g.clock, mobility = m.repo, transactions = RoomTransactionRunner(g.db))
            val diary = diaryRepository.loadDiary(date)
            assertEquals(listOf(m.home.id, cantina.id, store.id, m.home.id), diary.visits.map { it.placeId })
            val lunch = diary.visits.single { it.placeId == cantina.id }
            assertEquals("Cantina da Praça", lunch.placeName)
            assertEquals(originalVisit.startedAt, lunch.arrivalAt)
            assertEquals(com.hoodie.app.engine.at(MONDAY, 13, 10).toInstant().toEpochMilli(), lunch.departureAt)
            assertEquals(ContextSource.CONFIRMATION, lunch.source)
            assertEquals(PlaceType.STORE, diary.visits.single { it.placeId == store.id }.placeType)
            assertTrue(diary.movements.any { it.mode == MovementMode.CAR })
            assertEquals(3, diary.movements.count { it.mode == MovementMode.CAR })
            assertEquals(diary.visits.map { it.placeId }, diary.replay.visits.map { it.placeId })

            // Next day: no geofence registration is needed for an unambiguous point lookup.
            m.at(MONDAY + 1, 12); m.move(DetectedMovement.WALKING)
            m.geofence(m.home, GeofenceTransition.EXIT)
            m.at(MONDAY + 1, 12, 2); m.move(DetectedMovement.IN_VEHICLE)
            m.at(MONDAY + 1, 12, 15); m.move(DetectedMovement.STILL)
            m.restart()
            m.at(MONDAY + 1, 12, 18); m.check()
            assertNull(m.open())
            assertEquals(cantina.id, m.context()!!.placeId)
            assertTrue(m.context()!!.type in setOf(UserContextType.LUNCH, UserContextType.DINING))
            m.geofence(cantina, GeofenceTransition.ENTER)
            m.geofence(cantina, GeofenceTransition.DWELL)
            val revisited = g.places.byId(cantina.id)!!
            assertEquals(2, revisited.confirmationCount)
            assertEquals(m.context()!!.startedAt, revisited.lastVisitedAt)
            assertEquals(newPlaceQuestions, m.asked(QuestionKind.NEW_PLACE))
            assertEquals(saveQuestions, m.asked(QuestionKind.SAVE_PLACE))
            assertEquals(1, g.places.all().count { it.name == "Cantina da Praça" })
            assertEquals(2, m.location.reads)
            assertEquals(0, m.location.speedReads)
            val historicalAgain = diaryRepository.loadDiary(date)
            assertEquals(lunch.arrivalAt, historicalAgain.visits.single { it.placeId == cantina.id }.arrivalAt)
            assertEquals(lunch.departureAt, historicalAgain.visits.single { it.placeId == cantina.id }.departureAt)
        } finally { m.close() }
    }
}

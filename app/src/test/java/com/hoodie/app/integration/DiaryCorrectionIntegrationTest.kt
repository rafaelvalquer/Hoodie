package com.hoodie.app.integration

import com.hoodie.app.core.database.*
import com.hoodie.app.core.model.*
import com.hoodie.app.domain.correction.*
import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.engine.correction.*
import com.hoodie.app.engine.diary.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiaryCorrectionIntegrationTest {
    private lateinit var g: TestGraph
    private lateinit var service: DiaryCorrectionService
    private val date = java.time.LocalDate.of(2026, 10, 5)
    private var target = 0L
    private var restaurant = 0L
    private var first = 0L
    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli()

    @Before fun setup() = runBlocking {
        g = TestGraph(at(18))
        val office = g.addPlace(PlaceType.WORK, -23.60).id
        restaurant = g.addPlace(PlaceType.RESTAURANT, -23.61).id
        first = g.contextDao.insert(ContextEventEntity(type = UserContextType.WORK, startedAt = at(8), endedAt = at(12), confidence = .9f, placeId = office, source = ContextSource.GEOFENCE))
        target = g.contextDao.insert(ContextEventEntity(type = UserContextType.WORK, startedAt = at(12), endedAt = at(13), confidence = .7f, placeId = office, source = ContextSource.ROUTINE))
        g.contextDao.insert(ContextEventEntity(type = UserContextType.WORK, startedAt = at(13), endedAt = at(17), confidence = .9f, placeId = office, source = ContextSource.GEOFENCE))
        service = DiaryCorrectionService(g.db, RoomTransactionRunner(g.db), PhoneContextRecalculator(g.db, g.clock), g.clock, notifier = g.notifier)
    }
    @After fun close() = g.close()

    @Test fun lunchCorrectionUpdatesDiaryJourneyClockAndSavedPhoneContexts() = runBlocking {
        val usage = g.db.deviceUsageDao()
        usage.upsertDay(DailyDeviceUsageEntity(date.toString(), 3_000_000, 1, 1, at(12, 10), at(12, 40), 1_800_000, false, 1, at(18)))
        usage.insertApps(listOf(DailyAppUsageEntity(date.toString(), "chat", "Chat", "COMMUNICATION", 1_800_000, 1, at(12, 10), at(12, 40), at(18))))
        usage.insertSessions(listOf(PhoneAppSessionEntity("lunch-phone", date.toEpochDay(), "chat", at(12, 10), at(12, 40))))
        val staleQuestion = g.db.questionDao().insert(ContextQuestionEntity(kind = QuestionKind.CONFIRM_CONTEXT, candidate = UserContextType.WORK,
            placeId = null, encryptedCoordinates = null, contextEventId = target, askedAt = at(12)))
        service.save(DiaryCorrection(CorrectionTargetType.CONTEXT, target, UserContextType.LUNCH, restaurant, at(12), at(13)))
        val corrected = g.contextDao.getById(target)!!
        assertEquals(ContextSource.USER_CORRECTION, corrected.source)
        assertEquals(1f, corrected.confidence, 0f)
        val audit = g.db.intelligenceDao().correctionsSince(0).single()
        assertEquals("WORK", audit.originalContext)
        assertEquals("LUNCH", audit.correctedContext)
        val diary = DiaryAssembler.build(date, g.contextDao.all(), g.db.timelineDao().range(at(0), at(0) + 86_400_000), emptyList(), g.db.placeDao().getAll(), at(0), at(0) + 86_400_000, at(18))
        assertEquals(3_600_000L, diary.summary.lunchMs)
        assertTrue(diary.visits.any { it.placeId == restaurant && it.arrivalAt == at(12) })
        val journey = JourneyMapAssembler.build(diary, at(18))
        assertTrue(journey.nodes.any { it.placeId == restaurant && it.contextType == UserContextType.LUNCH })
        val clock = DayClockAssembler.build(diary, date, g.clock.zone(), at(18))
        assertTrue(clock.stays.any { it.placeId == restaurant && it.category == ClockCategory.MEAL })
        assertEquals(1_800_000L, usage.contextTotals(date.toString()).single { it.context == "LUNCH" }.foregroundMs)
        assertEquals(3_000_000L, usage.day(date.toString())!!.screenTimeMs)
        assertEquals("lunch-phone", usage.sessions(date.toEpochDay()).single().id)
        g.transitions.confirm(target, .2f, ContextSource.GEOFENCE)
        assertEquals(ContextSource.USER_CORRECTION, g.contextDao.getById(target)!!.source)
        assertEquals(1f, g.contextDao.getById(target)!!.confidence, 0f)
        assertEquals("CORRECTED", g.db.questionDao().getById(staleQuestion)?.answer)
        assertTrue(staleQuestion in g.notifier.cancelled)
        g.engine.answerYesNo(staleQuestion, true)
        assertEquals(UserContextType.LUNCH, g.contextDao.getById(target)!!.type)
    }

    @Test fun invalidEditRollsBackAuditAndEarlierNeighborAdjustments() = runBlocking {
        try {
            service.save(DiaryCorrection(CorrectionTargetType.CONTEXT, target, UserContextType.LUNCH, restaurant, at(11, 55), at(17, 10)))
            fail("An edit cannot erase an adjacent event")
        } catch (_: IllegalStateException) { }
        assertEquals(at(12), g.contextDao.getById(first)!!.endedAt)
        assertEquals(UserContextType.WORK, g.contextDao.getById(target)!!.type)
        assertTrue(g.db.intelligenceDao().correctionsSince(0).isEmpty())
    }

    @Test fun correctingFalseCommuteAuditsAndRemovesMovementFromEveryProjection() = runBlocking {
        val event = g.contextDao.getById(target)!!
        g.contextDao.update(event.copy(type = UserContextType.COMMUTING))
        val mode = com.hoodie.app.core.mobility.MovementMode.CAR
        val session = g.db.mobilitySessionDao().insert(MobilitySessionEntity(startedAt = at(12), endedAt = at(13),
            initialMode = mode, currentMode = mode, state = com.hoodie.app.core.mobility.MobilityState.ARRIVED,
            confidence = .8f, confirmed = false, source = com.hoodie.app.core.mobility.MobilitySource.ACTIVITY_RECOGNITION, leftOrigin = true))
        g.db.mobilitySegmentDao().insert(MobilitySegmentEntity(sessionId = session, mode = mode, startedAt = at(12), endedAt = at(13), confidence = .8f,
            source = com.hoodie.app.core.mobility.MobilitySource.ACTIVITY_RECOGNITION))
        service.save(DiaryCorrection(CorrectionTargetType.CONTEXT, target, UserContextType.LUNCH, restaurant, at(12), at(13)))
        val diary = com.hoodie.app.data.repository.DiaryRepository(g.contextDao, g.db.timelineDao(), g.db.hoodieActivityDao(), g.db.placeDao(), g.clock,
            mobility = com.hoodie.app.data.repository.MobilityRepository(g.db.mobilitySessionDao(), g.db.mobilitySegmentDao())).loadDiary(date)
        assertEquals(3_600_000L, diary.summary.lunchMs)
        assertTrue(diary.movements.isEmpty())
        assertTrue(diary.mobilityTotals.isEmpty())
        assertEquals("CAR", g.db.intelligenceDao().correctionsSince(0).single { it.targetType == "MOBILITY_SEGMENT" }.originalMode)
        assertEquals(com.hoodie.app.core.mobility.MovementMode.NONE, g.db.mobilitySegmentDao().forSession(session).single().mode)
    }

    @Test fun correctingOngoingFalseCommuteEndsMovementAndDoesNotTeachATrip() = runBlocking {
        val event = g.contextDao.insert(ContextEventEntity(type = UserContextType.COMMUTING, startedAt = at(17),
            endedAt = null, placeId = null, confidence = .7f, source = ContextSource.MOBILITY))
        val mode = com.hoodie.app.core.mobility.MovementMode.CAR
        val session = g.db.mobilitySessionDao().insert(MobilitySessionEntity(startedAt = at(17), originPlaceId = restaurant, destinationPlaceId = restaurant,
            initialMode = mode, currentMode = mode, state = com.hoodie.app.core.mobility.MobilityState.IN_VEHICLE,
            confidence = .7f, source = com.hoodie.app.core.mobility.MobilitySource.ACTIVITY_RECOGNITION, leftOrigin = true))
        g.db.mobilitySegmentDao().insert(MobilitySegmentEntity(sessionId = session, mode = mode, startedAt = at(17), confidence = .7f,
            source = com.hoodie.app.core.mobility.MobilitySource.ACTIVITY_RECOGNITION))
        service.save(DiaryCorrection(CorrectionTargetType.CONTEXT, event, UserContextType.LUNCH, restaurant, at(17), null))
        val repo = com.hoodie.app.data.repository.MobilityRepository(g.db.mobilitySessionDao(), g.db.mobilitySegmentDao())
        assertNull(repo.open())
        assertTrue(repo.trips(at(18)).isEmpty())
        assertEquals(at(18), g.db.mobilitySegmentDao().forSession(session).single().endedAt)
        assertEquals(UserContextType.LUNCH, g.contextDao.current()!!.type)
        assertNull(g.contextDao.current()!!.endedAt)
    }

    @Test fun editingLastSegmentEndAlsoEndsTheOngoingTrip() = runBlocking {
        val mode = com.hoodie.app.core.mobility.MovementMode.CAR
        val session = g.db.mobilitySessionDao().insert(MobilitySessionEntity(startedAt = at(17),
            initialMode = mode, currentMode = mode, state = com.hoodie.app.core.mobility.MobilityState.IN_VEHICLE,
            confidence = .7f, source = com.hoodie.app.core.mobility.MobilitySource.ACTIVITY_RECOGNITION))
        val segment = g.db.mobilitySegmentDao().insert(MobilitySegmentEntity(sessionId = session, mode = mode, startedAt = at(17), confidence = .7f,
            source = com.hoodie.app.core.mobility.MobilitySource.ACTIVITY_RECOGNITION))
        service.save(DiaryCorrection(CorrectionTargetType.MOBILITY_SEGMENT, segment, UserContextType.COMMUTING, restaurant, at(17), at(18), mode))
        assertNull(g.db.mobilitySessionDao().open())
        val ended = g.db.mobilitySessionDao().getById(session)!!
        assertEquals(at(18), ended.endedAt)
        assertEquals(com.hoodie.app.core.mobility.MobilityState.ARRIVED, ended.state)
    }
}

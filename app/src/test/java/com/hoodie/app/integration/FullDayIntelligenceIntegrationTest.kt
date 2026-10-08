package com.hoodie.app.integration

import com.hoodie.app.core.database.*
import com.hoodie.app.core.datastore.*
import com.hoodie.app.core.model.*
import com.hoodie.app.core.mobility.*
import com.hoodie.app.domain.correction.*
import com.hoodie.app.domain.daystate.*
import com.hoodie.app.domain.detection.*
import com.hoodie.app.domain.routine.*
import com.hoodie.app.engine.correction.*
import com.hoodie.app.engine.context.*
import com.hoodie.app.engine.daystate.*
import com.hoodie.app.engine.diary.*
import com.hoodie.app.engine.detection.ConfidenceEngine
import com.hoodie.app.engine.mobility.*
import com.hoodie.app.engine.routine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FullDayIntelligenceIntegrationTest {
    @Test fun completeDayUsesOneStoryLearnsCorrectionsAndRestoresSleepAfterMidnight() = runBlocking {
        val today = LocalDate.of(2026, 10, 7)
        val zone = java.time.ZoneId.of("America/Sao_Paulo")
        fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val g = TestGraph(at(today, 4))
        try {
            g.settings.completeOnboarding("Hoodie", g.clock.millis)
            g.settings.setDigital(DigitalSettings(analysisEnabled = true))
            g.settings.setMobility(MobilitySettings(detectionEnabled = true, learnTrips = true))
            g.setRoutine(com.hoodie.app.engine.officeRoutine)
            val home = g.addPlace(PlaceType.HOME, -23.55).id
            val work = g.addPlace(PlaceType.WORK, -23.60).id
            val restaurant = g.addPlace(PlaceType.RESTAURANT, -23.61).id
            val gym = g.addPlace(PlaceType.GYM, -23.63).id
            val tx = RoomTransactionRunner(g.db)
            val learner = RoutineLearningCoordinator(g.db, g.clock, g.settings)
            val transportLearner = TransportPatternLearner(g.db, tx, g.clock)
            val correction = DiaryCorrectionService(g.db, tx, PhoneContextRecalculator(g.db, g.clock), g.clock, dagger.Lazy { learner }, dagger.Lazy { transportLearner })
            var lastSession = 0L
            for (date in listOf(today.minusDays(1), today.minusDays(2), today.minusDays(5))) {
                val closed = date != today.minusDays(1)
                suspend fun context(type: UserContextType, start: Long, end: Long?, place: Long?) = g.contextDao.insert(ContextEventEntity(type = type,
                    startedAt = start, endedAt = end, confidence = .96f, placeId = place, source = ContextSource.GEOFENCE))
                context(UserContextType.HOME, at(date, 0), at(date, 7, 25), home)
                context(UserContextType.COMMUTING, at(date, 7, 25), at(date, 8, 10), null)
                context(UserContextType.WORK, at(date, 8, 10), at(date, 12, 8), work)
                val lunch = context(UserContextType.WORK, at(date, 12, 8), at(date, 13, 4), work)
                context(UserContextType.WORK, at(date, 13, 4), at(date, 18, 2), work)
                context(UserContextType.COMMUTING, at(date, 18, 2), at(date, 18, 25), null)
                context(UserContextType.GYM, at(date, 18, 25), at(date, 19, 15), gym)
                context(UserContextType.COMMUTING, at(date, 19, 15), at(date, 19, 40), null)
                context(UserContextType.HOME, at(date, 19, 40), if (closed) at(date.plusDays(1), 0) else null, home)
                g.db.deviceUsageDao().insertSessions(listOf(PhoneAppSessionEntity("wake-$date", date.toEpochDay(), "phone", at(date, 6, 42), at(date, 6, 46)),
                    PhoneAppSessionEntity("evening-$date", date.toEpochDay(), "phone", at(date, 22, 48), at(date, 22, 51))))
                val session = MobilitySessionEntity(startedAt = at(date, 7, 25), endedAt = at(date, 8, 10), originPlaceId = home, destinationPlaceId = work,
                    initialMode = MovementMode.WALKING, currentMode = MovementMode.WALKING, state = MobilityState.ARRIVED, confidence = .94f, confirmed = true, source = MobilitySource.GEOFENCE, leftOrigin = true)
                val id = g.db.mobilitySessionDao().insert(session)
                if (date == today.minusDays(1)) lastSession = id
                suspend fun segment(mode: MovementMode, from: Long, end: Long) = g.db.mobilitySegmentDao().insert(MobilitySegmentEntity(sessionId = id,
                    mode = mode, startedAt = from, endedAt = end, confidence = .94f, confirmed = true, source = MobilitySource.CONFIRMATION))
                segment(MovementMode.WALKING, at(date, 7, 25), at(date, 7, 34))
                val vehicle = segment(MovementMode.CAR, at(date, 7, 34), at(date, 8, 5))
                segment(MovementMode.WALKING, at(date, 8, 5), at(date, 8, 10))
                val eveningId = g.db.mobilitySessionDao().insert(session.copy(startedAt = at(date, 18, 2), endedAt = at(date, 18, 25),
                    originPlaceId = work, destinationPlaceId = gym, initialMode = MovementMode.BUS, currentMode = MovementMode.BUS))
                g.db.mobilitySegmentDao().insert(MobilitySegmentEntity(sessionId = eveningId, mode = MovementMode.BUS,
                    startedAt = at(date, 18, 2), endedAt = at(date, 18, 25), confidence = .94f, confirmed = true, source = MobilitySource.CONFIRMATION))
                correction.save(DiaryCorrection(CorrectionTargetType.CONTEXT, lunch, UserContextType.LUNCH, restaurant, at(date, 12, 8), at(date, 13, 4)))
                correction.save(DiaryCorrection(CorrectionTargetType.MOBILITY_SEGMENT, vehicle, UserContextType.COMMUTING, work, at(date, 7, 34), at(date, 8, 5), MovementMode.BUS))
            }
            learner.recompute()
            val learnedLunch = g.db.intelligenceDao().routineSlots().single { it.dayGroup == "WEEKDAY" && it.type == "LUNCH_START" }
            assertEquals(12 * 60 + 8, learnedLunch.medianMinute)
            assertTrue(learnedLunch.confidence >= .85f)
            val configured = g.db.routineDao().get()!!
            assertEquals(com.hoodie.app.engine.officeRoutine.lunchStartMinute, configured.lunchStartMinute)
            val date = today.minusDays(1)
            val diary = com.hoodie.app.data.repository.DiaryRepository(g.contextDao, g.db.timelineDao(), g.db.hoodieActivityDao(), g.db.placeDao(), g.clock,
                mobility = com.hoodie.app.data.repository.MobilityRepository(g.db.mobilitySessionDao(), g.db.mobilitySegmentDao())).loadDiary(date)
            assertEquals(56 * 60_000L, diary.summary.lunchMs)
            assertTrue(JourneyMapAssembler.build(diary, g.clock.millis).nodes.any { it.placeId == restaurant && it.contextType == UserContextType.LUNCH })
            assertTrue(JourneyMapAssembler.build(diary, g.clock.millis).nodes.any { it.placeId == gym && it.contextType == UserContextType.GYM })
            assertTrue(DayClockAssembler.build(diary, date, zone, g.clock.millis).stays.any { it.placeId == restaurant })
            assertEquals(listOf(MovementMode.WALKING, MovementMode.BUS, MovementMode.WALKING), g.db.mobilitySegmentDao().forSession(lastSession).map { it.mode })
            assertEquals(54 * 60_000L, diary.mobilityTotals[MovementMode.BUS])
            val patterns = g.db.intelligenceDao().transportPatterns(home, work, "WEEKDAY", (7 * 60 + 25) / 30)
            assertEquals(3, patterns.single { it.mode == "BUS" }.confirmations)
            assertEquals(3, patterns.single { it.mode == "CAR" }.rejections)
            val inferred = TransportClassifier.classify(TransportFeatures(31 * 60_000L, true, 25f, 50f, .3f, 5, 150_000, 30_000, false), patterns, g.clock.millis)
            assertEquals(MovementMode.BUS, inferred.mode)
            assertEquals(DetectionDecision.AUTO_ACCEPT, ConfidenceEngine.decide(inferred.detection))
            val coordinator = DayStateCoordinator(g.db, g.settings, g.clock)
            g.db.intelligenceDao().saveDayState(DayStateSnapshot(DayState.SLEEPING, at(date, 4), ConfidenceScore(.9f), DayStateReason.SLEEP_INACTIVITY, false).toEntity())
            g.clock.millis = at(date, 6, 46)
            assertEquals(DayState.ACTIVE, coordinator.reconcile()!!.state)
            val morning = g.db.mobilitySessionDao().getById(lastSession)!!
            g.db.mobilitySessionDao().update(morning.copy(endedAt = null, state = MobilityState.WALKING))
            g.clock.millis = at(date, 7, 30)
            assertEquals(DayState.COMMUTING, coordinator.reconcile()!!.state)
            g.db.mobilitySessionDao().update(morning)
            g.clock.millis = at(date, 8, 15)
            assertEquals(DayState.ACTIVE, coordinator.reconcile()!!.state)
            g.clock.millis = at(date, 12, 15)
            assertEquals(DayState.ACTIVE, coordinator.reconcile()!!.state)
            val evening = g.db.mobilitySessionDao().overlapping(at(date, 18, 2), at(date, 18, 25)).single()
            g.db.mobilitySessionDao().update(evening.copy(endedAt = null, state = MobilityState.IN_VEHICLE))
            g.clock.millis = at(date, 18, 10)
            assertEquals(DayState.COMMUTING, coordinator.reconcile()!!.state)
            g.db.mobilitySessionDao().update(evening)
            g.clock.millis = at(date, 18, 35)
            assertEquals(DayState.ACTIVE, coordinator.reconcile()!!.state)
            g.clock.millis = at(date, 23, 25)
            assertEquals(DayState.WINDING_DOWN, coordinator.reconcile()!!.state)
            g.clock.millis = at(today, 4)
            val sleeping = coordinator.reconcile()!!
            assertEquals(DayState.SLEEPING, sleeping.state)
            assertEquals(sleeping, DayStateCoordinator(g.db, g.settings, g.clock).snapshots.first())
            assertEquals(6, g.db.intelligenceDao().correctionsSince(0).size)
        } finally { g.close() }
    }
}

package com.hoodie.app.integration.mobility

import com.hoodie.app.core.database.RoomTransactionRunner
import com.hoodie.app.core.mobility.*
import com.hoodie.app.core.model.*
import com.hoodie.app.domain.correction.*
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.correction.*
import com.hoodie.app.engine.mobility.TransportPatternLearner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransportIntelligenceIntegrationTest {
    @Test fun insufficientEvidenceStaysUnknownAndCorrectionSurvivesUntilMultimodalArrival() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            m.at(MONDAY, 7, 47); m.move(DetectedMovement.WALKING)
            m.geofence(m.home, GeofenceTransition.EXIT)
            m.at(MONDAY, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
            m.engine.onAppOpened()
            assertEquals(0, m.asked(QuestionKind.SELECT_TRANSPORT_MODE))
            val session = m.open()!!
            val vehicle = m.segmentsOf(session.id).last()
            assertEquals(MovementMode.VEHICLE_UNKNOWN, vehicle.mode)
            val tx = RoomTransactionRunner(m.g.db)
            val patterns = TransportPatternLearner(m.g.db, tx, m.g.clock)
            val service = DiaryCorrectionService(m.g.db, tx, PhoneContextRecalculator(m.g.db, m.g.clock), m.g.clock,
                transportLearning = dagger.Lazy { patterns })
            service.save(DiaryCorrection(CorrectionTargetType.MOBILITY_SEGMENT, vehicle.id,
                context = UserContextType.COMMUTING, placeId = m.work.id, startedAt = vehicle.startedAt, endedAt = null, mode = MovementMode.BUS))
            m.at(MONDAY, 8, 0); m.move(DetectedMovement.IN_VEHICLE)
            m.check()
            assertEquals(MobilitySource.USER_CORRECTION, m.segmentsOf(session.id).last().source)
            assertEquals(MovementMode.BUS, m.open()!!.currentMode)
            m.at(MONDAY, 8, 20); m.move(DetectedMovement.WALKING)
            m.at(MONDAY, 8, 30); m.geofence(m.work, GeofenceTransition.ENTER)
            val trip = m.repo.session(session.id)!!
            assertTrue(trip.confirmed)
            assertEquals(m.work.id, trip.destinationPlaceId)
            assertEquals(listOf(MovementMode.WALKING, MovementMode.BUS, MovementMode.WALKING), m.segmentsOf(session.id).map { it.mode })
            assertTrue(m.segmentsOf(session.id).all { it.endedAt != null })
            assertEquals(0, m.location.reads)
            val learned = m.g.db.intelligenceDao().transportPatterns(m.home.id, m.work.id, "WEEKDAY", (7 * 60 + 47) / 30)
            assertEquals(1, learned.single { it.mode == "BUS" }.confirmations)
            val correctedVehicle = m.segmentsOf(session.id).single { it.mode == MovementMode.BUS }
            service.save(DiaryCorrection(CorrectionTargetType.MOBILITY_SEGMENT, correctedVehicle.id,
                UserContextType.COMMUTING, m.restaurant.id, correctedVehicle.startedAt, correctedVehicle.endedAt, MovementMode.BUS))
            assertEquals(1, m.g.db.intelligenceDao().transportPatterns(m.home.id, m.work.id, "WEEKDAY", (7 * 60 + 47) / 30).single { it.mode == "BUS" }.rejections)
            assertEquals(1, m.g.db.intelligenceDao().transportPatterns(m.home.id, m.restaurant.id, "WEEKDAY", (7 * 60 + 47) / 30).single { it.mode == "BUS" }.confirmations)
            m.engine.clearHistory()
            assertTrue(m.g.db.intelligenceDao().transportPatterns(m.home.id, m.work.id, "WEEKDAY", (7 * 60 + 47) / 30).isEmpty())
        } finally { m.close() }
    }
}

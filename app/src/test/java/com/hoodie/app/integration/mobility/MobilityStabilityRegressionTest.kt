package com.hoodie.app.integration.mobility

import com.hoodie.app.core.location.SpeedObservation
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.context.GeofenceTransition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobilityStabilityRegressionTest {
    @Test fun pendingWalkSurvivesRestartAndOnCheckCreatesOneSegmentWithoutAnotherEnter() {
        val m = MobilityGraph()
        try {
            m.at(MONDAY, 7, 47); m.move(DetectedMovement.WALKING)
            m.geofence(m.home, GeofenceTransition.EXIT)
            m.at(MONDAY, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
            val id = m.open()!!.id
            m.at(MONDAY, 8, 20); m.move(DetectedMovement.WALKING)
            assertEquals(MovementMode.WALKING, m.open()!!.pendingMovementMode)
            m.restart()
            m.at(MONDAY, 8, 21); m.check()
            m.check()
            assertEquals(listOf(MovementMode.WALKING, MovementMode.VEHICLE_UNKNOWN, MovementMode.WALKING), m.segmentsOf(id).map { it.mode })
            assertNull(m.open()!!.pendingMovementMode)
        } finally { m.close() }
    }

    @Test fun aBlockedSpeedLookupDoesNotHoldMobilityEngineMutexAndItsStaleResultIsDropped() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<SpeedObservation?>()
        try {
            m.location.speedOverride = { started.complete(Unit); release.await() }
            m.at(MONDAY, 7, 47); m.move(DetectedMovement.WALKING)
            m.geofence(m.home, GeofenceTransition.EXIT)
            m.at(MONDAY, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
            withTimeout(3_000) { started.await() }

            m.at(MONDAY, 7, 56); m.move(DetectedMovement.STILL)
            assertNotNull("STILL processed while location lookup was suspended", m.open()?.stillSince)

            release.complete(SpeedObservation(25f, m.now()))
            kotlinx.coroutines.delay(250)
            assertEquals(0, m.open()!!.speedSampleCount)
        } finally {
            if (!release.isCompleted) release.complete(null)
            m.close()
        }
    }
}

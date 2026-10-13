package com.hoodie.app.integration.mobility

import com.hoodie.app.core.database.*
import com.hoodie.app.core.mobility.*
import com.hoodie.app.core.location.*
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.context.GeofenceTransition
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VehicleEvidenceTest {
    @Test fun recentPointReadingIsAcceptedButExpiredAndFutureReadingsAreRejected() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m); val e = m.repo.evidenceFor(id)!!
            m.g.clock.millis += 180_000
            val now = m.now()
            val r = m.repo.reserveEvidenceSample(id, e.id, "freshness", now)!!
            assertFalse(m.repo.applyEvidenceSample(r, SpeedObservation(10f, now - 120_001), now))
            assertFalse(m.repo.applyEvidenceSample(r, SpeedObservation(10f, now + 1), now))
            assertTrue(m.repo.applyEvidenceSample(r, SpeedObservation(10f, now - 1_000), now))
            assertFalse(m.repo.applyEvidenceSample(r, SpeedObservation(10f, now - 1_000), now))
        } finally { m.close() }
    }
    @Test fun foregroundOnlyPermissionDoesNotReadLocationWhileProcessIsHidden() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m); val e = m.repo.evidenceFor(id)!!
            m.g.location.state = LocationPermissionState.FOREGROUND
            m.sampler!!.sample(id, e.id, "hidden")
            assertEquals(0, m.location.speedReads)
            assertEquals(0, m.repo.evidenceFor(id)!!.attempts)
            val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).create().start().resume()
            try {
                m.location.speedOverride = { SpeedObservation(10f, m.now()) }
                m.sampler!!.sample(id, e.id, "visible")
                assertEquals(1, m.location.speedReads)
                assertEquals(1, m.repo.evidenceFor(id)!!.sampleCount)
                activity.pause().stop()
                m.g.clock.millis += 60_000
                m.sampler!!.sample(id, e.id, "hidden-again")
                assertEquals(1, m.location.speedReads)
            } finally { activity.destroy() }
        } finally { m.close() }
    }
    private fun vehicle(m: MobilityGraph): Long {
        m.at(MONDAY, 7, 47); m.move(DetectedMovement.WALKING)
        m.geofence(m.home, GeofenceTransition.EXIT)
        m.at(MONDAY, 7, 55); m.move(DetectedMovement.IN_VEHICLE)
        return m.open()!!.id
    }

    @Test fun multimodalPeriodsKeepIndependentAggregatesButShareTheTripBudgetAfterRestart() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m)
            val first = m.repo.evidenceFor(id)!!
            repeat(5) { index ->
                val now = m.now() + index * 60_000
                val reservation = m.repo.reserveEvidenceSample(id, first.id, "first-$index", now)!!
                assertTrue(m.repo.applyEvidenceSample(reservation, SpeedObservation(12f, now), now))
                assertFalse(m.repo.applyEvidenceSample(reservation, SpeedObservation(12f, now), now))
            }
            assertNull(m.repo.reserveEvidenceSample(id, first.id, "sixth", m.now() + 6 * 60_000))
            val s = m.open()!!
            assertTrue(m.repo.switchSegment(s.currentMode, s.copy(currentMode = MovementMode.BUS),
                MobilitySegmentEntity(sessionId = id, mode = MovementMode.BUS, startedAt = m.now() + 6 * 60_000,
                    confidence = .9f, source = MobilitySource.LEARNED)))
            assertEquals(first.id, m.repo.evidenceFor(id)!!.id)
            m.at(MONDAY, 8, 20); m.move(DetectedMovement.WALKING)
            m.at(MONDAY, 8, 30); m.check()
            m.at(MONDAY, 8, 31); m.move(DetectedMovement.IN_VEHICLE)
            val second = m.repo.evidenceFor(id)!!
            assertNotEquals(first.id, second.id)
            assertEquals(0, second.sampleCount)
            assertEquals(0, second.attempts)
            m.restart()
            assertEquals(second.id, m.repo.evidenceFor(id)!!.id)
            assertNull(m.repo.reserveEvidenceSample(id, second.id, "second", m.now()))
            assertFalse(m.repo.canSampleSpeed(id, m.now()))
            assertNull(m.repo.speedSampleDelay(id, m.now()))
            assertEquals(5, m.repo.session(id)!!.speedSampleAttempts)
            val checks = m.scheduler.scheduled.size
            val reads = m.speedScheduler.enqueued.size
            m.check()
            assertEquals(checks, m.scheduler.scheduled.size)
            assertEquals(reads, m.speedScheduler.enqueued.size)
        } finally { m.close() }
    }

    @Test fun pendingWalkInvalidatesInFlightReadAndArrivalConfirmsObservedBoundary() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m)
            val e = m.repo.evidenceFor(id)!!
            val reservation = m.repo.reserveEvidenceSample(id, e.id, "inflight", m.now())!!
            m.at(MONDAY, 8, 25); m.move(DetectedMovement.WALKING)
            assertNull(m.repo.reserveEvidenceSample(id, e.id, "pending", m.now()))
            assertFalse(m.repo.applyEvidenceSample(reservation, SpeedObservation(20f, m.now()), m.now()))
            val observed = m.now()
            m.at(MONDAY, 8, 35); m.geofence(m.work, GeofenceTransition.ENTER)
            val segments = m.repo.segmentsOf(id)
            assertEquals(MovementMode.WALKING, segments.last().mode)
            assertEquals(observed, segments.last().startedAt)
            assertEquals(m.now(), segments.last().confirmedAt)
            assertEquals(observed, segments[segments.lastIndex - 1].endedAt)
        } finally { m.close() }
    }

    @Test fun throttleContinuesAcrossVehiclePeriodsAndEngineRestart() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m); val first = m.repo.evidenceFor(id)!!; val start = m.now()
            val reservation = m.repo.reserveEvidenceSample(id, first.id, "before-walk", start)!!
            m.repo.releaseSpeedReservation(reservation)
            m.g.clock.millis = start + 1_000; m.move(DetectedMovement.WALKING)
            m.g.clock.millis = start + 46_000; m.check()
            assertEquals(MovementMode.WALKING, m.open()!!.currentMode)
            m.g.clock.millis = start + 47_000; m.move(DetectedMovement.IN_VEHICLE)
            val second = m.repo.evidenceFor(id)!!
            assertNotEquals(first.id, second.id)
            m.restart()
            assertFalse(m.repo.canSampleSpeed(id, start + 59_999))
            assertEquals(1L, m.repo.speedSampleDelay(id, start + 59_999))
            assertNull(m.repo.reserveEvidenceSample(id, second.id, "early", start + 59_999))
            assertNotNull(m.repo.reserveEvidenceSample(id, second.id, "allowed", start + 60_000))
            assertEquals(2, m.repo.session(id)!!.speedSampleAttempts)
        } finally { m.close() }
    }

    @Test fun persistedPeriodAttemptsRestoreTripAccountingBeforeReservation() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m); val e = m.repo.evidenceFor(id)!!; val now = m.now()
            // Database state written by the version that only counted attempts per period.
            m.g.db.transportEvidenceDao().update(e.copy(attempts = 3, lastAttemptAt = now))
            val restored = com.hoodie.app.data.repository.MobilityRepository(m.g.db)
            assertNull(restored.reserveEvidenceSample(id, e.id, "early", now + 59_999))
            assertEquals(3, restored.session(id)!!.speedSampleAttempts)
            assertNotNull(restored.reserveEvidenceSample(id, e.id, "four", now + 60_000))
            assertNotNull(restored.reserveEvidenceSample(id, e.id, "five", now + 120_000))
            assertNull(restored.reserveEvidenceSample(id, e.id, "six", now + 180_000))
            assertEquals(5, restored.session(id)!!.speedSampleAttempts)
            assertEquals(0, restored.evidenceFor(id)!!.sampleCount)
        } finally { m.close() }
    }

    @Test fun concurrentRepositoriesCannotExceedTheLastTripAttempt() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m); val e = m.repo.evidenceFor(id)!!; val now = m.now()
            repeat(4) { i ->
                val reservation = m.repo.reserveEvidenceSample(id, e.id, "attempt-$i", now + i * 60_000)!!
                m.repo.releaseSpeedReservation(reservation)
            }
            val other = com.hoodie.app.data.repository.MobilityRepository(m.g.db)
            val results = awaitAll(
                async(Dispatchers.Default) { m.repo.reserveEvidenceSample(id, e.id, "last-a", now + 240_000) },
                async(Dispatchers.Default) { other.reserveEvidenceSample(id, e.id, "last-b", now + 240_000) },
            )
            assertEquals(1, results.count { it != null })
            assertEquals(5, m.repo.session(id)!!.speedSampleAttempts)
            assertEquals(5, m.repo.evidenceFor(id)!!.attempts)
        } finally { m.close() }
    }

    @Test fun workIdCannotBeReservedAgainAndThrottleSurvivesRepositoryRecreation() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m); val e = m.repo.evidenceFor(id)!!; val now = m.now()
            assertNotNull(m.repo.reserveEvidenceSample(id, e.id, "one", now))
            assertNull(m.repo.reserveEvidenceSample(id, e.id, "too-soon", now + 59_999))
            assertNotNull(m.repo.reserveEvidenceSample(id, e.id, "two", now + 60_000))
            val restored = com.hoodie.app.data.repository.MobilityRepository(m.g.db)
            assertNull(restored.reserveEvidenceSample(id, e.id, "one", now + 120_000))
            assertEquals(2, restored.evidenceFor(id)!!.attempts)
        } finally { m.close() }
    }

    @Test fun disablingDetectionDuringReadRejectsSampleAndCancellationReachesProvider() = runBlocking {
        val m = MobilityGraph(intelligenceEnabled = true)
        try {
            val id = vehicle(m); val e = m.repo.evidenceFor(id)!!
            val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            m.location.speedOverride = { started.complete(Unit); release.await(); SpeedObservation(15f, m.now()) }
            val job = async { m.sampler!!.sample(id, e.id, "disable") }
            started.await()
            m.g.settings.setMobility(m.g.settings.current().mobility.copy(detectionEnabled = false))
            release.complete(Unit); job.await()
            assertEquals(0, m.repo.evidenceFor(id)!!.sampleCount)
            m.g.settings.setMobility(m.g.settings.current().mobility.copy(detectionEnabled = true))
            m.g.clock.millis += 60_000
            val cancelled = CompletableDeferred<Unit>(); val startedAgain = CompletableDeferred<Unit>()
            m.location.speedOverride = { try { startedAgain.complete(Unit); awaitCancellation() } finally { cancelled.complete(Unit) } }
            val second = launch { m.sampler!!.sample(id, e.id, "cancel") }
            startedAgain.await(); second.cancelAndJoin(); cancelled.await()
            assertNull(m.repo.evidenceFor(id)!!.reservationId)
            assertEquals(2, m.repo.session(id)!!.speedSampleAttempts)
        } finally { m.close() }
    }
}

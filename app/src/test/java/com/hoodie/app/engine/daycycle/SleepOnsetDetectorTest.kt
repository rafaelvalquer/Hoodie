package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.daycycle.SleepConfidence
import com.hoodie.app.domain.daycycle.SleepOnsetReason
import com.hoodie.app.domain.phoneinsights.model.AppSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SleepOnsetDetectorTest {
    private val detector = SleepOnsetDetector()
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 5, 10)
    private val day = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val dayEnd = day + 24 * 60 * 60_000L
    private fun at(h: Int, m: Int = 0, offset: Long = 0) = day + offset + (h * 60L + m) * 60_000L

    private fun detect(
        activities: List<RealUserActivity>,
        contexts: List<ContextEventEntity> = emptyList(),
        mobility: List<MobilitySessionEntity> = emptyList(),
        until: Long = dayEnd + 4 * 60 * 60_000L,
        now: Long = until,
        schedule: SleepSchedule = SleepSchedule(),
    ) = detector.detect(date, at(7), dayEnd, until, now, zone, schedule, activities, contexts, mobility)

    private fun activity(time: Long, source: RealUserActivitySource = RealUserActivitySource.TIMELINE) = RealUserActivity(time, source)

    private fun home(start: Long, end: Long? = null) = ContextEventEntity(
        id = 1, type = UserContextType.HOME, startedAt = start, endedAt = end,
        confidence = 1f, placeId = 1, source = ContextSource.GEOFENCE,
    )

    @Test fun homePhoneAndBedtimeProduceHighConfidenceOnsetAfterGrace() {
        val context = home(at(21, 30))
        val movement = MobilitySessionEntity(
            id = 2, startedAt = at(21, 25), endedAt = at(21, 28),
            initialMode = MovementMode.WALKING, currentMode = MovementMode.WALKING,
            state = MobilityState.ARRIVED, confidence = 1f, confirmed = true,
            source = MobilitySource.CONFIRMATION,
        )
        val phone = RealUserActivityBuilder.build(
            day, dayEnd + 4 * 60 * 60_000L,
            appSessions = listOf(AppSession("video", at(22, 15), at(22, 20))),
            contexts = listOf(context), mobilitySessions = listOf(movement), analysisEnabled = true,
        )

        val result = detect(phone, listOf(context), listOf(movement))

        assertEquals(at(22, 28), result?.startedAt)
        assertEquals(SleepConfidence.HIGH, result?.confidence)
        assertEquals(SleepOnsetReason.CORROBORATED, result?.reason)
        assertFalse(result?.provisional ?: true)
    }

    @Test fun homeAndScheduleCanDetectWithoutPhoneAtMediumConfidence() {
        val context = home(at(21, 40))
        val activity = listOf(activity(at(22, 10)))

        val result = detect(activity, listOf(context))

        assertEquals(at(22, 18), result?.startedAt)
        assertEquals(SleepConfidence.MEDIUM, result?.confidence)
        assertEquals(SleepOnsetReason.HOME_INACTIVITY, result?.reason)
    }

    @Test fun laterPhoneActivityInvalidatesEarlierOnsetAndMovesItForward() {
        val context = home(at(21, 30))
        val sessions = listOf(
            AppSession("video", at(22), at(22, 5)),
            AppSession("video", at(23, 10), at(23, 11)),
        )
        val activities = RealUserActivityBuilder.build(day, dayEnd + 4 * 60 * 60_000L, sessions, contexts = listOf(context), analysisEnabled = true)

        val result = detect(activities, listOf(context))

        assertEquals(at(23, 19), result?.startedAt)
        assertTrue(requireNotNull(result).startedAt > at(22, 8))
    }

    @Test fun confirmedMovementAfterLeavingHomeDoesNotBecomeSleepEvidence() {
        val home = home(at(20), at(22, 40))
        val movement = MobilitySessionEntity(
            id = 3, startedAt = at(22, 40), endedAt = at(22, 42),
            initialMode = MovementMode.WALKING, currentMode = MovementMode.WALKING,
            state = MobilityState.WALKING, confidence = 1f, confirmed = true,
            source = MobilitySource.CONFIRMATION,
        )
        val activities = RealUserActivityBuilder.build(day, dayEnd + 4 * 60 * 60_000L, mobilitySessions = listOf(movement), contexts = listOf(home), mobilityEnabled = true)

        assertNull(detect(activities, listOf(home), listOf(movement)))
    }

    @Test fun unsupportedGapAndShortCurrentInactivityRemainUnknown() {
        val unsupported = detect(listOf(activity(at(20, 30))))
        assertNull(unsupported)

        val context = home(at(21, 30))
        val recent = listOf(activity(at(22, 20), RealUserActivitySource.PHONE))
        assertNull(detect(recent, listOf(context), until = at(23, 0), now = at(23, 0)))
    }

    @Test fun lookaheadActivityCanCorroborateAndMarkPastDayNonProvisional() {
        val context = home(at(21, 30))
        val activities = listOf(activity(at(22, 20), RealUserActivitySource.PHONE), activity(at(1, 30, 24 * 60 * 60_000L)))

        val result = detect(activities, listOf(context))

        assertEquals(at(22, 28), result?.startedAt)
        assertEquals(SleepConfidence.HIGH, result?.confidence)
        assertFalse(result?.provisional ?: true)
    }
}

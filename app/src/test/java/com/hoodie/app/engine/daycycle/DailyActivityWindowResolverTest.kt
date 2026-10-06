package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.daycycle.WakeReason
import com.hoodie.app.domain.phoneinsights.model.AppSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailyActivityWindowResolverTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val date = LocalDate.of(2026, 3, 8)
    private val day = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun at(hour: Int, minute: Int = 0) = day + (hour * 60L + minute) * 60_000

    private fun resolve(
        now: Long = at(22), phone: List<AppSession> = emptyList(),
        contexts: List<ContextEventEntity> = emptyList(), activities: List<HoodieActivityEntity> = emptyList(),
        movements: List<MobilitySessionEntity> = emptyList(), timeline: List<TimelineEventEntity> = emptyList(),
        digital: Boolean = true, mobility: Boolean = true, schedule: SleepSchedule = SleepSchedule(),
    ) = DailyActivityWindowResolver.resolve(date, day, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now, zone, schedule, contexts, activities, timeline, phone, movements, digital, mobility)

    @Test fun phoneSignalWinsWhenDigitalAnalysisEnabledAndNightPeekDoesNot() {
        val peek = listOf(AppSession("chat", at(3), at(3) + 20_000))
        val sustained = listOf(AppSession("chat", at(6, 50), at(6, 53)))
        assertEquals(at(6, 50), resolve(phone = peek + sustained).activeStartAt)
        assertEquals(at(6, 50), resolve(phone = sustained).activeStartAt)
        assertEquals(WakeReason.PHONE_SUSTAINED, resolve(phone = sustained).wakeReason)
        assertEquals(at(7), resolve(phone = sustained, digital = false).activeStartAt)
    }

    @Test fun mobilityAndHomeExitWorkWhenPhoneAnalysisIsOff() {
        val movement = MobilitySessionEntity(startedAt = at(7, 10), endedAt = at(7, 30), initialMode = MovementMode.WALKING, currentMode = MovementMode.WALKING, state = MobilityState.WALKING, confidence = 1f, confirmed = true, source = MobilitySource.ACTIVITY_RECOGNITION)
        assertEquals(at(7, 10), resolve(movements = listOf(movement), digital = false).activeStartAt)
        assertEquals(at(7), resolve(movements = listOf(movement), digital = false, mobility = false).activeStartAt)
        val sustained = listOf(AppSession("app", at(6, 30), at(6, 34)))
        assertEquals(at(6, 30), resolve(phone = sustained, mobility = false).activeStartAt)
        val home = ContextEventEntity(id = 1, type = UserContextType.HOME, startedAt = day - 5_000, endedAt = at(7, 20), confidence = 1f, placeId = 1, source = ContextSource.GEOFENCE)
        assertEquals(at(7, 20), resolve(contexts = listOf(home), digital = false).activeStartAt)
        assertEquals(WakeConfidence.HIGH, resolve(contexts = listOf(home), digital = false).wakeConfidence)
    }

    @Test fun usesScheduleFallbackAndAcceptsEarlyOrLateWakeTime() {
        val early = listOf(AppSession("app", at(4, 30), at(4, 34)))
        val late = listOf(AppSession("app", at(11), at(11, 4)))
        assertEquals(at(4, 30), resolve(phone = early).activeStartAt)
        assertEquals(at(11), resolve(phone = late).activeStartAt)
        val fallback = resolve(digital = false, mobility = false)
        assertEquals(at(7), fallback.activeStartAt)
        assertEquals(WakeConfidence.LOW, fallback.wakeConfidence)
        assertTrue(fallback.provisional)
    }

    @Test fun overnightWakeDoesNotInferSleepFromMidnight() {
        val awake = listOf(AppSession("app", at(0, 45), at(0, 46)), AppSession("app", at(1, 20), at(1, 32)))
        val wake = listOf(AppSession("app", at(8, 12), at(8, 16)))
        val result = resolve(phone = awake + wake)
        assertEquals(at(8, 12), result.activeStartAt)
        assertTrue(result.sleepBeforeStart?.startedAt ?: Long.MAX_VALUE > at(1, 32))
        assertFalse(result.sleepBeforeStart?.startedAt == day)
    }

    @Test fun dstDayUsesZoneBoundariesAndKeepsCurrentCivilStart() {
        val ny = ZoneId.of("America/New_York")
        val dstDate = LocalDate.of(2026, 3, 8)
        val from = dstDate.atStartOfDay(ny).toInstant().toEpochMilli()
        val to = dstDate.plusDays(1).atStartOfDay(ny).toInstant().toEpochMilli()
        val result = DailyActivityWindowResolver.resolve(dstDate, from, to, from + 10 * 3_600_000L, ny, SleepSchedule(), emptyList(), emptyList(), emptyList())
        assertEquals(from, result.civilStartAt)
        assertTrue(to - from == 23 * 3_600_000L)
    }
}

package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.engine.daycycle.DailyActivityWindowResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.hoodie.app.domain.daycycle.WakeConfidence
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class JourneyActiveDayIntegrationTest {
    @Test fun nightToWakeToCommuteUsesPhoneStartAndCarriesHomeIntoReplay() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val date = LocalDate.of(2026, 6, 1)
        val day = date.atStartOfDay(zone).toInstant().toEpochMilli()
        fun at(h: Int, m: Int = 0) = day + (h * 60L + m) * 60_000
        val home = ContextEventEntity(10, UserContextType.HOME, day - 50 * 60_000, at(7, 28), 1f, 1, ContextSource.GEOFENCE)
        val work = ContextEventEntity(11, UserContextType.WORK, at(8, 2), at(17), 1f, 2, ContextSource.GEOFENCE)
        val sleep = HoodieActivityEntity(12, HoodieActivity.SLEEPING, day - 40 * 60_000, at(6, 40), UserContextType.HOME)
        val workActivity = HoodieActivityEntity(13, HoodieActivity.WORKING, at(8, 2), at(17), UserContextType.WORK)
        val rawTimeline = listOf(TimelineEventEntity(1, at(0), TimelineActor.HOODIE, "🌙", "Foi dormir", TimelineSourceType.HOODIE_ACTIVITY, sleep.startedAt))
        val phone = listOf(AppSession("chat", at(6, 47), at(6, 48)), AppSession("social", at(6, 48), at(6, 51)))
        val walk = MobilitySessionEntity(startedAt = at(7, 10), endedAt = at(7, 28), initialMode = MovementMode.WALKING, currentMode = MovementMode.WALKING, state = MobilityState.ARRIVED, confidence = 1f, confirmed = true, source = MobilitySource.CONFIRMATION)
        val window = DailyActivityWindowResolver.resolve(date, day, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), at(20), zone, SleepSchedule(), listOf(home, work), listOf(sleep, workActivity), rawTimeline, phone, listOf(walk), analysisEnabled = true)
        val hoodieHome = PlaceEntity(1, "Casa", PlaceType.HOME, "", 100f, 1f, createdAt = day)
        val office = PlaceEntity(2, "Trabalho", PlaceType.WORK, "", 100f, 1f, createdAt = day)
        val diary = DiaryAssembler.build(date, listOf(home, work), rawTimeline, listOf(sleep, workActivity), listOf(hoodieHome, office), day, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), at(20), window)
        assertEquals(at(6, 47), diary.activityWindow.activeStartAt)
        assertEquals(WakeConfidence.HIGH, diary.activityWindow.wakeConfidence)
        assertEquals(at(6, 47), diary.replay.startAt)
        assertEquals(at(6, 47), diary.visits.first().arrivalAt)
        assertFalse(diary.timeline.any { it.timestamp == day && (it.title.contains("Chegou") || it.title.contains("dormir")) })
    }
}

package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneSummary
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import java.time.LocalDate
import java.time.ZoneId

/**
 * Dia completo da Definition of Done da V0.2 (Diary Lab e testes de regressão):
 *
 *     06:50 Casa · 08:00 sai (transporte) · 08:45 Trabalho · 12:00 Restaurante
 *     13:00 Trabalho · 18:00 Academia · 19:30 Casa · fim às 22:00
 *
 * Celular: 07:05 WhatsApp · 08:12 Maps · 09:10 Teams · 12:15 YouTube ·
 *          14:20 Chrome · 18:20 Spotify · 20:00 YouTube
 */
object DiaryRegressionScenario {
    const val WHATSAPP = "com.whatsapp"
    const val MAPS = "com.google.android.apps.maps"
    const val TEAMS = "com.microsoft.teams"
    const val YOUTUBE = "com.google.android.youtube"
    const val CHROME = "com.android.chrome"
    const val SPOTIFY = "com.spotify.music"

    private val APPS = mapOf(
        WHATSAPP to ("WhatsApp" to HoodieAppCategory.SOCIAL),
        MAPS to ("Maps" to HoodieAppCategory.NAVIGATION),
        TEAMS to ("Teams" to HoodieAppCategory.WORK),
        YOUTUBE to ("YouTube" to HoodieAppCategory.VIDEO),
        CHROME to ("Chrome" to HoodieAppCategory.TOOLS),
        SPOTIFY to ("Spotify" to HoodieAppCategory.MUSIC),
    )

    fun create(date: LocalDate, zone: ZoneId): DailyDiary {
        val day = startOfDay(date, zone)
        fun at(h: Int, m: Int = 0) = day + (h * 60L + m) * 60_000L
        data class Stop(val id: Long, val name: String, val type: PlaceType, val start: Long, val end: Long, val ctx: UserContextType)
        val stops = listOf(
            Stop(1, "Casa", PlaceType.HOME, at(6, 50), at(8, 0), UserContextType.HOME),
            Stop(2, "Trabalho", PlaceType.WORK, at(8, 45), at(12, 0), UserContextType.WORK),
            Stop(3, "Restaurante", PlaceType.RESTAURANT, at(12, 0), at(13, 0), UserContextType.LUNCH),
            Stop(2, "Trabalho", PlaceType.WORK, at(13, 0), at(18, 0), UserContextType.WORK),
            Stop(4, "Academia", PlaceType.GYM, at(18, 0), at(19, 15), UserContextType.GYM),
            Stop(1, "Casa", PlaceType.HOME, at(19, 30), at(22, 0), UserContextType.HOME),
        )
        val timeline = listOf(
            DiaryTimelineItem("rc-1", at(6, 50), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Acordou em casa", emoji = "🌤️", relatedPlaceId = 1, relatedContext = UserContextType.HOME),
            DiaryTimelineItem("rc-2", at(8, 0), DiaryTimelineType.LEFT, DiaryActor.USER, "Hoodie saiu de casa", emoji = "🚶", relatedPlaceId = 1, relatedContext = UserContextType.COMMUTING),
            DiaryTimelineItem("rc-3", at(8, 45), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou ao trabalho", emoji = "🏢", relatedPlaceId = 2, relatedContext = UserContextType.WORK),
            DiaryTimelineItem("rc-4", at(12, 0), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Almoço no restaurante", emoji = "🍽️", relatedPlaceId = 3, relatedContext = UserContextType.LUNCH),
            DiaryTimelineItem("rc-5", at(13, 0), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Voltou ao trabalho", emoji = "🏢", relatedPlaceId = 2, relatedContext = UserContextType.WORK),
            DiaryTimelineItem("rc-6", at(18, 0), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou à academia", emoji = "🏋️", relatedPlaceId = 4, relatedContext = UserContextType.GYM),
            DiaryTimelineItem("rc-7", at(19, 30), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou em casa", emoji = "🏠", relatedPlaceId = 1, relatedContext = UserContextType.HOME),
        )
        val visits = stops.map { s ->
            PlaceVisit(
                placeId = s.id, placeName = s.name, placeType = s.type, arrivalAt = s.start, departureAt = s.end, durationMs = s.end - s.start,
                visitsCount = stops.count { it.id == s.id },
                relatedTimelineIds = timeline.filter { it.timestamp in s.start until s.end || (it.timestamp == s.start) }.map { it.id },
            )
        }
        val contexts = stops.map { s -> ContextEventEntity(type = s.ctx, startedAt = s.start, endedAt = s.end, confidence = 1f, placeId = s.id, source = ContextSource.GEOFENCE) } +
            ContextEventEntity(type = UserContextType.COMMUTING, startedAt = at(8, 0), endedAt = at(8, 45), confidence = 1f, placeId = null, source = ContextSource.GEOFENCE)
        val activities = listOf(
            HoodieActivityEntity(activity = HoodieActivity.COMMUTING, startedAt = at(8, 0), endedAt = at(8, 45), userContext = UserContextType.COMMUTING),
            HoodieActivityEntity(activity = HoodieActivity.WORKING, startedAt = at(8, 50), endedAt = at(12, 0), userContext = UserContextType.WORK),
            HoodieActivityEntity(activity = HoodieActivity.EATING, startedAt = at(12, 5), endedAt = at(12, 55), userContext = UserContextType.LUNCH),
            HoodieActivityEntity(activity = HoodieActivity.WORKING, startedAt = at(13, 5), endedAt = at(18, 0), userContext = UserContextType.WORK),
            HoodieActivityEntity(activity = HoodieActivity.TRAINING, startedAt = at(18, 5), endedAt = at(19, 10), userContext = UserContextType.GYM),
            HoodieActivityEntity(activity = HoodieActivity.GAMING, startedAt = at(20, 0), endedAt = at(21, 15), userContext = UserContextType.HOME),
        )
        val sessions = listOf(
            AppSession(WHATSAPP, at(7, 5), at(7, 15)),
            AppSession(MAPS, at(8, 12), at(8, 40)),
            AppSession(TEAMS, at(9, 10), at(10, 0)),
            AppSession(YOUTUBE, at(12, 15), at(12, 45)),
            AppSession(CHROME, at(14, 20), at(14, 50)),
            AppSession(SPOTIFY, at(18, 20), at(19, 0)),
            AppSession(YOUTUBE, at(20, 0), at(21, 0)),
        )
        val topApps = sessions.groupBy { it.packageName }.map { (pkg, list) ->
            val (label, category) = APPS.getValue(pkg)
            AppUsageEntry(pkg, label, category, list.sumOf { it.durationMs }, list.size, list.minOf { it.startedAt }, list.maxOf { it.endedAt }, AppIconSource.Installed(pkg))
        }.sortedByDescending { it.foregroundMs }
        val phone = DailyPhoneInsights(
            summary = DailyPhoneSummary(date, sessions.sumOf { it.durationMs }, sessions.size, sessions.size, sessions.first().startedAt, sessions.last().endedAt, sessions.maxOf { it.durationMs }),
            topApps = topApps, usageByContext = emptyList(), appTimeline = emptyList(), categoryUsage = emptyList(),
            appSessions = sessions,
        )
        val map = DailyMapBuilder.build(visits)
        val end = at(22, 0)
        val replay = ReplaySequenceBuilder.build(visits, timeline, at(6, 50), end, contexts, activities, end, map)
        val hour = 60 * 60_000L
        return DailyDiary(
            summary = DailySummary(date, homeMs = (70 + 150) * 60_000L, workMs = (195 + 300) * 60_000L, commutingMs = 45 * 60_000L, lunchMs = hour, gymMs = 75 * 60_000L),
            timeline = timeline,
            visits = visits,
            map = map,
            replay = replay,
            phoneInsights = phone,
        )
    }
}

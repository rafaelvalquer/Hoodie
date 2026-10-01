package com.hoodie.app.data.repository

import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.HoodieActivityDao
import com.hoodie.app.core.database.PlaceDao
import com.hoodie.app.core.database.TimelineDao
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.engine.diary.DiaryAssembler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val contexts: ContextEventDao,
    private val timeline: TimelineDao,
    private val activities: HoodieActivityDao,
    private val places: PlaceDao,
    private val clock: ClockProvider,
) {
    suspend fun loadDiary(date: LocalDate): DailyDiary = withContext(Dispatchers.IO) {
        val zone = clock.zone()
        val from = startOfDay(date, zone)
        val to = startOfDay(date.plusDays(1), zone)
        val now = clock.nowMillis()
        val contextEvents = contexts.overlapping(from, to)
        val timelineEvents = timeline.range(from, to)
        val hoodieActivities = activities.overlapping(from, to)
        val knownPlaces = places.getAll()
        withContext(Dispatchers.Default) {
            DiaryAssembler.build(date, contextEvents, timelineEvents, hoodieActivities, knownPlaces, from, to, now)
        }
    }
}

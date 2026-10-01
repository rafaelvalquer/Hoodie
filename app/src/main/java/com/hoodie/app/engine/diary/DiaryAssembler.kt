package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.engine.timeline.ContextSpan
import java.time.LocalDate

object DiaryAssembler {
    fun build(date: LocalDate, contexts: List<ContextEventEntity>, events: List<TimelineEventEntity>, activities: List<HoodieActivityEntity>, places: List<PlaceEntity>, dayStart: Long, dayEnd: Long, now: Long): DailyDiary {
        val clippedContexts = contexts.map { ContextSpan(it.type, it.startedAt, it.endedAt) }
        val dayContexts = contexts.filter { it.startedAt < minOf(dayEnd, now) && (it.endedAt == null || it.endedAt > dayStart) }
        val dayEvents = events.filter { it.timestamp >= dayStart && it.timestamp < dayEnd && it.timestamp <= now }
        val dayActivities = activities.filter { it.startedAt < minOf(dayEnd, now) && it.endedAt > dayStart }
        val timeline = DailyTimelineBuilder.build(dayContexts, dayEvents, dayActivities, dayStart, dayEnd, now)
        val visits = PlaceVisitBuilder.build(dayContexts, places, dayStart, dayEnd, now, timeline, dayActivities)
        val enriched = timeline.map { item ->
            val context = clippedContexts.lastOrNull { item.timestamp >= it.startedAt && (it.endedAt == null || item.timestamp < it.endedAt) }
            val activity = dayActivities.lastOrNull { item.timestamp >= it.startedAt && item.timestamp < it.endedAt }
            item.copy(relatedContext = item.relatedContext ?: context?.type, subtitle = item.subtitle ?: activity?.activity?.label)
        }
        val visitEnriched = visits.map { visit ->
            visit.copy(relatedTimelineIds = enriched.filter { it.timestamp in visit.arrivalAt..(visit.departureAt ?: now) }.map { it.id })
        }
        val summary = DailySummaryCalculator.compute(clippedContexts, date, dayStart, dayEnd, now)
        val map = DailyMapBuilder.build(visitEnriched)
        val replay = ReplaySequenceBuilder.build(visitEnriched, enriched, dayStart, minOf(dayEnd, now), dayContexts, dayActivities, now, map)
        return DailyDiary(summary, enriched, visitEnriched, map, replay)
    }
}

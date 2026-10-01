package com.hoodie.app.engine.diary

import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.ReplaySequence
import com.hoodie.app.domain.diary.model.DiaryMapData
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity

object ReplaySequenceBuilder {
    fun build(visits: List<PlaceVisit>, timeline: List<DiaryTimelineItem>, dayStart: Long, dayEnd: Long, contexts: List<ContextEventEntity> = emptyList(), activities: List<HoodieActivityEntity> = emptyList(), now: Long = dayEnd, map: DiaryMapData = DiaryMapData()): ReplaySequence {
        val contextRanges = contexts.mapNotNull { e ->
            val start = maxOf(e.startedAt, dayStart); val end = minOf(e.endedAt ?: now, dayEnd, now)
            if (end <= start) null else (start..end) to e.type
        }
        val activityRanges = activities.mapNotNull { e ->
            val start = maxOf(e.startedAt, dayStart); val end = minOf(e.endedAt, dayEnd, now)
            if (end <= start) null else (start..end) to e.activity
        }
        val intervalEnds = contextRanges.map { it.first.last } + activityRanges.map { it.first.last }
        val timestamps = visits.flatMap { listOfNotNull(it.arrivalAt, it.departureAt) } + timeline.map { it.timestamp } + intervalEnds
        val observedStart = timestamps.minOrNull() ?: dayStart
        val observedEnd = maxOf(observedStart, timestamps.maxOrNull()?.coerceAtMost(dayEnd) ?: observedStart)
        return ReplaySequence(observedStart, observedEnd, visits, timeline, contextRanges, activityRanges, map)
    }
}

package com.hoodie.app.engine.diary

import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.ReplaySequence

object ReplaySequenceBuilder {
    fun build(visits: List<PlaceVisit>, timeline: List<DiaryTimelineItem>, dayStart: Long, dayEnd: Long): ReplaySequence {
        val timestamps = visits.flatMap { listOfNotNull(it.arrivalAt, it.departureAt) } + timeline.map { it.timestamp }
        return ReplaySequence(timestamps.minOrNull() ?: dayStart, timestamps.maxOrNull()?.coerceAtMost(dayEnd) ?: dayEnd, visits, timeline)
    }
}

package com.hoodie.app.engine.diary

import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.ReplaySequence
import com.hoodie.app.domain.diary.model.DiaryMapData
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.config.HoodieConfig

object ReplaySequenceBuilder {
    fun build(
        visits: List<PlaceVisit>, timeline: List<DiaryTimelineItem>, dayStart: Long, dayEnd: Long,
        contexts: List<ContextEventEntity> = emptyList(), activities: List<HoodieActivityEntity> = emptyList(),
        now: Long = dayEnd, map: DiaryMapData = DiaryMapData(), activeStartAt: Long = dayStart,
        activeEndAt: Long? = null,
    ): ReplaySequence {
        val replayFrom = maxOf(dayStart, activeStartAt)
        val replayTo = minOf(activeEndAt ?: minOf(dayEnd, now), dayEnd, now).coerceAtLeast(replayFrom)
        val contextRanges = contexts.mapNotNull { e ->
            val start = maxOf(e.startedAt, replayFrom); val end = minOf(e.endedAt ?: now, replayTo, now)
            if (end <= start) null else (start..end) to e.type
        }
        val activityRanges = activities.mapNotNull { e ->
            if (e.activity == HoodieActivity.SLEEPING && e.startedAt < replayFrom) return@mapNotNull null
            val start = maxOf(e.startedAt, replayFrom); val end = minOf(e.endedAt, replayTo, now)
            if (end <= start) null else (start..end) to e.activity
        }.toMutableList()
        // Apresentação efêmera: não grava nem altera a atividade canônica que veio do banco.
        val hasSleepPrelude = activities.any { it.activity == HoodieActivity.SLEEPING && it.startedAt < replayFrom && it.endedAt >= replayFrom }
        if (hasSleepPrelude) {
            val nextActivity = activityRanges.filter { it.second != HoodieActivity.SLEEPING && it.first.first > replayFrom }
                .minOfOrNull { it.first.first } ?: replayFrom + HoodieConfig.WAKE_PRESENTATION_MS
            val wakeEnd = minOf(replayTo, replayFrom + HoodieConfig.WAKE_PRESENTATION_MS, nextActivity)
            if (wakeEnd > replayFrom) activityRanges += (replayFrom..wakeEnd) to HoodieActivity.WAKING_UP
        }
        val intervalEnds = contextRanges.map { it.first.last } + activityRanges.map { it.first.last }
        val visibleVisits = visits.filter { it.departureAt == null || it.departureAt >= replayFrom }
            .map { visit -> visit.copy(arrivalAt = maxOf(visit.arrivalAt, replayFrom), departureAt = visit.departureAt?.coerceAtMost(replayTo)) }
            .filter { it.arrivalAt <= replayTo }
        val visibleTimeline = timeline.filter { it.timestamp in replayFrom..replayTo }
        val timestamps = visibleVisits.flatMap { listOfNotNull(it.arrivalAt, it.departureAt) } + visibleTimeline.map { it.timestamp } + intervalEnds
        // A janela ativa é o começo do dia narrativo mesmo se o primeiro evento mostrado vier depois.
        val observedStart = replayFrom
        val observedEnd = activeEndAt?.let { replayTo } ?: maxOf(observedStart, timestamps.maxOrNull()?.coerceAtMost(replayTo) ?: observedStart)
        return ReplaySequence(observedStart, observedEnd, visibleVisits, visibleTimeline, contextRanges, activityRanges, map)
    }
}

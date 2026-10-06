package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType

object DailyTimelineBuilder {
    fun build(
        contexts: List<ContextEventEntity>, timeline: List<TimelineEventEntity>, activities: List<HoodieActivityEntity>,
        dayStart: Long = Long.MIN_VALUE, dayEnd: Long = Long.MAX_VALUE, now: Long = dayEnd,
        activeStartAt: Long = dayStart,
    ): List<DiaryTimelineItem> {
        val items = mutableListOf<DiaryTimelineItem>()
        val visibleStart = maxOf(dayStart, activeStartAt)
        contexts.forEach { event ->
            val startAt = maxOf(event.startedAt, visibleStart)
            val endAt = minOf(event.endedAt ?: now, dayEnd, now)
            if (endAt <= startAt) return@forEach
            val enter = timeline.firstOrNull { it.sourceType == TimelineSourceType.CONTEXT && it.sourceId == event.id && it.actor == TimelineActor.USER && it.timestamp in visibleStart until dayEnd }
            val equivalentRecorded = timeline.any {
                it.sourceType == TimelineSourceType.CONTEXT && it.actor == TimelineActor.USER &&
                    it.timestamp == startAt && it.timestamp in visibleStart until dayEnd &&
                    (it.sourceId == event.id || it.text.contains(event.type.label, ignoreCase = true))
            }
            if (enter != null) {
                if (enter.timestamp in startAt..endAt) items += DiaryTimelineItem("timeline-${enter.id}", enter.timestamp, DiaryTimelineType.CONTEXT_CHANGE, DiaryActor.USER, enter.text, emoji = enter.emoji, relatedPlaceId = event.placeId, relatedContext = event.type)
            } else if (event.startedAt >= visibleStart && !equivalentRecorded) {
                items += DiaryTimelineItem("context-${event.id}", startAt, DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou: ${event.type.label.lowercase()}", emoji = event.type.emoji, relatedPlaceId = event.placeId, relatedContext = event.type)
            }
            if (event.endedAt != null && event.endedAt in visibleStart..endAt && event.endedAt > visibleStart) items += DiaryTimelineItem("left-${event.id}", event.endedAt, DiaryTimelineType.LEFT, DiaryActor.USER, "Saiu de ${event.type.label.lowercase()}", emoji = "🚶", relatedPlaceId = event.placeId, relatedContext = event.type)
        }
        timeline.forEach { e ->
            if (e.timestamp in visibleStart until dayEnd) {
                val activity = if (e.sourceType == TimelineSourceType.HOODIE_ACTIVITY) activities.firstOrNull { it.startedAt == e.sourceId } else null
                val isMemory = e.sourceType == TimelineSourceType.MEMORY
                val isSystem = e.sourceType == TimelineSourceType.SYSTEM
                val persistedContext = e.sourceType == TimelineSourceType.CONTEXT
                val context = if (persistedContext) contexts.firstOrNull { it.id == e.sourceId } else null
                items += DiaryTimelineItem(
                    id = "timeline-${e.id}", timestamp = e.timestamp,
                    type = when { isMemory -> DiaryTimelineType.MEMORY; activity != null -> DiaryTimelineType.ACTIVITY; persistedContext -> DiaryTimelineType.CONTEXT_CHANGE; else -> DiaryTimelineType.NOTE },
                    actor = when { isSystem -> DiaryActor.SYSTEM; activity != null -> DiaryActor.HOODIE; else -> DiaryActor.valueOf(e.actor.name) },
                    title = e.text, subtitle = activity?.activity?.label, emoji = e.emoji,
                    relatedPlaceId = context?.placeId, relatedContext = activity?.userContext ?: context?.type,
                )
            }
        }
        activities.forEach { a ->
            val recorded = timeline.any { it.sourceType == TimelineSourceType.HOODIE_ACTIVITY && it.sourceId == a.startedAt && it.timestamp in visibleStart until dayEnd }
            val started = maxOf(a.startedAt, visibleStart)
            val equivalentRecorded = timeline.any {
                it.sourceType == TimelineSourceType.HOODIE_ACTIVITY && it.actor == TimelineActor.HOODIE &&
                    it.timestamp == started && it.timestamp in visibleStart until dayEnd
            }
            if (a.startedAt >= visibleStart && !recorded && !equivalentRecorded && started < minOf(a.endedAt, dayEnd, now)) {
                items += DiaryTimelineItem("activity-${a.id}", started, DiaryTimelineType.ACTIVITY, DiaryActor.HOODIE, a.activity.pastTense.replaceFirstChar { it.uppercase() }, subtitle = a.activity.label, emoji = a.activity.emoji, relatedContext = a.userContext)
            }
        }
        return items.distinctBy { it.id }.sortedWith(compareBy<DiaryTimelineItem> { it.timestamp }.thenBy { it.actor.ordinal })
    }
}

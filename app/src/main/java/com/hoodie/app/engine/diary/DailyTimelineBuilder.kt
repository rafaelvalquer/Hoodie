package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType

object DailyTimelineBuilder {
    fun build(contexts: List<ContextEventEntity>, timeline: List<TimelineEventEntity>, activities: List<HoodieActivityEntity>): List<DiaryTimelineItem> {
        val items = mutableListOf<DiaryTimelineItem>()
        contexts.forEach { event ->
            val enter = timeline.firstOrNull { it.sourceId == event.id && it.actor == TimelineActor.USER }
            if (enter != null) {
                items += DiaryTimelineItem("timeline-${enter.id}", enter.timestamp, DiaryTimelineType.CONTEXT_CHANGE, DiaryActor.USER, enter.text, emoji = enter.emoji, relatedPlaceId = event.placeId, relatedContext = event.type)
            } else {
                items += DiaryTimelineItem("context-${event.id}", event.startedAt, DiaryTimelineType.CONTEXT_CHANGE, DiaryActor.USER, "Você está em ${event.type.label.lowercase()}", emoji = event.type.emoji, relatedPlaceId = event.placeId, relatedContext = event.type)
            }
            event.endedAt?.let { ended ->
                if (ended > event.startedAt) items += DiaryTimelineItem("left-${event.id}", ended, DiaryTimelineType.LEFT, DiaryActor.USER, "Saiu de ${event.type.label.lowercase()}", emoji = "🚶", relatedPlaceId = event.placeId, relatedContext = event.type)
            }
        }
        timeline.forEach { e ->
            if (e.sourceType?.name in setOf("MEMORY", "SYSTEM")) {
                items += DiaryTimelineItem(
                    id = "timeline-${e.id}", timestamp = e.timestamp,
                    type = if (e.sourceType?.name == "MEMORY") DiaryTimelineType.MEMORY else DiaryTimelineType.NOTE,
                    actor = if (e.sourceType?.name == "SYSTEM") DiaryActor.SYSTEM else DiaryActor.valueOf(e.actor.name),
                    title = e.text, emoji = e.emoji,
                )
            }
        }
        activities.forEach { a ->
            items += DiaryTimelineItem("activity-${a.id}", a.startedAt, DiaryTimelineType.ACTIVITY, DiaryActor.HOODIE, a.activity.pastTense.replaceFirstChar { it.uppercase() }, subtitle = a.activity.label, emoji = a.activity.emoji, relatedContext = a.userContext)
        }
        return items.distinctBy { it.id }.sortedWith(compareBy<DiaryTimelineItem> { it.timestamp }.thenBy { it.actor.ordinal })
    }
}

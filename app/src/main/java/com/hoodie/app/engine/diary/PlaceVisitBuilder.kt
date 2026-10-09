package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.PlaceVisit

object PlaceVisitBuilder {
    fun build(
        contexts: List<ContextEventEntity>, places: List<PlaceEntity>, dayStart: Long, dayEnd: Long, now: Long,
        timeline: List<DiaryTimelineItem> = emptyList(), activities: List<HoodieActivityEntity> = emptyList(),
        activeStartAt: Long = dayStart,
        activeEndAt: Long? = null,
    ): List<PlaceVisit> {
        val placeById = places.associateBy { it.id }
        val orderedTimeline = timeline.sortedBy { it.timestamp }
        val orderedActivities = activities.sortedBy { it.startedAt }
        val visits = contexts.asSequence().filter { it.type != com.hoodie.app.core.model.UserContextType.COMMUTING }
            .mapNotNull { event ->
                val start = maxOf(event.startedAt, dayStart, activeStartAt)
                val visibleEnd = minOf(dayEnd, now, activeEndAt ?: dayEnd)
                val end = minOf(event.endedAt ?: now, visibleEnd)
                if (end <= start) return@mapNotNull null
                val place = event.placeId?.let(placeById::get)
                val type = place?.type ?: when (event.type) {
                    com.hoodie.app.core.model.UserContextType.HOME -> PlaceType.HOME
                    com.hoodie.app.core.model.UserContextType.WORK -> PlaceType.WORK
                    com.hoodie.app.core.model.UserContextType.LUNCH -> PlaceType.RESTAURANT
                    com.hoodie.app.core.model.UserContextType.DINING -> PlaceType.RESTAURANT
                    com.hoodie.app.core.model.UserContextType.GYM -> PlaceType.GYM
                    com.hoodie.app.core.model.UserContextType.STUDY -> PlaceType.SCHOOL
                    com.hoodie.app.core.model.UserContextType.SHOPPING -> PlaceType.MARKET
                    com.hoodie.app.core.model.UserContextType.VISITING -> PlaceType.FAMILY
                    com.hoodie.app.core.model.UserContextType.LEISURE -> PlaceType.LEISURE
                    com.hoodie.app.core.model.UserContextType.UNKNOWN, com.hoodie.app.core.model.UserContextType.TRAVEL -> PlaceType.OTHER
                    else -> PlaceType.OTHER
                }
                val name = place?.name ?: when (event.type) {
                    com.hoodie.app.core.model.UserContextType.LUNCH -> "Almoço"
                    com.hoodie.app.core.model.UserContextType.DINING -> "Restaurante"
                    else -> if (type == PlaceType.OTHER) "Outro lugar" else type.label
                }
                val firstTimelineItem = orderedTimeline.lowerBoundTimestamp(start)
                val afterTimelineItems = orderedTimeline.upperBoundTimestamp(end)
                val related = orderedTimeline.subList(firstTimelineItem, afterTimelineItems).filter {
                    it.relatedPlaceId == event.placeId || it.relatedContext == event.type
                }
                val activityLimit = orderedActivities.lowerBoundStartedAt(end)
                val dominant = orderedActivities.subList(0, activityLimit).filter {
                    it.startedAt < end && it.endedAt > start &&
                        (it.activity != com.hoodie.app.core.model.HoodieActivity.SLEEPING || it.startedAt >= activeStartAt)
                }
                    .groupBy { it.activity }
                    .mapValues { (_, spans) -> spans.sumOf { (minOf(it.endedAt, end) - maxOf(it.startedAt, start)).coerceAtLeast(0) } }
                    .maxByOrNull { it.value }?.key
                val recordedDeparture = event.endedAt?.takeIf { it > start && it < dayEnd && it <= now }
                val departure = when {
                    recordedDeparture != null -> minOf(recordedDeparture, visibleEnd)
                    activeEndAt != null && activeEndAt < minOf(dayEnd, now) -> visibleEnd
                    else -> null
                }
                PlaceVisit(event.placeId, name, type, start, departure, end - start, 1, related.map { it.id }, dominant, event.confidence, event.source)
            }.sortedBy { it.arrivalAt }.toList()
        fun key(visit: PlaceVisit): String = visit.placeId?.let { "place:$it" }
            ?: if (visit.placeType == PlaceType.OTHER) "unknown:${visit.arrivalAt}" else "type:${visit.placeType.name}"
        val counts = visits.groupingBy(::key).eachCount()
        return visits.map { it.copy(visitsCount = counts[key(it)] ?: 1) }
    }

    private fun List<DiaryTimelineItem>.lowerBoundTimestamp(value: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle].timestamp < value) low = middle + 1 else high = middle
        }
        return low
    }

    private fun List<DiaryTimelineItem>.upperBoundTimestamp(value: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle].timestamp <= value) low = middle + 1 else high = middle
        }
        return low
    }

    private fun List<HoodieActivityEntity>.lowerBoundStartedAt(value: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle].startedAt < value) low = middle + 1 else high = middle
        }
        return low
    }
}

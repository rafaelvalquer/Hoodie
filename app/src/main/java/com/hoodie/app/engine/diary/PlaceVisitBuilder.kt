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
    ): List<PlaceVisit> {
        val placeById = places.associateBy { it.id }
        val visits = contexts.asSequence().filter { it.type != com.hoodie.app.core.model.UserContextType.COMMUTING }
            .filter { it.placeId != null || it.type in setOf(com.hoodie.app.core.model.UserContextType.HOME, com.hoodie.app.core.model.UserContextType.WORK) }
            .mapNotNull { event ->
                val start = maxOf(event.startedAt, dayStart)
                val end = minOf(event.endedAt ?: now, dayEnd, now)
                if (end <= start) return@mapNotNull null
                val place = event.placeId?.let(placeById::get)
                val type = place?.type ?: when (event.type) {
                    com.hoodie.app.core.model.UserContextType.HOME -> PlaceType.HOME
                    com.hoodie.app.core.model.UserContextType.WORK -> PlaceType.WORK
                    com.hoodie.app.core.model.UserContextType.LUNCH -> PlaceType.RESTAURANT
                    com.hoodie.app.core.model.UserContextType.GYM -> PlaceType.GYM
                    com.hoodie.app.core.model.UserContextType.STUDY -> PlaceType.SCHOOL
                    com.hoodie.app.core.model.UserContextType.SHOPPING -> PlaceType.MARKET
                    com.hoodie.app.core.model.UserContextType.VISITING -> PlaceType.FAMILY
                    com.hoodie.app.core.model.UserContextType.LEISURE -> PlaceType.LEISURE
                    else -> PlaceType.OTHER
                }
                val name = place?.name ?: type.label
                val related = timeline.filter { it.timestamp in start..end && (it.relatedPlaceId == event.placeId || it.relatedContext == event.type) }
                val dominant = activities.filter { it.startedAt < end && it.endedAt > start }
                    .groupBy { it.activity }
                    .mapValues { (_, spans) -> spans.sumOf { (minOf(it.endedAt, end) - maxOf(it.startedAt, start)).coerceAtLeast(0) } }
                    .maxByOrNull { it.value }?.key
                PlaceVisit(event.placeId, name, type, start, event.endedAt?.takeIf { it < dayEnd }?.coerceAtMost(now), end - start, 1, related.map { it.id }, dominant)
            }.sortedBy { it.arrivalAt }.toList()
        val counts = visits.groupingBy { it.placeId ?: -it.placeType.ordinal.toLong() - 1 }.eachCount()
        return visits.map { it.copy(visitsCount = counts[it.placeId ?: -it.placeType.ordinal.toLong() - 1] ?: 1) }
    }
}

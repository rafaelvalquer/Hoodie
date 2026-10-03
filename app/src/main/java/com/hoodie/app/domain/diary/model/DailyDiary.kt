package com.hoodie.app.domain.diary.model

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.UserContextType
import java.time.LocalDate

data class DailySummary(
    val date: LocalDate,
    val homeMs: Long = 0,
    val workMs: Long = 0,
    val commutingMs: Long = 0,
    val lunchMs: Long = 0,
    val gymMs: Long = 0,
    val leisureMs: Long = 0,
    val otherMs: Long = 0,
) {
    val totalMs: Long get() = homeMs + workMs + commutingMs + lunchMs + gymMs + leisureMs + otherMs
}

enum class DiaryTimelineType { ARRIVED, LEFT, ACTIVITY, CONTEXT_CHANGE, MEMORY, NOTE, APP_USAGE, MOVEMENT }
enum class DiaryActor { USER, HOODIE, SYSTEM, PHONE }

data class DiaryTimelineItem(
    val id: String,
    val timestamp: Long,
    val type: DiaryTimelineType,
    val actor: DiaryActor,
    val title: String,
    val subtitle: String? = null,
    val emoji: String? = null,
    val relatedPlaceId: Long? = null,
    val relatedContext: UserContextType? = null,
)

data class PlaceVisit(
    val placeId: Long?,
    val placeName: String,
    val placeType: PlaceType,
    val arrivalAt: Long,
    val departureAt: Long?,
    val durationMs: Long,
    val visitsCount: Int,
    val relatedTimelineIds: List<String> = emptyList(),
    val dominantHoodieActivity: HoodieActivity? = null,
)

enum class DiaryMapNodeType { HOME, WORK, RESTAURANT, GYM, SCHOOL, MARKET, LEISURE, FAMILY, OTHER }
enum class EdgeStyle { NORMAL, COMMUTE }

data class DiaryMapNode(
    val id: String,
    val placeId: Long?,
    val label: String,
    val type: DiaryMapNodeType,
    val x: Int,
    val y: Int,
    val visitIndex: Int,
    val arrivalAt: Long?,
    val departureAt: Long?,
    val durationMs: Long,
)

data class DiaryMapEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val startedAt: Long?,
    val endedAt: Long?,
    val durationMs: Long,
    val style: EdgeStyle = EdgeStyle.COMMUTE,
)

data class DiaryMapData(val nodes: List<DiaryMapNode> = emptyList(), val edges: List<DiaryMapEdge> = emptyList())

data class ReplayFrame(
    val timestamp: Long,
    val activeNodeId: String?,
    val activeEdgeId: String?,
    val progressOnEdge: Float,
    val highlightedTimelineItemIds: Set<String>,
    val currentContext: UserContextType?,
    val currentHoodieActivity: HoodieActivity?,
)

data class ReplaySequence(
    val startAt: Long,
    val endAt: Long,
    val visits: List<PlaceVisit>,
    val timeline: List<DiaryTimelineItem>,
    val contexts: List<Pair<LongRange, UserContextType>> = emptyList(),
    val activities: List<Pair<LongRange, HoodieActivity>> = emptyList(),
    val map: DiaryMapData = DiaryMapData(),
) {
    fun frameAt(timestamp: Long): ReplayFrame {
        val at = timestamp.coerceIn(startAt, endAt)
        val current = visits.indexOfLast { it.arrivalAt <= at }.takeIf { it >= 0 }
        val visit = current?.let(visits::get)
        val next = current?.let { visits.getOrNull(it + 1) }
        val edgeDuration = if (visit != null && next != null) (next.arrivalAt - (visit.departureAt ?: visit.arrivalAt)).coerceAtLeast(1) else 0
        val edgeStart = visit?.departureAt ?: visit?.arrivalAt ?: at
        val onEdge = visit != null && next != null && at >= edgeStart && at < next.arrivalAt
        val edgeProgress = if (onEdge && edgeDuration > 0) ((at - edgeStart).toFloat() / edgeDuration).coerceIn(0f, 1f) else 0f
        val highlighted = timeline.filter { it.timestamp <= at }.takeLast(1).map { it.id }.toSet()
        return ReplayFrame(
            timestamp = at,
            activeNodeId = if (onEdge) null else current?.let { "visit-$it" },
            activeEdgeId = if (onEdge) current?.let { "edge-$it" } else null,
            progressOnEdge = edgeProgress,
            highlightedTimelineItemIds = highlighted,
            currentContext = contexts.lastOrNull { at in it.first }?.second,
            currentHoodieActivity = activities.lastOrNull { at in it.first }?.second,
        )
    }

    companion object {
        val EMPTY = ReplaySequence(0, 0, emptyList(), emptyList())
    }
}

data class DailyDiary(
    val summary: DailySummary,
    val timeline: List<DiaryTimelineItem>,
    val visits: List<PlaceVisit>,
    val map: DiaryMapData,
    val replay: ReplaySequence,
    /** Camada digital do dia (null = análise do celular desligada, sem permissão ou sem dados). */
    val phoneInsights: com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights? = null,
    /** Tempo por meio de deslocamento no dia (🚶 16 min, 🚌 31 min), sem trajeto. */
    val mobilityTotals: Map<com.hoodie.app.core.mobility.MovementMode, Long> = emptyMap(),
    /** Cada trecho de deslocamento com modo e horários (já recortado no dia), em ordem. */
    val movements: List<DiaryMovement> = emptyList(),
)

/** Um trecho de deslocamento: só modo e horários — nunca posição ou rota. */
data class DiaryMovement(
    val mode: com.hoodie.app.core.mobility.MovementMode,
    val startedAt: Long,
    val endedAt: Long,
) {
    val durationMs: Long get() = endedAt - startedAt
}

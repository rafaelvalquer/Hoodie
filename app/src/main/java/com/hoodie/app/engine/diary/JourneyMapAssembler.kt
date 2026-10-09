package com.hoodie.app.engine.diary

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DiaryMovement
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.domain.diary.model.JourneySegment
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.engine.deviceusage.VisitPhoneUsageCalculator

/**
 * DailyDiary → jornada do dia. Puro (sem Android, sem relógio próprio):
 * visitas viram nós na ordem, cada intervalo entre visitas vira um trecho com o
 * meio de transporte dominante, e cada nó sabe se é um retorno.
 */
object JourneyMapAssembler {

    /** Folga para casar um trecho de mobilidade com o intervalo entre duas visitas (relógios de fontes diferentes). */
    const val MODE_MATCH_SLACK_MS = 2 * MINUTE_MS

    fun build(diary: DailyDiary, now: Long): JourneyMapData {
        val visits = diary.visits.sortedBy { it.arrivalAt }
        if (visits.isEmpty()) return JourneyMapData.EMPTY.copy(startAt = diary.replay.startAt, endAt = diary.replay.endAt)
        val phone = diary.phoneInsights
        val keys = visits.map(::placeKey)
        val occurrences = keys.groupingBy { it }.eachCount()
        val totals = visits.indices.groupBy { keys[it] }.mapValues { (_, idx) -> idx.sumOf { visits[it].durationMs } }
        val previousCounts = mutableMapOf<String, Int>()

        val nodes = visits.mapIndexed { i, v ->
            val key = keys[i]
            val revisitCount = previousCounts.getOrDefault(key, 0)
            previousCounts[key] = revisitCount + 1
            JourneyNode(
                id = nodeId(i),
                visitIndex = i,
                placeId = v.placeId,
                placeName = v.placeName,
                placeType = v.placeType,
                arrivalAt = v.arrivalAt,
                departureAt = v.departureAt,
                durationMs = v.durationMs,
                hoodieActivity = v.dominantHoodieActivity,
                contextType = contextAt(diary, v.arrivalAt + v.durationMs / 2),
                phoneUsageMs = phone?.let {
                    VisitPhoneUsageCalculator.calculate(v.arrivalAt, v.departureAt, now, it.appSessions, it::labelOf, it::categoryOf)?.foregroundMs
                } ?: 0L,
                revisitCount = revisitCount,
                placeOccurrences = occurrences.getValue(key),
                placeTotalMs = totals.getValue(key),
            )
        }
        val segments = visits.zipWithNext().mapIndexed { i, (from, to) ->
            val start = from.departureAt ?: from.arrivalAt
            val end = maxOf(to.arrivalAt, start)
            JourneySegment(
                id = segmentId(i),
                index = i,
                fromNodeId = nodeId(i),
                toNodeId = nodeId(i + 1),
                startedAt = start,
                endedAt = end,
                durationMs = end - start,
                movementMode = dominantMode(diary.movements, start, end),
            )
        }
        val startAt = if (diary.replay.endAt > diary.replay.startAt) diary.replay.startAt else visits.first().arrivalAt
        val endAt = if (diary.replay.endAt > diary.replay.startAt) diary.replay.endAt else visits.last().let { it.departureAt ?: now }
        return JourneyMapData(nodes, segments, startAt, endAt)
    }

    fun nodeId(visitIndex: Int) = "journey-$visitIndex"
    fun segmentId(index: Int) = "journey-seg-$index"

    /** O meio com mais tempo dentro do intervalo; NONE nunca conta como deslocamento. */
    fun dominantMode(movements: List<DiaryMovement>, start: Long, end: Long): MovementMode? {
        val from = start - MODE_MATCH_SLACK_MS
        val to = end + MODE_MATCH_SLACK_MS
        return movements
            .filter { it.mode != MovementMode.NONE }
            .groupBy { it.mode }
            .mapValues { (_, list) -> list.sumOf { (minOf(it.endedAt, to) - maxOf(it.startedAt, from)).coerceAtLeast(0) } }
            .filterValues { it > 0 }
            .maxByOrNull { it.value }?.key
    }

    private fun placeKey(v: PlaceVisit) = v.placeId?.let { "id:$it" } ?: "name:${v.placeName.lowercase()}|${v.placeType}"

    private fun contextAt(diary: DailyDiary, at: Long) = diary.replay.contexts.lastOrNull { at in it.first }?.second
}

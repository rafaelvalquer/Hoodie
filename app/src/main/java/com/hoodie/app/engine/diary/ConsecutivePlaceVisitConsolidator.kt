package com.hoodie.app.engine.diary

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.ConsolidatedJourneyVisit
import com.hoodie.app.domain.diary.model.DiaryMovement
import com.hoodie.app.domain.diary.model.JourneyVisitProjection
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.diary.model.VisitSourceInterval

/**
 * Une visitas consecutivas ao mesmo lugar físico numa única permanência da Jornada.
 *
 *     Casa 08:00–09:00, Casa 09:00–10:00, Casa 10:00–11:00, Trabalho 11:30…
 *       → Casa 08:00–11:00 (3 eventos de origem), Trabalho 11:30…
 *
 * Só une quando: o mesmo `placeId` (nunca pelo nome ou tipo), os dois lados vêm de eventos
 * canônicos conhecidos, a folga entre eles cabe na [ConsolidationPolicy] e, nessa folga, não há
 * deslocamento, contexto COMMUTING nem evento de outro lugar. Casa → Trabalho → Casa continua com o
 * retorno, e trabalhar em casa continua uma permanência só (o contexto não é o lugar).
 *
 * Puro, determinístico e idempotente: a mesma entrada dá sempre o mesmo resultado, e consolidar o
 * resultado de novo não muda nada. A permanência é a união dos intervalos de origem — sobreposições
 * contam uma vez e a folga desconhecida não vira tempo no lugar.
 */
object ConsecutivePlaceVisitConsolidator {

    fun consolidate(
        visits: List<PlaceVisit>,
        evidence: List<VisitSourceInterval>,
        movements: List<DiaryMovement>,
        policy: ConsolidationPolicy = ConsolidationPolicy.DEFAULT,
    ): JourneyVisitProjection {
        if (visits.isEmpty()) return JourneyVisitProjection.EMPTY
        val byId = evidence.associateBy { it.contextEventId }
        val ordered = visits.sortedWith(compareBy<PlaceVisit> { it.arrivalAt }.thenBy { it.contextEventIds.minOrNull() ?: Long.MAX_VALUE }.thenBy { it.placeId ?: Long.MAX_VALUE })
        val realMoves = movements.filter { it.mode != MovementMode.NONE && it.endedAt > it.startedAt }
        val groups = mutableListOf<MutableList<PlaceVisit>>()
        for (visit in ordered) {
            val group = groups.lastOrNull()
            if (group != null && policy.enabled && canJoin(group, visit, evidence, realMoves, policy)) group += visit
            else groups += mutableListOf(visit)
        }
        val merged = groups.map { group -> ConsolidatedJourneyVisit(mergeVisits(group), sources(group, byId)) }
        // Contagem de visitas ao lugar por permanência (não por evento), como o PlaceVisitBuilder conta.
        val counts = merged.groupingBy { key(it.visit) }.eachCount()
        return JourneyVisitProjection(merged.map { it.copy(visit = it.visit.copy(visitsCount = counts[key(it.visit)] ?: 1)) })
    }

    /** Fim da presença confirmada da visita (intervalo semifechado). */
    private fun PlaceVisit.endAt(): Long = arrivalAt + durationMs.coerceAtLeast(0)

    private fun canJoin(
        group: List<PlaceVisit>, next: PlaceVisit,
        evidence: List<VisitSourceInterval>, moves: List<DiaryMovement>, policy: ConsolidationPolicy,
    ): Boolean {
        val first = group.first()
        val placeId = first.placeId ?: return false
        // Identidade física só por placeId; sem evento canônico conhecido, não arrisca.
        if (next.placeId != placeId) return false
        if (group.any { it.contextEventIds.isEmpty() } || next.contextEventIds.isEmpty()) return false
        val groupEnd = group.maxOf { it.endAt() }
        val gap = next.arrivalAt - groupEnd
        if (gap > policy.maxContiguousGapMs) return false
        if (gap <= 0) return true // adjacentes ou sobrepostos: nenhum instante sem presença entre eles
        val from = groupEnd
        val to = next.arrivalAt
        // Deslocamento confirmado na folga = saiu e voltou.
        if (moves.any { overlap(it.startedAt, it.endedAt, from, to) > 0 }) return false
        // Contexto de deslocamento ou de outro lugar (ou lugar desconhecido) na folga: identidade ambígua.
        val ids = (group.flatMap { it.contextEventIds } + next.contextEventIds).toSet()
        return evidence.none { e ->
            e.contextEventId !in ids && overlap(e.startAt, e.endAt, from, to) > 0 &&
                (e.contextType == UserContextType.COMMUTING || e.placeId != placeId)
        }
    }

    private fun overlap(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long = minOf(aEnd, bEnd) - maxOf(aStart, bStart)

    private fun mergeVisits(group: List<PlaceVisit>): PlaceVisit {
        if (group.size == 1) return group.single()
        val first = group.first()
        val longest = group.maxWith(compareBy<PlaceVisit> { it.durationMs }.thenByDescending { it.arrivalAt })
        val departure = if (group.any { it.departureAt == null }) null else group.maxOf { it.departureAt!! }
        return first.copy(
            arrivalAt = group.minOf { it.arrivalAt },
            departureAt = departure,
            durationMs = unionLength(group.map { it.arrivalAt to it.endAt() }),
            relatedTimelineIds = group.flatMap { it.relatedTimelineIds }.distinct(),
            dominantHoodieActivity = longest.dominantHoodieActivity ?: group.firstNotNullOfOrNull { it.dominantHoodieActivity },
            confidence = group.mapNotNull { it.confidence }.minOrNull(),
            source = longest.source,
            contextEventIds = group.flatMap { it.contextEventIds }.distinct(),
        )
    }

    /** Comprimento da união de intervalos [início, fim): sobreposições contam uma vez, buracos não contam. */
    fun unionLength(intervals: List<Pair<Long, Long>>): Long {
        var total = 0L
        var curStart = Long.MIN_VALUE
        var curEnd = Long.MIN_VALUE
        intervals.filter { it.second > it.first }.sortedBy { it.first }.forEach { (s, e) ->
            if (curEnd == Long.MIN_VALUE || s > curEnd) {
                if (curEnd != Long.MIN_VALUE) total += curEnd - curStart
                curStart = s; curEnd = e
            } else if (e > curEnd) curEnd = e
        }
        if (curEnd != Long.MIN_VALUE) total += curEnd - curStart
        return total
    }

    /** Intervalos normalizados (união) — base para medir celular e Relógio sem contar duas vezes. */
    fun union(intervals: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        val out = mutableListOf<Pair<Long, Long>>()
        intervals.filter { it.second > it.first }.sortedBy { it.first }.forEach { (s, e) ->
            val last = out.lastOrNull()
            if (last != null && s <= last.second) out[out.lastIndex] = last.first to maxOf(last.second, e) else out += s to e
        }
        return out
    }

    private fun sources(group: List<PlaceVisit>, byId: Map<Long, VisitSourceInterval>): List<VisitSourceInterval> =
        group.flatMap { visit ->
            val known = visit.contextEventIds.mapNotNull(byId::get)
            // Visita sem evidência no recorte (fixtures, dados antigos): o próprio intervalo, com o ID que houver.
            known.ifEmpty {
                listOf(VisitSourceInterval(visit.contextEventIds.firstOrNull() ?: -1L, visit.placeId, visit.placeType.toContext(),
                    visit.arrivalAt, visit.endAt(), open = visit.departureAt == null))
            }
        }.distinctBy { it.contextEventId to it.startAt }.sortedWith(compareBy({ it.startAt }, { it.contextEventId }))

    private fun key(visit: PlaceVisit): String = visit.placeId?.let { "place:$it" }
        ?: if (visit.placeType == PlaceType.OTHER) "unknown:${visit.arrivalAt}" else "type:${visit.placeType.name}"
}

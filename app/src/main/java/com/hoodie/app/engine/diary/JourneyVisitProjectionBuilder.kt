package com.hoodie.app.engine.diary

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.domain.diary.model.JourneyVisitProjection
import com.hoodie.app.domain.diary.model.PlaceVisit

/**
 * Última etapa do Diário (depois do celular e da mobilidade, quando todos os deslocamentos já são
 * conhecidos): calcula as permanências da Jornada e faz o mapa, o replay e a timeline resumida
 * usarem a mesma definição. Roda uma vez por carregamento, fora da thread principal.
 *
 * Nada é gravado: [DailyDiary.visits] continua com as visitas originais e o Room fica intacto.
 * Com a flag desligada o Diário sai exatamente como entrou.
 */
object JourneyVisitProjectionBuilder {

    fun apply(
        diary: DailyDiary,
        enabled: Boolean = HoodieConfig.HOODIE_CONSECUTIVE_VISIT_MERGE_V1,
        policy: ConsolidationPolicy = ConsolidationPolicy.DEFAULT,
    ): DailyDiary {
        if (!enabled || !policy.enabled) return diary
        val projection = ConsecutivePlaceVisitConsolidator.consolidate(diary.visits, diary.contextIntervals, diary.movements, policy)
        if (!projection.hasMerges) return diary.copy(journeyProjection = projection)
        val journeyVisits = projection.visits.map { it.visit }
        val map = DailyMapBuilder.build(journeyVisits)
        val hidden = internalBoundaryItemIds(projection)
        return diary.copy(
            journeyProjection = projection,
            map = map,
            timeline = diary.timeline.filterNot { it.hiddenBy(hidden) },
            replay = diary.replay.copy(
                visits = regroup(diary.replay.visits, projection),
                timeline = diary.replay.timeline.filterNot { it.hiddenBy(hidden) },
                map = map,
            ),
        )
    }

    /**
     * Agrupa visitas já recortadas (as do replay) como a projeção agrupou as originais, pelos IDs dos
     * eventos canônicos. Visitas sem ID ficam como estão.
     */
    fun regroup(visits: List<PlaceVisit>, projection: JourneyVisitProjection): List<PlaceVisit> {
        val out = mutableListOf<PlaceVisit>()
        val groupIndex = mutableMapOf<Int, Int>()
        visits.sortedBy { it.arrivalAt }.forEach { v ->
            val owner = v.contextEventIds.firstOrNull()?.let { id -> projection.visits.indexOfFirst { id in it.sourceContextEventIds } }?.takeIf { it >= 0 }
            val at = owner?.let(groupIndex::get)
            if (at == null) { out += v; if (owner != null) groupIndex[owner] = out.lastIndex }
            else out[at] = merge(out[at], v)
        }
        return out
    }

    private fun merge(a: PlaceVisit, b: PlaceVisit): PlaceVisit {
        val intervals = listOf(a.arrivalAt to a.arrivalAt + a.durationMs, b.arrivalAt to b.arrivalAt + b.durationMs)
        return a.copy(
            arrivalAt = minOf(a.arrivalAt, b.arrivalAt),
            departureAt = if (a.departureAt == null || b.departureAt == null) null else maxOf(a.departureAt, b.departureAt),
            durationMs = ConsecutivePlaceVisitConsolidator.unionLength(intervals),
            relatedTimelineIds = (a.relatedTimelineIds + b.relatedTimelineIds).distinct(),
            contextEventIds = (a.contextEventIds + b.contextEventIds).distinct(),
        )
    }

    /**
     * "Saiu de Casa 09:00 / Chegou: casa 09:00" entre dois eventos da mesma permanência são fronteiras
     * internas, não saídas: somem da timeline resumida. Se o contexto muda (trabalhar em casa), a
     * mudança continua visível. Deslocamentos nunca são escondidos.
     */
    fun internalBoundaryItemIds(projection: JourneyVisitProjection): Set<String> = buildSet {
        projection.visits.filter { it.isMerged }.forEach { group ->
            group.sourceIntervals.zipWithNext().forEach { (a, b) ->
                if (a.contextType != b.contextType) return@forEach
                add("left-${a.contextEventId}")
                add("context-${b.contextEventId}")
            }
        }
    }

    /** Só chegadas e saídas automáticas podem sumir; deslocamentos e mudanças de contexto ficam. */
    private val HIDEABLE_TYPES = setOf(DiaryTimelineType.ARRIVED, DiaryTimelineType.LEFT)

    private fun DiaryTimelineItem.hiddenBy(ids: Set<String>) = type in HIDEABLE_TYPES && id in ids
}

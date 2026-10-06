package com.hoodie.app.engine.diary.journey

import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.domain.diary.journey.JourneyLeg
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.domain.diary.model.JourneyMapData
import java.time.LocalDate
import java.time.ZoneId

/**
 * Dia → plano → serpentina (um mapa ou um por capítulo). Calculado uma vez por dia
 * (e quando a parada protegida muda), nunca por quadro.
 */
data class JourneyOverworldModel(
    val data: JourneyMapData,
    val plan: JourneyPlan,
    val date: LocalDate,
    /** Mapa único (SINGLE). */
    val single: OverworldLayout?,
    /** Um layout por capítulo (CHAPTERS); capítulo vazio não tem layout. */
    val chapters: Map<DayChapter, OverworldLayout>,
) {
    /** Seed do chão e da decoração: a data. */
    val seed: Long get() = date.toEpochDay()
    val isChapters: Boolean get() = plan is JourneyPlan.Chapters

    fun layoutOf(chapter: DayChapter?): OverworldLayout? = if (chapter == null) single else chapters[chapter]

    companion object {
        fun build(
            data: JourneyMapData,
            zone: ZoneId,
            protectedStopIds: Set<String> = emptySet(),
            config: JourneyPlanConfig = JourneyPlanConfig(),
            spec: SerpentineSpec = SerpentineSpec(),
        ): JourneyOverworldModel {
            val date = JourneyChapterPlanner.dateOf(data, zone)
            val plan = JourneyChapterPlanner.plan(data, zone, protectedStopIds, config, date)
            val between = { a: String, b: String -> legBetween(plan, a, b) }
            return when (plan) {
                is JourneyPlan.Single -> JourneyOverworldModel(data, plan, date, SerpentineLayoutEngine.layout(plan.stops, spec, between), emptyMap())
                is JourneyPlan.Chapters -> JourneyOverworldModel(
                    data, plan, date, null,
                    plan.chapters.filter { !it.isEmpty }.associate { c ->
                        c.chapter to SerpentineLayoutEngine.layout(
                            c.stopIds.mapNotNull { plan.stop(it) }, spec, between, c.entryLegId, c.exitLegId,
                        )
                    },
                )
            }
        }

        /**
         * Trecho entre duas paradas do mapa: fantasma conta como a visita original; um
         * grupo recebe pelo primeiro membro e despacha pelo último.
         */
        fun legBetween(plan: JourneyPlan, fromStopId: String, toStopId: String): JourneyLeg? {
            fun lastVisit(id: String) = when (val s = plan.stop(id)) {
                is JourneyStop.Ghost -> s.ofStopId
                is JourneyStop.QuickCluster -> s.stopIds.last()
                else -> id
            }
            fun firstVisit(id: String) = when (val s = plan.stop(id)) {
                is JourneyStop.Ghost -> s.ofStopId
                is JourneyStop.QuickCluster -> s.stopIds.first()
                else -> id
            }
            val from = lastVisit(fromStopId); val to = firstVisit(toStopId)
            return plan.legs.firstOrNull { it.fromStopId == from && it.toStopId == to }
        }
    }
}

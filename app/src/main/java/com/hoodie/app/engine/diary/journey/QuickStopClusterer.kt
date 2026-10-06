package com.hoodie.app.engine.diary.journey

import com.hoodie.app.domain.diary.journey.JourneyStop

/**
 * Paradas rápidas consecutivas viram um marco "×k" quando o capítulo passa do limite.
 * Puro. Nunca engole uma parada protegida (atual do replay ou selecionada): o grupo é
 * quebrado em volta dela, e os ids internos são preservados para o detalhe.
 */
object QuickStopClusterer {

    data class Result(val stops: List<JourneyStop>, val overflow: Boolean)

    fun clusterId(firstStopId: String) = "cluster-$firstStopId"

    fun cluster(
        visits: List<JourneyStop.Visit>,
        maxStops: Int,
        quickMs: Long,
        protectedIds: Set<String> = emptySet(),
    ): Result {
        if (visits.size <= maxStops) return Result(visits, overflow = false)
        val out = mutableListOf<JourneyStop>()
        var run = mutableListOf<JourneyStop.Visit>()
        fun flush() {
            if (run.size >= 2) {
                val last = run.last()
                out += JourneyStop.QuickCluster(
                    id = clusterId(run.first().id),
                    stopIds = run.map { it.id },
                    startAt = run.first().arrivalAt,
                    endAt = last.departureAt ?: (last.arrivalAt + last.durationMs),
                )
            } else out += run
            run = mutableListOf()
        }
        visits.forEach { v ->
            val quick = v.departureAt != null && v.durationMs < quickMs && v.id !in protectedIds
            if (quick) run += v else { flush(); out += v }
        }
        flush()
        return Result(out, overflow = out.size > maxStops)
    }
}

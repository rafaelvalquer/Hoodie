package com.hoodie.app.engine.dayreport

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.dayreport.DayReportStop

object DayJourneySummaryBuilder {
    fun build(diary: DailyDiary): List<DayReportStop> {
        val visits = diary.visits.sortedBy { it.arrivalAt }
        return visits.mapIndexedNotNull { index, visit ->
            val previous = visits.getOrNull(index - 1)
            val movement = previous?.let { before -> diary.movements
                .filter { it.startedAt >= (before.departureAt ?: before.arrivalAt) && it.endedAt <= visit.arrivalAt }
                .maxByOrNull { it.endedAt } }
            DayReportStop(visit.placeId, visit.placeName, visit.placeType, visit.arrivalAt, visit.departureAt,
                movement?.mode?.takeUnless { it == MovementMode.NONE })
        }.fold(mutableListOf()) { result, stop ->
            val last = result.lastOrNull()
            if (last == null || last.placeId != stop.placeId || last.type != stop.type ||
                stop.arrivedAt - (last.departedAt ?: last.arrivedAt) > 5 * 60_000L) result += stop
            else result[result.lastIndex] = last.copy(departedAt = stop.departedAt ?: last.departedAt)
            result
        }
    }
}

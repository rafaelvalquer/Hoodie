package com.hoodie.app.engine.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.DiaryMapData
import com.hoodie.app.domain.diary.model.DiaryMapEdge
import com.hoodie.app.domain.diary.model.DiaryMapNode
import com.hoodie.app.domain.diary.model.DiaryMapNodeType
import com.hoodie.app.domain.diary.model.PlaceVisit

object DailyMapBuilder {
    fun build(visits: List<PlaceVisit>, columns: Int = 4): DiaryMapData {
        val nodes = visits.mapIndexed { i, visit ->
            val row = i / columns
            val column = i % columns
            val visualColumn = if (row % 2 == 0) column else columns - column - 1
            val verticalOffset = when (visit.placeType) {
                PlaceType.HOME -> -1
                PlaceType.GYM, PlaceType.SCHOOL -> 1
                else -> 0
            }
            DiaryMapNode("visit-$i", visit.placeId, visit.placeName, visit.placeType.toMapType(), visualColumn * 2, row * 2 + verticalOffset, i + 1, visit.arrivalAt, visit.departureAt, visit.durationMs)
        }
        val edges = visits.zipWithNext().mapIndexed { i, (a, b) ->
            val from = nodes[i]; val to = nodes[i + 1]
            val start = a.departureAt ?: a.arrivalAt
            DiaryMapEdge("edge-$i", from.id, to.id, start, b.arrivalAt, (b.arrivalAt - start).coerceAtLeast(0))
        }
        return DiaryMapData(nodes, edges)
    }

    private fun PlaceType.toMapType() = when (this) {
        PlaceType.HOME -> DiaryMapNodeType.HOME
        PlaceType.WORK -> DiaryMapNodeType.WORK
        PlaceType.GYM -> DiaryMapNodeType.GYM
        PlaceType.SCHOOL -> DiaryMapNodeType.SCHOOL
        PlaceType.RESTAURANT -> DiaryMapNodeType.RESTAURANT
        PlaceType.MARKET -> DiaryMapNodeType.MARKET
        PlaceType.FAMILY -> DiaryMapNodeType.FAMILY
        PlaceType.LEISURE -> DiaryMapNodeType.LEISURE
        PlaceType.OTHER -> DiaryMapNodeType.OTHER
    }
}

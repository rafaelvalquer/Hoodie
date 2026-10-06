package com.hoodie.app.engine.diary.journey

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.domain.diary.model.JourneySegment
import java.time.LocalDate
import java.time.ZoneId

/** Dias com horários exatos para os testes da Jornada 3.0. */
object JourneyV3Fixtures {
    val UTC: ZoneId = ZoneId.of("UTC")
    val DATE: LocalDate = LocalDate.of(2026, 10, 5)

    fun at(h: Int, m: Int = 0, date: LocalDate = DATE, zone: ZoneId = UTC): Long =
        date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    data class V(val name: String, val type: PlaceType, val from: Long, val to: Long?, val mode: MovementMode? = MovementMode.WALKING)

    fun day(vararg visits: V, date: LocalDate = DATE, zone: ZoneId = UTC, end: Long? = null): JourneyMapData {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = end ?: date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 60_000
        val keys = visits.map { "${it.name}|${it.type}" }
        val nodes = visits.mapIndexed { i, v ->
            JourneyNode(
                "journey-$i", i, keys.indexOf(keys[i]).toLong(), v.name, v.type, v.from, v.to, (v.to ?: dayEnd) - v.from,
                null, null, revisitCount = keys.subList(0, i).count { it == keys[i] }, placeOccurrences = keys.count { it == keys[i] },
            )
        }
        val segments = nodes.zipWithNext().mapIndexed { i, (a, b) ->
            val s = a.departureAt ?: a.arrivalAt
            JourneySegment("journey-seg-$i", i, a.id, b.id, s, b.arrivalAt, b.arrivalAt - s, visits[i].mode)
        }
        return JourneyMapData(nodes, segments, start, dayEnd)
    }

    /** [n] visitas de 30 min a cada 70 min a partir das 6h. */
    fun stops(n: Int, quickEvery: Int = 0): JourneyMapData = day(*Array(n) { i ->
        val from = at(6) + i * 70 * 60_000L
        val stay = if (quickEvery > 0 && i % quickEvery == 1) 5 * 60_000L else 30 * 60_000L
        V("Lugar $i", PlaceType.entries[i % PlaceType.entries.size], from, from + stay)
    })
}

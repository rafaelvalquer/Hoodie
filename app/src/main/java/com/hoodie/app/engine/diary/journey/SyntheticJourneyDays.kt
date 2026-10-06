package com.hoodie.app.engine.diary.journey

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.domain.diary.model.JourneySegment
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Dias sintéticos da Jornada 3.0 para o Diary Lab, os testes e os goldens.
 * Determinísticos: mesma chamada → mesmos dados.
 */
object SyntheticJourneyDays {

    enum class Kind(val label: String) {
        EMPTY("Vazio"), THREE("3 paradas"), NINE("9 paradas"), TEN("10 paradas"),
        FOURTEEN("14 paradas"), TWENTY("20 paradas"), AFTERNOON_AT_WORK("Tarde inteira no trabalho"),
    }

    private data class Spec(val name: String, val type: PlaceType, val from: LocalTime, val to: LocalTime?, val mode: MovementMode?)

    private val modes = listOf(MovementMode.WALKING, MovementMode.BUS, MovementMode.CAR, MovementMode.BICYCLE, MovementMode.TRAIN, MovementMode.METRO, null)

    fun build(kind: Kind, date: LocalDate, zone: ZoneId): JourneyMapData = when (kind) {
        Kind.EMPTY -> JourneyMapData.EMPTY.copy(startAt = ms(date, LocalTime.MIDNIGHT, zone), endAt = ms(date, LocalTime.of(23, 59), zone))
        Kind.THREE -> fromSpecs(date, zone, listOf(
            Spec("Casa", PlaceType.HOME, t(6), t(7, 40), MovementMode.WALKING),
            Spec("Trabalho", PlaceType.WORK, t(8, 10), t(17, 30), MovementMode.BUS),
            Spec("Casa", PlaceType.HOME, t(18, 15), null, null),
        ))
        Kind.NINE -> stops(date, zone, 9)
        Kind.TEN -> stops(date, zone, 10)
        Kind.FOURTEEN -> stops(date, zone, 14)
        Kind.TWENTY -> twenty(date, zone)
        Kind.AFTERNOON_AT_WORK -> fromSpecs(date, zone, listOf(
            Spec("Casa", PlaceType.HOME, t(6), t(7, 30), MovementMode.WALKING),
            Spec("Padaria", PlaceType.RESTAURANT, t(7, 40), t(7, 55), MovementMode.WALKING),
            Spec("Escola", PlaceType.SCHOOL, t(8, 10), t(9), MovementMode.BUS),
            Spec("Mercado", PlaceType.MARKET, t(9, 20), t(9, 50), MovementMode.WALKING),
            Spec("Trabalho", PlaceType.WORK, t(10, 30), t(18, 40), MovementMode.CAR),
            Spec("Academia", PlaceType.GYM, t(19, 10), t(20), MovementMode.BICYCLE),
            Spec("Família", PlaceType.FAMILY, t(20, 20), t(21, 30), MovementMode.CAR),
            Spec("Parque", PlaceType.LEISURE, t(21, 40), t(22), MovementMode.WALKING),
            Spec("Outro", PlaceType.OTHER, t(22, 10), t(22, 30), MovementMode.WALKING),
            Spec("Casa", PlaceType.HOME, t(22, 50), null, null),
        ))
    }

    /** Dia com [n] paradas (slider do Diary Lab). */
    fun custom(n: Int, date: LocalDate, zone: ZoneId): JourneyMapData =
        if (n <= 0) build(Kind.EMPTY, date, zone) else stops(date, zone, n.coerceAtMost(40))

    /** [n] paradas espalhadas de 06:00 a ~23:00, passando por todos os tipos. */
    private fun stops(date: LocalDate, zone: ZoneId, n: Int): JourneyMapData {
        val types = listOf(PlaceType.HOME, PlaceType.WORK, PlaceType.RESTAURANT, PlaceType.WORK, PlaceType.GYM, PlaceType.MARKET, PlaceType.SCHOOL, PlaceType.LEISURE, PlaceType.FAMILY, PlaceType.OTHER, PlaceType.STORE)
        val span = (17 * 60 / n).coerceAtLeast(10)
        return fromSpecs(date, zone, (0 until n).map { i ->
            val start = 6 * 60 + i * span
            val stay = (span * 3 / 4).coerceAtLeast(8)
            val type = if (i == n - 1) PlaceType.HOME else types[i % types.size]
            Spec(nameOf(type, i), type, LocalTime.of(start / 60, start % 60), if (i == n - 1) null else LocalTime.of((start + stay) / 60, (start + stay) % 60), modes[i % modes.size])
        })
    }

    /** 20 paradas com rajadas de paradas rápidas na tarde (força o agrupamento ×k). */
    private fun twenty(date: LocalDate, zone: ZoneId): JourneyMapData {
        val specs = mutableListOf(
            Spec("Casa", PlaceType.HOME, t(6), t(7, 30), MovementMode.WALKING),
            Spec("Café", PlaceType.RESTAURANT, t(7, 40), t(7, 55), MovementMode.BUS),
            Spec("Trabalho", PlaceType.WORK, t(8, 20), t(11, 50), MovementMode.WALKING),
        )
        // Tarde: 13 paradas, várias com 5–8 min (entregas, lojas…).
        var m = 12 * 60
        repeat(13) { i ->
            val quick = i in 2..8
            val stay = if (quick) 5 + i % 4 else 25
            val type = listOf(PlaceType.STORE, PlaceType.MARKET, PlaceType.OTHER, PlaceType.RESTAURANT)[i % 4]
            specs += Spec(if (quick) "Rápida ${i + 1}" else nameOf(type, i), type, LocalTime.of(m / 60, m % 60), LocalTime.of((m + stay) / 60, (m + stay) % 60), modes[i % modes.size])
            m += stay + 4
        }
        specs += Spec("Academia", PlaceType.GYM, t(18, 30), t(19, 30), MovementMode.CAR)
        specs += Spec("Família", PlaceType.FAMILY, t(20), t(21), MovementMode.CAR)
        specs += Spec("Parque", PlaceType.LEISURE, t(21, 15), t(21, 40), MovementMode.WALKING)
        specs += Spec("Casa", PlaceType.HOME, t(22), null, null)
        return fromSpecs(date, zone, specs)
    }

    private fun nameOf(type: PlaceType, i: Int) = when (type) {
        PlaceType.HOME -> "Casa"
        PlaceType.WORK -> "Trabalho"
        PlaceType.RESTAURANT -> if (i % 2 == 0) "Almoço" else "Café"
        PlaceType.GYM -> "Academia"
        PlaceType.MARKET -> "Mercado"
        PlaceType.STORE -> "Loja"
        PlaceType.SCHOOL -> "Escola"
        PlaceType.LEISURE -> "Parque"
        PlaceType.FAMILY -> "Família"
        PlaceType.OTHER -> "Lugar novo"
    }

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun ms(date: LocalDate, time: LocalTime, zone: ZoneId) = date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun fromSpecs(date: LocalDate, zone: ZoneId, specs: List<Spec>): JourneyMapData {
        val dayStart = ms(date, LocalTime.MIDNIGHT, zone)
        val dayEnd = ms(date, LocalTime.of(23, 59), zone)
        val keys = specs.map { "${it.name}|${it.type}" }
        val nodes = specs.mapIndexed { i, s ->
            val arrival = ms(date, s.from, zone)
            val departure = s.to?.let { ms(date, it, zone) }
            val duration = (departure ?: dayEnd) - arrival
            JourneyNode(
                id = "journey-$i", visitIndex = i, placeId = keys.indexOf(keys[i]).toLong(), placeName = s.name, placeType = s.type,
                arrivalAt = arrival, departureAt = departure, durationMs = duration,
                hoodieActivity = null, contextType = null,
                revisitCount = keys.subList(0, i).count { it == keys[i] },
                placeOccurrences = keys.count { it == keys[i] },
            )
        }
        val segments = nodes.zipWithNext().mapIndexed { i, (a, b) ->
            val start = a.departureAt ?: a.arrivalAt
            JourneySegment("journey-seg-$i", i, a.id, b.id, start, b.arrivalAt, b.arrivalAt - start, specs[i].mode)
        }
        return JourneyMapData(nodes, segments, dayStart, dayEnd)
    }
}

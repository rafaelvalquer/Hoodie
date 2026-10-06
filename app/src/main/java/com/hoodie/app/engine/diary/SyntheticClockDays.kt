package com.hoodie.app.engine.diary

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.domain.diary.model.DiaryMapData
import com.hoodie.app.domain.diary.model.DiaryMovement
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.diary.model.ReplaySequence
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Dias sintéticos do Relógio do Dia 2.0 (Diary Lab e goldens): todos os tipos de
 * trecho — paradas, a pé, bicicleta, carro, ônibus, metrô, sem meio e buracos.
 */
object SyntheticClockDays {
    enum class Kind(val label: String) {
        EMPTY("Vazio"),
        MORNING_ONE_STOP("Manhã, 1 parada"),
        FULL("Dia completo"),
        MANY_SHORT_MOVES("Muitos trechos curtos"),
        DST("Horário de verão (23 h)"),
    }

    /** Uma visita e como saiu dela (null = sem dado de mobilidade até a próxima). */
    private data class V(
        val name: String, val type: PlaceType, val from: String, val to: String?,
        val activity: HoodieActivity, val context: UserContextType? = null,
        val leaveBy: MovementMode? = MovementMode.WALKING, val travelMin: Int = 10,
    )

    private val FULL_DAY = listOf(
        V("Casa", PlaceType.HOME, "00:00", "07:30", HoodieActivity.SLEEPING, leaveBy = MovementMode.WALKING, travelMin = 12),
        V("Padaria", PlaceType.RESTAURANT, "07:42", "08:02", HoodieActivity.BREAKFAST, leaveBy = MovementMode.BUS, travelMin = 38),
        V("Trabalho", PlaceType.WORK, "08:40", "12:00", HoodieActivity.WORKING, travelMin = 10),
        V("Restaurante", PlaceType.RESTAURANT, "12:10", "13:05", HoodieActivity.EATING, UserContextType.LUNCH, travelMin = 10),
        V("Trabalho", PlaceType.WORK, "13:15", "17:30", HoodieActivity.WORKING, leaveBy = MovementMode.CAR, travelMin = 25),
        V("Academia", PlaceType.GYM, "17:55", "19:00", HoodieActivity.TRAINING, leaveBy = MovementMode.BICYCLE, travelMin = 15),
        V("Parque", PlaceType.LEISURE, "19:15", "19:50", HoodieActivity.SIGHTSEEING, leaveBy = null, travelMin = 20),
        V("Casa", PlaceType.HOME, "20:10", null, HoodieActivity.WATCHING_TV),
    )

    data class Day(val diary: DailyDiary, val date: LocalDate, val zone: ZoneId, val now: Long)

    /** [nowAt] = hora local do "agora" (hoje); null = dia passado (agora no dia seguinte). */
    fun build(kind: Kind, date: LocalDate, zone: ZoneId, nowAt: LocalTime? = LocalTime.of(21, 40)): Day {
        val visits = when (kind) {
            Kind.EMPTY -> emptyList()
            Kind.MORNING_ONE_STOP -> listOf(V("Casa", PlaceType.HOME, "06:00", null, HoodieActivity.WAKING_UP))
            Kind.FULL, Kind.DST -> FULL_DAY
            Kind.MANY_SHORT_MOVES -> manyShortMoves()
        }
        val now = nowAt?.let { date.atTime(it).atZone(zone).toInstant().toEpochMilli() }
            ?: date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 60 * 60_000L
        return Day(diary(visits, date, zone, now), date, zone, now)
    }

    private fun manyShortMoves(): List<V> {
        val modes = listOf(MovementMode.WALKING, MovementMode.BUS, MovementMode.METRO, MovementMode.WALKING, MovementMode.BICYCLE, MovementMode.CAR, null)
        val types = listOf(PlaceType.STORE, PlaceType.MARKET, PlaceType.OTHER, PlaceType.RESTAURANT, PlaceType.SCHOOL)
        val out = mutableListOf(V("Casa", PlaceType.HOME, "00:00", "07:00", HoodieActivity.SLEEPING, leaveBy = MovementMode.WALKING, travelMin = 4))
        var t = 7 * 60 + 4
        repeat(16) { i ->
            val stay = 3 + i % 4
            out += V("Parada ${i + 1}", types[i % types.size], hhmm(t), hhmm(t + stay), HoodieActivity.SHOPPING, leaveBy = modes[i % modes.size], travelMin = 2 + i % 3)
            t += stay + 2 + i % 3
        }
        out += V("Trabalho", PlaceType.WORK, hhmm(t), "18:00", HoodieActivity.WORKING, leaveBy = MovementMode.METRO, travelMin = 30)
        out += V("Casa", PlaceType.HOME, "18:30", null, HoodieActivity.RESTING)
        return out
    }

    private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun diary(visits: List<V>, date: LocalDate, zone: ZoneId, now: Long): DailyDiary {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(now, dayEnd)
        fun at(s: String): Long = date.atTime(LocalTime.parse(s)).atZone(zone).toInstant().toEpochMilli()
        val placeVisits = visits.mapIndexedNotNull { i, v ->
            val from = at(v.from)
            if (from >= end) return@mapIndexedNotNull null
            val to = v.to?.let(::at)?.takeIf { it < end }
            PlaceVisit(v.name.hashCode().toLong(), v.name, v.type, from, to, (to ?: end) - from, 1, dominantHoodieActivity = v.activity)
        }
        val movements = visits.zipWithNext().mapNotNull { (a, b) ->
            val from = a.to?.let(::at) ?: return@mapNotNull null
            val mode = a.leaveBy ?: return@mapNotNull null
            DiaryMovement(mode, from, minOf(at(b.from), end)).takeIf { from < end }
        }
        val contexts = placeVisits.mapIndexed { i, v ->
            ((v.arrivalAt until (v.departureAt ?: end)) to (visits[i].context ?: v.placeType.toContext()))
        } + movements.map { (it.startedAt until it.endedAt) to UserContextType.COMMUTING }
        val activities = placeVisits.map { v -> (v.arrivalAt until (v.departureAt ?: end)) to v.dominantHoodieActivity!! } +
            movements.map { (it.startedAt until it.endedAt) to HoodieActivity.COMMUTING }
        val replay = ReplaySequence(
            startAt = placeVisits.firstOrNull()?.arrivalAt ?: dayStart, endAt = end,
            visits = placeVisits, timeline = emptyList(), contexts = contexts, activities = activities,
        )
        return DailyDiary(DailySummary(date), emptyList(), placeVisits, DiaryMapData(), replay, movements = movements)
    }
}

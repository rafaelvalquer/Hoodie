package com.hoodie.app.pixel.diary.journey

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
import com.hoodie.app.engine.diary.JourneyMapAssembler
import java.time.LocalDate
import java.time.ZoneId

/**
 * Dia de exemplo do plano (horários em UTC, a partir de 0h de 1970-01-01):
 *   Casa 06:00–07:47 → 🚶+🚌 → Trabalho 08:15–12:08 → 🚶 → Almoço 12:16–13:00
 *   → 🚶 → Trabalho 13:05–17:40 → 🚗 → Academia 18:10–19:20 → 🚲 → Casa 19:50–
 */
object JourneyTestFixtures {
    val ZONE: ZoneId = ZoneId.of("UTC")
    const val MIN = 60_000L
    fun t(h: Int, m: Int = 0) = (h * 60L + m) * MIN

    fun visit(id: Long?, name: String, type: PlaceType, from: Long, to: Long?, activity: HoodieActivity? = null) =
        PlaceVisit(id, name, type, from, to, ((to ?: t(23)) - from), 1, dominantHoodieActivity = activity)

    val visits = listOf(
        visit(1, "Casa", PlaceType.HOME, t(6), t(7, 47), HoodieActivity.entries.first()),
        visit(2, "Trabalho", PlaceType.WORK, t(8, 15), t(12, 8)),
        visit(3, "Almoço", PlaceType.RESTAURANT, t(12, 16), t(13)),
        visit(2, "Trabalho", PlaceType.WORK, t(13, 5), t(17, 40)),
        visit(4, "Academia", PlaceType.GYM, t(18, 10), t(19, 20)),
        visit(1, "Casa", PlaceType.HOME, t(19, 50), null),
    )

    val movements = listOf(
        DiaryMovement(MovementMode.WALKING, t(7, 47), t(7, 54)),
        DiaryMovement(MovementMode.BUS, t(7, 54), t(8, 15)),
        DiaryMovement(MovementMode.WALKING, t(12, 8), t(12, 16)),
        DiaryMovement(MovementMode.WALKING, t(13), t(13, 5)),
        DiaryMovement(MovementMode.CAR, t(17, 40), t(18, 10)),
        DiaryMovement(MovementMode.BICYCLE, t(19, 20), t(19, 50)),
    )

    val contexts = listOf(
        (t(6) until t(7, 47)) to UserContextType.HOME,
        (t(8, 15) until t(12, 8)) to UserContextType.WORK,
        (t(12, 16) until t(13)) to UserContextType.LUNCH,
        (t(13, 5) until t(17, 40)) to UserContextType.WORK,
        (t(18, 10) until t(19, 20)) to UserContextType.GYM,
        (t(19, 50) until t(23)) to UserContextType.HOME,
    )

    fun diary(visits: List<PlaceVisit> = this.visits, movements: List<DiaryMovement> = this.movements) = DailyDiary(
        summary = DailySummary(LocalDate.of(1970, 1, 1)),
        timeline = emptyList(),
        visits = visits,
        map = DiaryMapData(),
        replay = ReplaySequence(t(6), t(23), visits, emptyList(), contexts = contexts),
        movements = movements,
    )

    val NOW = t(23)
    val data by lazy { JourneyMapAssembler.build(diary(), NOW) }
    val layout by lazy { JourneyLayoutEngine.layout(data) }

    /** Dia curto: só casa → trabalho. */
    val shortData by lazy { JourneyMapAssembler.build(diary(visits.take(2), movements.take(2)), NOW) }

    /** Dia longo: 14 paradas alternando lugares. */
    val longData by lazy {
        val types = listOf(PlaceType.HOME, PlaceType.WORK, PlaceType.MARKET, PlaceType.SCHOOL, PlaceType.LEISURE, PlaceType.FAMILY, PlaceType.OTHER)
        val vs = (0 until 14).map { i -> visit(10L + i % 7, "Lugar ${i % 7}", types[i % types.size], t(6) + i * 70 * MIN, t(6) + i * 70 * MIN + 50 * MIN) }
        JourneyMapAssembler.build(diary(vs, emptyList()), NOW)
    }
}

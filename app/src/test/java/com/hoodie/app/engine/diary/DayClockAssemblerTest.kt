package com.hoodie.app.engine.diary

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.clock.DayClockData
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.domain.diary.model.DiaryMapData
import com.hoodie.app.domain.diary.model.DiaryMovement
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.diary.model.ReplaySequence
import com.hoodie.app.domain.daycycle.DailyActivityWindow
import com.hoodie.app.domain.daycycle.InferredSleepOnset
import com.hoodie.app.domain.daycycle.InferredSleepSpan
import com.hoodie.app.domain.daycycle.SleepConfidence
import com.hoodie.app.domain.daycycle.SleepOnsetReason
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.daycycle.WakeReason
import com.hoodie.app.engine.diary.SyntheticClockDays.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DayClockAssemblerTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val date = LocalDate.of(2026, 10, 5)
    private fun at(h: Int, m: Int = 0, d: LocalDate = date, z: ZoneId = zone) = d.atTime(h, m).atZone(z).toInstant().toEpochMilli()

    private fun synthetic(kind: Kind, now: LocalTime? = LocalTime.of(21, 40), z: ZoneId = zone, d: LocalDate = date): DayClockData {
        val day = SyntheticClockDays.build(kind, d, z, now)
        return DayClockAssembler.build(day.diary, d, z, day.now)
    }

    private fun diary(
        visits: List<PlaceVisit>,
        movements: List<DiaryMovement> = emptyList(),
        contexts: List<Pair<LongRange, UserContextType>> = emptyList(),
        activities: List<Pair<LongRange, HoodieActivity>> = emptyList(),
    ) = DailyDiary(
        DailySummary(date), emptyList(), visits, DiaryMapData(),
        ReplaySequence(visits.firstOrNull()?.arrivalAt ?: 0, visits.lastOrNull()?.let { it.departureAt ?: it.arrivalAt } ?: 0, visits, emptyList(), contexts, activities),
        movements = movements,
    )

    private fun visit(name: String, type: PlaceType, from: Long, to: Long?, activity: HoodieActivity? = null) =
        PlaceVisit(name.hashCode().toLong(), name, type, from, to, (to ?: from) - from, 1, dominantHoodieActivity = activity)

    private fun assertCoverage(d: DayClockData) {
        assertEquals(0, d.segments.first().startMinute)
        d.segments.zipWithNext().forEach { (a, b) -> assertEquals("sem buraco nem sobreposição entre ${a.id} e ${b.id}", a.endMinute, b.startMinute) }
        assertEquals(d.endMinute, d.segments.last().endMinute)
        d.segments.forEach { assertTrue(it.endMinute >= it.startMinute) }
    }

    @Test fun segmentsAreOrderedAndCoverTheDayWithoutOverlap() {
        listOf(synthetic(Kind.FULL), synthetic(Kind.FULL, null), synthetic(Kind.MANY_SHORT_MOVES, LocalTime.of(20, 0)), synthetic(Kind.MORNING_ONE_STOP, LocalTime.of(9, 0)))
            .forEach(::assertCoverage)
    }

    @Test fun repeatedVisitsStaySeparateWithReturnNumber() {
        val work = synthetic(Kind.FULL).stays.filter { it.placeName == "Trabalho" }
        assertEquals(2, work.size)
        assertEquals(listOf(1, 2), work.map { it.visitNumber })
        assertEquals(listOf(3, 5), work.map { it.stopIndex })
        assertTrue(work[0].id != work[1].id)
    }

    @Test fun todayIsCutAtNowAndThePastDayGoesToMidnight() {
        val today = synthetic(Kind.FULL)
        assertEquals(21 * 60 + 40, today.nowMinute)
        val home = today.stays.last()
        assertTrue(home.ongoing)
        assertEquals(today.nowMinute, home.endMinute)
        val past = synthetic(Kind.FULL, null)
        assertNull(past.nowMinute)
        assertEquals(1440, past.segments.last().endMinute)
    }

    @Test fun visitCrossingMidnightIsClippedToTheDay() {
        val d = DayClockAssembler.build(diary(listOf(visit("Casa", PlaceType.HOME, at(22, 0, date.minusDays(1)), at(7)))), date, zone, at(23))
        val stay = d.stays.single()
        assertEquals(0, stay.startMinute)
        assertEquals(7 * 60, stay.endMinute)
    }

    @Test fun emptyDayIsOnlyUnknown() {
        val d = synthetic(Kind.EMPTY, LocalTime.of(10, 0))
        assertTrue(d.isEmpty)
        assertTrue(d.segments.all { it is ClockSegment.Unknown })
        assertTrue(d.totals.isEmpty())
    }

    @Test fun gapsWithoutContextBecomeUnknownNeverAGuess() {
        val d = synthetic(Kind.MORNING_ONE_STOP, LocalTime.of(9, 0))
        val first = d.segments.first()
        assertTrue(first is ClockSegment.Unknown)
        assertEquals(6 * 60, first.endMinute)
        assertFalse(d.totals.containsKey(ClockCategory.HOME) && d.totals.getValue(ClockCategory.HOME) > 3 * 60)
    }

    @Test fun movesUseTheJourneyDominantModeAndLegsWithoutDataStayMoves() {
        val d = synthetic(Kind.FULL)
        val bus = d.segments.filterIsInstance<ClockSegment.Move>().single { it.mode == MovementMode.BUS }
        assertEquals("journey-seg-1", bus.id)
        assertEquals("journey-2", bus.toStopId)
        val noData = d.segments.filterIsInstance<ClockSegment.Move>().single { it.fromStopId == "journey-6" }
        assertNull("parque → casa sem dado de meio: deslocamento sem meio, não Unknown", noData.mode)
    }

    @Test fun veryShortStopsStayInTheModel() {
        val d = DayClockAssembler.build(diary(listOf(
            visit("Casa", PlaceType.HOME, at(0), at(8)),
            visit("Banca", PlaceType.STORE, at(8, 5), at(8, 5) + 30_000),
            visit("Trabalho", PlaceType.WORK, at(8, 20), at(17)),
        )), date, zone, at(23))
        val banca = d.stays.single { it.placeName == "Banca" }
        assertTrue(banca.minutes <= 1)
        assertEquals(3, d.stopCount)
        assertCoverage(d)
    }

    @Test fun hoodieActivityIsTheLongestOverlap() {
        val s = at(9); val e = at(10)
        val d = DayClockAssembler.build(diary(
            listOf(visit("Trabalho", PlaceType.WORK, s, e, HoodieActivity.IDLE)),
            activities = listOf((s until s + 20 * 60_000) to HoodieActivity.READING, (s + 20 * 60_000 until e) to HoodieActivity.WORKING),
        ), date, zone, at(23))
        assertEquals(HoodieActivity.WORKING, d.stays.single().hoodieActivity)
    }

    @Test fun categoriesFollowTheDaySummaryCards() {
        val d = synthetic(Kind.FULL)
        assertEquals(ClockCategory.MEAL, d.stays.single { it.placeName == "Restaurante" }.category)
        assertEquals("restaurante sem contexto de almoço vai para Outros, como nos cards", ClockCategory.OTHER, d.stays.single { it.placeName == "Padaria" }.category)
        assertEquals(ClockCategory.GYM, d.stays.single { it.placeName == "Academia" }.category)
        val covered = d.segments.filter { it !is ClockSegment.Unknown }.sumOf { it.minutes }
        assertEquals(covered, d.totals.values.sum())
    }

    @Test fun daylightSavingDaysHave23And25Hours() {
        val berlin = ZoneId.of("Europe/Berlin")
        val short = synthetic(Kind.DST, null, berlin, LocalDate.of(2026, 3, 29))
        assertEquals(1380, short.dayLengthMinutes)
        assertEquals(1380, short.segments.last().endMinute)
        assertEquals("02:00 não existe", 23, short.hourMarks.size)
        assertEquals(5 * 60, short.hourMarks.single { it.hour == 6 }.minute)
        val long = synthetic(Kind.DST, null, berlin, LocalDate.of(2026, 10, 25))
        assertEquals(1500, long.dayLengthMinutes)
        assertEquals(7 * 60, long.hourMarks.single { it.hour == 6 }.minute)
        assertCoverage(short); assertCoverage(long)
    }

    @Test fun inferredSleepIsExplicitAndDoesNotInflatePlaceTotals() {
        val wake = at(7)
        val onset = at(22)
        val activeEnd = at(21)
        val window = DailyActivityWindow(
            civilStartAt = at(0), civilEndAt = at(0, d = date.plusDays(1)),
            activeStartAt = wake, activeEndAt = activeEnd,
            sleepBeforeStart = InferredSleepSpan(at(23, d = date.minusDays(1)), wake, SleepConfidence.HIGH),
            wakeReason = WakeReason.PHONE_SUSTAINED, wakeConfidence = WakeConfidence.HIGH, provisional = false,
            sleepAfterEnd = InferredSleepOnset(onset, SleepOnsetReason.CORROBORATED, SleepConfidence.HIGH),
        )
        val base = diary(listOf(visit("Trabalho", PlaceType.WORK, at(9), at(17))))
        val data = DayClockAssembler.build(base.copy(activityWindow = window), date, zone, at(23))

        val sleep = data.segments.filterIsInstance<ClockSegment.Sleep>()
        assertEquals(2, sleep.size)
        assertEquals(0, sleep.first().startMinute)
        assertEquals(7 * 60, sleep.first().endMinute)
        assertEquals(22 * 60, sleep.last().startMinute)
        assertEquals(23 * 60, sleep.last().endMinute)
        assertEquals(8 * 60, data.totals.getValue(ClockCategory.WORK))
        assertCoverage(data)
    }

    @Test fun scheduleFallbackSleepIsMarkedLowAndOtherGapsStayUnknown() {
        val window = DailyActivityWindow(
            civilStartAt = at(0), civilEndAt = at(0, d = date.plusDays(1)),
            activeStartAt = at(7), activeEndAt = at(17),
            sleepBeforeStart = InferredSleepSpan(null, at(7), SleepConfidence.LOW),
            wakeReason = WakeReason.SCHEDULE_FALLBACK, wakeConfidence = WakeConfidence.LOW, provisional = false,
        )
        val data = DayClockAssembler.build(diary(emptyList()).copy(activityWindow = window), date, zone, at(23))
        val sleep = data.segments.filterIsInstance<ClockSegment.Sleep>().single()
        assertEquals(SleepConfidence.LOW, sleep.confidence)
        assertTrue(data.segments.any { it is ClockSegment.Unknown && it.startMinute <= 7 * 60 && it.endMinute >= 17 * 60 })
        assertTrue(data.totals.isEmpty())
        assertCoverage(data)
    }
}

package com.hoodie.app.engine.dayreport

import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.daycycle.*
import com.hoodie.app.domain.diary.model.*
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.dayreport.*
import com.hoodie.app.domain.routine.*
import org.junit.Assert.*
import org.junit.Test
import com.hoodie.app.engine.diary.DailySummaryCalculator
import com.hoodie.app.engine.timeline.ContextSpan
import java.time.LocalDate
import java.time.ZoneId

class DayReportAssemblerTest {
    private val date = LocalDate.of(2026, 10, 5)
    private val zone = ZoneId.of("America/New_York")
    private val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun visit(type: PlaceType, atMinute: Int, duration: Long = 30 * 60_000L, id: Long? = atMinute.toLong(), confidence: Float? = .9f) =
        PlaceVisit(id, type.label, type, start + atMinute * 60_000L, start + atMinute * 60_000L + duration,
            duration, 1, confidence = confidence, source = ContextSource.GEOFENCE)

    private fun diary(visits: List<PlaceVisit>, movements: List<DiaryMovement> = emptyList(), exception: Boolean = false,
                      activity: DailyActivityWindow = DailyActivityWindow.civil(start, end, end)) = DailyDiary(
        DailySummary(date, workMs = 8 * 60 * 60_000L, commutingMs = movements.sumOf { it.durationMs }, gymMs = 0, lunchMs = 0),
        emptyList(), visits, DiaryMapData(), ReplaySequence.EMPTY, mobilityTotals = emptyMap(), movements = movements,
        activityWindow = activity, isException = exception,
    )

    @Test fun assemblesCanonicalMetricsWithoutTreatingMissingPhoneDataAsZero() {
        val d = diary(listOf(visit(PlaceType.HOME, 7 * 60), visit(PlaceType.WORK, 8 * 60)),
            listOf(DiaryMovement(MovementMode.BUS, start + 7 * 60 * 60_000L + 30 * 60_000L, start + 8 * 60 * 60_000L)))
        val report = DayReportAssembler().assemble(d, emptyList(), DayReportStatus.READY, end, zone)
        assertEquals(8 * 60 * 60_000L, report.workMs)
        assertEquals(d.summary.commutingMs, report.commutingMs)
        assertNull(report.screenTimeMs)
        assertEquals(2, report.journey.size)
    }

    @Test fun journeyPreservesRepeatedReturnsAndObservedTransport() {
        val d = diary(listOf(visit(PlaceType.HOME, 7 * 60, id = 1), visit(PlaceType.WORK, 8 * 60, id = 2),
            visit(PlaceType.HOME, 18 * 60, id = 3), visit(PlaceType.GYM, 19 * 60, id = 4), visit(PlaceType.HOME, 20 * 60, id = 5)),
            listOf(DiaryMovement(MovementMode.BUS, start + 7 * 60 * 60_000L + 30 * 60_000L, start + 8 * 60 * 60_000L)))
        val stops = DayJourneySummaryBuilder.build(d)
        assertEquals(listOf(PlaceType.HOME, PlaceType.WORK, PlaceType.HOME, PlaceType.GYM, PlaceType.HOME), stops.map { it.type })
        assertEquals(MovementMode.BUS, stops[1].transportBefore)
        assertNull(stops[2].transportBefore)
    }

    @Test fun fallbackWakeIsNeverPresentedAsAConfirmedTime() {
        val window = DailyActivityWindow(start, end, start + 7 * 60 * 60_000L, end, null,
            WakeReason.SCHEDULE_FALLBACK, WakeConfidence.LOW, provisional = false)
        val report = DayReportAssembler().assemble(diary(listOf(visit(PlaceType.HOME, 8 * 60)), activity = window), emptyList(), DayReportStatus.READY, end, zone)
        assertNull(report.wakeAt)
        assertTrue(report.wakeEstimated)
        assertEquals(DayReportStatus.READY, report.status)
    }

    @Test fun homeReturnHighlightRequiresObservedEvidenceAndEnoughRoutineSamples() {
        val home = visit(PlaceType.HOME, 18 * 60 + 20, id = 55).copy(confidence = .9f, source = ContextSource.GEOFENCE)
        val routine = LearnedRoutineSlot("WEEKDAY", RoutineEventType.HOME_RETURN, 19 * 60, 10, 5, ConfidenceScore(.8f))
        val engine = DayHighlightEngine()
        assertEquals(DayHighlightType.HOME_RETURN_EARLIER, engine.select(diary(listOf(home)), listOf(routine), zone)?.type)
        assertNull(engine.select(diary(listOf(home)), listOf(routine.copy(sampleCount = 4)), zone))
        assertNull(engine.select(diary(listOf(home.copy(confidence = .4f))), listOf(routine), zone))
    }

    @Test fun exceptionsSuppressHighlightsAndCivilDayUsesLocalBoundaryLength() {
        val exceptionDate = LocalDate.of(2026, 3, 8)
        val zone = ZoneId.of("America/New_York")
        val civilLength = exceptionDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - exceptionDate.atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(23 * 60 * 60_000L, civilLength)
        val gym = visit(PlaceType.GYM, 9 * 60, 54 * 60_000L).copy(confidence = .9f, source = ContextSource.CONFIRMATION)
        assertNull(DayHighlightEngine().select(diary(listOf(gym), exception = true), emptyList(), zone))
    }

    @Test fun fallBackCivilDayKeepsIts25HourWorkDurationInTheReport() {
        val date = LocalDate.of(2026, 11, 1)
        val zone = ZoneId.of("America/New_York")
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val civilLength = to - from
        assertEquals(25 * 60 * 60_000L, civilLength)
        val summary = DailySummaryCalculator.compute(
            listOf(ContextSpan(com.hoodie.app.core.model.UserContextType.WORK, from - 60 * 60_000L, to + 60 * 60_000L)),
            date, from, to, to,
        )
        val diary = DailyDiary(
            summary = summary,
            timeline = listOf(DiaryTimelineItem("work", from, DiaryTimelineType.CONTEXT_CHANGE, DiaryActor.USER,
                "Trabalhando", relatedContext = com.hoodie.app.core.model.UserContextType.WORK)),
            visits = emptyList(), map = DiaryMapData(), replay = ReplaySequence.EMPTY,
            activityWindow = DailyActivityWindow.civil(from, to, to),
        )
        val report = DayReportAssembler().assemble(diary, emptyList(), DayReportStatus.READY, to, zone)

        assertEquals(civilLength, summary.workMs)
        assertEquals(civilLength, report.workMs)
    }

    @Test fun observedLateReturnAndRoutineConfidenceAreEvaluatedSafely() {
        val lateReturn = visit(PlaceType.HOME, 19 * 60 + 35, id = 56)
        val routine = LearnedRoutineSlot("WEEKDAY", RoutineEventType.HOME_RETURN, 19 * 60, 10, 8, ConfidenceScore(.8f))
        val highlight = DayHighlightEngine().select(diary(listOf(lateReturn)), listOf(routine), zone)
        assertEquals(DayHighlightType.HOME_RETURN_LATER, highlight?.type)
        assertEquals(listOf("35"), highlight?.arguments)
        assertNull(DayHighlightEngine(minRoutineConfidence = .85f).select(diary(listOf(lateReturn)), listOf(routine), zone))
    }

    @Test fun gymWinsDeterministicPriorityAndCorrectedCanonicalDiaryRebuildsReport() {
        val original = diary(listOf(visit(PlaceType.HOME, 7 * 60, id = 1), visit(PlaceType.WORK, 8 * 60, id = 2),
            visit(PlaceType.HOME, 18 * 60, id = 3)))
        val corrected = original.copy(
            summary = original.summary.copy(workMs = 7 * 60 * 60_000L, lunchMs = 56 * 60_000L),
            visits = original.visits + visit(PlaceType.GYM, 19 * 60, 54 * 60_000L, id = 4),
        )
        val routine = LearnedRoutineSlot("WEEKDAY", RoutineEventType.HOME_RETURN, 19 * 60 + 30, 10, 8, ConfidenceScore(.8f))
        val report = DayReportAssembler().assemble(corrected, listOf(routine), DayReportStatus.READY, end, zone)

        assertEquals(7 * 60 * 60_000L, report.workMs)
        assertEquals(56 * 60_000L, report.lunchMs)
        assertEquals(DayHighlightType.GYM, report.highlight?.type)
        assertEquals(4, report.journey.size)
    }
}

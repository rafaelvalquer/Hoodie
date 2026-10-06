package com.hoodie.app.engine.diary.journey

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.V
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.at
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class JourneyChapterPlannerTest {
    private val zone = JourneyV3Fixtures.UTC

    private fun chapters(plan: JourneyPlan) = plan as JourneyPlan.Chapters

    @Test fun nineStopsAreSingleAndTenAreChapters() {
        assertTrue(JourneyChapterPlanner.plan(JourneyV3Fixtures.stops(9), zone) is JourneyPlan.Single)
        val ten = JourneyChapterPlanner.plan(JourneyV3Fixtures.stops(10), zone)
        assertTrue(ten is JourneyPlan.Chapters)
        assertEquals(listOf(DayChapter.MORNING, DayChapter.AFTERNOON, DayChapter.NIGHT), chapters(ten).chapters.map { it.chapter })
    }

    @Test fun boundaryElevenFiftyNineIsMorningAndNoonIsAfternoon() {
        val w = JourneyChapterPlanner.windows(JourneyV3Fixtures.DATE, zone)
        assertEquals(DayChapter.MORNING, JourneyChapterPlanner.chapterOf(at(11, 59), w))
        assertEquals(DayChapter.AFTERNOON, JourneyChapterPlanner.chapterOf(at(12, 0), w))
        assertEquals(DayChapter.AFTERNOON, JourneyChapterPlanner.chapterOf(at(17, 59), w))
        assertEquals(DayChapter.NIGHT, JourneyChapterPlanner.chapterOf(at(18, 0), w))
        assertEquals("madrugada é Manhã", DayChapter.MORNING, JourneyChapterPlanner.chapterOf(at(2), w))
    }

    private fun busyDay(vararg extra: V) = day(
        V("Casa", PlaceType.HOME, at(6), at(7)),
        V("Café", PlaceType.RESTAURANT, at(7, 10), at(7, 20)),
        V("Escola", PlaceType.SCHOOL, at(7, 30), at(9)),
        V("Mercado", PlaceType.MARKET, at(9, 10), at(9, 30)),
        V("Trabalho", PlaceType.WORK, at(10), at(18, 30)),
        V("Academia", PlaceType.GYM, at(19), at(20)),
        V("Família", PlaceType.FAMILY, at(20, 10), at(21)),
        V("Parque", PlaceType.LEISURE, at(21, 10), at(21, 30)),
        V("Outro", PlaceType.OTHER, at(21, 40), at(22)),
        *extra,
        V("Casa", PlaceType.HOME, at(22, 30), null),
    )

    @Test fun visitCrossingPeriodsCreatesAGhostAndOnlyGhostChapter() {
        val plan = chapters(JourneyChapterPlanner.plan(busyDay(), zone))
        val afternoon = plan.chapter(DayChapter.AFTERNOON)
        assertEquals("Trabalho continua na Tarde", "journey-4", afternoon.ghostOfStopId)
        assertTrue(afternoon.onlyGhost)
        assertEquals(0, afternoon.visitCount)
        val ghost = plan.stop(afternoon.stopIds.single()) as JourneyStop.Ghost
        assertEquals(at(12), ghost.startAt)
        // Noite também começa com o fantasma do Trabalho (que vai até 18:30).
        assertEquals("journey-4", plan.chapter(DayChapter.NIGHT).ghostOfStopId)
        assertEquals("Academia, Família, Parque, Outro e Casa", 5, plan.chapter(DayChapter.NIGHT).visitCount)
    }

    @Test fun ghostDoesNotCountAsStopAndChapterTimeIsClipped() {
        val plan = chapters(JourneyChapterPlanner.plan(busyDay(), zone))
        val total = plan.chapters.sumOf { it.visitCount }
        assertEquals(10, total)
        assertEquals("tarde inteira no trabalho = 6 h", 6 * 3_600_000L, plan.chapter(DayChapter.AFTERNOON).totalStayMs)
    }

    @Test fun periodWithoutArrivalNorOngoingVisitIsEmpty() {
        val d = day(
            *(0 until 10).map { V("Manhã $it", PlaceType.OTHER, at(6) + it * 30 * 60_000L, at(6) + it * 30 * 60_000L + 20 * 60_000L) }.toTypedArray(),
            V("Noite", PlaceType.HOME, at(19), at(20)),
        )
        val plan = chapters(JourneyChapterPlanner.plan(d, zone))
        val afternoon = plan.chapter(DayChapter.AFTERNOON)
        assertTrue("Sem paradas", afternoon.isEmpty)
        assertNull(afternoon.ghostOfStopId)
        // Portal: o trecho sai na Manhã e só chega à Noite.
        assertEquals(plan.chapter(DayChapter.MORNING).exitLegId, plan.chapter(DayChapter.NIGHT).entryLegId)
    }

    @Test fun midnightAndDaylightSavingUseZonedBoundaries() {
        // Brasil não tem mais horário de verão; usa um fuso com mudança (Europa) — dia de 23 h.
        val berlin = ZoneId.of("Europe/Berlin")
        val dstDay = LocalDate.of(2026, 3, 29)
        val w = JourneyChapterPlanner.windows(dstDay, berlin)
        assertEquals(23 * 3_600_000L, w.last().endAt - w.first().startAt)
        assertEquals(dstDay.atTime(12, 0).atZone(berlin).toInstant().toEpochMilli(), w[1].startAt)
        // Visita que vem da madrugada (recortada no dia) fica na Manhã.
        val d = day(V("Casa", PlaceType.HOME, at(0), at(7)), *(1..10).map { V("L$it", PlaceType.OTHER, at(7) + it * 60 * 60_000L, at(7) + it * 60 * 60_000L + 30 * 60_000L) }.toTypedArray())
        val plan = chapters(JourneyChapterPlanner.plan(d, zone))
        assertTrue("journey-0" in plan.chapter(DayChapter.MORNING).stopIds)
    }

    @Test fun activeChapterFollowsReplayManualTodayThenLongestStay() {
        val plan = chapters(JourneyChapterPlanner.plan(busyDay(), zone))
        assertEquals(DayChapter.NIGHT, JourneyChapterPlanner.activeChapter(plan, at(21), playing = true, manual = DayChapter.MORNING, isToday = false, now = 0))
        assertEquals(DayChapter.MORNING, JourneyChapterPlanner.activeChapter(plan, at(21), playing = false, manual = DayChapter.MORNING, isToday = true, now = at(15)))
        assertEquals(DayChapter.AFTERNOON, JourneyChapterPlanner.activeChapter(plan, null, playing = false, manual = null, isToday = true, now = at(15)))
        val longest = plan.chapters.maxBy { it.totalStayMs }.chapter
        assertEquals(longest, JourneyChapterPlanner.activeChapter(plan, null, playing = false, manual = null, isToday = false, now = 0))
    }

    @Test fun planningIsDeterministic() {
        val d = JourneyV3Fixtures.stops(14)
        assertEquals(JourneyChapterPlanner.plan(d, zone), JourneyChapterPlanner.plan(d, zone))
    }
}

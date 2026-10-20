package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DiaryMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * HOODIE-JOURNEY-01 na Jornada inteira: o mesmo Diário consolidado vira um nó só no mapa, uma
 * estadia no Relógio, nenhum trajeto Casa → Casa no replay, e a timeline resumida sem as fronteiras
 * internas. Com a flag desligada, nada muda.
 */
class JourneyVisitProjectionTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val date = LocalDate.of(2026, 10, 5)
    private fun at(h: Int, m: Int = 0) = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private val dayStart = at(0)
    private val dayEnd = dayStart + 86_400_000L
    private val now = at(23)
    private val hour = 3_600_000L

    private val homeId = 1L
    private val workId = 2L
    private val places = listOf(
        PlaceEntity(homeId, "Casa", PlaceType.HOME, "", 100f, 1f, createdAt = dayStart),
        PlaceEntity(workId, "Trabalho", PlaceType.WORK, "", 100f, 1f, createdAt = dayStart),
    )

    private fun ctx(id: Long, type: UserContextType, place: Long?, from: Long, to: Long?, source: ContextSource = ContextSource.GEOFENCE) =
        ContextEventEntity(id, type, from, to, .9f, place, source)

    /** Casa 08–09, Casa 09–10 (a "academia" corrigida), Casa 10–11, Trabalho 11:30–17, Casa 18–23. */
    private val day = listOf(
        ctx(101, UserContextType.HOME, homeId, at(8), at(9)),
        ctx(102, UserContextType.HOME, homeId, at(9), at(10), ContextSource.USER_CORRECTION),
        ctx(103, UserContextType.HOME, homeId, at(10), at(11)),
        ctx(104, UserContextType.COMMUTING, null, at(11), at(11, 30)),
        ctx(105, UserContextType.WORK, workId, at(11, 30), at(17)),
        ctx(106, UserContextType.COMMUTING, null, at(17), at(18)),
        ctx(107, UserContextType.HOME, homeId, at(18), null),
    )

    private fun diary(contexts: List<ContextEventEntity> = day, moves: List<DiaryMovement> = emptyList(), enabled: Boolean = true): DailyDiary {
        val base = DiaryAssembler.build(date, contexts, emptyList(), emptyList(), places, dayStart, dayEnd, now).copy(movements = moves)
        return JourneyVisitProjectionBuilder.apply(base, enabled = enabled)
    }

    @Test fun journeyHasOneHomeThenWorkThenTheRealReturn() {
        val d = diary()
        assertEquals(5, d.visits.size) // originais preservadas
        val journey = JourneyMapAssembler.build(d, now)
        assertEquals(listOf(homeId, workId, homeId), journey.nodes.map { it.placeId })
        val first = journey.nodes[0]
        assertEquals(at(8), first.arrivalAt)
        assertEquals(at(11), first.departureAt)
        assertEquals(3 * hour, first.durationMs)
        assertEquals(listOf(101L, 102L, 103L), first.sourceIntervals.map { it.contextEventId })
        assertTrue(first.sourceIntervals[1].corrected)
        assertFalse(first.isReturn)
        assertTrue(journey.nodes[2].isReturn)
        assertEquals(2, first.placeOccurrences)
        assertEquals(3 * hour + 5 * hour, first.placeTotalMs)
        // Só dois trechos (Casa → Trabalho → Casa), nenhum Casa → Casa.
        assertEquals(2, journey.segments.size)
        assertEquals(listOf("journey-0" to "journey-1", "journey-1" to "journey-2"), journey.segments.map { it.fromNodeId to it.toNodeId })
    }

    @Test fun realMovementsStillDriveTheSegmentMode() {
        val d = diary(moves = listOf(DiaryMovement(MovementMode.BUS, at(11, 2), at(11, 28))))
        assertEquals(MovementMode.BUS, JourneyMapAssembler.build(d, now).segments.first().movementMode)
    }

    @Test fun legacyMapAndReplayUseTheSameStays() {
        val d = diary()
        assertEquals(3, d.map.nodes.size)
        assertEquals(2, d.map.edges.size)
        assertEquals(3, d.replay.visits.size)
        // Às 09:30 e 10:30 o Hoodie continua no nó Casa, sem trajeto.
        listOf(at(9, 30), at(10, 30)).forEach { t ->
            val frame = d.replay.frameAt(t)
            assertEquals("visit-0", frame.activeNodeId)
            assertNull(frame.activeEdgeId)
        }
        assertEquals("edge-0", d.replay.frameAt(at(11, 10)).activeEdgeId)
    }

    @Test fun clockShowsOneHomeStayAndTheSameStops() {
        val d = diary()
        val clock = DayClockAssembler.build(d, date, zone, now)
        val homeMorning = clock.segments.filterIsInstance<ClockSegment.Stay>().filter { it.id == "journey-0" }
        assertEquals(1, homeMorning.size)
        assertEquals(8 * 60, homeMorning.single().startMinute)
        assertEquals(11 * 60, homeMorning.single().endMinute)
        assertEquals(listOf(1, 2, 3), clock.segments.filterIsInstance<ClockSegment.Stay>().map { it.stopIndex }.distinct())
        assertEquals((3 + 5) * 60, clock.totals[ClockCategory.HOME])
    }

    @Test fun smallUnknownGapStaysUnknownOnTheClock() {
        val gap = listOf(
            ctx(101, UserContextType.HOME, homeId, at(8), at(9)),
            ctx(102, UserContextType.HOME, homeId, at(9, 1), at(10)),
            ctx(105, UserContextType.WORK, workId, at(11), at(17)),
        )
        val d = diary(gap)
        val node = JourneyMapAssembler.build(d, now).nodes.first()
        assertEquals(2 * hour - 60_000L, node.durationMs)
        val clock = DayClockAssembler.build(d, date, zone, now)
        val parts = clock.segments.filter { it.id == "journey-0" }
        assertEquals(2, parts.size)
        assertTrue(clock.segments.any { it is ClockSegment.Unknown && it.startMinute == 9 * 60 && it.endMinute == 9 * 60 + 1 })
        assertEquals(1, (parts as List<ClockSegment.Stay>).map { it.stopIndex }.distinct().size)
        assertEquals(119, clock.totals[ClockCategory.HOME])
    }

    @Test fun internalArrivalsAndDeparturesLeaveTheSummaryTimeline() {
        val d = diary()
        val ids = d.timeline.map { it.id }
        assertFalse("left-101" in ids); assertFalse("context-102" in ids)
        assertFalse("left-102" in ids); assertFalse("context-103" in ids)
        assertTrue("context-101" in ids)
        assertTrue("left-103" in ids)
        assertTrue("context-107" in ids)
    }

    @Test fun workingAtHomeKeepsItsContextChangeVisible() {
        val wfh = listOf(
            ctx(101, UserContextType.HOME, homeId, at(8), at(9)),
            ctx(102, UserContextType.WORK, homeId, at(9), at(17)),
        )
        val d = diary(wfh)
        val journey = JourneyMapAssembler.build(d, now)
        assertEquals(1, journey.nodes.size)
        assertTrue("context-102" in d.timeline.map { it.id })
        val clock = DayClockAssembler.build(d, date, zone, now)
        assertEquals(8 * 60, clock.totals[ClockCategory.WORK] ?: 0)
    }

    @Test fun visitDetailsFollowTheNodesAndKnowTheirSources() {
        val d = diary()
        val details = ReplayHudAssembler.visitDetails(d, now)
        assertEquals(3, details.size)
        assertEquals(listOf(101L, 102L, 103L), details[0].sources.map { it.contextEventId })
    }

    @Test fun flagOffLeavesTheDiaryUntouched() {
        val base = DiaryAssembler.build(date, day, emptyList(), emptyList(), places, dayStart, dayEnd, now)
        assertSame(base, JourneyVisitProjectionBuilder.apply(base, enabled = false))
        assertEquals(5, JourneyMapAssembler.build(base, now).nodes.size)
    }

    @Test fun replayStateAfterAnEditNeverPointsAtAMissingNode() {
        val before = diary(enabled = false)
        val after = diary()
        val ids = (0 until after.replay.visits.size).map { "visit-$it" }.toSet()
        listOf(at(8, 30), at(9, 30), at(10, 30), at(12), at(20)).forEach { t ->
            before.replay.frameAt(t)
            after.replay.frameAt(t).activeNodeId?.let { assertTrue("$t: $it", it in ids) }
        }
    }
}

package com.hoodie.app.engine.diary

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DiaryMovement
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.diary.model.VisitSourceInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** HOODIE-JOURNEY-01: casos T01–T16 da consolidação de visitas consecutivas. */
class ConsecutivePlaceVisitConsolidatorTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 5, h, m).atZone(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    private val home = 15L
    private val work = 20L

    /** Visita + evidência canônica correspondente. */
    private data class V(val id: Long, val place: Long?, val type: PlaceType, val start: Long, val end: Long?, val context: UserContextType, val now: Long)

    private fun v(id: Long, place: Long?, start: Long, end: Long?, type: PlaceType = if (place == work) PlaceType.WORK else PlaceType.HOME,
        context: UserContextType = type.toContext(), now: Long = at(23)) = V(id, place, type, start, end, context, now)

    private fun visit(x: V) = PlaceVisit(x.place, x.type.label, x.type, x.start, x.end, (x.end ?: x.now) - x.start, 1, contextEventIds = listOf(x.id))
    private fun evidence(x: V) = VisitSourceInterval(x.id, x.place, x.context, x.start, x.end ?: x.now, open = x.end == null)

    private fun run(vararg xs: V, moves: List<DiaryMovement> = emptyList(), extra: List<VisitSourceInterval> = emptyList()) =
        ConsecutivePlaceVisitConsolidator.consolidate(xs.map(::visit), xs.map(::evidence) + extra, moves)

    @Test fun t01_threeAdjacentHomesBecomeOne() {
        val p = run(v(101, home, at(8), at(9)), v(102, home, at(9), at(10)), v(103, home, at(10), at(11)))
        val only = p.visits.single()
        assertEquals(at(8), only.visit.arrivalAt)
        assertEquals(at(11), only.visit.departureAt)
        assertEquals(3 * hour, only.visit.durationMs)
        assertEquals(listOf(101L, 102L, 103L), only.sourceContextEventIds)
        assertEquals(1, only.visit.visitsCount)
    }

    @Test fun t02_homeHomeWorkKeepsWork() {
        val p = run(v(1, home, at(8), at(9)), v(2, home, at(9), at(10)), v(3, work, at(10, 30), at(17)))
        assertEquals(listOf(home, work), p.visits.map { it.visit.placeId })
    }

    @Test fun t03_trueReturnIsPreserved() {
        val p = run(v(1, home, at(8), at(9)), v(2, work, at(9), at(17)), v(3, home, at(17), at(22)))
        assertEquals(listOf(home, work, home), p.visits.map { it.visit.placeId })
        assertEquals(2, p.visits.first().visit.visitsCount)
        assertFalse(p.hasMerges)
    }

    @Test fun t04_sameNameDifferentIdsNeverMerge() {
        val p = run(v(1, 15, at(8), at(9)), v(2, 28, at(9), at(10)))
        assertEquals(2, p.visits.size)
    }

    @Test fun t04b_withoutPlaceIdNeverMerge() {
        val p = run(v(1, null, at(8), at(9)), v(2, null, at(9), at(10)))
        assertEquals(2, p.visits.size)
    }

    @Test fun t05_overlapCountsOnce() {
        val p = run(v(1, home, at(8), at(9, 30)), v(2, home, at(9), at(11)))
        val only = p.visits.single().visit
        assertEquals(3 * hour, only.durationMs)
        assertEquals(at(11), only.departureAt)
    }

    @Test fun t06_oneMinuteGapMergesButKeepsUnknownTime() {
        val p = run(v(1, home, at(8), at(9)), v(2, home, at(9, 1), at(10)), v(3, home, at(10), at(11)))
        val only = p.visits.single().visit
        assertEquals(at(8), only.arrivalAt)
        assertEquals(at(11), only.departureAt)
        assertEquals(2 * hour + 59 * 60_000L, only.durationMs)
    }

    @Test fun t07_gapLongerThanToleranceKeepsTwoVisits() {
        val p = run(v(1, home, at(8), at(9)), v(2, home, at(9, 3), at(10)))
        assertEquals(2, p.visits.size)
    }

    @Test fun t08_confirmedTripBetweenHomesKeepsReturn() {
        val p = run(v(1, home, at(8), at(9)), v(2, home, at(9, 2), at(10)), moves = listOf(DiaryMovement(MovementMode.WALKING, at(9), at(9, 2))))
        assertEquals(2, p.visits.size)
    }

    @Test fun t09_commutingContextBetweenHomesKeepsReturn() {
        val commute = VisitSourceInterval(50, null, UserContextType.COMMUTING, at(9), at(9, 1))
        val p = run(v(1, home, at(8), at(9)), v(2, home, at(9, 1), at(10)), extra = listOf(commute))
        assertEquals(2, p.visits.size)
    }

    @Test fun t10_workingAtHomeIsOneStayWithBothContexts() {
        val p = run(v(1, home, at(8), at(9)), v(2, home, at(9), at(17), context = UserContextType.WORK))
        val only = p.visits.single()
        assertEquals(listOf(UserContextType.HOME, UserContextType.WORK), only.sourceIntervals.map { it.contextType })
        assertEquals(9 * hour, only.visit.durationMs)
    }

    @Test fun t11_openLastVisitStaysOpen() {
        val now = at(12)
        val p = run(v(1, home, at(8), at(9), now = now), v(2, home, at(9), null, now = now))
        val only = p.visits.single().visit
        assertNull(only.departureAt)
        assertEquals(4 * hour, only.durationMs)
    }

    @Test fun t12_emptyInput() {
        assertTrue(ConsecutivePlaceVisitConsolidator.consolidate(emptyList(), emptyList(), emptyList()).visits.isEmpty())
    }

    @Test fun t13_singleVisitIsPreserved() {
        val x = v(7, home, at(8), at(9))
        val only = run(x).visits.single()
        assertEquals(visit(x), only.visit)
        assertEquals(listOf(7L), only.sourceContextEventIds)
    }

    @Test fun t14_consolidatingTwiceChangesNothing() {
        val xs = arrayOf(v(1, home, at(8), at(9)), v(2, home, at(9, 1), at(10)), v(3, work, at(10, 30), at(17)), v(4, home, at(18), at(22)))
        val once = run(*xs)
        val twice = ConsecutivePlaceVisitConsolidator.consolidate(once.visits.map { it.visit }, xs.map(::evidence), emptyList())
        assertEquals(once, twice)
    }

    @Test fun t15_durationsAcrossADstJumpAreRealMilliseconds() {
        val lisbon = ZoneId.of("Europe/Lisbon")
        fun l(h: Int, m: Int = 0) = LocalDateTime.of(2026, 3, 29, h, m).atZone(lisbon).toInstant().toEpochMilli()
        // 01:00 → 02:00 não existe nesse dia: 00:30–03:00 local são 1h30 reais.
        val p = run(v(1, home, l(0, 30), l(1, 0)), v(2, home, l(1, 0), l(3, 0)))
        assertEquals(l(3, 0) - l(0, 30), p.visits.single().visit.durationMs)
        assertEquals(90 * 60_000L, p.visits.single().visit.durationMs)
    }

    @Test fun t16_outOfOrderInputIsDeterministic() {
        val xs = listOf(v(3, home, at(10), at(11)), v(1, home, at(8), at(9)), v(4, work, at(12), at(17)), v(2, home, at(9), at(10)))
        val a = ConsecutivePlaceVisitConsolidator.consolidate(xs.map(::visit), xs.map(::evidence), emptyList())
        val b = ConsecutivePlaceVisitConsolidator.consolidate(xs.reversed().map(::visit), xs.reversed().map(::evidence), emptyList())
        assertEquals(a, b)
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(4L)), a.visits.map { it.sourceContextEventIds })
    }

    @Test fun disabledPolicyKeepsEveryVisit() {
        val xs = listOf(v(1, home, at(8), at(9)), v(2, home, at(9), at(10)))
        val p = ConsecutivePlaceVisitConsolidator.consolidate(xs.map(::visit), xs.map(::evidence), emptyList(), ConsolidationPolicy.DISABLED)
        assertEquals(2, p.visits.size)
    }

    @Test fun unionLengthIgnoresGapsAndOverlaps() {
        assertEquals(30L, ConsecutivePlaceVisitConsolidator.unionLength(listOf(0L to 10L, 5L to 20L, 25L to 35L)))
        assertEquals(listOf(0L to 20L, 25L to 35L), ConsecutivePlaceVisitConsolidator.union(listOf(25L to 35L, 0L to 10L, 5L to 20L)))
    }
}

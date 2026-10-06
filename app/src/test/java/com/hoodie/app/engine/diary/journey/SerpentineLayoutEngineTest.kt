package com.hoodie.app.engine.diary.journey

import com.hoodie.app.domain.diary.journey.JourneyPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerpentineLayoutEngineTest {
    private val spec = SerpentineSpec()
    private fun layout(n: Int): OverworldLayout {
        val plan = JourneyChapterPlanner.plan(JourneyV3Fixtures.stops(n), JourneyV3Fixtures.UTC, config = JourneyPlanConfig(forceChapters = false))
        return SerpentineLayoutEngine.layout(plan.stops, spec, legBetween = { a, b -> JourneyOverworldModel.legBetween(plan, a, b) })
    }

    @Test fun directionAlternatesEveryRow() {
        val l = layout(7)
        assertEquals(listOf(0, 1, 2), l.stops.take(3).map { it.col })
        assertEquals(listOf(2, 1, 0), l.stops.drop(3).take(3).map { it.col })
        assertEquals(0, l.stops[6].col)
        assertTrue("linha 1 vai para a direita", l.stops[1].point.x > l.stops[0].point.x)
        assertTrue("linha 2 volta para a esquerda", l.stops[4].point.x < l.stops[3].point.x)
    }

    @Test fun heightIsAFunctionOfRowsAndSingleNeverPassesThreeRows() {
        (1..9).forEach { n ->
            val rows = (n + 2) / 3
            assertEquals(spec.topPadding + (rows - 1) * spec.rowHeight + spec.bottomPadding, layout(n).height)
            assertTrue(layout(n).rows <= 3)
        }
    }

    @Test fun buildingsAndSignsNeverOverlapForOneToTwentyStops() {
        (1..20).forEach { n ->
            val rects = layout(n).stops.flatMap { listOf(it.building, it.sign) }
            rects.forEachIndexed { i, a -> rects.drop(i + 1).forEach { b -> assertFalse("$n paradas: $a × $b", a.intersects(b)) } }
            layout(n).stops.forEach { s ->
                assertTrue("placa dentro do mapa", s.sign.x >= 0 && s.sign.right <= spec.width && s.building.y >= 0)
            }
        }
    }

    @Test fun signsSitInsideTheSerpentineAndBelowInTheMiddleColumn() {
        layout(9).stops.forEach { s ->
            when (s.col) {
                0 -> assertEquals(SignSide.RIGHT, s.signSide)
                1 -> assertEquals(SignSide.BELOW, s.signSide)
                2 -> assertEquals(SignSide.LEFT, s.signSide)
            }
        }
    }

    @Test fun touchTargetsAreAtLeast48dpAtTheSmallestIntegerScale() {
        // Em telas comuns a escala inteira é ≥ 2 (480 px / 240); 48 dp em 2.625 dpi ≈ 126 px → 63 px lógicos a 2×.
        // O layout garante a área lógica; a UI completa até 48 dp com coerceAtLeast.
        layout(9).stops.forEach { s -> assertTrue(s.hit.w >= spec.buildingSize && s.hit.h >= spec.buildingSize) }
    }

    @Test fun turnsAreUCurvesAtTheEdgeAndStraightWithinARow() {
        val l = layout(6)
        val kinds = l.links.map { it.kind }
        assertEquals(listOf(LinkKind.STRAIGHT, LinkKind.STRAIGHT, LinkKind.TURN, LinkKind.STRAIGHT, LinkKind.STRAIGHT), kinds)
        val turn = l.links[2].path
        assertTrue("o bojo sai pela borda direita", turn.points.maxOf { it.x } > l.stops[2].point.x + 10)
    }

    @Test fun layoutIsDeterministic() {
        assertEquals(layout(14), layout(14))
    }

    @Test fun portalsAppearOnlyWhenTheChapterHasEntryOrExit() {
        val plan = JourneyChapterPlanner.plan(JourneyV3Fixtures.stops(3), JourneyV3Fixtures.UTC) as JourneyPlan.Single
        val l = SerpentineLayoutEngine.layout(plan.stops, spec, entryLegId = "in", exitLegId = "out")
        assertEquals(LinkKind.PORTAL_IN, l.links.first().kind)
        assertEquals(LinkKind.PORTAL_OUT, l.links.last().kind)
        assertEquals(0f, l.links.first().path.points.first().y)
        assertEquals((l.height - 1).toFloat(), l.links.last().path.points.last().y)
    }
}

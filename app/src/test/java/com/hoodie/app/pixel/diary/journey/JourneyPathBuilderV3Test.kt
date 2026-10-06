package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.pixel.diary.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Caminhos da serpentina (plano §4.5): reto, curva em U pela borda e portais. */
class JourneyPathBuilderV3Test {
    @Test fun sameRowIsAStraightHorizontalSegment() {
        val p = JourneyPathBuilder.straight("s", MapPoint(44f, 46f), MapPoint(120f, 46f))
        assertEquals(76f, p.length)
        assertTrue(p.points.all { it.y == 46f })
    }

    @Test fun rowChangeIsAUTurnBulgingOutwardWithIntegerPoints() {
        val from = MapPoint(196f, 46f); val to = MapPoint(196f, 112f)
        val p = JourneyPathBuilder.uTurn("u", from, to, bulge = 34, outward = 1)
        assertEquals(from, p.points.first()); assertEquals(to, p.points.last())
        assertTrue(p.points.all { it.x == it.x.toInt().toFloat() && it.y == it.y.toInt().toFloat() })
        assertTrue("bojo para fora", p.points.maxOf { it.x } > 210f)
        val left = JourneyPathBuilder.uTurn("u", MapPoint(44f, 46f), MapPoint(44f, 112f), 34, outward = -1)
        assertTrue(left.points.minOf { it.x } < 30f)
    }

    @Test fun portalsLeaveByTheBottomAndEnterByTheTop() {
        val out = JourneyPathBuilder.portalOut("o", MapPoint(120f, 112f), 160)
        assertEquals(160f, out.points.last().y)
        val inn = JourneyPathBuilder.portalIn("i", 22, MapPoint(44f, 46f))
        assertEquals(0f, inn.points.first().y)
        assertEquals(MapPoint(44f, 46f), inn.points.last())
    }

    @Test fun pointAtIsMonotonicAlongTheLength() {
        val p = JourneyPathBuilder.uTurn("u", MapPoint(196f, 46f), MapPoint(196f, 112f), 34, 1)
        var travelled = 0f
        var prev = p.pointAt(0f)
        (1..200).forEach { i ->
            val q = p.pointAt(i / 200f)
            travelled += hypot(q.x - prev.x, q.y - prev.y)
            prev = q
        }
        assertEquals(p.length, travelled, 1.5f)
        assertEquals(p.points.last(), p.pointAt(1f))
    }
}

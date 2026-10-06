package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.engine.diary.DayClockAssembler
import com.hoodie.app.engine.diary.SyntheticClockDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.hypot

class DayClockGeometryTest {
    private val g = DayClockGeometry

    @Test fun anglesAtTheCardinalHours() {
        assertEquals(0f, g.minuteToAngle(0, 1440), 0.001f)
        assertEquals(90f, g.minuteToAngle(6 * 60, 1440), 0.001f)
        assertEquals(180f, g.minuteToAngle(12 * 60, 1440), 0.001f)
        assertEquals(270f, g.minuteToAngle(18 * 60, 1440), 0.001f)
        // 00h no topo, 06h à direita, 12h embaixo, 18h à esquerda.
        assertEquals(ClockPx(52, 12), g.pointAt(0, 40f, 1440))
        assertEquals(ClockPx(92, 52), g.pointAt(6 * 60, 40f, 1440))
        assertEquals(ClockPx(52, 92), g.pointAt(12 * 60, 40f, 1440))
        assertEquals(ClockPx(12, 52), g.pointAt(18 * 60, 40f, 1440))
    }

    @Test fun minuteToPointToMinuteRoundTrips() {
        listOf(1380, 1440, 1500).forEach { len ->
            (0 until len step 7).forEach { m ->
                val p = g.pointAt(m, g.ACTIVITY_MID, len)
                val back = g.minuteAt(p.x + 0.5f, p.y + 0.5f, len)
                // Um pixel no raio do anel cobre ~6 min.
                val d = minOf(kotlin.math.abs(back - m), len - kotlin.math.abs(back - m))
                assertTrue("$len: $m → $p → $back", d <= len / (2 * Math.PI * g.ACTIVITY_MID).toFloat() * 1.2f)
            }
        }
    }

    @Test fun tablesMatchTheMath() {
        val i = 10 * g.SIZE + 70
        assertEquals(hypot(70.5f - 52f, 10.5f - 52f), g.RADIUS[i], 0.0001f)
        assertEquals(g.fractionAt(70.5f, 10.5f), g.FRACTION[i], 0.0001f)
        assertTrue(g.FRACTION.all { it in 0f..1f })
    }

    @Test fun hitTestInEachRadiusBand() {
        val day = SyntheticClockDays.build(SyntheticClockDays.Kind.FULL, LocalDate.of(2026, 10, 5), ZoneId.of("UTC"), LocalTime.of(21, 40))
        val data = DayClockAssembler.build(day.diary, day.date, day.zone, day.now)
        fun at(minute: Int, r: Float): Pair<Float, Float> {
            val a = minute / 1440.0 * 2 * Math.PI
            return (52 + r * kotlin.math.sin(a)).toFloat() to (52 - r * kotlin.math.cos(a)).toFloat()
        }
        val (cx, cy) = at(0, 10f)
        assertEquals(ClockHit.Center, g.hitTest(cx, cy, data))
        val work = data.stays.first { it.placeName == "Trabalho" }
        val mid = work.startMinute + work.minutes / 2
        listOf(33f, 38f, 42.5f, 46f).forEach { r ->
            val (x, y) = at(mid, r)
            assertEquals("raio $r", ClockHit.Segment(work.id), g.hitTest(x, y, data))
        }
        val (fx, fy) = at(23 * 60, 36f)
        assertEquals(ClockHit.FutureArea, g.hitTest(fx, fy, data))
        val (ox, oy) = at(9 * 60, 52f)
        assertEquals(ClockHit.None, g.hitTest(ox, oy, data))
    }

    @Test fun tinySegmentsGetTheirOwnTouchArea() {
        val day = SyntheticClockDays.build(SyntheticClockDays.Kind.MANY_SHORT_MOVES, LocalDate.of(2026, 10, 5), ZoneId.of("UTC"), LocalTime.of(20, 0))
        val data = DayClockAssembler.build(day.diary, day.date, day.zone, day.now)
        val tiny = data.segments.first { it !is ClockSegment.Unknown && g.arcPx(it.minutes.toFloat(), 1440) < 2f }
        val mid = tiny.startMinute + tiny.minutes / 2f
        val a = mid / 1440.0 * 2 * Math.PI
        val hit = g.hitTest((52 + 36.5 * kotlin.math.sin(a)).toFloat(), (52 - 36.5 * kotlin.math.cos(a)).toFloat(), data)
        assertTrue(hit is ClockHit.Segment)
    }
}

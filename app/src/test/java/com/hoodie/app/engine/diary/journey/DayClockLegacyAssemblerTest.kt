package com.hoodie.app.engine.diary.journey

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.journey.ClockArc
import com.hoodie.app.domain.diary.journey.ClockTick
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.V
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.at
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayClockLegacyAssemblerTest {
    private val zone = JourneyV3Fixtures.UTC

    @Test fun anglesFollowTheHourOfDayClockwiseFromTheTop() {
        val d = DayClockLegacyAssembler.day(JourneyV3Fixtures.DATE, zone)
        assertEquals(0f, d.deg(at(0)), 0.01f)
        assertEquals(90f, d.deg(at(6)), 0.01f)
        assertEquals(180f, d.deg(at(12)), 0.01f)
        assertEquals(270f, d.deg(at(18)), 0.01f)
        val data = DayClockLegacyAssembler.build(day(V("Casa", PlaceType.HOME, at(6), at(12))), zone)
        val arc = data.arcs.single { it.kind == ClockArc.Kind.STAY }
        assertEquals(90f, arc.startDeg, 0.01f); assertEquals(90f, arc.sweepDeg, 0.01f)
    }

    @Test fun shortStaysBecomeTicksAndAdjacentTicksMerge() {
        val data = DayClockLegacyAssembler.build(day(
            V("Casa", PlaceType.HOME, at(6), at(9)),
            V("Banca", PlaceType.STORE, at(9, 5), at(9, 9)),
            V("Farmácia", PlaceType.OTHER, at(9, 12), at(9, 16)),
            V("Trabalho", PlaceType.WORK, at(10), at(18)),
            V("Posto", PlaceType.OTHER, at(18, 30), at(18, 34)),
        ), zone)
        assertEquals("dois tiques: um ×2 e um individual", listOf(2, 1), data.ticks.map(ClockTick::count))
        assertTrue(data.ticks.first().grouped)
        assertTrue(data.arcs.none { it.stopId == "journey-1" || it.stopId == "journey-2" })
    }

    @Test fun onlyLongArcsGetLabelsAndCollidingOnesAreDroppedSmallestFirst() {
        val data = DayClockLegacyAssembler.build(day(
            V("Casa", PlaceType.HOME, at(0), at(7)),
            V("Trabalho", PlaceType.WORK, at(7, 10), at(9, 0)), // 27° colado na Casa
            V("Almoço", PlaceType.RESTAURANT, at(12), at(12, 40)),
            V("Escritório", PlaceType.WORK, at(13), at(19)),
        ), zone)
        val labelled = data.labels.map { it.text }
        assertTrue("Casa (maior) fica", "Casa" in labelled)
        assertTrue("Escritório fica", "Escritório" in labelled)
        assertTrue("Almoço (< 25°) não ganha rótulo", "Almoço" !in labelled)
        // Rótulos aceitos não se sobrepõem.
        val sorted = data.labels.sortedBy { it.deg }
        sorted.zipWithNext().forEach { (a, b) -> assertTrue(b.deg - a.deg > 8f) }
    }

    @Test fun shortAndLongDaysScaleProportionally() {
        val berlin = ZoneId.of("Europe/Berlin")
        val short = DayClockLegacyAssembler.day(LocalDate.of(2026, 3, 29), berlin)
        val long = DayClockLegacyAssembler.day(LocalDate.of(2026, 10, 25), berlin)
        assertEquals(23f, short.hours, 0.01f)
        assertEquals(25f, long.hours, 0.01f)
        // Meio do dia real = meio do anel.
        assertEquals(180f, short.deg(short.start + short.lengthMs / 2), 0.01f)
        assertEquals(360f, long.deg(long.end), 0.01f)
    }

    @Test fun hitPrefersTicksThenStays() {
        val data = DayClockLegacyAssembler.build(day(
            V("Casa", PlaceType.HOME, at(6), at(9)),
            V("Banca", PlaceType.STORE, at(9, 5), at(9, 9)),
            V("Trabalho", PlaceType.WORK, at(10), at(18)),
        ), zone)
        val tick = data.ticks.single()
        assertTrue(DayClockLegacyAssembler.hit(data, tick.deg) is ClockTick)
        assertEquals("journey-2", (DayClockLegacyAssembler.hit(data, 210f) as ClockArc).stopId)
    }
}

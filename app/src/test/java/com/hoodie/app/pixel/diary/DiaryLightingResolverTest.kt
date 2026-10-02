package com.hoodie.app.pixel.diary

import com.hoodie.app.core.time.DayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DiaryLightingResolverTest {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private fun at(h: Int) = LocalDate.of(2026, 10, 5).atTime(h, 30).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `horario vira periodo, tinta e luzes`() {
        assertEquals(DayPeriod.MORNING, DiaryLightingResolver.resolve(at(7), zone).period)
        val day = DiaryLightingResolver.resolve(at(12), zone)
        assertEquals(DayPeriod.DAY, day.period); assertNull(day.tint); assertFalse(day.lightsOn)
        val evening = DiaryLightingResolver.resolve(at(19), zone)
        assertEquals(DayPeriod.EVENING, evening.period); assertTrue(evening.lightsOn)
        val night = DiaryLightingResolver.resolve(at(22), zone)
        assertEquals(DayPeriod.NIGHT, night.period); assertTrue(night.lightsOn); assertTrue(night.tint!!.second > evening.tint!!.second)
    }

    @Test
    fun `sem horario e dia`() {
        assertEquals(DayPeriod.DAY, DiaryLightingResolver.resolve(null, zone).period)
    }

    @Test
    fun `postes acendem so a noite no render`() {
        val layout = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay)
        val lamp = DiaryMapRenderCache.create(layout).animatedTiles.first { it.second == Tile.LAMP }.first
        fun lit(period: DayPeriod) = DiaryMapRenderer.render(DiaryMapScene(layout, period = period))[lamp.col * 8 + 4, lamp.row * 8 + 1] == DiaryMapPalette.LAMP_LIGHT
        assertTrue(lit(DayPeriod.NIGHT))
        assertFalse(lit(DayPeriod.DAY))
    }
}

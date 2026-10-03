package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyLightingRendererTest {

    @Test
    fun `sem replay e dia claro, sem tinta`() {
        val l = JourneyLightingRenderer.resolve(null, JourneyTestFixtures.ZONE)
        assertEquals(JourneyLightingRenderer.DAYLIGHT, l)
        assertEquals(0, l.tintAlpha)
        assertFalse(l.lightsOn)
        assertEquals(DayPeriod.DAY, l.period)
    }

    @Test
    fun `manha, dia, entardecer e noite`() {
        assertEquals(DayPeriod.MORNING, JourneyLightingRenderer.at(7 * 60 + 30).period)
        assertEquals(28, JourneyLightingRenderer.at(7 * 60 + 30).tintAlpha)
        assertEquals(0, JourneyLightingRenderer.at(13 * 60).tintAlpha)
        val evening = JourneyLightingRenderer.at(19 * 60)
        assertEquals(DayPeriod.EVENING, evening.period); assertEquals(52, evening.tintAlpha); assertTrue(evening.lightsOn)
        val night = JourneyLightingRenderer.at(23 * 60)
        assertEquals(DayPeriod.NIGHT, night.period); assertEquals(120, night.tintAlpha); assertTrue(night.lightsOn)
        assertEquals(255, night.starAlpha)
        assertEquals(0, JourneyLightingRenderer.at(12 * 60).starAlpha)
    }

    @Test
    fun `transicao suave - a luz muda aos poucos, sem saltos`() {
        // Entre 17:00 (dia) e 18:30 (entardecer) a tinta sobe gradualmente.
        val ramp = (17 * 60..18 * 60 + 30 step 5).map { JourneyLightingRenderer.at(it).tintAlpha }
        assertTrue(ramp.zipWithNext().all { (a, b) -> b >= a && b - a <= 4 })
        assertTrue(ramp.first() == 0 && ramp.last() == 52)
        // Minuto a minuto o céu nunca pula mais que um degrau pequeno.
        (0 until 24 * 60 - 1).forEach { m ->
            val a = JourneyLightingRenderer.at(m).skyTop; val b = JourneyLightingRenderer.at(m + 1).skyTop
            val jump = maxOf(kotlin.math.abs((a shr 16 and 255) - (b shr 16 and 255)), kotlin.math.abs((a shr 8 and 255) - (b shr 8 and 255)), kotlin.math.abs((a and 255) - (b and 255)))
            assertTrue("salto de $jump em $m", jump <= 6)
        }
    }

    @Test
    fun `tinta so cobre o chao e escurece a noite`() {
        val b = PixelBuffer(10, JourneyLayoutEngine.HEADER + 10).apply { fill(0xFF808080.toInt()) }
        JourneyLightingRenderer.applyTint(b, JourneyLightingRenderer.at(23 * 60))
        assertEquals(0xFF808080.toInt(), b[5, 2])
        assertNotEquals(0xFF808080.toInt(), b[5, JourneyLayoutEngine.HEADER + 5])
        val lum = b[5, JourneyLayoutEngine.HEADER + 5].let { (it shr 16 and 255) + (it shr 8 and 255) + (it and 255) }
        assertTrue(lum < 3 * 0x80)
    }
}

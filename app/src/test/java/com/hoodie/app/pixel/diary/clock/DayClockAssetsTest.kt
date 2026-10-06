package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.pixel.diary.journey.JourneyPalette
import com.hoodie.app.pixel.diary.overworld.OverworldPalette
import com.hoodie.app.pixel.sprite.HoodiePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paleta, ícones, dígitos e Hoodie mini do Relógio do Dia 2.0. */
class DayClockAssetsTest {
    /** Cores já validadas no projeto: Hoodie (17), biomas/trilhas da Jornada e céu por período. */
    private val approved: Set<Int> = buildSet {
        addAll(HoodiePalette.ALL)
        addAll(listOf(OverworldPalette.GOLD, OverworldPalette.GOLD_DARK, OverworldPalette.DIRT, OverworldPalette.DIRT_DARK,
            OverworldPalette.PURPLE, OverworldPalette.PURPLE_DARK, OverworldPalette.GRASS, OverworldPalette.GRASS_TUFT,
            OverworldPalette.STONE, OverworldPalette.STONE_LIGHT))
        (MovementMode.entries + listOf(null)).forEach { add(OverworldPalette.trail(it).mark) }
        DayPeriod.entries.forEach { add(JourneyPalette.sky(it).first) }
    }

    @Test fun everyClockColorComesFromAnApprovedPalette() {
        DayClockPalette.ALL.forEach { assertTrue("#%08X não é de uma paleta validada".format(it), it in approved) }
        assertTrue("a base é a paleta de 17 cores do Hoodie", DayClockPalette.ALL.containsAll(HoodiePalette.ALL))
    }

    @Test fun categoriesHaveDistinctFillsAndADarkerEdge() {
        val fills = ClockCategory.entries.map { DayClockPalette.category(it).fill }
        assertEquals(fills.size, fills.toSet().size)
        ClockCategory.entries.forEach { c ->
            val f = DayClockPalette.category(c)
            assertTrue("$c: borda mais escura", luma(f.edge) < luma(f.fill))
        }
    }

    @Test fun iconsAreExactly7x7AndCoverEveryCategory() {
        assertEquals(ClockCategory.entries.toSet(), ClockIcons.ICONS.keys)
        ClockIcons.ICONS.forEach { (c, rows) ->
            assertEquals("$c", ClockIcons.SIZE, rows.size)
            rows.forEach { assertEquals("$c", ClockIcons.SIZE, it.length) }
        }
        assertEquals("ícones diferentes", ClockIcons.ICONS.size, ClockIcons.ICONS.values.toSet().size)
    }

    @Test fun digitsAreExactly3x5() {
        assertEquals(('0'..'9').toSet(), PixelDigits.GLYPHS.keys)
        PixelDigits.GLYPHS.values.forEach { rows ->
            assertEquals(PixelDigits.H, rows.size)
            rows.forEach { assertEquals(PixelDigits.W, it.length) }
        }
        assertEquals(7, PixelDigits.width("06"))
    }

    @Test fun miniHoodieFitsTheRingAndUsesItsPalette() {
        ClockHoodieMarker.Frame.entries.forEach { f ->
            ClockHoodieMarker.Ride.entries.forEach { r ->
                val s = ClockHoodieMarker.sprite(f, r, MovementMode.BUS)
                assertTrue(s.width in 9..12 && s.height in 9..12)
                s.pixels.filter { it ushr 24 != 0 }.forEach { assertTrue(it in DayClockPalette.ALL) }
            }
        }
        // Idle ≠ piscada ≠ andar.
        val idle = ClockHoodieMarker.sprite(ClockHoodieMarker.Frame.IDLE, ClockHoodieMarker.Ride.FOOT, null).pixels
        assertTrue(!idle.contentEquals(ClockHoodieMarker.sprite(ClockHoodieMarker.Frame.BLINK, ClockHoodieMarker.Ride.FOOT, null).pixels))
        assertTrue(!idle.contentEquals(ClockHoodieMarker.sprite(ClockHoodieMarker.Frame.WALK_A, ClockHoodieMarker.Ride.FOOT, null).pixels))
    }

    private fun luma(c: Int) = 0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF)
}

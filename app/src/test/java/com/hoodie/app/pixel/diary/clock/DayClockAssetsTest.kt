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
        DayClockPalette.DIAL.forEach { assertTrue("#%08X não é de uma paleta validada".format(it), it in approved) }
        assertTrue("a base é a paleta de 17 cores do Hoodie", DayClockPalette.DIAL.containsAll(HoodiePalette.ALL))
        // O Hoodie do mostrador é o marcador da Jornada: suas cores entram, nada além disso.
        assertEquals(DayClockPalette.DIAL + ClockHoodieMarker.COLORS, DayClockPalette.ALL)
    }

    @Test fun categoriesHaveDistinctFillsAndADarkerEdge() {
        val fills = ClockCategory.entries.map { DayClockPalette.category(it).fill }
        assertEquals(fills.size, fills.toSet().size)
        ClockCategory.entries.forEach { c ->
            val f = DayClockPalette.category(c)
            assertTrue("$c: borda mais escura", luma(f.edge) < luma(f.fill))
        }
    }

    @Test fun iconsAreExactly15x15AndCoverEveryCategory() {
        assertEquals(ClockCategory.entries.toSet(), ClockIcons.ICONS.keys)
        ClockIcons.ICONS.forEach { (c, rows) ->
            assertEquals("$c", ClockIcons.SIZE, rows.size)
            rows.forEach { assertEquals("$c", ClockIcons.SIZE, it.length) }
            assertTrue("$c usa só O/W/S", rows.joinToString("").all { it in ".OWS" })
        }
        assertEquals(15, ClockIcons.SIZE)
        assertEquals("ícones diferentes", ClockIcons.ICONS.size, ClockIcons.ICONS.values.toSet().size)
    }

    @Test fun digitsAreExactly5x7() {
        assertEquals(('0'..'9').toSet(), PixelDigits.GLYPHS.keys)
        PixelDigits.GLYPHS.values.forEach { rows ->
            assertEquals(PixelDigits.H, rows.size)
            rows.forEach { assertEquals(PixelDigits.W, it.length) }
        }
        assertEquals(7, PixelDigits.H)
        assertEquals(11, PixelDigits.width("06"))
    }

    @Test fun hoodieFitsTheThirtyPixelRingOnFootAndInEveryVehicle() {
        (MovementMode.entries + listOf(null)).forEach { mode ->
            listOf(true, false).forEach { moving ->
                val (w, h) = ClockHoodieMarker.bounds(ClockHoodieMarker.state(300f, 1440, mode, moving), 400)
                assertTrue("$mode/$moving: ${w}x$h", w in 8..30 && h in 8..30)
            }
        }
    }

    private fun luma(c: Int) = 0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF)
}

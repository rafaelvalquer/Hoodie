package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.pixel.diary.journey.JourneyPalette
import com.hoodie.app.pixel.diary.overworld.OverworldPalette
import com.hoodie.app.pixel.sprite.HoodiePalette

/**
 * Cores do Relógio do Dia 2.0. Paleta fechada e SEM cores novas: a base vem da
 * paleta de 17 cores do Hoodie; as categorias usam as cores dos biomas da Jornada,
 * os deslocamentos as cores de trilha da Jornada e o céu as cores de período da
 * iluminação. `DayClockPaletteTest` garante as duas coisas.
 */
object DayClockPalette {
    const val OUTLINE = HoodiePalette.OUTLINE
    const val DEEP = HoodiePalette.EYE
    const val TRACK = HoodiePalette.NOSE
    const val TRACK_MARK = HoodiePalette.HOOD_DARK
    const val PLATE = HoodiePalette.HOOD_LIGHT
    const val PLATE_RING = HoodiePalette.HOOD
    const val PLATE_EDGE = HoodiePalette.HOOD_SHADE
    const val TICK = HoodiePalette.HOOD_SHADE
    const val TICK_MAJOR = HoodiePalette.WHITE
    const val NOW = HoodiePalette.WHITE
    const val STAR = HoodiePalette.WHITE
    const val STAR_DIM = HoodiePalette.STRING
    const val DIGIT = HoodiePalette.WHITE
    const val ICON_LIGHT = HoodiePalette.WHITE
    const val SELECTION = OverworldPalette.GOLD
    const val GLOW = OverworldPalette.GOLD
    const val GLOW_DIM = OverworldPalette.GOLD_DARK
    /** Leito dos deslocamentos (a trilha de terra da Jornada). */
    const val PATH = OverworldPalette.DIRT
    const val PATH_DARK = OverworldPalette.DIRT_DARK

    data class Fill(val fill: Int, val edge: Int)

    fun category(c: ClockCategory): Fill = when (c) {
        ClockCategory.HOME -> Fill(HoodiePalette.BACKPACK, HoodiePalette.BACKPACK_DARK)
        ClockCategory.WORK -> Fill(HoodiePalette.FUR, HoodiePalette.INNER_EAR)
        ClockCategory.COMMUTE -> Fill(PATH, PATH_DARK)
        ClockCategory.MEAL -> Fill(HoodiePalette.BLUSH, HoodiePalette.TONGUE)
        ClockCategory.GYM -> Fill(OverworldPalette.PURPLE, OverworldPalette.PURPLE_DARK)
        ClockCategory.LEISURE -> Fill(OverworldPalette.GRASS, OverworldPalette.GRASS_TUFT)
        ClockCategory.OTHER -> Fill(OverworldPalette.STONE_LIGHT, OverworldPalette.STONE)
    }

    /** Cor da trilha por meio — a mesma da Jornada. */
    fun mode(m: MovementMode?): Int = OverworldPalette.trail(m).mark

    fun sky(p: DayPeriod): Int = JourneyPalette.sky(p).first

    val ALL: Set<Int> = buildSet {
        addAll(listOf(OUTLINE, DEEP, TRACK, TRACK_MARK, PLATE, PLATE_RING, PLATE_EDGE, TICK, TICK_MAJOR, NOW, STAR, STAR_DIM, DIGIT, ICON_LIGHT, SELECTION, GLOW, GLOW_DIM, PATH, PATH_DARK))
        ClockCategory.entries.forEach { add(category(it).fill); add(category(it).edge) }
        (MovementMode.entries + listOf(null)).forEach { add(mode(it)) }
        DayPeriod.entries.forEach { add(sky(it)) }
        addAll(HoodiePalette.ALL)
    }
}

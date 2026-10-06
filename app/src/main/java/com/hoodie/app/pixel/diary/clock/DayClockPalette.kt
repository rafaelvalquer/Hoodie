package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.pixel.diary.overworld.OverworldPalette

/** Cores do relógio do dia: a mesma família do overworld, uma cor por tipo de lugar. */
object DayClockPalette {
    const val PLAZA = 0xFFD8C39A.toInt()
    const val PLAZA_DARK = 0xFFB9A27A.toInt()
    const val RING = OverworldPalette.STONE
    const val RING_DARK = OverworldPalette.STONE_DARK
    const val RING_LIGHT = OverworldPalette.STONE_LIGHT
    const val HOUR = OverworldPalette.OUTLINE
    const val POINTER = OverworldPalette.GOLD
    const val GNOMON = OverworldPalette.WOOD_DARK

    fun biome(b: BiomeType?): Int = when (b) {
        BiomeType.HOUSE -> 0xFFE05D5D.toInt()
        BiomeType.OFFICE_CASTLE -> 0xFF5B86D8.toInt()
        BiomeType.TEMPLE -> 0xFFF0A13E.toInt()
        BiomeType.TAVERN -> 0xFFB07848.toInt()
        BiomeType.CAFE -> 0xFFE58AAE.toInt()
        BiomeType.WIZARD_TOWER -> 0xFF8E6BC4.toInt()
        BiomeType.MARKET -> 0xFF3FC9A6.toInt()
        BiomeType.PARK -> 0xFF4FB676.toInt()
        BiomeType.FAMILY_LODGE -> 0xFFF2CF5B.toInt()
        BiomeType.MILESTONE -> OverworldPalette.STONE_LIGHT
        BiomeType.CAMP, null -> 0xFFC9B79A.toInt()
    }
}

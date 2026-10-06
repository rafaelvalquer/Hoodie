package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Ícones 7×7 das paradas, desenhados no meio do arco (O = contorno, W = claro). */
object ClockIcons {
    const val SIZE = 7

    val ICONS: Map<ClockCategory, List<String>> = mapOf(
        ClockCategory.HOME to listOf(
            "...O...",
            "..OWO..",
            ".OWWWO.",
            "OWWWWWO",
            ".OWOWO.",
            ".OWOWO.",
            ".OOOOO.",
        ),
        ClockCategory.WORK to listOf(
            "..OOO..",
            ".OO.OO.",
            "OOOOOOO",
            "OWWWWWO",
            "OWWOWWO",
            "OWWWWWO",
            "OOOOOOO",
        ),
        ClockCategory.MEAL to listOf(
            ".O.O.O.",
            ".O.O.O.",
            ".......",
            "OOOOOOO",
            "OWWWWWO",
            ".OWWWO.",
            "..OOO..",
        ),
        ClockCategory.GYM to listOf(
            ".......",
            "OO...OO",
            "OWO.OWO",
            "OWOOOWO",
            "OWO.OWO",
            "OO...OO",
            ".......",
        ),
        ClockCategory.LEISURE to listOf(
            "..OOO..",
            ".OWWWO.",
            "OWWWWWO",
            "OWWWWWO",
            ".OOOOO.",
            "...O...",
            "..OOO..",
        ),
        ClockCategory.OTHER to listOf(
            "..OOO..",
            ".OWWWO.",
            ".OWOWO.",
            ".OWWWO.",
            "..OWO..",
            "..OWO..",
            "...O...",
        ),
        ClockCategory.COMMUTE to listOf(
            ".......",
            ".OOOOO.",
            "OWWWWWO",
            "OWWWWWO",
            "OOOOOOO",
            ".O...O.",
            ".......",
        ),
    )

    /** Desenha com o canto superior esquerdo em ([x], [y]). */
    fun draw(b: PixelBuffer, c: ClockCategory, x: Int, y: Int) {
        ICONS.getValue(c).forEachIndexed { row, line ->
            line.forEachIndexed { col, p ->
                when (p) {
                    'O' -> b.set(x + col, y + row, DayClockPalette.OUTLINE)
                    'W' -> b.set(x + col, y + row, DayClockPalette.ICON_LIGHT)
                }
            }
        }
    }
}

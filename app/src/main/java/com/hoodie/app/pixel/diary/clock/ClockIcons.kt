package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Ícones 15×15 das paradas, desenhados no meio do arco.
 * O = contorno, W = claro, S = sombra (todas cores da paleta do relógio).
 */
object ClockIcons {
    const val SIZE = 15

    val ICONS: Map<ClockCategory, List<String>> = mapOf(
        ClockCategory.HOME to listOf(
            ".......O.......",
            "......OWO......",
            ".....OWWWO.....",
            "....OWWWWWO....",
            "...OWWWWWWWO...",
            "..OWWWWWWWWWO..",
            ".OOOOOOOOOOOOO.",
            "..OWWWWWWWWWO..",
            "..OWOOWWWOOOO..",
            "..OWOSWWWOSSO..",
            "..OWOOWWWOSSO..",
            "..OWWWWWWOSSO..",
            "..OSSSSSSOSSO..",
            "..OOOOOOOOOOO..",
            "...............",
        ),
        ClockCategory.WORK to listOf(
            "...............",
            ".....OOOOO.....",
            ".....O...O.....",
            ".OOOOOOOOOOOOO.",
            ".OWWWWWWWWWWWO.",
            ".OWWWWWWWWWWWO.",
            ".OSSSSSOSSSSSO.",
            ".OOOOOOWOOOOOO.",
            ".OWWWWWOWWWWWO.",
            ".OWWWWWWWWWWWO.",
            ".OWWWWWWWWWWWO.",
            ".OSSSSSSSSSSSO.",
            ".OOOOOOOOOOOOO.",
            "...............",
            "...............",
        ),
        ClockCategory.MEAL to listOf(
            "...O...O...O...",
            "..O...O...O....",
            "...O...O...O...",
            "..O...O...O....",
            "...............",
            "OOOOOOOOOOOOOOO",
            "OWWWWWWWWWWWWWO",
            "OWWWWWWWWWWWWWO",
            ".OWWWWWWWWWWWO.",
            ".OSWWWWWWWWWSO.",
            "..OSSWWWWWSSO..",
            "...OOSSSSSOO...",
            "....OOOOOOO....",
            ".....OOOOO.....",
            "...............",
        ),
        ClockCategory.GYM to listOf(
            "...............",
            "...............",
            ".OOO.......OOO.",
            ".OWO.......OWO.",
            "OOWOO.....OOWOO",
            "OWWWO.....OWWWO",
            "OWWWOOOOOOOWWWO",
            "OWWWWWWWWWWWWWO",
            "OWWWOOOOOOOWWWO",
            "OWWWO.....OWWWO",
            "OOSOO.....OOSOO",
            ".OSO.......OSO.",
            ".OOO.......OOO.",
            "...............",
            "...............",
        ),
        ClockCategory.LEISURE to listOf(
            ".....OOOOO.....",
            "...OOWWWWWOO...",
            "..OWWWWWWWWWO..",
            ".OWWWWWWWWWWWO.",
            ".OWWWWWWWWWSWO.",
            "OWWWWWWWWWWSWWO",
            "OWSWWWWWWWSSWWO",
            ".OWSSWWWWSSWWO.",
            "..OOSSSSSSSOO..",
            "....OOOOOOO....",
            "......OSO......",
            "......OSO......",
            "......OSO......",
            "....OOOOOOO....",
            "...............",
        ),
        ClockCategory.OTHER to listOf(
            ".....OOOOO.....",
            "....OWWWWWO....",
            "...OWWWWWWWO...",
            "..OWWWOOOWWWO..",
            "..OWWO...OWWO..",
            "..OWWO...OWWO..",
            "..OWWWOOOWWWO..",
            "...OWWWWWWWO...",
            "...OSWWWWWSO...",
            "....OSWWWSO....",
            ".....OSWSO.....",
            "......OSO......",
            ".......O.......",
            "...............",
            "...............",
        ),
        ClockCategory.COMMUTE to listOf(
            "...............",
            "..OOOOOOOOOOO..",
            ".OWWWWWWWWWWWO.",
            ".OWOOOOOOOOOWO.",
            ".OWOSSSOSSSOWO.",
            ".OWOSSSOSSSOWO.",
            ".OWOOOOOOOOOWO.",
            ".OWWWWWWWWWWWO.",
            ".OWWWWWWWWWWWO.",
            ".OOWWWWWWWWWOO.",
            ".OWWOOOOOOOWWO.",
            ".OOOOOOOOOOOOO.",
            "..OOO.....OOO..",
            "...............",
            "...............",
        ),
    )

    /** Desenha com o canto superior esquerdo em ([x], [y]). */
    fun draw(b: PixelBuffer, c: ClockCategory, x: Int, y: Int) {
        ICONS.getValue(c).forEachIndexed { row, line ->
            line.forEachIndexed { col, p ->
                when (p) {
                    'O' -> b.set(x + col, y + row, DayClockPalette.OUTLINE)
                    'W' -> b.set(x + col, y + row, DayClockPalette.ICON_LIGHT)
                    'S' -> b.set(x + col, y + row, DayClockPalette.ICON_SHADE)
                }
            }
        }
    }
}

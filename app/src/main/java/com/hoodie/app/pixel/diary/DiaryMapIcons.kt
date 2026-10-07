package com.hoodie.app.pixel.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Ícones 7×7 da placa de cada prédio ('#' = contorno, 'o' = cor do ícone). */
object DiaryMapIcons {
    internal val ICONS: Map<PlaceType, List<String>> = mapOf(
        PlaceType.HOME to listOf(
            "...#...",
            "..#o#..",
            ".#ooo#.",
            "#ooooo#",
            ".o#o#o.",
            ".o#o#o.",
            ".ooooo.",
        ),
        PlaceType.WORK to listOf(
            "..###..",
            "..#.#..",
            "#######",
            "#ooooo#",
            "#oo#oo#",
            "#ooooo#",
            "#######",
        ),
        PlaceType.RESTAURANT to listOf(
            "#.#..#.",
            "#.#.##.",
            "#.#.##.",
            "###.##.",
            ".#...#.",
            ".#...#.",
            ".#...#.",
        ),
        PlaceType.GYM to listOf(
            ".......",
            "#.....#",
            "##...##",
            "#######",
            "##...##",
            "#.....#",
            ".......",
        ),
        PlaceType.MARKET to listOf(
            "#......",
            ".######",
            ".#oooo#",
            ".#oooo#",
            "..####.",
            "..#..#.",
            ".......",
        ),
        PlaceType.STORE to listOf(
            "#######",
            "#o#o#o#",
            "#ooooo#",
            "#o#o#o#",
            "#o#o#o#",
            "#ooooo#",
            "#######",
        ),
        PlaceType.SCHOOL to listOf(
            ".......",
            "###.###",
            "#oo#oo#",
            "#oo#oo#",
            "#oo#oo#",
            "#######",
            ".......",
        ),
        PlaceType.LEISURE to listOf(
            "...#...",
            "..#o#..",
            "##ooo##",
            ".#ooo#.",
            ".#o#o#.",
            "#.#.#.#",
            ".......",
        ),
        PlaceType.FAMILY to listOf(
            ".......",
            ".##.##.",
            "#oo#oo#",
            "#ooooo#",
            ".#ooo#.",
            "..#o#..",
            "...#...",
        ),
        PlaceType.OTHER to listOf(
            "..###..",
            ".#ooo#.",
            ".#o#o#.",
            ".#ooo#.",
            "..#o#..",
            "..#o#..",
            "...#...",
        ),
    )

    const val SIZE = 7

    fun draw(b: PixelBuffer, type: PlaceType, x: Int, y: Int, ink: Int, fill: Int) {
        ICONS.getValue(type).forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                when (ch) {
                    '#' -> b.set(x + col, y + row, ink)
                    'o' -> b.set(x + col, y + row, fill)
                }
            }
        }
    }
}

package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.pixel.renderer.PixelBuffer

/** Dígitos 5×7 das plaquinhas 00/06/12/18. */
object PixelDigits {
    const val W = 5
    const val H = 7

    val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
    )

    fun width(text: String) = if (text.isEmpty()) 0 else text.length * (W + 1) - 1

    fun draw(b: PixelBuffer, text: String, x: Int, y: Int, color: Int) {
        text.forEachIndexed { i, c ->
            GLYPHS[c]?.forEachIndexed { row, line ->
                line.forEachIndexed { col, p -> if (p == '#') b.set(x + i * (W + 1) + col, y + row, color) }
            }
        }
    }
}

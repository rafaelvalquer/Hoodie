package com.hoodie.app.pixel.review

import com.hoodie.app.pixel.character.CharacterFrame
import com.hoodie.app.pixel.renderer.PixelBuffer

internal object ReviewRaster {
    const val BG = 0xFF2B2E4A.toInt()
    const val GRID = 0xFF454B70.toInt()
    const val GOLD = 0xFFF1C66D.toInt()
    const val CYAN = 0xFF62D3CF.toInt()
    const val PINK = 0xFFE86A9B.toInt()

    private val font = mapOf(
        'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"), 'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
        'C' to listOf(".##", "#..", "#..", "#..", ".##"), 'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
        'E' to listOf("###", "#..", "##.", "#..", "###"), 'F' to listOf("###", "#..", "##.", "#..", "#.."),
        'G' to listOf(".##", "#..", "#.#", "#.#", ".##"), 'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
        'I' to listOf("###", ".#.", ".#.", ".#.", "###"), 'J' to listOf("..#", "..#", "..#", "#.#", ".#."),
        'K' to listOf("#.#", "##.", "#..", "##.", "#.#"), 'L' to listOf("#..", "#..", "#..", "#..", "###"),
        'M' to listOf("#.#", "###", "###", "#.#", "#.#"), 'N' to listOf("#.#", "###", "###", "###", "#.#"),
        'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."), 'P' to listOf("##.", "#.#", "##.", "#..", "#.."),
        'Q' to listOf(".#.", "#.#", "#.#", ".##", "..#"), 'R' to listOf("##.", "#.#", "##.", "##.", "#.#"),
        'S' to listOf(".##", "#..", ".#.", "..#", "##."), 'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
        'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"), 'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."),
        'W' to listOf("#.#", "#.#", "###", "###", "#.#"), 'X' to listOf("#.#", "#.#", ".#.", "#.#", "#.#"),
        'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."), 'Z' to listOf("###", "..#", ".#.", "#..", "###"),
        '0' to listOf("###", "#.#", "#.#", "#.#", "###"), '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
        '2' to listOf("##.", "..#", ".#.", "#..", "###"), '3' to listOf("##.", "..#", ".#.", "..#", "##."),
        '4' to listOf("#.#", "#.#", "###", "..#", "..#"), '5' to listOf("###", "#..", "##.", "..#", "##."),
        '6' to listOf(".##", "#..", "###", "#.#", ".#."), '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
        '8' to listOf(".#.", "#.#", ".#.", "#.#", ".#."), '9' to listOf(".#.", "#.#", "###", "..#", "##."),
        '.' to listOf("...", "...", "...", "...", ".#."), '-' to listOf("...", "...", "###", "...", "..."),
        '%' to listOf("#.#", "..#", ".#.", "#..", "#.#"), ':' to listOf("...", ".#.", "...", ".#.", "..."),
    )

    fun text(buffer: PixelBuffer, value: String, x: Int, y: Int, color: Int = GOLD, scale: Int = 1) {
        var cursor = x
        value.uppercase().forEach { ch ->
            val glyph = font[ch]
            if (glyph == null) cursor += 4 * scale
            else {
                glyph.forEachIndexed { yy, row -> row.forEachIndexed { xx, p -> if (p == '#') buffer.box(cursor + xx * scale, y + yy * scale, cursor + (xx + 1) * scale - 1, y + (yy + 1) * scale - 1, color) } }
                cursor += 4 * scale
            }
        }
    }

    fun framedCell(out: PixelBuffer, frame: CharacterFrame, x: Int, y: Int, label: String? = null, ground: Boolean = true) {
        if (label != null) text(out, label, x + 2, y, scale = 1)
        val top = y + if (label == null) 0 else 7
        out.blit(frame.image, x + 2, top)
        if (ground) out.hline(x + 1, x + 49, top + frame.anchors.feet.y, CYAN)
    }

    fun newSheet(columns: Int, rows: Int, cellWidth: Int = 52, cellHeight: Int = 84, left: Int = 0, header: Int = 14): PixelBuffer =
        PixelBuffer(left + columns * cellWidth + 2, header + rows * cellHeight + 2).also { it.fill(BG) }

    fun divider(buffer: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int) {
        if (y0 == y1) buffer.hline(x0, x1, y0, GRID) else buffer.vline(x0, y0, y1, GRID)
    }
}

package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Balões ficam fora do painter corporal e são posicionados pela âncora de cabeça. */
object NpcSpeechBubbleRenderer {
    private const val INK = 0xFF1A1C33.toInt()
    private const val PAPER = 0xFFFFFEF8.toInt()

    fun draw(
        buffer: PixelBuffer,
        profile: NpcSpeechProfile,
        centerX: Int,
        floorY: Int,
        seed: Int,
        timeMs: Long,
        speechElapsedMs: Long,
        headAnchorY: Int,
        headHeight: Int,
    ) {
        if (profile.lines.isEmpty() || profile.cycleMs <= 0 || profile.visibleMs <= 0) return
        if (speechElapsedMs < 0 || speechElapsedMs >= profile.visibleMs) return
        val index = Math.floorMod((timeMs / profile.cycleMs + seed).toInt(), profile.lines.size)
        val message = java.text.Normalizer.normalize(profile.lines[index].uppercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").take(22)
        val width = (message.length * 4 + 8).coerceIn(20, 96)
        val left = (centerX - width / 2).coerceIn(2, (buffer.width - width - 2).coerceAtLeast(2))
        // headAnchorY is the head center; use each species' dimensions to find its top edge.
        val headTopY = floorY - CharacterCanvas.FEET.y + headAnchorY - headHeight.coerceAtLeast(1) / 2
        val top = (headTopY - BUBBLE_GAP - 12).coerceIn(2, buffer.height - 17)
        buffer.outlined(left, top, left + width, top + 12, PAPER, INK)
        val tailX = centerX.coerceIn(left + 2, left + width - 2)
        buffer.line(tailX, top + 12, tailX - 2, top + 15, INK)
        drawText(buffer, message, left + 4, top + 4)
    }

    private fun drawText(buffer: PixelBuffer, text: String, x: Int, y: Int) {
        text.forEachIndexed { index, char ->
            val glyph = GLYPHS[char] ?: GLYPHS[' ']!!
            glyph.forEachIndexed { row, bits -> bits.forEachIndexed { col, pixel ->
                if (pixel == '#') buffer.set(x + index * 4 + col, y + row, INK)
            } }
        }
    }

    private const val BUBBLE_GAP = 5

    private val GLYPHS = mapOf(
        'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"), 'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
        'C' to listOf(".##", "#..", "#..", "#..", ".##"), 'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
        'E' to listOf("###", "#..", "##.", "#..", "###"), 'F' to listOf("###", "#..", "##.", "#..", "#.."),
        'G' to listOf(".##", "#..", "#.#", "#.#", ".##"), 'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
        'I' to listOf("###", ".#.", ".#.", ".#.", "###"), 'J' to listOf("..#", "..#", "..#", "#.#", ".#."),
        'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"), 'L' to listOf("#..", "#..", "#..", "#..", "###"),
        'M' to listOf("#.#", "###", "###", "#.#", "#.#"), 'N' to listOf("#.#", "###", "###", "###", "#.#"),
        'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."), 'P' to listOf("##.", "#.#", "##.", "#..", "#.."),
        'Q' to listOf(".#.", "#.#", "#.#", ".#.", "..#"),
        'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"), 'S' to listOf(".##", "#..", ".#.", "..#", "##."),
        'T' to listOf("###", ".#.", ".#.", ".#.", ".#."), 'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"),
        'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."), 'W' to listOf("#.#", "#.#", "###", "###", "#.#"),
        'X' to listOf("#.#", "#.#", ".#.", "#.#", "#.#"), 'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."),
        'Z' to listOf("###", "..#", ".#.", "#..", "###"),
        '0' to listOf("###", "#.#", "#.#", "#.#", "###"), '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
        '2' to listOf("##.", "..#", ".#.", "#..", "###"), '3' to listOf("##.", "..#", ".#.", "..#", "##."),
        '4' to listOf("#.#", "#.#", "###", "..#", "..#"), '5' to listOf("###", "#..", "##.", "..#", "##."),
        '6' to listOf(".##", "#..", "###", "#.#", ".#."), '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
        '8' to listOf(".#.", "#.#", ".#.", "#.#", ".#."), '9' to listOf(".#.", "#.#", "###", "..#", "##."),
        ',' to listOf("...", "...", "...", ".#.", "#.."),
        '.' to listOf("...", "...", "...", "...", ".#."), ' ' to listOf("...", "...", "...", "...", "..."),
    )
}

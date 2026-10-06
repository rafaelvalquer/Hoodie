package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.pixel.diary.journey.JourneyRect
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Moldura de HUD do capítulo aberto (cantos de pedra) e os portais: a trilha sai por
 * um degrau com seta na borda de baixo e entra pela borda de cima do capítulo seguinte.
 */
object ChapterFramePainter {

    fun paintFrame(b: PixelBuffer) {
        val w = b.width; val h = b.height
        b.hline(0, w - 1, 0, OverworldPalette.OUTLINE); b.hline(0, w - 1, h - 1, OverworldPalette.OUTLINE)
        b.vline(0, 0, h - 1, OverworldPalette.OUTLINE); b.vline(w - 1, 0, h - 1, OverworldPalette.OUTLINE)
        for ((x, y) in listOf(0 to 0, w - 6 to 0, 0 to h - 6, w - 6 to h - 6)) {
            b.outlined(x, y, x + 5, y + 5, OverworldPalette.STONE, OverworldPalette.OUTLINE)
            b.set(x + 2, y + 2, OverworldPalette.STONE_LIGHT)
        }
    }

    /** Portal de saída: degraus de pedra e seta descendo (anima no replay). */
    fun paintExit(b: PixelBuffer, r: JourneyRect, timeMs: Long) {
        b.outlined(r.x, r.y, r.right - 1, r.bottom - 1, OverworldPalette.STONE, OverworldPalette.OUTLINE)
        b.hline(r.x + 1, r.right - 2, r.y + 3, OverworldPalette.STONE_DARK)
        arrow(b, r.x + r.w / 2, r.y - 6 + bob(timeMs))
    }

    /** Portal de entrada: arco no topo com a seta vindo para dentro. */
    fun paintEntry(b: PixelBuffer, r: JourneyRect, timeMs: Long) {
        b.outlined(r.x, r.y, r.right - 1, r.bottom - 1, OverworldPalette.STONE, OverworldPalette.OUTLINE)
        b.hline(r.x + 1, r.right - 2, r.bottom - 3, OverworldPalette.STONE_DARK)
        arrow(b, r.x + r.w / 2, r.bottom + 2 + bob(timeMs))
    }

    private fun bob(timeMs: Long) = ((timeMs / 300) % 2).toInt()

    private fun arrow(b: PixelBuffer, cx: Int, y: Int) {
        b.box(cx - 1, y, cx + 1, y + 2, OverworldPalette.ARROW)
        b.hline(cx - 3, cx + 3, y + 3, OverworldPalette.ARROW)
        b.hline(cx - 2, cx + 2, y + 4, OverworldPalette.ARROW)
        b.set(cx, y + 5, OverworldPalette.ARROW)
        b.set(cx - 4, y + 3, OverworldPalette.OUTLINE); b.set(cx + 4, y + 3, OverworldPalette.OUTLINE)
    }
}

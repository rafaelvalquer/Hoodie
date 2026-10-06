package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.engine.diary.journey.SignSide
import com.hoodie.app.pixel.diary.journey.JourneyRect
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Plaquinha de madeira da parada. O texto (nome + chegada) é Compose por cima, com a
 * Press Start 2P, posicionado pelo mesmo viewport; aqui fica a madeira, o poste e o
 * selo de retorno ("2", "3"… escrito pela UI).
 */
object SignpostPainter {
    /** Raio do selo de retorno, no canto superior direito. */
    const val BADGE_R = 4

    fun paint(b: PixelBuffer, r: JourneyRect, side: SignSide, state: BiomeState, returnBadge: Boolean) {
        fun c(color: Int) = OverworldPalette.state(color, state)
        // Poste(s) até o chão.
        if (side == SignSide.BELOW) {
            b.vline(r.x + r.w / 2, r.y - 4, r.y, c(OverworldPalette.SIGN_DARK))
        } else {
            b.vline(r.x + 4, r.bottom, r.bottom + 4, c(OverworldPalette.SIGN_DARK))
            b.vline(r.right - 5, r.bottom, r.bottom + 4, c(OverworldPalette.SIGN_DARK))
        }
        // A parada atual ganha contorno dourado; as outras, o contorno escuro da paleta.
        val border = if (state == BiomeState.CURRENT) OverworldPalette.GOLD else c(OverworldPalette.OUTLINE)
        b.outlined(r.x, r.y, r.right - 1, r.bottom - 1, c(OverworldPalette.SIGN), border)
        b.hline(r.x + 1, r.right - 2, r.y + 1, c(OverworldPalette.SIGN_LIGHT))
        b.hline(r.x + 1, r.right - 2, r.bottom - 2, c(OverworldPalette.SIGN_DARK))
        b.set(r.x + 2, r.y + 2, c(OverworldPalette.OUTLINE)); b.set(r.right - 3, r.y + 2, c(OverworldPalette.OUTLINE))
        if (returnBadge) {
            val bx = r.right - 2; val by = r.y + 1
            b.disc(bx, by, BADGE_R, OverworldPalette.OUTLINE)
            b.disc(bx, by, BADGE_R - 1, c(OverworldPalette.GOLD))
        }
    }

    /** Centro do selo (para o número em Compose). */
    fun badgeCenter(r: JourneyRect) = (r.right - 2) to (r.y + 1)
}

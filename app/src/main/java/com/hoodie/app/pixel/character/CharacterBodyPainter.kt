package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.procedural.R

/** Anatomia que independe da espécie e da roupa para os personagens do Character System. */
internal object CharacterBodyPainter {
    fun drawNeck(buffer: PixelBuffer, palette: CharacterPalette, centerX: Int, top: Int, bottom: Int) {
        if (bottom < top) return
        buffer.outlined(centerX - 6, top, centerX + 6, bottom, palette.fur, palette.outline)
        buffer.vline(centerX - 3, top + 1, bottom - 1, palette.furLight)
        buffer.vline(centerX + 4, top + 1, bottom - 1, palette.furDark)
    }

    fun drawMammalHand(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int) {
        CharacterLimbPainter.drawShape(buffer, R(x - 3, y - 3, x + 2, y + 2, r = 2), palette.fur, palette.outline)
        buffer.box(x - 2, y - 2, x + 1, y - 2, palette.furLight)
        buffer.set(x, y + 1, palette.furDark)
    }

    fun drawMammalFoot(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int, farSide: Boolean) {
        // Mantém a pata arredondada e alta o bastante para ler como extremidade,
        // não como uma linha sob a perna. O pé distante fica um tom mais escuro.
        val fill = if (farSide) palette.furDark else palette.fur
        val paw = R(x - 5, y - 5, x + 5, y, r = 2)
        CharacterLimbPainter.drawShape(buffer, paw, fill, palette.outline)
        buffer.hline(x - 3, x - 1, y - 4, palette.furLight)
        buffer.hline(x - 3, x + 3, y - 1, palette.furDark)
        buffer.set(x - 2, y - 1, palette.outline)
        buffer.set(x + 2, y - 1, palette.outline)
    }
}

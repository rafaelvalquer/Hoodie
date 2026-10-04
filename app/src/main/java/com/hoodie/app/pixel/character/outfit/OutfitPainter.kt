package com.hoodie.app.pixel.character.outfit

import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterLimbPainter
import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Point

data class OutfitLegColors(val front: Int, val back: Int)

/** Roupa é uma camada independente: a anatomia e os anchors permanecem invariáveis. */
object OutfitPainter {
    fun drawTorso(
        b: PixelBuffer,
        style: OutfitStyle,
        p: CharacterPalette,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        facing: Facing = Facing.FRONT,
    ) {
        if (facing == Facing.BACK) {
            drawBack(b, style, p, left, top, right, bottom)
            return
        }
        if (facing == Facing.SIDE) {
            drawSide(b, style, p, left, top, right, bottom)
            return
        }
        when (style) {
            OutfitStyle.Suit -> drawSuit(b, p, left, top, right, bottom)
            OutfitStyle.Hoodie -> drawHoodie(b, p, left, top, right, bottom)
            OutfitStyle.Student -> drawStudent(b, p, left, top, right, bottom)
            OutfitStyle.Sport -> drawSport(b, p, left, top, right, bottom)
            OutfitStyle.Commuter -> drawCommuter(b, p, left, top, right, bottom)
            OutfitStyle.Casual -> drawCasual(b, p, left, top, right, bottom)
        }
    }

    /** Perfil com silhueta enxuta e construção lateral, sem camisa ou gravata de frente. */
    private fun drawSide(b: PixelBuffer, style: OutfitStyle, p: CharacterPalette, l: Int, t: Int, r: Int, bot: Int) {
        val fill = if (style == OutfitStyle.Commuter) p.outfitDark else p.outfit
        val shade = if (style == OutfitStyle.Commuter) p.outfit else p.outfitDark
        torso(b, l, t, r, bot, fill, shade, p.outline)
        when (style) {
            OutfitStyle.Suit -> {
                b.line(l + 3, t + 3, r - 3, t + 8, p.outfitLight)
                b.vline(r - 4, t + 8, bot - 4, p.outfitDark)
                b.set(r - 5, t + 14, p.outfitLight)
            }
            OutfitStyle.Hoodie -> {
                b.outlined(l + 2, t - 2, l + 9, t + 5, p.outfitLight, p.outline)
                b.box(l + 4, t, l + 7, t + 3, p.outfitDark)
                b.vline(r - 4, t + 6, bot - 4, p.outfitDark)
            }
            OutfitStyle.Student -> {
                // Mochila aparece atrás do ombro; a camisa e a gravata ficam de perfil.
                b.outlined(l - 3, t + 5, l + 3, bot - 3, p.outfitDark, p.outline)
                b.vline(l, t + 7, bot - 5, p.outfitLight)
                b.line(l + 2, t + 3, r - 3, t + 7, p.accent)
            }
            OutfitStyle.Sport -> {
                b.vline(r - 4, t + 3, bot - 3, p.outfitLight)
                b.hline(l + 3, r - 3, bot - 4, p.accent)
            }
            OutfitStyle.Commuter -> {
                b.line(l + 3, t + 3, r - 4, t + 13, p.outfitLight)
                b.vline(r - 4, t + 14, bot - 4, p.accent)
            }
            OutfitStyle.Casual -> {
                b.vline(r - 4, t + 4, bot - 3, p.outfitLight)
                b.hline(l + 3, r - 4, t + 12, p.outfitDark)
            }
        }
    }

    /** Costas mostram tecido, costuras e painéis traseiros, sem repetir gravata ou bolso frontal. */
    private fun drawBack(b: PixelBuffer, style: OutfitStyle, p: CharacterPalette, l: Int, t: Int, r: Int, bot: Int) {
        val fill = if (style == OutfitStyle.Commuter) p.outfitDark else p.outfit
        val shade = if (style == OutfitStyle.Commuter) p.outfit else p.outfitDark
        torso(b, l, t, r, bot, fill, shade, p.outline)
        val mid = (l + r) / 2
        when (style) {
            OutfitStyle.Suit -> {
                b.hline(l + 5, r - 5, t + 4, p.outfitLight)
                b.vline(mid, t + 5, bot - 4, p.outfitDark)
                b.set(mid, t + 3, p.outfitDark)
            }
            OutfitStyle.Hoodie -> {
                // O capuz visto por trás fica na nuca; os cordões e o bolso pertencem à frente.
                b.outlined(mid - 7, t - 3, mid + 7, t + 5, p.outfitLight, p.outline)
                b.box(mid - 5, t, mid + 5, t + 4, p.outfitDark)
                b.vline(mid, t + 8, bot - 3, p.outfitDark)
            }
            OutfitStyle.Commuter -> {
                b.hline(l + 4, r - 4, t + 5, p.outfitLight)
                b.vline(mid, t + 6, bot - 4, p.outfitDark)
            }
            OutfitStyle.Student -> {
                // Mochila: painel central e duas alças visíveis sobre os ombros.
                b.line(l + 4, t + 2, l + 6, bot - 3, p.accent)
                b.line(r - 4, t + 2, r - 6, bot - 3, p.accent)
                b.outlined(mid - 5, t + 7, mid + 5, bot - 3, p.outfitDark, p.outline)
                b.vline(mid, t + 9, bot - 5, p.outfitLight)
            }
            OutfitStyle.Sport -> {
                b.vline(mid, t + 3, bot - 3, p.outfitLight)
                b.hline(l + 5, r - 5, bot - 4, p.accent)
            }
            OutfitStyle.Casual -> {
                b.vline(mid, t + 4, bot - 3, p.outfitLight)
                b.hline(mid + 3, r - 4, t + 11, p.outfitDark)
            }
        }
    }

    /** A roupa decide se as pernas recebem calça ou se permanecem como anatomia aparente. */
    fun legColors(style: OutfitStyle, p: CharacterPalette): OutfitLegColors =
        if (style == OutfitStyle.Suit) OutfitLegColors(p.outfit, p.outfitDark)
        else OutfitLegColors(p.fur, p.furDark)

    /** Calçados fazem parte do figurino, mantendo as patas compartilhadas como base anatômica. */
    fun drawFootwear(
        b: PixelBuffer,
        style: OutfitStyle,
        p: CharacterPalette,
        x: Int,
        y: Int,
        farSide: Boolean,
        facing: Facing,
    ) {
        if (style != OutfitStyle.Suit) return
        val toeDirection = if (facing == Facing.SIDE) 1 else if (farSide) 1 else -1
        b.outlined(x - 5, y - 4, x + 5, y, p.outfitDark, p.outline)
        b.hline(x - 3, x + 1, y - 3, p.outfit)
        b.set(x + toeDirection * 4, y - 2, p.outfitLight)
        b.hline(x - 3, x + 3, y, p.outline)
    }

    /** Mangas e acabamento pertencem à roupa; as coordenadas dos anchors vêm do corpo. */
    fun drawSleeves(
        b: PixelBuffer,
        p: CharacterPalette,
        bodyLeft: Int,
        bodyRight: Int,
        bodyTop: Int,
        leftHand: Point,
        rightHand: Point,
    ) {
        val halfWidth = (bodyRight - bodyLeft) / 2
        CharacterLimbPainter.drawSegment(b, CharacterCanvas.CENTER_X - halfWidth + 2, bodyTop + 5, leftHand.x, leftHand.y, p.outfit, p.outline, 6)
        CharacterLimbPainter.drawSegment(b, CharacterCanvas.CENTER_X - halfWidth + 3, bodyTop + 6, leftHand.x, leftHand.y - 1, p.outfitLight, p.outfit, 3)
        CharacterLimbPainter.drawSegment(b, CharacterCanvas.CENTER_X + halfWidth - 2, bodyTop + 5, rightHand.x, rightHand.y, p.outfit, p.outline, 6)
        CharacterLimbPainter.drawSegment(b, CharacterCanvas.CENTER_X + halfWidth - 3, bodyTop + 6, rightHand.x, rightHand.y - 1, p.outfitLight, p.outfit, 3)
    }

    fun drawSideSleeve(b: PixelBuffer, p: CharacterPalette, shoulderX: Int, bodyTop: Int, hand: Point) {
        CharacterLimbPainter.drawSegment(b, shoulderX, bodyTop + 5, hand.x, hand.y, p.outfit, p.outline, 6)
        CharacterLimbPainter.drawSegment(b, shoulderX - 1, bodyTop + 6, hand.x - 1, hand.y - 1, p.outfitLight, p.outfit, 3)
    }

    private fun drawSuit(b: PixelBuffer, p: CharacterPalette, l: Int, t: Int, r: Int, bot: Int) {
        torso(b, l, t, r, bot, p.outfit, p.outfitDark, p.outline)
        b.box(l + 2, t + 3, l + 4, bot - 3, p.outfitDark)
        b.box(r - 4, t + 3, r - 2, bot - 3, p.outfitDark)
        val mid = (l + r) / 2
        b.box(mid - 4, t + 2, mid + 4, bot - 2, p.shirt)
        // Lapelas em degraus e pesponto criam volume de paletó mesmo em poucos pixels.
        b.line(l + 4, t + 2, mid - 2, t + 9, p.outfitLight)
        b.line(l + 4, t + 3, mid - 3, t + 10, p.outfitDark)
        b.line(r - 4, t + 2, mid + 2, t + 9, p.outfitLight)
        b.line(r - 4, t + 3, mid + 3, t + 10, p.outfitDark)
        // Gola dobrada sobre a camisa; a gravata parte de um nó e afunila até a ponta.
        b.line(mid - 4, t + 3, mid - 2, t + 8, p.outfitLight)
        b.line(mid + 4, t + 3, mid + 2, t + 8, p.outfitLight)
        b.box(mid - 2, t + 8, mid + 2, t + 10, p.accent)
        b.box(mid - 1, t + 11, mid + 1, bot - 7, p.accent)
        b.set(mid, bot - 6, p.accent)
        b.set(l + 6, t + 15, p.outfitLight)
        b.set(r - 6, t + 15, p.outfitLight)
        b.hline(l + 5, r - 5, bot - 2, p.outfitDark)
    }

    private fun drawCasual(b: PixelBuffer, p: CharacterPalette, l: Int, t: Int, r: Int, bot: Int) {
        torso(b, l, t, r, bot, p.outfit, p.outfitDark, p.outline)
        b.box(l + 2, t + 2, l + 4, bot - 3, p.outfitDark)
        b.box(l + 5, t + 2, l + 6, bot - 3, p.outfitLight)
        b.hline(l + 4, r - 4, t + 5, p.outfitLight)
        b.box(r - 6, t + 10, r - 4, t + 15, p.accent)
    }

    private fun drawHoodie(b: PixelBuffer, p: CharacterPalette, l: Int, t: Int, r: Int, bot: Int) {
        torso(b, l, t, r, bot, p.outfit, p.outfitDark, p.outline)
        val mid = (l + r) / 2
        // Capuz apoiado sobre a nuca, com abertura e sombra para separar cabeça e tronco.
        b.outlined(mid - 7, t - 3, mid + 7, t + 7, p.outfitLight, p.outline)
        b.box(mid - 5, t, mid + 5, t + 5, p.outfitDark)
        b.hline(mid - 3, mid + 3, t + 3, p.outfit)
        // Cordões e bolso canguru mantêm leitura mesmo com paletas de espécies diferentes.
        b.vline(mid - 3, t + 7, t + 13, p.shirt)
        b.vline(mid + 3, t + 7, t + 13, p.shirt)
        b.outlined(mid - 7, t + 14, mid + 7, t + 21, p.outfitLight, p.outfitDark)
        b.hline(mid - 5, mid + 5, t + 15, p.outfitDark)
        b.hline(l + 3, r - 3, bot - 2, p.outfitDark)
        for (x in l + 2..r - 2 step 3) b.set(x, bot - 1, p.outfitLight)
    }

    private fun drawStudent(b: PixelBuffer, p: CharacterPalette, l: Int, t: Int, r: Int, bot: Int) {
        torso(b, l, t, r, bot, p.outfit, p.outfitDark, p.outline)
        b.box(l + 3, t + 4, r - 3, t + 6, p.outfitLight)
        val mid = (l + r) / 2
        b.box(mid - 2, t + 3, mid + 2, t + 7, p.shirt)
        b.line(mid, t + 7, mid - 2, t + 15, p.accent)
        b.line(mid - 2, t + 15, mid + 2, t + 15, p.accent)
        b.box(l + 5, t + 12, l + 7, t + 17, p.outfitDark)
    }

    private fun drawSport(b: PixelBuffer, p: CharacterPalette, l: Int, t: Int, r: Int, bot: Int) {
        torso(b, l + 2, t, r - 2, bot, p.outfit, p.outfitDark, p.outline)
        b.box(l + 1, t + 2, l + 4, bot - 4, p.outfitLight)
        b.box(r - 4, t + 2, r - 1, bot - 4, p.outfitLight)
        b.vline(r - 6, t + 5, bot - 3, p.accent)
        b.hline(l + 5, r - 5, bot - 3, p.outfitDark)
    }

    private fun drawCommuter(b: PixelBuffer, p: CharacterPalette, l: Int, t: Int, r: Int, bot: Int) {
        torso(b, l, t, r, bot, p.outfitDark, p.outfit, p.outline)
        b.box(l + 3, t + 2, r - 3, bot - 3, p.outfit)
        val mid = (l + r) / 2
        b.line(mid - 2, t + 3, mid - 4, t + 13, p.outfitLight)
        b.line(mid - 4, t + 13, mid + 2, t + 17, p.outfitLight)
        b.box(mid, t + 2, mid + 2, t + 13, p.accent)
        b.set(r - 6, t + 20, p.outfitLight)
    }

    private fun torso(b: PixelBuffer, left: Int, top: Int, right: Int, bottom: Int, fill: Int, shade: Int, outline: Int) {
        b.outlined(left, top, right, bottom, fill, outline)
        if (right - left > 8 && bottom - top > 12) {
            b.box(left + 2, top + 2, left + 3, bottom - 2, shade)
            b.box(right - 3, top + 3, right - 2, bottom - 2, outline)
            b.hline(left + 3, right - 3, bottom - 2, outline)
        }
    }
}

package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.character.outfit.OutfitPainter
import com.hoodie.app.pixel.character.species.LegStyle
import com.hoodie.app.pixel.character.species.SpeciesStyle
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing

/**
 * Anatomia V3 independente de espécie e roupa: pescoço, ombros, tronco, quadril,
 * pernas, mãos e pés. O tronco é uma silhueta (ombros arredondados afunilando até
 * o quadril), nunca uma caixa.
 */
internal object CharacterBodyPainter {

    /** Ombros + tronco + quadril como UMA máscara (a roupa pinta por cima). */
    fun torsoMask(l: BodyLayout, art: CharacterArtProfile, breath: Int = 0, hemRaise: Int = 0): CharacterMask {
        val r = art.silhouette.shoulderRoundness
        val sl = l.shoulderLeft - breath; val sr = l.shoulderRight + breath
        val top = l.shoulderY - breath
        val bottom = l.torsoBottom - hemRaise
        return drawShoulders(sl, sr, top, r)
            .union(CharacterMask.trapezoid(top + r, bottom - 2, sl, sr, l.hipLeft, l.hipRight))
            .union(drawHip(l, bottom))
    }

    /** Ombros arredondados: a curva que liga pescoço e braço. */
    fun drawShoulders(sl: Int, sr: Int, top: Int, radius: Int): CharacterMask =
        CharacterMask.roundRect(sl, top, sr, top + radius * 2 + 2, radius)

    /** Quadril: barra levemente arredondada por baixo. */
    fun drawHip(l: BodyLayout, bottom: Int): CharacterMask =
        CharacterMask.roundRect(l.hipLeft, bottom - 4, l.hipRight, bottom, 2)

    /** Sombra do pescoço sob o queixo (o pescoço quase não aparece no estilo chibi). */
    fun drawNeck(b: PixelBuffer, p: CharacterPalette, l: BodyLayout, art: CharacterArtProfile) {
        val n = art.silhouette.neckShadow
        if (n <= 0 || l.facing == Facing.BACK) return
        val cx = if (l.facing == Facing.SIDE) l.headCx - 2 else l.headCx
        CharacterMask.roundRect(cx - 4, l.shoulderY - 1, cx + 4, l.shoulderY + n, 1).fill(b, p.furDark)
    }

    /** Pernas e pés de frente/costas: duas colunas de calça com sombra, pés por cima. */
    fun drawLegsFront(
        b: PixelBuffer, style: CharacterStyle, outfit: OutfitPainter, l: BodyLayout, gait: GaitSample,
    ) {
        val p = style.palette; val pr = style.artProfile.proportions
        val species = style.species
        val bird = species.legStyle == LegStyle.BIRD
        val legW = if (bird) 3 else pr.legWidth
        val inset = if (bird) 3 else 1
        val baseCx = CharacterCanvas.CENTER_X
        val lx = l.hipLeft + inset + legW / 2
        val rx = l.hipRight - inset - legW / 2
        // Pé esquerdo (do quadro) = pé "near" na frente; o direito usa o "far".
        val legs = listOf(Triple(lx, gait.nearLift, -1), Triple(rx, gait.farLift, 1))
        legs.forEach { (x, lift, side) ->
            val ankle = l.ankleY - lift
            val top = l.hipY - 2
            val bottom = maxOf(ankle + 1, top + 3)
            val mask = CharacterMask.roundRect(x - legW / 2, top, x - legW / 2 + legW - 1, bottom, if (bird) 0 else 1)
            mask.paint(b, if (bird) Tones(p.accent, p.accent, p.furDark, p.outline) else outfit.legTones(p), style.artProfile.shading)
        }
        legs.forEach { (x, lift, side) ->
            val footX = x + side * (if (bird) 1 else 0) + (baseCx - l.cx).coerceIn(-1, 1)
            drawFoot(b, style, outfit, footX, l.groundY - lift, pr.footWidth, farSide = false, facing = l.facing)
        }
    }

    /**
     * Pernas de perfil (autorado olhando para a direita): perna de trás mais escura
     * primeiro; sentado, a coxa vai para a frente e a canela desce até o pé.
     */
    fun drawLegsSide(
        b: PixelBuffer, style: CharacterStyle, outfit: OutfitPainter, l: BodyLayout, gait: GaitSample,
    ) {
        val p = style.palette; val pr = style.artProfile.proportions
        val bird = style.species.legStyle == LegStyle.BIRD
        val legW = if (bird) 3 else pr.legWidth - 1
        val near = if (bird) Tones(p.accent, p.accent, p.furDark, p.outline) else outfit.legTones(p)
        val far = if (bird) Tones(p.furDark, p.furDark, p.outline, p.outline) else Tones(near.dark, near.dark, p.outline, p.outline)
        val hx = (l.hipLeft + l.hipRight) / 2
        val hipY = l.hipY - 1
        listOf(Triple(gait.farX, gait.farLift, true), Triple(gait.nearX, gait.nearLift, false)).forEach { (dx, lift, isFar) ->
            val x = hx + (if (isFar) -1 else 1)
            val tones = if (isFar) far else near
            val mask = if (l.sitting) {
                val knee = x + 7
                CharacterMask.capsule(x, hipY, knee, hipY, legW).union(CharacterMask.capsule(knee, hipY, knee + 1, l.ankleY, legW))
            } else {
                CharacterMask.capsule(x, hipY, CharacterCanvas.CENTER_X + dx, l.ankleY - lift, legW)
            }
            mask.paint(b, tones, if (isFar) null else style.artProfile.shading)
            val footX = if (l.sitting) x + 8 else CharacterCanvas.CENTER_X + dx
            drawFoot(b, style, outfit, footX, l.groundY - if (l.sitting) 0 else lift, pr.footWidth - 2, farSide = isFar, facing = Facing.SIDE)
        }
    }

    fun drawFoot(b: PixelBuffer, style: CharacterStyle, outfit: OutfitPainter, x: Int, y: Int, width: Int, farSide: Boolean, facing: Facing) {
        val p = style.palette
        if (outfit.hasShoes && style.species.legStyle == LegStyle.MAMMAL) outfit.drawShoe(b, p, x, y, width, farSide, facing)
        else style.species.drawFoot(b, p, x, y, width, farSide, facing)
    }

    /** Manga do ombro até a mão (com cotovelo quando a mão sobe) e punho opcional. */
    fun drawSleeve(
        b: PixelBuffer, style: CharacterStyle, outfit: OutfitPainter,
        jointX: Int, jointY: Int, hand: com.hoodie.app.pixel.sprite.Point, outward: Int, far: Boolean = false,
    ) {
        val p = style.palette
        val w = style.artProfile.proportions.armWidth
        val raised = hand.y < jointY + 8
        val elbow = if (raised) com.hoodie.app.pixel.sprite.Point(jointX + outward * 2, jointY + 8) else null
        val endX = hand.x; val endY = hand.y - 2
        val mask = if (elbow != null) {
            CharacterMask.capsule(jointX, jointY, elbow.x, elbow.y, w).union(CharacterMask.capsule(elbow.x, elbow.y, endX, endY, w - 1))
        } else CharacterMask.capsule(jointX, jointY, endX, endY, w)
        val tones = outfit.sleeveTones(p).let { if (far) Tones(it.dark, it.dark, p.outline, p.outline) else it }
        mask.paint(b, tones, if (far) null else style.artProfile.shading)
        outfit.cuffColor(p)?.let { cuff -> if (!far) { b.set(endX - 1, endY + 1, cuff); b.set(endX, endY + 1, cuff); b.set(endX + 1, endY + 1, cuff) } }
    }

    fun drawHand(b: PixelBuffer, species: SpeciesStyle, p: CharacterPalette, hand: com.hoodie.app.pixel.sprite.Point) =
        species.drawHand(b, p, hand.x, hand.y)
}

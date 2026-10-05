package com.hoodie.app.pixel.character.outfit

import com.hoodie.app.pixel.character.BodyLayout
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.GarmentDetailLevel
import com.hoodie.app.pixel.character.Tones
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing

/** O tronco já resolvido (máscara ombro→quadril) e o esqueleto do quadro. */
class OutfitContext(
    val b: PixelBuffer,
    val style: CharacterStyle,
    val layout: BodyLayout,
    val torso: CharacterMask,
) {
    val p: CharacterPalette get() = style.palette
    val detail: GarmentDetailLevel get() = style.artProfile.garmentDetail
    val shading get() = style.artProfile.shading.takeIf { it.useGarmentShadow }
    val cx get() = (layout.shoulderLeft + layout.shoulderRight) / 2
    val sy get() = layout.shoulderY
    val hy get() = layout.hipY
    val tb get() = layout.torsoBottom
    val sl get() = layout.shoulderLeft
    val sr get() = layout.shoulderRight

    /** Pinta só os pixels internos do tronco (detalhes nunca vazam a silhueta). */
    fun inside(x: Int, y: Int, color: Int) { if (torso.interior(x, y)) b.set(x, y, color) }
    fun hline(x0: Int, x1: Int, y: Int, color: Int) { for (x in minOf(x0, x1)..maxOf(x0, x1)) inside(x, y, color) }
    fun vline(x: Int, y0: Int, y1: Int, color: Int) { for (y in minOf(y0, y1)..maxOf(y0, y1)) inside(x, y, color) }
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        val n = maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)).coerceAtLeast(1)
        for (i in 0..n) inside(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, color)
    }
    fun fill(mask: CharacterMask, color: Int) = mask.copy().intersect(torso).fill(b, color)
}

/**
 * Roupa como camada: frente, perfil e costas desenhados sobre a máscara do tronco
 * (volume e luz vêm da máscara); calça, manga e calçado também pertencem à roupa.
 */
interface OutfitPainter {
    fun drawFront(ctx: OutfitContext)
    fun drawSide(ctx: OutfitContext)
    fun drawBack(ctx: OutfitContext)

    /** Tons do tecido principal do tronco. */
    fun torsoTones(p: CharacterPalette): Tones = Tones.outfit(p)
    fun sleeveTones(p: CharacterPalette): Tones = torsoTones(p)
    /** Calça (mamíferos). */
    fun legTones(p: CharacterPalette): Tones = Tones.deep(p)
    /** Punho visível entre a manga e a mão (camisa do terno). */
    fun cuffColor(p: CharacterPalette): Int? = null
    val hasShoes: Boolean get() = false

    fun drawShoe(b: PixelBuffer, p: CharacterPalette, x: Int, y: Int, width: Int, farSide: Boolean, facing: Facing) {
        val half = width / 2
        val toe = if (facing == Facing.SIDE) 2 else 0
        val tones = if (farSide) Tones(p.outfitDark, p.outfitDark, p.outline, p.outline) else Tones(p.outfitLight, p.outfitDark, p.outline, p.outline)
        CharacterMask.roundRect(x - half, y - 3, x - half + width - 1 + toe, y, 2).paint(b, tones, null)
        b.hline(x - half + 1, x - half + width - 2 + toe, y, p.outline)
        if (!farSide) b.hline(x - half + 2, x - half + 3, y - 2, p.outfitLight)
    }

    fun draw(ctx: OutfitContext, facing: Facing) {
        ctx.torso.paint(ctx.b, torsoTones(ctx.p), ctx.shading)
        when (facing) {
            Facing.FRONT -> drawFront(ctx)
            Facing.SIDE -> drawSide(ctx)
            Facing.BACK -> drawBack(ctx)
        }
    }

    // ───── Peças comuns ─────

    /** Barra/ribana: linha escura logo acima do contorno inferior. */
    fun hem(ctx: OutfitContext, color: Int = ctx.p.outfitDark) = ctx.hline(ctx.layout.hipLeft, ctx.layout.hipRight, ctx.tb - 2, color)

    /** Costura central das costas. */
    fun backSeam(ctx: OutfitContext) = ctx.vline(ctx.cx, ctx.sy + 3, ctx.tb - 3, ctx.p.outfitDark)
}

/** Alças/volume da mochila e da bolsa transversal. */
internal object BackAccessoryPainter {
    /** Parte que fica atrás do corpo (desenhada antes do tronco). */
    fun drawBehind(b: PixelBuffer, p: CharacterPalette, accessory: BackAccessory, l: BodyLayout) {
        when (accessory) {
            BackAccessory.Backpack -> when (l.facing) {
                Facing.SIDE -> CharacterMask.roundRect(l.shoulderLeft - 6, l.shoulderY + 2, l.shoulderLeft + 3, l.hipY, 3)
                    .paint(b, Tones(p.outfit, p.outfitDark, p.outline, p.outline), null)
                // De frente, a mochila aparece só como volume atrás dos ombros.
                Facing.FRONT -> CharacterMask.roundRect(l.shoulderLeft - 2, l.shoulderY + 2, l.shoulderRight + 2, l.hipY - 3, 4)
                    .paint(b, Tones(p.outfitDark, p.outfitDark, p.outline, p.outline), null)
                Facing.BACK -> Unit
            }
            BackAccessory.ShoulderBag -> if (l.facing == Facing.SIDE) {
                CharacterMask.roundRect(l.shoulderLeft - 3, l.hipY - 5, l.shoulderLeft + 4, l.hipY + 1, 2)
                    .paint(b, Tones(p.outfitLight, p.outfitDark, p.outline, p.outline), null)
            }
            BackAccessory.None -> Unit
        }
    }

    /** Parte que fica na frente (alças) ou, de costas, a mochila inteira. */
    fun drawFront(ctx: OutfitContext, accessory: BackAccessory) {
        val p = ctx.p; val l = ctx.layout
        when (accessory) {
            BackAccessory.Backpack -> when (l.facing) {
                Facing.FRONT -> {
                    ctx.vline(ctx.sl + 3, ctx.sy + 1, ctx.hy - 3, p.accent)
                    ctx.vline(ctx.sr - 3, ctx.sy + 1, ctx.hy - 3, p.accent)
                    ctx.vline(ctx.sl + 4, ctx.sy + 1, ctx.hy - 3, p.outline)
                    ctx.vline(ctx.sr - 4, ctx.sy + 1, ctx.hy - 3, p.outline)
                }
                Facing.SIDE -> ctx.line(ctx.sl + 2, ctx.sy + 1, ctx.sl + 4, ctx.hy - 2, p.accent)
                Facing.BACK -> {
                    CharacterMask.roundRect(ctx.cx - 7, ctx.sy + 2, ctx.cx + 7, ctx.hy, 3)
                        .paint(ctx.b, Tones(p.outfit, p.outfitDark, p.outline, p.outline), null)
                    ctx.b.hline(ctx.cx - 5, ctx.cx + 5, ctx.sy + 7, p.outline)
                    ctx.b.box(ctx.cx - 1, ctx.sy + 8, ctx.cx + 1, ctx.sy + 9, p.accent)
                }
            }
            BackAccessory.ShoulderBag -> when (l.facing) {
                Facing.FRONT -> {
                    // Alça transversal do ombro esquerdo ao quadril direito, com fivela.
                    ctx.line(ctx.sl + 3, ctx.sy, ctx.sr - 2, ctx.hy - 2, p.shirt)
                    ctx.line(ctx.sl + 3, ctx.sy + 1, ctx.sr - 2, ctx.hy - 1, p.outline)
                    val bx = (ctx.sl + ctx.sr) / 2 + 2
                    ctx.b.box(bx, ctx.sy + (ctx.hy - ctx.sy) / 2, bx + 1, ctx.sy + (ctx.hy - ctx.sy) / 2 + 1, p.accent)
                }
                Facing.SIDE -> ctx.line(ctx.sl + 3, ctx.sy, ctx.sl + 6, ctx.hy - 4, p.shirt)
                Facing.BACK -> ctx.line(ctx.sr - 3, ctx.sy, ctx.sl + 2, ctx.hy - 2, p.shirt)
            }
            BackAccessory.None -> Unit
        }
    }
}

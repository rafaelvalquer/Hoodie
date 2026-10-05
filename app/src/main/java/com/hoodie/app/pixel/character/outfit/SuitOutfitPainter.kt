package com.hoodie.app.pixel.character.outfit

import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.GarmentDetailLevel
import com.hoodie.app.pixel.character.Tones

/** Terno: camisa, lapela esquerda e direita, gravata, botão, barra do paletó, calça e sapato. */
object SuitOutfitPainter : OutfitPainter {
    override fun legTones(p: CharacterPalette) = Tones(p.outfitLight, p.outfit, p.outfitDark, p.outline)
    override fun cuffColor(p: CharacterPalette) = p.shirt
    override val hasShoes = true

    override fun drawFront(ctx: OutfitContext) {
        val p = ctx.p; val cx = ctx.cx; val sy = ctx.sy
        val vBottom = sy + (ctx.hy - sy) * 3 / 5
        // Camisa em V.
        ctx.fill(CharacterMask.trapezoid(sy, vBottom, cx - 4, cx + 4, cx, cx), p.shirt)
        // Lapelas: faixa clara com borda escura de cada lado do V.
        ctx.line(cx - 5, sy, cx - 1, vBottom, p.outfitLight)
        ctx.line(cx - 6, sy + 1, cx - 2, vBottom + 1, p.outline)
        ctx.line(cx + 5, sy, cx + 1, vBottom, p.outfitLight)
        ctx.line(cx + 6, sy + 1, cx + 2, vBottom + 1, p.outline)
        // Gravata: nó + lâmina que alarga e termina em ponta.
        ctx.hline(cx - 1, cx + 1, sy + 1, p.accent)
        ctx.inside(cx, sy + 2, p.outline)
        ctx.fill(CharacterMask.trapezoid(sy + 3, vBottom - 1, cx - 1, cx + 1, cx - 2, cx + 2), p.accent)
        ctx.inside(cx, vBottom, p.accent)
        ctx.inside(cx + 1, sy + 4, p.outfitDark.takeIf { ctx.detail == GarmentDetailLevel.RICH } ?: p.accent)
        // Fechamento do paletó, botões e abertura da barra.
        ctx.vline(cx, vBottom + 1, ctx.tb - 2, p.outfitDark)
        ctx.inside(cx - 1, vBottom + 3, p.outline); ctx.inside(cx - 1, vBottom + 6, p.outline)
        ctx.inside(cx, ctx.tb - 1, p.outline)
        // Bolsos com lapela.
        ctx.hline(ctx.sl + 3, ctx.sl + 6, ctx.hy - 3, p.outline)
        ctx.hline(ctx.sr - 6, ctx.sr - 3, ctx.hy - 3, p.outline)
        if (ctx.detail == GarmentDetailLevel.RICH) ctx.hline(ctx.sr - 6, ctx.sr - 4, sy + 5, p.shirt)
    }

    override fun drawSide(ctx: OutfitContext) {
        val p = ctx.p
        // De perfil: frente do paletó à direita, filete de camisa e gravata.
        ctx.vline(ctx.sr - 2, ctx.sy + 1, ctx.sy + 2, p.shirt)
        ctx.vline(ctx.sr - 1, ctx.sy + 2, ctx.sy + 6, p.accent)
        ctx.line(ctx.sr - 4, ctx.sy, ctx.sr - 2, ctx.sy + 8, p.outfitLight)
        ctx.vline(ctx.sr - 3, ctx.sy + 9, ctx.tb - 2, p.outfitDark)
        ctx.hline(ctx.cx - 2, ctx.cx + 2, ctx.hy - 3, p.outline)
    }

    override fun drawBack(ctx: OutfitContext) {
        val p = ctx.p
        ctx.hline(ctx.sl + 3, ctx.sr - 3, ctx.sy + 2, p.outfitLight)
        backSeam(ctx)
        // Fenda do paletó.
        ctx.vline(ctx.cx, ctx.tb - 4, ctx.tb - 1, p.outline)
    }
}

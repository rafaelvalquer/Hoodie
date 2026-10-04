package com.hoodie.app.pixel.character.outfit

import com.hoodie.app.pixel.character.CharacterMask

/** Jaqueta aberta sobre camiseta clara: abertura, gola, bolso e botão de pressão. */
object CommuterOutfitPainter : OutfitPainter {
    override fun drawFront(ctx: OutfitContext) {
        val p = ctx.p; val cx = ctx.cx; val sy = ctx.sy
        // Camiseta na abertura, alargando para baixo.
        ctx.fill(CharacterMask.trapezoid(sy, ctx.tb, cx - 3, cx + 3, cx - 4, cx + 4), p.shirt)
        ctx.hline(cx - 2, cx + 2, sy, p.outfitDark)
        // Bordas da jaqueta: luz na lapela, contorno na dobra.
        ctx.vline(cx - 4, sy + 1, ctx.tb - 1, p.outfitLight); ctx.vline(cx - 5, sy + 2, ctx.tb - 1, p.outline)
        ctx.vline(cx + 4, sy + 1, ctx.tb - 1, p.outfitLight); ctx.vline(cx + 5, sy + 2, ctx.tb - 1, p.outline)
        // Gola dobrada.
        ctx.line(cx - 7, sy, cx - 4, sy + 4, p.outfitLight)
        ctx.line(cx + 7, sy, cx + 4, sy + 4, p.outfitLight)
        // Bolsos e botão de pressão no acento.
        ctx.hline(ctx.sl + 3, ctx.sl + 6, ctx.hy - 4, p.outfitDark)
        ctx.hline(ctx.sr - 6, ctx.sr - 3, ctx.hy - 4, p.outfitDark)
        ctx.inside(ctx.sr - 5, ctx.sy + 6, p.accent)
    }

    override fun drawSide(ctx: OutfitContext) {
        val p = ctx.p
        ctx.vline(ctx.sr - 1, ctx.sy + 1, ctx.tb - 1, p.shirt)
        ctx.vline(ctx.sr - 2, ctx.sy + 2, ctx.tb - 1, p.outfitLight)
        ctx.line(ctx.sr - 6, ctx.sy, ctx.sr - 3, ctx.sy + 4, p.outfitLight)
        ctx.hline(ctx.cx - 2, ctx.cx + 1, ctx.hy - 4, p.outfitDark)
    }

    override fun drawBack(ctx: OutfitContext) {
        ctx.hline(ctx.cx - 7, ctx.cx + 7, ctx.sy + 1, ctx.p.outfitLight)
        backSeam(ctx)
        hem(ctx)
    }
}

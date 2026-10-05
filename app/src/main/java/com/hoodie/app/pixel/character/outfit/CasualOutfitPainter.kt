package com.hoodie.app.pixel.character.outfit

import com.hoodie.app.pixel.character.CharacterMask

/** Jaqueta casual de zíper: gola, camiseta escura no decote, zíper claro e bolso no peito. */
object CasualOutfitPainter : OutfitPainter {
    override fun drawFront(ctx: OutfitContext) {
        val p = ctx.p; val cx = ctx.cx; val sy = ctx.sy
        // Gola levantada nos dois lados do pescoço.
        ctx.fill(CharacterMask.trapezoid(sy, sy + 3, cx - 7, cx - 3, cx - 7, cx - 2), p.outfitLight)
        ctx.fill(CharacterMask.trapezoid(sy, sy + 3, cx + 3, cx + 7, cx + 2, cx + 7), p.outfitLight)
        // Camiseta escura aparecendo no decote.
        ctx.fill(CharacterMask.trapezoid(sy, sy + 4, cx - 2, cx + 2, cx, cx), p.outfitDark)
        // Zíper.
        ctx.vline(cx, sy + 5, ctx.tb - 2, p.shirt)
        ctx.vline(cx - 1, sy + 5, ctx.tb - 2, p.outfitDark)
        // Bolso no peito e bolsos laterais.
        ctx.hline(cx + 3, cx + 6, sy + 7, p.shirt)
        ctx.line(ctx.sl + 4, ctx.hy - 6, ctx.sl + 6, ctx.hy - 2, p.outfitDark)
        ctx.line(ctx.sr - 4, ctx.hy - 6, ctx.sr - 6, ctx.hy - 2, p.outfitDark)
        hem(ctx)
    }

    override fun drawSide(ctx: OutfitContext) {
        val p = ctx.p
        ctx.vline(ctx.sr - 2, ctx.sy + 4, ctx.tb - 2, p.shirt)
        ctx.fill(CharacterMask.trapezoid(ctx.sy, ctx.sy + 3, ctx.sr - 6, ctx.sr - 1, ctx.sr - 5, ctx.sr - 1), p.outfitLight)
        ctx.line(ctx.cx - 1, ctx.hy - 6, ctx.cx + 1, ctx.hy - 2, p.outfitDark)
        hem(ctx)
    }

    override fun drawBack(ctx: OutfitContext) {
        ctx.fill(CharacterMask.trapezoid(ctx.sy, ctx.sy + 2, ctx.cx - 7, ctx.cx + 7, ctx.cx - 7, ctx.cx + 7), ctx.p.outfitLight)
        backSeam(ctx)
        hem(ctx)
    }
}

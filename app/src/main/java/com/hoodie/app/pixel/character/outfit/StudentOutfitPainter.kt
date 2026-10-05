package com.hoodie.app.pixel.character.outfit

import com.hoodie.app.pixel.character.CharacterMask

/** Suéter de uniforme: gola branca, decote em V, distintivo no peito e ribana. */
object StudentOutfitPainter : OutfitPainter {
    override fun drawFront(ctx: OutfitContext) {
        val p = ctx.p; val cx = ctx.cx; val sy = ctx.sy
        // Decote em V com gola de camisa branca dobrada para fora.
        ctx.fill(CharacterMask.trapezoid(sy, sy + 5, cx - 3, cx + 3, cx, cx), p.outfitDark)
        ctx.fill(CharacterMask.trapezoid(sy, sy + 2, cx - 6, cx - 2, cx - 4, cx - 1), p.shirt)
        ctx.fill(CharacterMask.trapezoid(sy, sy + 2, cx + 2, cx + 6, cx + 1, cx + 4), p.shirt)
        ctx.inside(cx, sy + 1, p.shirt)
        // Distintivo.
        ctx.hline(cx + 3, cx + 4, sy + 7, p.accent); ctx.hline(cx + 3, cx + 4, sy + 8, p.accent)
        hem(ctx)
        ctx.hline(ctx.layout.hipLeft + 1, ctx.layout.hipRight - 1, ctx.tb - 3, p.outfitDark)
    }

    override fun drawSide(ctx: OutfitContext) {
        ctx.fill(CharacterMask.trapezoid(ctx.sy, ctx.sy + 2, ctx.sr - 5, ctx.sr - 1, ctx.sr - 3, ctx.sr - 1), ctx.p.shirt)
        hem(ctx)
    }

    override fun drawBack(ctx: OutfitContext) {
        ctx.hline(ctx.cx - 6, ctx.cx + 6, ctx.sy + 1, ctx.p.shirt)
        hem(ctx)
    }
}

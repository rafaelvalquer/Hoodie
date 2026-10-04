package com.hoodie.app.pixel.character.outfit

import com.hoodie.app.pixel.character.CharacterMask

/** Moletom esportivo: capuz atrás do pescoço, cordões, bolso canguru e ribana. */
object SportOutfitPainter : OutfitPainter {
    override fun drawFront(ctx: OutfitContext) {
        val p = ctx.p; val cx = ctx.cx; val sy = ctx.sy
        // Borda do capuz em volta do pescoço.
        ctx.fill(CharacterMask.trapezoid(sy, sy + 2, cx - 7, cx + 7, cx - 6, cx + 6), p.outfitDark)
        // Cordões.
        ctx.vline(cx - 2, sy + 2, sy + 7, p.accent); ctx.vline(cx + 2, sy + 2, sy + 7, p.accent)
        ctx.inside(cx - 2, sy + 8, p.outline); ctx.inside(cx + 2, sy + 8, p.outline)
        // Bolso canguru.
        val pocket = CharacterMask.roundRect(cx - 6, ctx.hy - 7, cx + 6, ctx.hy - 1, 2)
        pocket.copy().intersect(ctx.torso).paintOutline(ctx.b, p.outfitDark)
        ctx.hline(cx - 4, cx + 4, ctx.hy - 6, p.outfitLight)
        hem(ctx)
    }

    override fun drawSide(ctx: OutfitContext) {
        val p = ctx.p
        // Capuz caído nas costas (à esquerda do perfil).
        CharacterMask.roundRect(ctx.sl - 1, ctx.sy - 2, ctx.sl + 6, ctx.sy + 5, 3).paint(ctx.b, com.hoodie.app.pixel.character.Tones.outfit(p), null)
        ctx.vline(ctx.sr - 2, ctx.sy + 2, ctx.sy + 7, p.accent)
        ctx.hline(ctx.cx, ctx.sr - 1, ctx.hy - 6, p.outfitDark)
        hem(ctx)
    }

    override fun drawBack(ctx: OutfitContext) {
        val p = ctx.p
        // Capuz visto por trás.
        CharacterMask.roundRect(ctx.cx - 7, ctx.sy - 2, ctx.cx + 7, ctx.sy + 7, 4)
            .paint(ctx.b, com.hoodie.app.pixel.character.Tones.outfit(p), null)
        ctx.b.hline(ctx.cx - 4, ctx.cx + 4, ctx.sy + 2, p.outfitDark)
        hem(ctx)
    }
}

package com.hoodie.app.pixel.character.outfit

import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.Tones

/**
 * Moletom de capuz para NPCs. O Hoodie protagonista NÃO passa por aqui: ele usa o
 * renderer legado pixel a pixel ([com.hoodie.app.pixel.character.CharacterPainter]).
 */
object HoodieOutfitPainter : OutfitPainter {
    override fun drawFront(ctx: OutfitContext) {
        val p = ctx.p; val cx = ctx.cx; val sy = ctx.sy
        ctx.fill(CharacterMask.trapezoid(sy, sy + 3, cx - 8, cx + 8, cx - 6, cx + 6), p.outfitLight)
        ctx.vline(cx - 3, sy + 3, sy + 9, p.shirt); ctx.vline(cx + 3, sy + 3, sy + 9, p.shirt)
        CharacterMask.roundRect(cx - 7, ctx.hy - 8, cx + 7, ctx.hy - 1, 2).intersect(ctx.torso).paint(ctx.b, Tones(p.outfitLight, p.outfitLight, p.outfit, p.outfitDark), null)
        hem(ctx)
    }

    override fun drawSide(ctx: OutfitContext) = SportOutfitPainter.drawSide(ctx)

    override fun drawBack(ctx: OutfitContext) = SportOutfitPainter.drawBack(ctx)
}

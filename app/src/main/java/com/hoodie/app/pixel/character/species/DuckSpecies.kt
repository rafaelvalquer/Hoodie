package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterArtProfile
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.FaceProfile
import com.hoodie.app.pixel.character.ProportionProfile
import com.hoodie.app.pixel.character.SilhouetteProfile
import com.hoodie.app.pixel.character.Tones
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing

/**
 * Pato: cabeça redonda com topete, bico grande em dois volumes, olhos cansados,
 * asa curta no lugar da mão e pé palmado — nada de anatomia de mamífero.
 */
object DuckSpecies : SpeciesStyle {
    override val id = "duck"
    override val artProfile = CharacterArtProfile(
        silhouette = SilhouetteProfile(headRoundness = 13),
        face = FaceProfile(eyeSpacing = 7, eyeLine = 0.40f),
        proportions = ProportionProfile.DUCK,
    )
    override val earStyle = EarStyle.WING
    override val muzzleStyle = MuzzleStyle.BEAK
    override val tailStyle = TailStyle.DUCK
    override val headShape = HeadShape.DUCK
    override val legStyle = LegStyle.BIRD
    override val earClearance = 5

    override fun drawHead(ctx: HeadContext) = when (ctx.facing) {
        Facing.FRONT -> front(ctx)
        Facing.SIDE -> side(ctx)
        Facing.BACK -> back(ctx)
    }

    private fun skull(ctx: HeadContext) = CharacterMask.oval(ctx.l, ctx.t, ctx.r, ctx.bot)
        // Topete de penas.
        .union(CharacterMask.capsule(ctx.cx, ctx.t + 2, ctx.cx + 2 + ctx.earLag.coerceIn(-1, 1), ctx.t - 4, 3))
        .union(CharacterMask.capsule(ctx.cx + 2, ctx.t - 3, ctx.cx + 4, ctx.t - 2, 2))

    private fun beak(ctx: HeadContext, x0: Int, x1: Int, y: Int) {
        val b = ctx.b; val p = ctx.p
        val beakTones = Tones(p.accent, p.accent, p.furDark, p.outline)
        // Mandíbula e maxila: dois volumes separados por uma linha.
        CharacterMask.oval(x0 + 2, y + 3, x1 - 2, y + 7).paint(b, beakTones, null)
        CharacterMask.oval(x0, y, x1, y + 4).paint(b, beakTones, null)
        val mid = (x0 + x1) / 2
        b.set(mid - 2, y + 2, p.outline); b.set(mid + 2, y + 2, p.outline)
        b.hline(x0 + 2, x0 + 4, y + 1, p.shirt)
    }

    private fun front(ctx: HeadContext) {
        val cx = ctx.cx; val ey = ctx.eyeY
        SpeciesArt.paintFur(ctx, skull(ctx))
        SpeciesArt.eyes(ctx, listOf(cx - ctx.art.face.eyeSpacing, cx + ctx.art.face.eyeSpacing), ey - 1, ctx.style.eyeStyle)
        beak(ctx, cx - 6, cx + 6, ey + 3)
        // Bochechas rosadas sempre visíveis no pato.
        ctx.b.hline(cx - 12, cx - 10, ey + 3, ctx.p.inner); ctx.b.hline(cx + 10, cx + 12, ey + 3, ctx.p.inner)
    }

    private fun side(ctx: HeadContext) {
        val ey = ctx.eyeY
        SpeciesArt.paintFur(ctx, skull(ctx))
        SpeciesArt.sideEye(ctx, ctx.r - 8, ey - 1, ctx.style.eyeStyle)
        beak(ctx, ctx.r - 4, ctx.r + 6, ey + 2)
    }

    private fun back(ctx: HeadContext) = SpeciesArt.paintFur(ctx, skull(ctx))

    override fun drawHand(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int) {
        // Ponta de asa com três penas.
        CharacterMask.oval(x - 3, y - 3, x + 2, y + 3).union(CharacterMask.box(x - 2, y + 2, x + 2, y + 4))
            .paint(buffer, Tones.fur(palette), null)
        buffer.set(x - 1, y + 4, palette.outline); buffer.set(x + 1, y + 4, palette.outline)
        buffer.set(x - 2, y - 1, palette.furLight); buffer.set(x - 1, y - 2, palette.furLight)
    }

    override fun drawFoot(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int, width: Int, farSide: Boolean, facing: Facing) {
        // Pé palmado: três dedos com membrana; o de trás fica um tom mais escuro.
        val half = width / 2
        val toe = if (facing == Facing.SIDE) 2 else 0
        CharacterMask.trapezoid(y - 4, y, x - 2, x + 2, x - half, x - half + width - 1 + toe)
            .paint(buffer, Tones(palette.accent, palette.accent, palette.furDark, palette.outline), null)
        // O pé de trás leva a sombra na membrana, sem perder o laranja do pato.
        if (farSide) buffer.hline(x - 1, x + 1, y - 3, palette.furDark)
        if (facing != Facing.BACK) {
            buffer.vline(x - half + 3, y - 1, y, palette.outline)
            buffer.vline(x - half + width - 4 + toe, y - 1, y, palette.outline)
        }
    }
}

package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterArtProfile
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.FaceProfile
import com.hoodie.app.pixel.character.ProportionProfile
import com.hoodie.app.pixel.character.SilhouetteProfile
import com.hoodie.app.pixel.character.Tones
import com.hoodie.app.pixel.sprite.Facing

/** Rato: orelhas redondas enormes, rosto menor, focinho fino, olhos redondos, bigodes. */
object MouseSpecies : SpeciesStyle {
    override val id = "mouse"
    override val artProfile = CharacterArtProfile(
        silhouette = SilhouetteProfile(headRoundness = 9),
        face = FaceProfile(eyeSpacing = 5, eyeLine = 0.40f),
        proportions = ProportionProfile.MOUSE,
    )
    override val earStyle = EarStyle.ROUND
    override val muzzleStyle = MuzzleStyle.FINE
    override val tailStyle = TailStyle.LONG
    override val headShape = HeadShape.MOUSE
    override val earClearance = 7

    override fun drawHead(ctx: HeadContext) = when (ctx.facing) {
        Facing.FRONT -> front(ctx)
        Facing.SIDE -> side(ctx)
        Facing.BACK -> back(ctx)
    }

    private fun ear(ctx: HeadContext, cx: Int, inner: Boolean = true) {
        val d = SpeciesArt.earDrop(ctx)
        val cy = ctx.t + 1 + d
        CharacterMask.ellipse(cx, cy, 7, 7).paint(ctx.b, Tones.fur(ctx.p), ctx.art.shading)
        if (inner) CharacterMask.ellipse(cx, cy + 1, 4, 4).fill(ctx.b, ctx.p.inner)
    }

    private fun skull(ctx: HeadContext) = CharacterMask.roundRect(ctx.l + 3, ctx.t, ctx.r - 3, ctx.bot, 9)
        .union(CharacterMask.oval(ctx.l + 4, ctx.eyeY, ctx.r - 4, ctx.bot))

    private fun front(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val cx = ctx.cx; val ey = ctx.eyeY
        ear(ctx, ctx.l + 4)
        ear(ctx, ctx.r - 4)
        SpeciesArt.paintFur(ctx, skull(ctx))
        // Topete.
        b.set(cx - 1, ctx.t, p.furDark); b.set(cx, ctx.t - 1, p.outline); b.set(cx + 1, ctx.t, p.furDark)
        CharacterMask.oval(cx - 4, ey + 3, cx + 4, ctx.bot - 1).fill(b, p.furLight)
        SpeciesArt.eyes(ctx, listOf(cx - ctx.art.face.eyeSpacing - 1, cx + ctx.art.face.eyeSpacing + 1), ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, cx, ey + 4, 2, 2, p.outline, glint = false)
        SpeciesArt.mouth(ctx, cx, ey + 7, MouthKind.CAT_W)
        // Bigodes saindo das bochechas.
        b.line(cx - 4, ey + 5, cx - 11, ey + 4, p.furDark); b.line(cx - 4, ey + 6, cx - 11, ey + 7, p.furDark)
        b.line(cx + 4, ey + 5, cx + 11, ey + 4, p.furDark); b.line(cx + 4, ey + 6, cx + 11, ey + 7, p.furDark)
        SpeciesArt.blush(ctx, listOf(cx - 8, cx + 8), ey + 3)
    }

    private fun side(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val ey = ctx.eyeY
        ear(ctx, ctx.l + 6)
        val head = CharacterMask.roundRect(ctx.l, ctx.t, ctx.r - 3, ctx.bot, 9)
            .union(CharacterMask.trapezoid(ey + 1, ey + 7, ctx.r - 4, ctx.r - 3, ctx.r - 6, ctx.r + 4))
        SpeciesArt.paintFur(ctx, head)
        SpeciesArt.sideEye(ctx, ctx.r - 8, ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, ctx.r + 3, ey + 4, 2, 2, p.outline, glint = false)
        b.line(ctx.r - 1, ey + 6, ctx.r + 3, ey + 5, p.furDark); b.line(ctx.r - 1, ey + 7, ctx.r + 3, ey + 8, p.furDark)
    }

    private fun back(ctx: HeadContext) {
        ear(ctx, ctx.l + 4, inner = false)
        ear(ctx, ctx.r - 4, inner = false)
        SpeciesArt.paintFur(ctx, skull(ctx))
    }
}

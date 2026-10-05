package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterArtProfile
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.FaceProfile
import com.hoodie.app.pixel.character.ProportionProfile
import com.hoodie.app.pixel.character.SilhouetteProfile
import com.hoodie.app.pixel.sprite.Facing

/** Guaxinim: máscara escura, faixa clara acima dela, orelha pontuda e cauda grossa anelada. */
object RaccoonSpecies : SpeciesStyle {
    override val id = "raccoon"
    override val artProfile = CharacterArtProfile(
        silhouette = SilhouetteProfile(headRoundness = 9),
        face = FaceProfile(eyeSpacing = 7, eyeLine = 0.45f),
        proportions = ProportionProfile.RACCOON,
    )
    override val earStyle = EarStyle.POINTED
    override val muzzleStyle = MuzzleStyle.MASKED
    override val tailStyle = TailStyle.RINGED
    override val headShape = HeadShape.RACCOON
    override val earClearance = 7

    override fun drawHead(ctx: HeadContext) = when (ctx.facing) {
        Facing.FRONT -> front(ctx)
        Facing.SIDE -> side(ctx)
        Facing.BACK -> back(ctx)
    }

    private fun ears(ctx: HeadContext) {
        val d = SpeciesArt.earDrop(ctx)
        SpeciesArt.pointedEar(ctx.b, ctx.p, ctx.l + 5, ctx.t - 6 + d, ctx.l + 1, ctx.l + 11, ctx.t + 4)
        SpeciesArt.pointedEar(ctx.b, ctx.p, ctx.r - 6, ctx.t - 6 + d, ctx.r - 11, ctx.r - 1, ctx.t + 4)
    }

    private fun skull(ctx: HeadContext) = SpeciesArt.headMask(ctx, 9)
        // Bochechas pontudas de pelo.
        .union(CharacterMask.trapezoid(ctx.bot - 9, ctx.bot - 3, ctx.l + 1, ctx.l + 3, ctx.l - 2, ctx.l + 4))
        .union(CharacterMask.trapezoid(ctx.bot - 9, ctx.bot - 3, ctx.r - 3, ctx.r - 1, ctx.r - 4, ctx.r + 2))

    private fun front(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val cx = ctx.cx; val ey = ctx.eyeY
        ears(ctx)
        SpeciesArt.paintFur(ctx, skull(ctx))
        // Faixa clara acima da máscara e focinho branco.
        CharacterMask.oval(cx - 13, ey - 6, cx - 1, ey - 1).union(CharacterMask.oval(cx + 1, ey - 6, cx + 13, ey - 1)).fill(b, p.furLight)
        // Máscara escura ao redor de cada olho, descendo para as bochechas.
        CharacterMask.oval(cx - 13, ey - 3, cx - 2, ey + 4).union(CharacterMask.oval(cx + 2, ey - 3, cx + 13, ey + 4)).fill(b, p.furDark)
        CharacterMask.oval(cx - 5, ey + 1, cx + 5, ctx.bot - 1).fill(b, p.furLight)
        b.vline(cx, ctx.t + 2, ey - 2, p.furDark)
        SpeciesArt.eyes(ctx, listOf(cx - ctx.art.face.eyeSpacing, cx + ctx.art.face.eyeSpacing), ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, cx, ey + 3, 4, 2)
        SpeciesArt.mouth(ctx, cx, ey + 7, MouthKind.LINE)
    }

    private fun side(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val ey = ctx.eyeY
        val d = SpeciesArt.earDrop(ctx)
        SpeciesArt.pointedEar(b, p, ctx.l + 7, ctx.t - 6 + d, ctx.l + 2, ctx.l + 12, ctx.t + 4)
        val head = SpeciesArt.headMask(ctx, 8)
            .union(CharacterMask.trapezoid(ey + 1, ey + 7, ctx.r - 4, ctx.r - 2, ctx.r - 6, ctx.r + 4))
            .union(CharacterMask.trapezoid(ctx.bot - 9, ctx.bot - 3, ctx.l + 1, ctx.l + 3, ctx.l - 2, ctx.l + 4))
        SpeciesArt.paintFur(ctx, head)
        CharacterMask.oval(ctx.r - 14, ey - 6, ctx.r - 2, ey - 2).fill(b, p.furLight)
        CharacterMask.oval(ctx.r - 14, ey - 3, ctx.r - 2, ey + 4).fill(b, p.furDark)
        CharacterMask.oval(ctx.r - 5, ey + 2, ctx.r + 3, ey + 7).fill(b, p.furLight)
        SpeciesArt.sideEye(ctx, ctx.r - 7, ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, ctx.r + 3, ey + 2, 2, 2)
    }

    private fun back(ctx: HeadContext) {
        ears(ctx)
        SpeciesArt.paintFur(ctx, skull(ctx))
        ctx.b.hline(ctx.cx - 5, ctx.cx + 5, ctx.t + 6, ctx.p.furDark)
    }
}

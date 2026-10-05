package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterArtProfile
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.FaceProfile
import com.hoodie.app.pixel.character.ProportionProfile
import com.hoodie.app.pixel.character.SilhouetteProfile
import com.hoodie.app.pixel.sprite.Facing

/**
 * Gato NPC: mesma "espécie artística" do Hoodie (cabeça larga de cantos suaves,
 * orelhas pontudas com miolo, olhos 3×4 com brilho), mas com listras e roupa própria.
 */
object CatSpecies : SpeciesStyle {
    override val id = "cat"
    override val artProfile = CharacterArtProfile(
        silhouette = SilhouetteProfile(headRoundness = 8),
        face = FaceProfile(eyeSpacing = 7, eyeLine = 0.42f),
        proportions = ProportionProfile.CAT,
    )
    override val earStyle = EarStyle.POINTED
    override val muzzleStyle = MuzzleStyle.SHORT
    override val tailStyle = TailStyle.CAT
    override val headShape = HeadShape.CAT
    override val earClearance = 8

    override fun drawHead(ctx: HeadContext) = when (ctx.facing) {
        Facing.FRONT -> front(ctx)
        Facing.SIDE -> side(ctx)
        Facing.BACK -> back(ctx)
    }

    private fun ears(ctx: HeadContext) {
        val d = SpeciesArt.earDrop(ctx)
        val twL = if (ctx.pose.ears == com.hoodie.app.pixel.sprite.Ears.TWITCH_LEFT) 1 else 0
        val twR = if (ctx.pose.ears == com.hoodie.app.pixel.sprite.Ears.TWITCH_RIGHT) 1 else 0
        SpeciesArt.pointedEar(ctx.b, ctx.p, ctx.l + 5 - twL, ctx.t - 7 + d + twL, ctx.l + 1, ctx.l + 12, ctx.t + 4)
        SpeciesArt.pointedEar(ctx.b, ctx.p, ctx.r - 6 + twR, ctx.t - 7 + d + twR, ctx.r - 12, ctx.r - 1, ctx.t + 4)
    }

    private fun skull(ctx: HeadContext) = SpeciesArt.headMask(ctx, ctx.art.silhouette.headRoundness)
        // Bochechas fofas que quebram a caixa.
        .union(CharacterMask.ellipse(ctx.l + 2, ctx.bot - 6, 2, 3))
        .union(CharacterMask.ellipse(ctx.r - 2, ctx.bot - 6, 2, 3))

    private fun front(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val cx = ctx.cx; val ey = ctx.eyeY
        ears(ctx)
        SpeciesArt.paintFur(ctx, skull(ctx))
        // Listras na testa e nas bochechas.
        for (dx in intArrayOf(-3, 0, 3)) b.vline(cx + dx, ctx.t + 2, ctx.t + 4 + if (dx == 0) 1 else 0, p.furDark)
        b.hline(ctx.l + 1, ctx.l + 4, ey + 4, p.furDark); b.hline(ctx.r - 4, ctx.r - 1, ey + 4, p.furDark)
        // Focinho claro.
        CharacterMask.oval(cx - 4, ey + 3, cx + 4, ey + 8).fill(b, p.furLight)
        SpeciesArt.eyes(ctx, listOf(cx - ctx.art.face.eyeSpacing, cx + ctx.art.face.eyeSpacing), ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, cx, ey + 4, 3, 2, p.inner, glint = false)
        SpeciesArt.mouth(ctx, cx, ey + 7, MouthKind.CAT_W)
        SpeciesArt.blush(ctx, listOf(cx - 10, cx + 10), ey + 5)
    }

    private fun side(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val ey = ctx.eyeY
        val d = SpeciesArt.earDrop(ctx)
        SpeciesArt.pointedEar(b, p, ctx.l + 7, ctx.t - 7 + d, ctx.l + 2, ctx.l + 13, ctx.t + 4)
        val head = SpeciesArt.headMask(ctx, 8)
            .union(CharacterMask.oval(ctx.r - 4, ey + 2, ctx.r + 3, ey + 8))
            .union(CharacterMask.ellipse(ctx.l + 2, ctx.bot - 6, 2, 3))
        SpeciesArt.paintFur(ctx, head)
        CharacterMask.oval(ctx.r - 6, ey + 3, ctx.r + 2, ey + 8).fill(b, p.furLight)
        b.vline(ctx.l + 8, ctx.t + 2, ctx.t + 4, p.furDark); b.vline(ctx.l + 11, ctx.t + 2, ctx.t + 5, p.furDark)
        SpeciesArt.sideEye(ctx, ctx.r - 6, ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, ctx.r + 2, ey + 2, 2, 2, p.inner, glint = false)
        b.set(ctx.r, ey + 6, p.outline); b.set(ctx.r - 1, ey + 7, p.outline)
    }

    private fun back(ctx: HeadContext) {
        ears(ctx)
        SpeciesArt.paintFur(ctx, skull(ctx))
        for (dx in intArrayOf(-4, 0, 4)) ctx.b.vline(ctx.cx + dx, ctx.t + 3, ctx.t + 8, ctx.p.furDark)
    }
}

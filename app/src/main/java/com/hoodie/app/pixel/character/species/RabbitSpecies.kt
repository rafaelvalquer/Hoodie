package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterArtProfile
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.FaceProfile
import com.hoodie.app.pixel.character.ProportionProfile
import com.hoodie.app.pixel.character.SilhouetteProfile
import com.hoodie.app.pixel.character.Tones
import com.hoodie.app.pixel.sprite.Facing

/**
 * Coelho: orelhas grandes (contorno, pelo, miolo e 1 realce — nada além), cabeça
 * arredondada, olhos suaves, nariz rosado e "w" na boca.
 */
object RabbitSpecies : SpeciesStyle {
    override val id = "rabbit"
    override val artProfile = CharacterArtProfile(
        silhouette = SilhouetteProfile(headRoundness = 11),
        face = FaceProfile(eyeSpacing = 6, eyeLine = 0.40f),
        proportions = ProportionProfile.RABBIT,
    )
    override val earStyle = EarStyle.LONG
    override val muzzleStyle = MuzzleStyle.SHORT
    override val tailStyle = TailStyle.SHORT
    override val headShape = HeadShape.RABBIT
    override val earClearance = 15

    override fun drawHead(ctx: HeadContext) = when (ctx.facing) {
        Facing.FRONT -> front(ctx)
        Facing.SIDE -> side(ctx)
        Facing.BACK -> back(ctx)
    }

    /** Orelha alta; [lean] inclina a ponta (orelhas atrasadas na caminhada). */
    private fun ear(ctx: HeadContext, cx: Int, lean: Int, inner: Boolean = true) {
        val b = ctx.b; val p = ctx.p
        val d = SpeciesArt.earDrop(ctx)
        val top = ctx.t - 14 + d.coerceAtLeast(0) * 2
        CharacterMask.capsule(cx, ctx.t + 3, cx + lean, top + 3, 8).paint(b, Tones.fur(p), null)
        if (inner) CharacterMask.capsule(cx, ctx.t + 1, cx + lean, top + 5, 4).fill(b, p.inner)
        b.vline(cx + lean - 3, top + 4, top + 8, p.furLight)
    }

    private fun skull(ctx: HeadContext) = SpeciesArt.headMask(ctx, 11)
        .union(CharacterMask.ellipse(ctx.l + 3, ctx.bot - 5, 3, 3))
        .union(CharacterMask.ellipse(ctx.r - 3, ctx.bot - 5, 3, 3))

    private fun front(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val cx = ctx.cx; val ey = ctx.eyeY
        val lag = ctx.earLag.coerceIn(-2, 2)
        ear(ctx, cx - 6, -2 - lag)
        ear(ctx, cx + 6, 2 - lag)
        SpeciesArt.paintFur(ctx, skull(ctx))
        // Topete e bochechas claras.
        b.set(cx - 1, ctx.t + 2, p.furDark); b.set(cx + 1, ctx.t + 3, p.furDark)
        CharacterMask.oval(cx - 5, ey + 3, cx + 5, ctx.bot - 2).fill(b, p.furLight)
        SpeciesArt.eyes(ctx, listOf(cx - ctx.art.face.eyeSpacing, cx + ctx.art.face.eyeSpacing), ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, cx, ey + 4, 3, 2, p.inner, glint = false)
        SpeciesArt.mouth(ctx, cx, ey + 7, MouthKind.CAT_W)
        SpeciesArt.blush(ctx, listOf(cx - 9, cx + 9), ey + 4)
    }

    private fun side(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val ey = ctx.eyeY
        val lag = ctx.earLag.coerceIn(-2, 2)
        ear(ctx, ctx.cx - 3, -3 - lag, inner = false)
        ear(ctx, ctx.cx + 1, -2 - lag)
        val head = SpeciesArt.headMask(ctx, 10).union(CharacterMask.oval(ctx.r - 5, ey + 2, ctx.r + 2, ey + 9))
        SpeciesArt.paintFur(ctx, head)
        CharacterMask.oval(ctx.r - 6, ey + 3, ctx.r + 1, ey + 9).fill(b, p.furLight)
        SpeciesArt.sideEye(ctx, ctx.r - 6, ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, ctx.r + 1, ey + 3, 2, 2, p.inner, glint = false)
        b.set(ctx.r, ey + 6, p.outline)
    }

    private fun back(ctx: HeadContext) {
        val lag = ctx.earLag.coerceIn(-2, 2)
        ear(ctx, ctx.cx - 6, -2 - lag, inner = false)
        ear(ctx, ctx.cx + 6, 2 - lag, inner = false)
        SpeciesArt.paintFur(ctx, skull(ctx))
    }
}

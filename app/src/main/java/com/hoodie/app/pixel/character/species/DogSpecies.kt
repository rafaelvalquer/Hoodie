package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterArtProfile
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.FaceProfile
import com.hoodie.app.pixel.character.ProportionProfile
import com.hoodie.app.pixel.character.SilhouetteProfile
import com.hoodie.app.pixel.character.Tones
import com.hoodie.app.pixel.sprite.Facing

/**
 * Cachorro comum, claramente diferente do Bulldog: cabeça em domo menos larga,
 * focinho mais comprido, orelhas longas caídas até abaixo do queixo, faixa clara.
 */
object DogSpecies : SpeciesStyle {
    override val id = "dog"
    override val artProfile = CharacterArtProfile(
        silhouette = SilhouetteProfile(headRoundness = 11),
        face = FaceProfile(eyeSpacing = 6, eyeLine = 0.42f),
        proportions = ProportionProfile.DOG,
    )
    override val earStyle = EarStyle.FLOPPY
    override val muzzleStyle = MuzzleStyle.LONG
    override val tailStyle = TailStyle.CAT
    override val headShape = HeadShape.DOG

    override fun drawHead(ctx: HeadContext) = when (ctx.facing) {
        Facing.FRONT -> front(ctx)
        Facing.SIDE -> side(ctx)
        Facing.BACK -> back(ctx)
    }

    private fun skull(ctx: HeadContext) =
        CharacterMask.roundRect(ctx.l + 3, ctx.t, ctx.r - 3, ctx.bot - 2, 11)
            .union(CharacterMask.oval(ctx.l + 5, ctx.eyeY, ctx.r - 5, ctx.bot))

    private fun earTones(ctx: HeadContext) = Tones(ctx.p.fur, ctx.p.furDark, ctx.p.furDark, ctx.p.outline)

    /** Orelha longa e larga, pendurada e um pouco aberta para fora. */
    private fun ear(ctx: HeadContext, rootX: Int, dir: Int) {
        val d = SpeciesArt.earDrop(ctx)
        val sway = if (d < 0) -1 else 0
        CharacterMask.capsule(rootX, ctx.t + 4, rootX + dir * (3 + sway), ctx.bot - 2 + d, 9)
            .paint(ctx.b, earTones(ctx), ctx.art.shading)
    }

    private fun front(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val cx = ctx.cx; val ey = ctx.eyeY
        SpeciesArt.paintFur(ctx, skull(ctx))
        // Faixa clara da testa ao focinho.
        CharacterMask.trapezoid(ctx.t + 2, ey + 1, cx - 1, cx + 1, cx - 3, cx + 3).fill(b, p.furLight)
        CharacterMask.oval(cx - 6, ey + 2, cx + 6, ctx.bot - 1).fill(b, p.furLight)
        ear(ctx, ctx.l + 4, -1)
        ear(ctx, ctx.r - 4, 1)
        // Sobrancelhas curtas.
        b.hline(cx - 8, cx - 5, ey - 3, p.furDark); b.hline(cx + 5, cx + 8, ey - 3, p.furDark)
        SpeciesArt.eyes(ctx, listOf(cx - ctx.art.face.eyeSpacing, cx + ctx.art.face.eyeSpacing), ey - 1, ctx.style.eyeStyle)
        SpeciesArt.nose(b, p, cx, ey + 3, 5, 3)
        SpeciesArt.mouth(ctx, cx, ey + 8, MouthKind.LINE)
        SpeciesArt.blush(ctx, listOf(cx - 8, cx + 8), ey + 4)
    }

    private fun side(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val ey = ctx.eyeY
        // Focinho comprido projetado para a direita.
        val head = CharacterMask.roundRect(ctx.l, ctx.t, ctx.r - 2, ctx.bot - 1, 10)
            .union(CharacterMask.roundRect(ctx.r - 8, ey + 1, ctx.r + 5, ctx.bot - 3, 3))
        SpeciesArt.paintFur(ctx, head)
        CharacterMask.roundRect(ctx.r - 7, ey + 4, ctx.r + 4, ctx.bot - 3, 2).fill(b, p.furLight)
        SpeciesArt.nose(b, p, ctx.r + 4, ey, 3, 3)
        b.hline(ctx.r - 3, ctx.r + 3, ctx.bot - 4, p.outline)
        SpeciesArt.sideEye(ctx, ctx.r - 9, ey - 1, ctx.style.eyeStyle)
        b.hline(ctx.r - 11, ctx.r - 8, ey - 3, p.furDark)
        ear(ctx, ctx.l + 6, -1)
    }

    private fun back(ctx: HeadContext) {
        SpeciesArt.paintFur(ctx, skull(ctx))
        ear(ctx, ctx.l + 4, -1)
        ear(ctx, ctx.r - 4, 1)
        ctx.b.vline(ctx.cx, ctx.t + 2, ctx.t + 6, ctx.p.furLight)
    }
}

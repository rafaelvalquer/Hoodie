package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterArtProfile
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.EyeStyle
import com.hoodie.app.pixel.character.FaceProfile
import com.hoodie.app.pixel.character.GarmentDetailLevel
import com.hoodie.app.pixel.character.ProportionProfile
import com.hoodie.app.pixel.character.SilhouetteProfile
import com.hoodie.app.pixel.character.Tones
import com.hoodie.app.pixel.sprite.Facing

/**
 * Benchmark V3: cabeça muito larga, orelhas dobradas nos cantos, testa com rugas,
 * sobrancelha pesada, bochechas caídas, focinho em dois volumes e nariz largo.
 */
object BulldogSpecies : SpeciesStyle {
    override val id = "bulldog"
    override val artProfile = CharacterArtProfile(
        silhouette = SilhouetteProfile(headRoundness = 10, shoulderRoundness = 4),
        face = FaceProfile(eyeSpacing = 8, eyeLine = 0.40f, heavyBrow = true),
        proportions = ProportionProfile.BULLDOG,
        garmentDetail = GarmentDetailLevel.RICH,
    )
    override val earStyle = EarStyle.FLOPPY
    override val muzzleStyle = MuzzleStyle.BROAD
    override val tailStyle = TailStyle.SHORT
    override val headShape = HeadShape.BULLDOG

    override fun drawHead(ctx: HeadContext) = when (ctx.facing) {
        Facing.FRONT -> front(ctx)
        Facing.SIDE -> side(ctx)
        Facing.BACK -> back(ctx)
    }

    private fun skull(ctx: HeadContext): CharacterMask =
        // Crânio + papadas: a parte de baixo é mais larga que a testa.
        CharacterMask.roundRect(ctx.l + 2, ctx.t, ctx.r - 2, ctx.bot - 7, 10)
            .union(CharacterMask.oval(ctx.l, ctx.t + 9, ctx.r, ctx.bot))

    /** Orelha "rosa": aba pequena presa no canto do crânio, dobrada para fora e para baixo. */
    private fun ears(ctx: HeadContext, inner: Boolean = true) {
        val p = ctx.p; val drop = SpeciesArt.earDrop(ctx).coerceAtLeast(0)
        val y = ctx.t + 1 + drop
        val tones = Tones(p.fur, p.furDark, p.furDark, p.outline)
        CharacterMask.trapezoid(y, y + 7, ctx.l + 1, ctx.l + 8, ctx.l - 3, ctx.l + 1).paint(ctx.b, tones, null)
        CharacterMask.trapezoid(y, y + 7, ctx.r - 8, ctx.r - 1, ctx.r - 1, ctx.r + 3).paint(ctx.b, tones, null)
        if (inner) {
            ctx.b.hline(ctx.l + 1, ctx.l + 3, y + 2, p.inner)
            ctx.b.hline(ctx.r - 3, ctx.r - 1, y + 2, p.inner)
        }
        ctx.b.hline(ctx.l + 2, ctx.l + 6, y + 1, p.fur)
        ctx.b.hline(ctx.r - 6, ctx.r - 2, y + 1, p.fur)
    }

    private fun front(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val cx = ctx.cx; val ey = ctx.eyeY
        SpeciesArt.paintFur(ctx, skull(ctx))
        ears(ctx)
        // Faixa clara na testa que desce até o focinho.
        CharacterMask.trapezoid(ctx.t + 1, ey + 3, cx - 1, cx + 1, cx - 2, cx + 2).fill(b, p.furLight)
        // Rugas da testa e bochechas.
        b.hline(cx - 7, cx - 4, ctx.t + 5, p.furDark); b.hline(cx + 4, cx + 7, ctx.t + 5, p.furDark)
        b.hline(cx - 6, cx - 4, ctx.t + 7, p.furDark); b.hline(cx + 4, cx + 6, ctx.t + 7, p.furDark)
        // Focinho em dois volumes (cada lado do lábio) com dobra central.
        val muzzleTop = ey + 3
        val muzzle = CharacterMask.oval(cx - 12, muzzleTop, cx, ctx.bot - 3)
            .union(CharacterMask.oval(cx, muzzleTop, cx + 12, ctx.bot - 3))
        muzzle.paint(b, Tones(p.furLight, p.furLight, p.fur, p.furDark), null)
        // Queixo claro abaixo da boca.
        CharacterMask.oval(cx - 5, ctx.bot - 6, cx + 5, ctx.bot - 1).fill(b, p.furLight)
        b.hline(cx - 4, cx + 4, ctx.bot - 1, p.furDark)
        // Dobras laterais das papadas.
        b.line(ctx.l + 3, ey + 4, ctx.l + 5, ctx.bot - 4, p.furDark)
        b.line(ctx.r - 3, ey + 4, ctx.r - 5, ctx.bot - 4, p.furDark)
        SpeciesArt.eyes(ctx, listOf(cx - ctx.art.face.eyeSpacing, cx + ctx.art.face.eyeSpacing), ey, ctx.style.eyeStyle)
        // Nariz largo e achatado com reflexo.
        SpeciesArt.nose(b, p, cx, muzzleTop - 1, 8, 4)
        b.vline(cx, muzzleTop + 3, ctx.bot - 7, p.furDark)
        SpeciesArt.mouth(ctx, cx, ctx.bot - 6, MouthKind.FROWN)
        SpeciesArt.blush(ctx, listOf(cx - 10, cx + 10), ey + 5)
    }

    private fun side(ctx: HeadContext) {
        val b = ctx.b; val p = ctx.p; val ey = ctx.eyeY
        // Crânio + focinho chato projetado para a frente (direita) e papada pendente.
        val head = CharacterMask.roundRect(ctx.l, ctx.t, ctx.r - 3, ctx.bot - 2, 9)
            .union(CharacterMask.roundRect(ctx.r - 8, ey + 1, ctx.r + 4, ctx.bot - 3, 4))
            .union(CharacterMask.oval(ctx.l + 4, ey + 2, ctx.r - 2, ctx.bot))
        SpeciesArt.paintFur(ctx, head)
        CharacterMask.roundRect(ctx.r - 7, ey + 3, ctx.r + 3, ctx.bot - 3, 3).fill(b, p.furLight)
        CharacterMask.oval(ctx.r - 12, ey + 6, ctx.r - 1, ctx.bot - 1).fill(b, p.furLight)
        b.line(ctx.r - 12, ey + 5, ctx.r - 9, ctx.bot - 2, p.furDark)
        SpeciesArt.nose(b, p, ctx.r + 2, ey + 1, 4, 4)
        b.hline(ctx.r - 4, ctx.r + 3, ctx.bot - 4, p.outline)
        b.hline(ctx.r - 6, ctx.r - 2, ctx.t + 5, p.furDark)
        SpeciesArt.sideEye(ctx, ctx.r - 8, ey, ctx.style.eyeStyle)
        // Uma orelha visível na nuca.
        val drop = SpeciesArt.earDrop(ctx).coerceAtLeast(0)
        CharacterMask.oval(ctx.l + 2, ctx.t + 1 + drop, ctx.l + 10, ctx.t + 9 + drop).paint(b, Tones(p.fur, p.furDark, p.furDark, p.outline), null)
    }

    private fun back(ctx: HeadContext) {
        SpeciesArt.paintFur(ctx, skull(ctx))
        ears(ctx, inner = false)
        // Nuca: dobras de pele.
        ctx.b.hline(ctx.cx - 6, ctx.cx + 6, ctx.bot - 5, ctx.p.furDark)
        ctx.b.hline(ctx.cx - 4, ctx.cx + 4, ctx.bot - 3, ctx.p.furDark)
    }
}

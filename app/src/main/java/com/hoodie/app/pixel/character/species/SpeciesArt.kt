package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterLimbPainter
import com.hoodie.app.pixel.character.CharacterMask
import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.EyeStyle
import com.hoodie.app.pixel.character.Tones
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Ears
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Mouth
import com.hoodie.app.pixel.sprite.procedural.R

/** Formas de boca por anatomia: "w" felino, linha de focinho, carranca de Bulldog. */
enum class MouthKind { CAT_W, LINE, FROWN }

/**
 * Peças faciais e anatômicas compartilhadas pelas espécies V3. Cada peça respeita o
 * orçamento de cor: contorno + 3 tons de pelo + interno (orelha/nariz) + acento.
 */
object SpeciesArt {

    // ───── Cabeça ─────

    /** Silhueta arredondada padrão (mesma linguagem do Hoodie), já pintada com luz e sombra. */
    fun headMask(ctx: HeadContext, radius: Int): CharacterMask =
        CharacterMask.roundRect(ctx.l, ctx.t, ctx.r, ctx.bot, radius)

    fun paintFur(ctx: HeadContext, mask: CharacterMask) =
        mask.paint(ctx.b, Tones.fur(ctx.p), ctx.art.shading.takeIf { it.useFurShadow })

    /** Orelhas abaixam 2 px quando relaxadas/caídas; alerta sobe 1 px. */
    fun earDrop(ctx: HeadContext): Int = when (ctx.pose.ears) {
        Ears.DOWN, Ears.RELAXED -> 2
        Ears.ALERT -> -1
        else -> 0
    } + ctx.earLag

    /** Orelha pontuda (gato, guaxinim): triângulo com miolo rosado. */
    fun pointedEar(b: PixelBuffer, p: CharacterPalette, apexX: Int, apexY: Int, baseLeft: Int, baseRight: Int, baseY: Int, inner: Boolean = true) {
        CharacterMask.trapezoid(apexY, baseY, apexX, apexX + 1, baseLeft, baseRight).paint(b, Tones.fur(p), null)
        if (inner && baseY - apexY >= 5) {
            val mid = (baseLeft + baseRight) / 2
            CharacterMask.trapezoid(apexY + 3, baseY - 1, mid, mid, baseLeft + 3, baseRight - 3).fill(b, p.inner)
        }
    }

    // ───── Olhos ─────

    /**
     * Olhos V3: pupila grande com brilho (vida no olhar das referências), pálpebra para
     * sono/meio-aberto e arco feliz. [xs] são os centros; [y] o topo do olho.
     */
    fun eyes(ctx: HeadContext, xs: List<Int>, y: Int, style: EyeStyle) {
        val b = ctx.b; val ink = ctx.p.outline; val glint = ctx.p.shirt
        val e = ctx.pose.eyes
        val (w, h) = when (style) {
            EyeStyle.SOFT -> 2 to 3
            EyeStyle.ROUND -> 3 to 3
            EyeStyle.HEAVY -> 3 to 3
            EyeStyle.MASKED -> 3 to 3
            EyeStyle.HOODIE -> 3 to 4
        }
        xs.forEach { cx0 ->
            val cx = cx0 + when (e) { Eyes.LOOK_LEFT -> -1; Eyes.LOOK_RIGHT -> 1; else -> 0 }
            val y0 = y + when (e) { Eyes.LOOK_UP -> -1; Eyes.LOOK_DOWN -> 1; else -> 0 }
            val x0 = cx - w / 2
            val x1 = x0 + w - 1
            when (e) {
                Eyes.CLOSED -> {
                    b.hline(x0 - 1, x1 + 1, y0 + h / 2 + 1, ink)
                    b.set(x0 - 1, y0 + h / 2, ink)
                }
                Eyes.HAPPY -> {
                    b.set(x0 - 1, y0 + 2, ink); b.hline(x0, x1, y0 + 1, ink); b.set(x1 + 1, y0 + 2, ink)
                }
                Eyes.HALF, Eyes.SLEEPY -> {
                    // Pálpebra pesada: só a metade de baixo do olho aparece.
                    b.hline(x0 - 1, x1 + 1, y0 + h / 2, ink)
                    b.box(x0, y0 + h / 2 + 1, x1, y0 + h - 1, ink)
                }
                Eyes.WIDE -> {
                    b.box(x0 - 1, y0 - 1, x1 + 1, y0 + h - 1, ink)
                    if (ctx.art.face.eyeGlint) b.box(x0 - 1, y0 - 1, x0, y0, glint)
                }
                Eyes.FOCUSED -> {
                    b.hline(x0 - 1, x1 + 1, y0, ink)
                    b.box(x0, y0 + 1, x1, y0 + h - 1, ink)
                }
                else -> {
                    b.box(x0, y0, x1, y0 + h - 1, ink)
                    if (ctx.art.face.eyeGlint) b.set(x0, y0, glint)
                }
            }
            if (style == EyeStyle.HEAVY || ctx.art.face.heavyBrow) {
                // Sobrancelha pesada inclinada para o centro (cara de "chefe").
                val inward = if (cx < ctx.cx) 1 else -1
                b.hline(x0 - 1, x1 + 1, y0 - 2, ctx.p.furDark)
                b.set(if (inward > 0) x1 + 1 else x0 - 1, y0 - 1, ctx.p.furDark)
                b.hline(x0 - 1, x1 + 1, y0 - 1, ink)
            }
        }
    }

    /** Olho de perfil: um só, perto da frente da cabeça. */
    fun sideEye(ctx: HeadContext, x: Int, y: Int, style: EyeStyle) = eyes(ctx, listOf(x), y, style)

    // ───── Nariz e boca ─────

    /** Nariz com reflexo de 1 px. */
    fun nose(b: PixelBuffer, p: CharacterPalette, cx: Int, y: Int, w: Int, h: Int, color: Int = p.outline, glint: Boolean = true) {
        CharacterMask.roundRect(cx - w / 2, y, cx - w / 2 + w - 1, y + h - 1, if (w >= 4) 1 else 0).fill(b, color)
        if (glint && w >= 3) b.set(cx - w / 2 + 1, y, p.furLight)
    }

    /** Boca de acordo com a expressão da pose; [x] é o centro, [y] a linha da boca. */
    fun mouth(ctx: HeadContext, x: Int, y: Int, kind: MouthKind) {
        val b = ctx.b; val ink = ctx.p.outline
        when (ctx.pose.mouth) {
            Mouth.OPEN -> {
                b.box(x - 2, y, x + 2, y + 2, ink)
                b.hline(x - 1, x + 1, y + 2, ctx.p.inner)
            }
            Mouth.FLAT -> b.hline(x - 2, x + 2, y, ink)
            Mouth.CHEW -> { b.set(x - 2, y, ink); b.hline(x - 1, x + 1, y + 1, ink); b.set(x + 2, y, ink) }
            Mouth.SMILE -> when (kind) {
                MouthKind.CAT_W -> {
                    b.set(x, y - 1, ink)
                    b.set(x - 1, y, ink); b.set(x + 1, y, ink)
                    b.set(x - 2, y - 1, ink); b.set(x + 2, y - 1, ink)
                }
                MouthKind.LINE -> {
                    b.vline(x, y - 1, y, ink)
                    b.hline(x - 2, x - 1, y + 1, ink); b.hline(x + 1, x + 2, y + 1, ink)
                }
                MouthKind.FROWN -> {
                    b.vline(x, y - 2, y - 1, ink)
                    b.hline(x - 2, x + 2, y, ink)
                    b.set(x - 3, y + 1, ink); b.set(x + 3, y + 1, ink)
                }
            }
        }
    }

    fun blush(ctx: HeadContext, xs: List<Int>, y: Int) {
        if (!ctx.pose.blush || ctx.facing == Facing.BACK) return
        xs.forEach { ctx.b.hline(it - 1, it, y, ctx.p.inner) }
    }

    // ───── Mãos, pés e cauda ─────

    /** Pata com a mesma silhueta 6×6 chanfrada da pata do Hoodie, com realce e sombra. */
    fun paw(b: PixelBuffer, p: CharacterPalette, x: Int, y: Int) {
        CharacterLimbPainter.drawShape(b, R(x - 3, y - 3, x + 2, y + 2, r = 2), p.fur, p.outline)
        b.box(x - 2, y - 2, x + 1, y - 2, p.furLight)
        b.set(x, y + 1, p.furDark)
        b.set(x + 1, y, p.furDark)
    }

    /**
     * Pé descalço com o perfil arredondado de 6 px do pé do Hoodie: almofada, sola
     * escura e dois dedos marcados. O pé distante fica um tom mais escuro.
     */
    fun foot(b: PixelBuffer, p: CharacterPalette, x: Int, y: Int, width: Int, farSide: Boolean, facing: Facing) {
        val half = width / 2
        val toe = if (facing == Facing.SIDE) 2 else 0
        val x0 = x - half; val x1 = x - half + width - 1 + toe
        CharacterLimbPainter.drawShape(b, R(x0, y - 5, x1, y, r = 2), if (farSide) p.furDark else p.fur, p.outline)
        if (!farSide) b.hline(x0 + 2, x0 + 4, y - 4, p.furLight)
        b.hline(x0 + 2, x1 - 2, y - 1, p.furDark)
        if (facing != Facing.BACK) {
            b.set(x - 2 + toe, y - 1, p.outline)
            b.set(x + 2 + toe, y - 1, p.outline)
        }
    }

    /** Caudas por estilo; [TailContext.swing] dá o follow-through do corpo. */
    fun tail(ctx: TailContext, style: TailStyle) {
        val b = ctx.b; val p = ctx.p; val d = ctx.direction; val s = ctx.swing
        val x = ctx.rootX; val y = ctx.rootY
        when (style) {
            TailStyle.NONE -> Unit
            TailStyle.DUCK -> CharacterMask.trapezoid(y - 2, y + 2, x + d * 4, x + d * 4, x, x + d * 2).paint(b, Tones.fur(p), null)
            TailStyle.SHORT -> CharacterMask.ellipse(x + d * (3 + s / 2), y, 3, 3).paint(b, Tones(p.furLight, p.furLight, p.fur, p.outline), null)
            TailStyle.CAT -> {
                // Curva em S: sobe da raiz e dobra na ponta.
                CharacterMask.capsule(x, y, x + d * 6, y - 3, 4)
                    .union(CharacterMask.capsule(x + d * 6, y - 3, x + d * (8 + s), y - 11, 4))
                    .union(CharacterMask.capsule(x + d * (8 + s), y - 11, x + d * (6 + s), y - 15, 4))
                    .paint(b, Tones.fur(p), null)
                b.set(x + d * (6 + s), y - 14, p.furLight)
            }
            TailStyle.LONG -> {
                // Cauda de rato: fina, longa, curvando para cima.
                CharacterMask.capsule(x, y, x + d * 6, y + 4, 2)
                    .union(CharacterMask.capsule(x + d * 6, y + 4, x + d * (11 + s), y - 2, 2))
                    .union(CharacterMask.capsule(x + d * (11 + s), y - 2, x + d * (10 + s), y - 9, 2))
                    .paint(b, Tones(p.inner, p.inner, p.furDark, p.outline), null)
            }
            TailStyle.RINGED -> {
                // Cauda grossa de guaxinim com anéis: o elemento mais forte da silhueta.
                val mask = CharacterMask.capsule(x, y, x + d * 5, y - 2, 7)
                    .union(CharacterMask.capsule(x + d * 5, y - 2, x + d * (9 + s), y - 9, 8))
                    .union(CharacterMask.ellipse(x + d * (9 + s), y - 11, 4, 4))
                mask.paint(b, Tones.fur(p), null)
                // Anéis escuros perpendiculares ao eixo.
                for (k in 0..2) {
                    val ry = y - 2 - k * 4
                    val rx = x + d * (3 + k * 2 + if (k > 0) s / 2 else 0)
                    for (dx in -4..4) {
                        val px = rx + dx; val py = ry + dx * d / 2
                        if (mask.interior(px, py)) b.set(px, py, p.furDark)
                        if (mask.interior(px, py + 1)) b.set(px, py + 1, p.furDark)
                    }
                }
                b.set(x + d * (9 + s), y - 13, p.outline)
            }
        }
    }
}

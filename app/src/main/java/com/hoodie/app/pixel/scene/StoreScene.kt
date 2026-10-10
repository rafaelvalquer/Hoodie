package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Loja de roupas: porta de vidro, vitrine com manequim, duas araras (camisetas e
 * vestidos/casacos), provador com cortina, espelho de chão, prateleira de dobrados
 * e balcão com caixa e sacolas. Bem diferente do mercado (gôndolas, carrinho, esteira).
 *
 * Props dinâmicos: peça some da arara quando o Hoodie pega (GARMENT_HELD) ou o NPC
 * leva ([SceneEnv.shoppingNpc]); cortina fecha com o Hoodie dentro (CURTAIN_CLOSED,
 * o prop fica na frente dele); sacola sai do balcão (BAG_HELD); caixa acende
 * (CHECKOUT_ACTIVE ou NPC pagando).
 */
class StoreScene : PixelScene(SceneId.STORE) {
    override val spots = mapOf(
        SpotId.RACK_A to Spot(83, 234),
        SpotId.RACK_B to Spot(155, 234),
        SpotId.MIRROR to Spot(52, 286),
        SpotId.FITTING_ROOM to Spot(216, 200),
        SpotId.CHECKOUT to Spot(150, 296),
        SpotId.DOOR to Spot(26, 162),
        SpotId.CENTER to Spot(118, 262),
    )
    override val defaultSpot = SpotId.RACK_A
    override val hasAnimatedDoor = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        // Parede rosada com faixa de papel listrado e piso de tábuas de madeira.
        b.box(0, 0, 239, 151, WALL)
        for (x in 0..239 step 8) b.vline(x, 14, 58, WALL_STRIPE)
        b.box(0, 0, 239, 12, WALL_TOP)
        b.box(0, 58, 239, 61, WALL_TRIM); b.hline(0, 239, 58, P.OUTLINE)
        SceneArt.baseboard(b, 151, P.WOOD_DARK)
        for (y in 153..319 step 9) {
            val shift = if ((y / 9) % 2 == 0) 0 else 18
            b.box(0, y, 239, minOf(y + 8, 319), if ((y / 9) % 3 == 0) P.WOOD_LIGHT else P.WOOD)
            b.hline(0, 239, minOf(y + 8, 319), P.WOOD_DARK)
            for (x in shift..239 step 36) b.vline(x, y, minOf(y + 7, 319), P.WOOD_DARK)
        }
        b.box(0, 153, 239, 155, PixelBuffer.mix(P.WOOD_DARK, P.OUTLINE, 0.3f))
        // Letreiro "LOJA" em pixel dobrado.
        b.outlined(84, 18, 160, 50, SIGN, P.OUTLINE)
        b.hline(86, 158, 20, PixelBuffer.mix(SIGN, P.WHITE, 0.35f))
        bigGlyph(b, LOJA, 93, 24, P.WHITE)
        // Vitrine iluminada com manequim, ao lado da porta.
        b.outlined(52, 72, 86, 151, VITRINE_BACK, P.OUTLINE)
        b.box(54, 74, 84, 78, P.LAMP_LIGHT)
        b.outlined(55, 140, 83, 149, P.WOOD_DARK, P.OUTLINE)
        mannequin(b, 69, 140)
        // Prateleira de roupas dobradas na parede (atrás das araras).
        b.outlined(96, 66, 184, 70, P.WOOD_DARK, P.OUTLINE)
        b.outlined(96, 96, 184, 100, P.WOOD_DARK, P.OUTLINE)
        for ((i, x) in (100..176 step 13).withIndex()) {
            folded(b, x, 65, TEES[i % TEES.size])
            folded(b, x, 95, COATS[(i + 1) % COATS.size])
        }
        // Spots de luz quente no teto.
        for (lx in intArrayOf(60, 140, 216)) {
            b.outlined(lx - 6, 0, lx + 6, 5, P.METAL_DARK, P.OUTLINE)
            b.hline(lx - 4, lx + 4, 4, P.LAMP_LIGHT)
        }
    }

    override fun props(): List<Prop> = listOf(
        // Porta automática de vidro (mesma mecânica da do mercado, moldura de madeira).
        Prop(0) { b, env, _ ->
            b.outlined(6, 76, 46, 151, P.WOOD_DARK, P.OUTLINE)
            val sky = SceneArt.sky(env.period)
            b.box(8, 78, 44, 149, PixelBuffer.mix(sky.bottom, P.WHITE, 0.2f))
            val gap = env.doorFrame.coerceIn(0, SceneEnv.DOOR_OPEN) * 6
            val glass = 0xFFB8DDEE.toInt()
            b.outlined(8 - gap, 78, 26 - gap, 149, glass, P.METAL_DARK)
            b.outlined(26 + gap, 78, 44 + gap, 149, glass, P.METAL_DARK)
            b.line(12 - gap, 84, 18 - gap, 96, P.WHITE); b.line(30 + gap, 84, 36 + gap, 96, P.WHITE)
            b.box(6, 70, 46, 75, P.WOOD_DARK)
        },
        // Etiqueta de liquidação piscando na parede.
        Prop(0) { b, _, t ->
            val on = (t / 700) % 2 == 0L
            b.vline(204, 12, 22, P.OUTLINE)
            b.outlined(192, 22, 216, 40, if (on) P.YELLOW else 0xFFE8B84A.toInt(), P.OUTLINE)
            b.glyph(PERCENT, 200, 27, if (on) P.RED else 0xFFAA4040.toInt())
        },
        // Arara A (camisetas): perde uma peça quando o Hoodie pega ou o comprador leva.
        Prop(214) { b, env, t ->
            val gaps = buildSet {
                if (SceneFlag.GARMENT_HELD in env.flags || SceneFlag.BAG_HELD in env.flags) add(2)
                if (env.shoppingNpc.productRemovedA) add(0)
            }
            rack(b, 50, 116, TEES, long = false, gaps = gaps, t = t)
        },
        // Arara B (vestidos e casacos compridos).
        Prop(214) { b, env, t ->
            rack(b, 122, 188, COATS, long = true, gaps = if (env.shoppingNpc.productRemovedB) setOf(1) else emptySet(), t = t + 400)
        },
        // Provador: cabine com varão; a cortina abre para o lado ou fecha na frente do Hoodie.
        Prop(150) { b, _, _ ->
            b.outlined(192, 64, 238, 70, P.WOOD_DARK, P.OUTLINE)
            b.box(194, 71, 236, 205, FITTING_INSIDE)
            b.vline(193, 71, 205, P.OUTLINE); b.vline(237, 71, 205, P.OUTLINE)
            b.box(196, 72, 234, 74, P.LAMP_LIGHT)
            // Banquinho e gancho lá dentro.
            b.outlined(222, 176, 232, 186, P.WOOD, P.OUTLINE)
            b.set(228, 100, P.OUTLINE); b.set(228, 101, P.METAL)
        },
        Prop(208) { b, env, t ->
            val closed = SceneFlag.CURTAIN_CLOSED in env.flags
            b.hline(193, 237, 76, P.METAL)
            if (closed) {
                // Cortina fechada, ondulando, por cima do Hoodie (baseline 208 > pés em 200).
                for (x in 195..236) {
                    val fold = ((x - 195) / 4) % 2 == 0
                    b.vline(x, 77, 205, if (fold) CURTAIN else CURTAIN_SHADE)
                }
                b.hline(195, 236, 205, P.OUTLINE)
                b.vline(195, 77, 205, P.OUTLINE); b.vline(236, 77, 205, P.OUTLINE)
                // A cortina se mexe enquanto ele troca de roupa.
                if ((t / 350) % 2 == 0L) b.vline(214, 150, 204, CURTAIN_SHADE)
            } else {
                // Aberta: franzida à esquerda, balança de leve.
                val sway = if ((t / 900) % 2 == 0L) 0 else 1
                b.outlined(194, 77, 202 + sway, 205, CURTAIN, P.OUTLINE)
                for (x in 196..200 step 2) b.vline(x + sway, 79, 203, CURTAIN_SHADE)
            }
        },
        // Espelho de chão à esquerda (o Hoodie se olha nele).
        Prop(282) { b, _, t ->
            b.outlined(12, 190, 36, 282, P.WOOD_DARK, P.OUTLINE)
            b.box(15, 193, 33, 279, MIRROR_GLASS)
            val glint = ((t / 160) % 40).toInt()
            if (glint < 12) b.line(17, 212 + glint, 22, 200 + glint, P.WHITE)
            b.line(26, 230, 31, 222, PixelBuffer.mix(MIRROR_GLASS, P.WHITE, 0.6f))
            b.outlined(8, 280, 40, 284, P.WOOD_DARK, P.OUTLINE)
        },
        // Balcão com caixa, terminal e pilha de sacolas.
        Prop(304) { b, env, t ->
            val active = SceneFlag.CHECKOUT_ACTIVE in env.flags || env.shoppingNpc.checkoutActive
            b.outlined(168, 262, 238, 272, P.WOOD_LIGHT, P.OUTLINE)
            b.outlined(168, 271, 238, 304, COUNTER, P.OUTLINE)
            b.box(172, 278, 234, 280, PixelBuffer.mix(COUNTER, P.WHITE, 0.3f))
            // Sacolas (uma sai quando o Hoodie leva a dele).
            val bags = if (SceneFlag.BAG_HELD in env.flags) 1 else 2
            for (i in 0 until bags) {
                val bx = 208 + i * 15
                val top = 246 - i * 2
                b.outlined(bx, top, bx + 13, 262, BAG_COLORS[i], P.OUTLINE)
                b.hline(bx + 1, bx + 12, top + 1, PixelBuffer.mix(BAG_COLORS[i], P.WHITE, 0.35f))
                // Alças em arco e o logo da loja.
                b.line(bx + 3, top, bx + 4, top - 5, P.OUTLINE); b.hline(bx + 4, bx + 9, top - 5, P.OUTLINE); b.line(bx + 9, top - 5, bx + 10, top, P.OUTLINE)
                b.box(bx + 5, top + 6, bx + 8, top + 9, P.WHITE)
            }
            // Caixa registradora e terminal.
            b.outlined(176, 242, 202, 262, 0xFF2A2D3E.toInt(), P.OUTLINE)
            b.box(179, 245, 199, 251, if (active) P.CODE_1 else P.SCREEN_OFF)
            if (active && (t / 300) % 2 == 0L) b.hline(181, 197, 248, P.OUTLINE)
            b.box(180, 254, 198, 259, 0xFF3A3F55.toInt())
            if (active) b.outlined(204, 252, 210, 262, P.WHITE, P.OUTLINE) // recibo saindo
        },
        Prop(306) { b, _, _ -> SceneArt.plant(b, 100, 306, big = false) },
    )

    /** Arara: varão, pés e peças nos cabides; [gaps] = índices das peças que saíram. Os cabides balançam devagar. */
    private fun rack(b: PixelBuffer, x0: Int, x1: Int, colors: IntArray, long: Boolean, gaps: Set<Int>, t: Long) {
        // Pés e varão.
        b.vline(x0, 114, 212, P.METAL_DARK); b.vline(x1, 114, 212, P.METAL_DARK)
        b.hline(x0 - 4, x0 + 4, 213, P.OUTLINE); b.hline(x1 - 4, x1 + 4, 213, P.OUTLINE)
        b.outlined(x0, 112, x1, 115, P.METAL, P.OUTLINE)
        var i = 0
        var x = x0 + 4
        while (x + 14 <= x1) {
            if (i !in gaps) {
                val swing = if (((t / 600) + i) % 5 == 0L) 1 else 0
                garment(b, x + swing, 116, colors[i % colors.size], long)
            }
            x += 15; i++
        }
    }

    /**
     * Peça no cabide (14 px): gancho, cabide em V, mangas e corpo. Camiseta é curta e
     * tem gola; vestido/casaco é comprido, abre em saia e tem cinto.
     */
    private fun garment(b: PixelBuffer, x: Int, y: Int, c: Int, long: Boolean) {
        val light = PixelBuffer.mix(c, P.WHITE, 0.3f)
        val dark = PixelBuffer.mix(c, P.OUTLINE, 0.35f)
        b.set(x + 7, y - 1, P.OUTLINE); b.set(x + 7, y, P.OUTLINE)
        b.line(x + 2, y + 3, x + 7, y + 1, P.OUTLINE); b.line(x + 7, y + 1, x + 12, y + 3, P.OUTLINE)
        if (long) {
            b.outlined(x + 2, y + 3, x + 12, y + 20, c, P.OUTLINE)
            b.outlined(x, y + 20, x + 14, y + 52, c, P.OUTLINE)
            b.box(x + 3, y + 4, x + 11, y + 21, c)
            b.hline(x + 3, x + 11, y + 20, dark)
            b.vline(x + 4, y + 24, y + 50, light); b.vline(x + 10, y + 24, y + 50, dark)
        } else {
            // Mangas curtas e corpo.
            b.outlined(x, y + 3, x + 14, y + 10, c, P.OUTLINE)
            b.outlined(x + 2, y + 9, x + 12, y + 32, c, P.OUTLINE)
            b.box(x + 1, y + 4, x + 13, y + 10, c)
            b.box(x + 5, y + 3, x + 9, y + 5, dark)
            b.vline(x + 4, y + 11, y + 30, light)
            b.box(x + 6, y + 16, x + 9, y + 19, P.WHITE)
        }
    }

    private fun folded(b: PixelBuffer, x: Int, baseY: Int, c: Int) {
        b.outlined(x, baseY - 8, x + 10, baseY - 1, c, P.OUTLINE)
        b.hline(x + 2, x + 8, baseY - 5, PixelBuffer.mix(c, P.WHITE, 0.3f))
    }

    private fun mannequin(b: PixelBuffer, cx: Int, baseY: Int) {
        b.vline(cx, baseY - 18, baseY - 1, P.METAL_DARK)
        b.hline(cx - 5, cx + 5, baseY - 1, P.OUTLINE)
        b.disc(cx, baseY - 58, 5, P.OUTLINE); b.disc(cx, baseY - 58, 4, MANNEQUIN)
        // Vestido da vitrine.
        b.outlined(cx - 7, baseY - 52, cx + 7, baseY - 46, COATS[0], P.OUTLINE)
        b.outlined(cx - 9, baseY - 46, cx + 9, baseY - 18, COATS[0], P.OUTLINE)
        b.hline(cx - 7, cx + 7, baseY - 36, P.YELLOW)
    }

    private fun bigGlyph(b: PixelBuffer, rows: List<String>, x: Int, y: Int, c: Int) {
        rows.forEachIndexed { r, row -> row.forEachIndexed { col, ch -> if (ch == '#') b.box(x + col * 4, y + r * 4, x + col * 4 + 3, y + r * 4 + 3, c) } }
    }

    override fun lights(env: SceneEnv): List<Light> = buildList {
        add(Light.Glow(60, 4, 80, 0.7f)); add(Light.Glow(140, 4, 90, 0.75f)); add(Light.Glow(216, 4, 70, 0.6f))
        // Vitrine e provador sempre iluminados.
        add(Light.Emissive(54, 74, 84, 138))
        add(Light.Emissive(196, 72, 234, 80))
        add(Light.Emissive(192, 22, 216, 40))
        if (SceneFlag.CHECKOUT_ACTIVE in env.flags || env.shoppingNpc.checkoutActive) add(Light.Emissive(179, 245, 199, 251))
        if (env.period == DayPeriod.DAY || env.period == DayPeriod.MORNING) add(Light.Emissive(8, 78, 44, 149))
    }

    private companion object {
        const val WALL = 0xFFF2DCD6.toInt()
        const val WALL_STRIPE = 0xFFEACCC5.toInt()
        const val WALL_TOP = 0xFFE3C2BA.toInt()
        const val WALL_TRIM = 0xFFC98E86.toInt()
        const val SIGN = 0xFF9C6BC4.toInt()
        const val VITRINE_BACK = 0xFFFBEFD9.toInt()
        const val FITTING_INSIDE = 0xFFE6D3C7.toInt()
        const val CURTAIN = 0xFF7F5BB0.toInt()
        const val CURTAIN_SHADE = 0xFF5F4289.toInt()
        const val MIRROR_GLASS = 0xFFB9D8E8.toInt()
        const val MANNEQUIN = 0xFFF1E3C3.toInt()
        const val COUNTER = 0xFF6F4A8E.toInt()
        val TEES = intArrayOf(0xFFE07A93.toInt(), 0xFF7FE0C2.toInt(), 0xFFF2CF5B.toInt(), 0xFF86A9E8.toInt(), 0xFFF6F3EA.toInt())
        val COATS = intArrayOf(0xFFC9544F.toInt(), 0xFF4F7FC9.toInt(), 0xFF6B8E5A.toInt(), 0xFFE58A3A.toInt())
        val BAG_COLORS = intArrayOf(0xFF9C6BC4.toInt(), 0xFFE07A93.toInt())

        val LOJA = listOf(
            "#   ###   #  # ",
            "#   # #   # # #",
            "#   # #   # ###",
            "#   # # # # # #",
            "### ### ### # #",
        )
        val PERCENT = listOf(
            "##  #",
            "## # ",
            "  #  ",
            " # ##",
            "#  ##",
        )
    }
}

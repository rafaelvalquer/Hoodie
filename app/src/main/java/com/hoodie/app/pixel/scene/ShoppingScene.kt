package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Mercado: porta automática de vidro, duas gôndolas, carrinho, caixa com esteira e placa de promoção.
 * Props dinâmicos: buraco na prateleira quando o produto sai (ITEM_HELD/ITEM_IN_CART),
 * produtos dentro do carrinho (ITEM_IN_CART) e esteira + visor do caixa (CHECKOUT_ACTIVE).
 */
class ShoppingScene : PixelScene(SceneId.SHOPPING) {
    override val spots = mapOf(
        SpotId.AISLE_A to Spot(84, 216),
        SpotId.AISLE_B to Spot(170, 216),
        SpotId.CART to Spot(110, 262),
        SpotId.CHECKOUT to Spot(148, 294),
        SpotId.DOOR to Spot(26, 162),
        SpotId.CENTER to Spot(124, 238),
    )
    override val defaultSpot = SpotId.AISLE_A
    override val hasAnimatedDoor = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        // Parede clara com faixa de marca e piso de ladrilho.
        b.box(0, 0, 239, 151, 0xFFF0EBDD.toInt())
        b.box(0, 0, 239, 12, 0xFFE0D8C4.toInt())
        b.box(0, 62, 239, 70, 0xFF4FA36A.toInt()); b.hline(0, 239, 62, P.LEAF_DARK); b.hline(0, 239, 70, P.LEAF_DARK)
        SceneArt.baseboard(b, 151, 0xFF8A93A8.toInt())
        for (y in 153..319 step 14) for (x in 0..239 step 14) {
            b.box(x, y, x + 13, minOf(y + 13, 319), if ((x / 14 + y / 14) % 2 == 0) 0xFFEDEFF2.toInt() else 0xFFD5DAE2.toInt())
        }
        b.box(0, 153, 239, 155, PixelBuffer.mix(0xFFD5DAE2.toInt(), P.OUTLINE, 0.35f))
        // Letreiro "MERCADO".
        b.outlined(76, 20, 200, 48, 0xFFC9544F.toInt(), P.OUTLINE)
        b.glyph(listOf(
            "#   # ### ##   ##  #  ##   # ",
            "## ## #   # # #   # # # # # #",
            "# # # ##  ##  #   ### # # # #",
            "#   # #   # # #   # # # # # #",
            "#   # ### # #  ## # # ##   # ",
        ), 124, 32, P.WHITE)
        // Lâmpadas tubulares no teto.
        for (lx in intArrayOf(40, 120, 200)) b.outlined(lx - 18, 2, lx + 18, 6, P.WHITE, P.OUTLINE)
    }

    override fun props(): List<Prop> = listOf(
        // Porta automática de vidro: as folhas deslizam para os lados conforme doorFrame.
        Prop(0) { b, env, _ ->
            b.outlined(6, 76, 46, 151, P.METAL_DARK, P.OUTLINE)
            val sky = SceneArt.sky(env.period)
            b.box(8, 78, 44, 149, PixelBuffer.mix(sky.bottom, P.WHITE, 0.2f))
            val gap = env.doorFrame.coerceIn(0, SceneEnv.DOOR_OPEN) * 6
            val glass = 0xFFB8DDEE.toInt()
            b.outlined(8 - gap, 78, 26 - gap, 149, glass, P.METAL_DARK)
            b.outlined(26 + gap, 78, 44 + gap, 149, glass, P.METAL_DARK)
            b.line(12 - gap, 84, 18 - gap, 96, P.WHITE); b.line(30 + gap, 84, 36 + gap, 96, P.WHITE)
            b.box(6, 70, 46, 75, 0xFF3A3F55.toInt())
        },
        // Placa de promoção que pisca.
        Prop(0) { b, _, t ->
            val on = (t / 600) % 2 == 0L
            b.vline(222, 0, 18, P.OUTLINE)
            b.outlined(206, 18, 238, 40, if (on) P.YELLOW else 0xFFE8B84A.toInt(), P.OUTLINE)
            b.glyph(listOf(
                "### ### # #",
                "#   # #   #",
                "### # #  # ",
                "  # # # #  ",
                "### ### # #",
            ), 217, 26, if (on) P.RED else 0xFFAA4040.toInt())
            b.hline(210, 234, 34, P.RED)
        },
        // Gôndola A (frutas/latas): perde um produto quando o Hoodie pega.
        Prop(196) { b, env, _ ->
            val taken = SceneFlag.ITEM_HELD in env.flags || SceneFlag.ITEM_IN_CART in env.flags ||
                SceneFlag.CHECKOUT_ACTIVE in env.flags
            shelf(b, 52, 120, 0, if (taken) 1 else -1)
        },
        // Gôndola B (caixas e garrafas).
        Prop(196) { b, _, _ -> shelf(b, 138, 204, 1, -1) },
        // Carrinho: vazio ou com produtos.
        Prop(282) { b, env, _ ->
            val wire = P.METAL
            b.outlined(84, 246, 132, 272, 0xFFD8DEE8.toInt(), P.OUTLINE)
            for (x in 88..128 step 5) b.vline(x, 248, 270, wire)
            for (y in 252..268 step 5) b.hline(86, 130, y, wire)
            // Alça perto de onde o Hoodie segura.
            b.hline(130, 140, 244, P.OUTLINE); b.box(138, 242, 142, 246, P.RED)
            b.line(132, 272, 128, 280, P.OUTLINE); b.line(86, 272, 90, 280, P.OUTLINE)
            for (wx in intArrayOf(92, 124)) { b.disc(wx, 281, 2, P.OUTLINE); b.set(wx, 281, P.METAL) }
            if (SceneFlag.ITEM_IN_CART in env.flags || SceneFlag.CHECKOUT_ACTIVE in env.flags) {
                b.outlined(94, 238, 106, 250, 0xFF4F7FC9.toInt(), P.OUTLINE); b.hline(96, 104, 243, P.WHITE)
                b.disc(114, 244, 5, P.OUTLINE); b.disc(114, 244, 4, 0xFFE58A3A.toInt())
            }
            if (SceneFlag.CHECKOUT_ACTIVE !in env.flags) {
                b.outlined(118, 240, 126, 250, 0xFFF29B9B.toInt(), P.OUTLINE)
            }
        },
        // Caixa: balcão, esteira, terminal e visor.
        Prop(304) { b, env, t ->
            val active = SceneFlag.CHECKOUT_ACTIVE in env.flags
            b.outlined(174, 262, 238, 272, 0xFF3A3F55.toInt(), P.OUTLINE)
            // Esteira com riscas que correm quando o caixa está ativo.
            val shift = if (active) ((t / 120) % 6).toInt() else 0
            for (x in 176..236 step 6) b.vline((x + shift).coerceAtMost(236), 264, 270, 0xFF596278.toInt())
            b.outlined(174, 271, 238, 304, 0xFFC9544F.toInt(), P.OUTLINE)
            b.box(178, 278, 234, 280, 0xFFE07A72.toInt())
            if (active) {
                val px = 180 + ((t / 90) % 30).toInt()
                b.outlined(px, 254, px + 10, 263, 0xFF4F7FC9.toInt(), P.OUTLINE)
                b.disc(px + 18, 258, 4, P.OUTLINE); b.disc(px + 18, 258, 3, 0xFFE58A3A.toInt())
            }
            // Terminal de pagamento + visor.
            b.outlined(186, 240, 206, 262, 0xFF2A2D3E.toInt(), P.OUTLINE)
            b.box(189, 243, 203, 250, if (active) P.CODE_1 else P.SCREEN_OFF)
            if (active && (t / 300) % 2 == 0L) b.hline(191, 201, 246, P.OUTLINE)
            b.box(190, 253, 202, 259, 0xFF3A3F55.toInt())
            b.vline(224, 232, 262, P.METAL_DARK)
            b.outlined(216, 222, 234, 232, P.SCREEN, P.OUTLINE)
            if (active) b.hline(219, 231, 227, P.CODE_2)
        },
        Prop(300) { b, _, _ -> SceneArt.plant(b, 14, 300, big = true) },
    )

    /** Gôndola com 3 prateleiras; [gap] é o índice do produto que sumiu (−1 = nenhum). */
    private fun shelf(b: PixelBuffer, x0: Int, x1: Int, kind: Int, gap: Int) {
        b.outlined(x0, 92, x1, 196, 0xFFDDE2EA.toInt(), P.OUTLINE)
        b.box(x0 + 2, 94, x1 - 2, 98, if (kind == 0) 0xFF4FA36A.toInt() else 0xFF4F7FC9.toInt())
        val colors = if (kind == 0) intArrayOf(0xFFC9544F.toInt(), 0xFFF2CF5B.toInt(), 0xFFE58A3A.toInt(), 0xFF4FA36A.toInt())
        else intArrayOf(0xFF4F7FC9.toInt(), 0xFFF29B9B.toInt(), 0xFFF1E3C3.toInt(), 0xFF9C6BC4.toInt())
        for ((row, base) in intArrayOf(126, 156, 186).withIndex()) {
            b.outlined(x0 + 1, base, x1 - 1, base + 3, P.METAL, P.OUTLINE)
            var k = 0
            var x = x0 + 4
            while (x + 9 < x1) {
                val idx = row * 10 + k
                if (idx != gap) {
                    val c = colors[(idx + kind) % colors.size]
                    if (kind == 0 && row == 0) { b.disc(x + 4, base - 5, 4, P.OUTLINE); b.disc(x + 4, base - 5, 3, c) }
                    else if (kind == 1 && row == 2) { b.outlined(x + 2, base - 18, x + 6, base - 1, c, P.OUTLINE); b.box(x + 3, base - 21, x + 5, base - 18, P.OUTLINE) }
                    else { b.outlined(x, base - 12, x + 8, base - 1, c, P.OUTLINE); b.hline(x + 2, x + 6, base - 7, P.WHITE) }
                }
                x += 11; k++
            }
        }
    }

    override fun lights(env: SceneEnv): List<Light> = buildList {
        add(Light.Glow(40, 4, 90, 0.7f)); add(Light.Glow(120, 4, 100, 0.75f)); add(Light.Glow(200, 4, 90, 0.7f))
        add(Light.Emissive(206, 18, 238, 40))
        if (SceneFlag.CHECKOUT_ACTIVE in env.flags) add(Light.Emissive(189, 243, 203, 250))
        if (env.period == DayPeriod.DAY || env.period == DayPeriod.MORNING) add(Light.Emissive(8, 78, 44, 149))
    }
}

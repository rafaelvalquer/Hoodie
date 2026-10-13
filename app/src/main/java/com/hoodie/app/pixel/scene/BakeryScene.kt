package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.BakeryAmbient
import com.hoodie.app.pixel.npc.venue.BakeryVenue
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Padaria / cafeteria: prateleiras de pães e quadro de menu na parede, cafeteira na bancada de
 * trás, balcão com vitrine de vidro (salgados, doces, bolo), caixa e área de retirada, duas
 * mesas e a porta de vidro.
 *
 * O barista trabalha entre a parede e o balcão: a frente do balcão (baseline 208) passa na frente
 * dele, escondendo as pernas. Os objetos vêm da sessão ([BakeryAmbient]): comanda no caixa,
 * vapor da máquina, xícaras prontas/devolvidas, vão na vitrine e a porta abrindo.
 */
class BakeryScene : PixelScene(SceneId.BAKERY) {
    override val spots = mapOf(
        SpotId.COUNTER to Spot(BakeryVenue.HOODIE_COUNTER_X, BakeryVenue.HOODIE_COUNTER_Y),
        SpotId.CAFE_TABLE to Spot(BakeryVenue.HOODIE_TABLE_X, BakeryVenue.HOODIE_TABLE_Y),
        SpotId.CENTER to Spot(124, 270),
        SpotId.DOOR to Spot(216, 160),
    )
    override val defaultSpot = SpotId.CAFE_TABLE
    override val hasAnimatedDoor = true
    override val backgroundVariants = 3

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        val v = env.variant.mod(3)
        val wall = WALLS[v]
        // Parede de azulejos quentes com rodameio de madeira.
        b.box(0, 0, 239, 150, wall)
        for (y in 0..60 step 10) for (x in 0..239 step 10) b.set(x, y, PixelBuffer.mix(wall, P.OUTLINE, 0.08f))
        b.box(0, 62, 239, 150, PixelBuffer.mix(wall, P.WHITE, 0.12f))
        for (y in 66..150 step 8) b.hline(0, 239, y, PixelBuffer.mix(wall, P.OUTLINE, 0.06f))
        for (x in 0..239 step 12) b.vline(x, 62, 150, PixelBuffer.mix(wall, P.OUTLINE, 0.06f))
        b.box(0, 58, 239, 61, P.WOOD_DARK); b.hline(0, 239, 58, P.OUTLINE)
        // Letreiro "PADARIA".
        b.outlined(8, 10, 92, 34, SIGNS[v], P.OUTLINE)
        b.hline(10, 90, 12, PixelBuffer.mix(SIGNS[v], P.WHITE, 0.35f))
        b.glyph(PADARIA, 14, 18, P.CREAM)
        // Quadro de menu com xícara, croissant e preços.
        b.outlined(100, 8, 186, 54, CHALK, P.WOOD_DARK)
        b.box(102, 10, 184, 52, CHALK)
        cupIcon(b, 108, 16); croissantIcon(b, 108, 34)
        for ((i, y) in listOf(18, 24, 36, 42).withIndex()) {
            b.hline(124, 152 + i * 4, y, 0xFFDDE7E0.toInt()); b.box(170, y - 1, 177, y, P.YELLOW)
        }
        // Prateleiras de pães na parede (atrás do barista).
        for (shelf in intArrayOf(80, 104)) {
            b.outlined(6, shelf, 58, shelf + 3, P.WOOD_DARK, P.OUTLINE)
            for (x in 9..52 step 11) loaf(b, x, shelf - 1, (x / 11 + shelf) % 3)
        }
        // Bancada de trás (armário) com a cafeteira.
        b.outlined(0, 118, 200, 150, P.WOOD, P.OUTLINE)
        b.hline(1, 199, 120, P.WOOD_LIGHT)
        for (x in 24..180 step 44) b.outlined(x, 126, x + 36, 146, P.WOOD_DARK, P.OUTLINE)
        b.outlined(162, 88, 196, 118, MACHINE, P.OUTLINE)
        b.box(165, 91, 193, 96, PixelBuffer.mix(MACHINE, P.WHITE, 0.3f))
        b.outlined(172, 100, 186, 108, P.METAL_DARK, P.OUTLINE)
        b.vline(176, 108, 113, P.OUTLINE); b.vline(182, 108, 113, P.OUTLINE)
        b.box(168, 114, 190, 117, P.METAL_DARK)
        // Xícaras empilhadas ao lado da máquina.
        for (i in 0 until 3) cup(b, 146 + i * 5, 117 - i % 2, P.WHITE)
        // Piso de ladrilhos.
        val tileA = FLOORS_A[v]; val tileB = FLOORS_B[v]
        for (y in 151..319 step 14) for (x in 0..239 step 14) {
            b.box(x, y, minOf(x + 13, 239), minOf(y + 13, 319), if ((x / 14 + y / 14) % 2 == 0) tileA else tileB)
        }
        b.box(0, 150, 239, 152, P.OUTLINE)
    }

    override fun props(): List<Prop> = listOf(
        // Porta de vidro: abre quando o Hoodie passa (doorFrame) ou quando um cliente entra/sai.
        Prop(0) { b, env, _ ->
            val bakery = env.venueAmbient as? BakeryAmbient
            val open = maxOf(env.doorFrame, if (bakery?.doorOpen == true) SceneEnv.DOOR_OPEN else 0)
            b.outlined(196, 70, 236, 151, P.WOOD_DARK, P.OUTLINE)
            val sky = SceneArt.sky(env.period)
            b.box(198, 72, 234, 149, PixelBuffer.mix(sky.bottom, P.WHITE, 0.25f))
            val leaf = 36 - open * 9
            b.outlined(198, 72, 198 + leaf, 149, GLASS, P.WOOD_DARK)
            if (leaf > 8) b.line(202, 80, 208, 92, P.WHITE)
            b.box(198 + leaf - 3, 108, 198 + leaf - 2, 116, P.METAL_DARK)
        },
        // Luminárias pendentes.
        Prop(0) { b, env, _ ->
            val on = env.period != DayPeriod.DAY
            for (lx in intArrayOf(40, 120, 200)) {
                b.vline(lx, 0, 4, P.OUTLINE)
                b.outlined(lx - 7, 4, lx + 7, 10, LAMP, P.OUTLINE)
                b.hline(lx - 5, lx + 5, 11, if (on) P.LAMP_LIGHT else P.CREAM)
            }
        },
        // Cafeteira: luz e vapor enquanto o barista prepara.
        Prop(0) { b, env, t ->
            val brewing = (env.venueAmbient as? BakeryAmbient)?.brewing == true
            b.set(190, 92, if (brewing && (t / 300) % 2 == 0L) P.RED else P.CODE_1)
            if (brewing) {
                val k = ((t / 180) % 4).toInt()
                for (i in 0..2) b.set(178 + i * 2 - (k % 2), 86 - (k + i) % 4 * 2, STEAM)
                b.box(177, 109, 181, 112, COFFEE)
            }
        },
        // Balcão com vitrine, caixa e área de retirada (na frente do barista, atrás dos clientes).
        Prop(208) { b, env, t -> counter(b, env, t) },
        // Mesa dos clientes: cadeiras atrás de quem senta (quadril em y 268), tampo na frente do colo.
        Prop(262) { b, env, _ -> chairs(b, 172, 202, 268, TABLECLOTH[env.variant.mod(3)]) },
        Prop(276) { b, env, _ -> table(b, 158, 216, 268, TABLECLOTH[env.variant.mod(3)]) },
        // Mesa do Hoodie, na frente à esquerda (pés dele em y 284).
        Prop(278) { b, env, _ -> chairs(b, 62, null, 284, TABLECLOTH[(env.variant + 1).mod(3)]) },
        Prop(292) { b, env, _ -> table(b, 36, 94, 284, TABLECLOTH[(env.variant + 1).mod(3)]) },
        // Balcãozinho de pé junto à janela (parede direita).
        Prop(296) { b, _, _ ->
            b.outlined(206, 266, 238, 270, P.WOOD, P.OUTLINE)
            b.vline(234, 271, 296, P.WOOD_DARK)
        },
        Prop(312) { b, _, _ -> SceneArt.plant(b, 14, 312, big = true) },
    )

    private fun counter(b: PixelBuffer, env: SceneEnv, t: Long) {
        val bakery = env.venueAmbient as? BakeryAmbient ?: BakeryAmbient.idle(t)
        // Tampo (vai até a cafeteira: o barista fica sempre atrás dele).
        val right = BakeryVenue.COUNTER_RIGHT_X
        // O tampo fica na altura da cintura do barista (pés em y 186): ele aparece da cintura para cima.
        b.outlined(12, TOP, right, TOP + 6, COUNTER_TOP, P.OUTLINE)
        b.hline(14, right - 2, TOP + 1, PixelBuffer.mix(COUNTER_TOP, P.WHITE, 0.3f))
        // Frente: vitrine de vidro à esquerda, madeira à direita.
        b.outlined(12, TOP + 6, right, 208, P.WOOD_DARK, P.OUTLINE)
        b.outlined(16, TOP + 8, 114, 205, VITRINE, P.OUTLINE)
        b.hline(18, 112, TOP + 9, P.WHITE)
        // Duas bandejas: salgados em cima, doces e bolo embaixo; o vão aparece quando um sai.
        b.hline(18, 112, 193, P.METAL)
        for (i in 0 until 7) {
            if (bakery.showcaseGap && i == 2) continue
            pastry(b, 22 + i * 13, 191, i % 3)
        }
        cake(b, 26, 203)
        for (i in 0 until 4) sweet(b, 56 + i * 13, 203, i)
        b.line(20, TOP + 11, 26, TOP + 17, PixelBuffer.mix(VITRINE, P.WHITE, 0.6f))
        // Painel de madeira com frisos.
        for (x in 122..right - 4 step 8) b.vline(x, TOP + 9, 205, P.WOOD)
        // Caixa registradora com comanda (pedido de cliente ou do Hoodie).
        val ticket = bakery.orderTicket || SceneFlag.ORDER_PENDING in env.flags
        b.outlined(60, TOP - 14, 84, TOP, REGISTER, P.OUTLINE)
        b.box(63, TOP - 11, 81, TOP - 7, if (bakery.registerActive) P.CODE_1 else P.SCREEN_OFF)
        if (bakery.registerActive && (t / 300) % 2 == 0L) b.hline(65, 79, TOP - 9, P.OUTLINE)
        if (ticket) { b.vline(88, TOP - 12, TOP - 1, P.OUTLINE); b.outlined(85, TOP - 14, 91, TOP - 7, P.WHITE, P.OUTLINE) }
        // Retirada: xícaras prontas (com vapor) e devolvidas (vazias, aguardando o pano).
        for (i in 0 until bakery.cupsReady.coerceAtMost(2)) {
            cup(b, 138 + i * 8, TOP - 1, P.WHITE)
            if ((t / 300 + i) % 2 == 0L) b.set(140 + i * 8, TOP - 8, STEAM)
        }
        for (i in 0 until bakery.dirtyCups.coerceAtMost(3)) cup(b, 118 + i * 6, TOP - 1, 0xFFD8D2C6.toInt())
        // Porta-guardanapo e açucareiro.
        b.outlined(98, TOP - 6, 104, TOP, P.METAL, P.OUTLINE)
        b.outlined(160, TOP - 5, 166, TOP, P.CREAM, P.OUTLINE)
    }

    /** Cadeiras com o assento na altura do quadril de quem senta ([seatY]); encosto atrás. */
    private fun chairs(b: PixelBuffer, x1: Int, x2: Int?, seatY: Int, cloth: Int) {
        for (x in listOfNotNull(x1, x2)) {
            b.outlined(x - 9, seatY - 30, x + 9, seatY - 4, P.FURNITURE_LIGHT, P.OUTLINE)
            b.box(x - 7, seatY - 27, x + 7, seatY - 7, PixelBuffer.mix(cloth, P.FURNITURE, 0.5f))
            b.outlined(x - 10, seatY - 4, x + 10, seatY, P.FURNITURE, P.OUTLINE)
            b.vline(x - 8, seatY + 1, seatY + 10, P.OUTLINE); b.vline(x + 8, seatY + 1, seatY + 10, P.OUTLINE)
        }
    }

    /** Tampo logo acima do colo de quem senta em [seatY], toalha caindo na frente e o pé central. */
    private fun table(b: PixelBuffer, x0: Int, x1: Int, seatY: Int, cloth: Int) {
        val top = seatY - 12
        b.outlined(x0, top, x1, top + 6, PixelBuffer.mix(cloth, P.WHITE, 0.55f), P.OUTLINE)
        b.outlined(x0, top + 6, x1, top + 14, cloth, P.OUTLINE)
        for (x in x0 + 3..x1 - 3 step 6) b.set(x, top + 10, PixelBuffer.mix(cloth, P.OUTLINE, 0.25f))
        val mid = (x0 + x1) / 2
        b.box(mid - 1, top + 15, mid + 1, top + 26, P.METAL_DARK)
        b.hline(mid - 6, mid + 6, top + 27, P.OUTLINE)
    }

    private fun cup(b: PixelBuffer, x: Int, baseY: Int, color: Int) {
        b.outlined(x, baseY - 5, x + 5, baseY, color, P.OUTLINE)
        b.vline(x + 6, baseY - 4, baseY - 2, P.OUTLINE)
        b.hline(x - 1, x + 6, baseY + 1, P.OUTLINE)
    }

    private fun loaf(b: PixelBuffer, x: Int, baseY: Int, kind: Int) {
        val crust = intArrayOf(0xFFD9954A.toInt(), 0xFFC07A36.toInt(), 0xFFE3B064.toInt())[kind]
        b.outlined(x, baseY - 7, x + 9, baseY, crust, P.OUTLINE)
        for (dx in 2..7 step 3) b.set(x + dx, baseY - 5, PixelBuffer.mix(crust, P.WHITE, 0.4f))
    }

    /** Salgados: coxinha, pão de queijo e croissant. */
    private fun pastry(b: PixelBuffer, x: Int, baseY: Int, kind: Int) {
        when (kind) {
            0 -> { b.disc(x + 4, baseY - 3, 4, P.OUTLINE); b.disc(x + 4, baseY - 3, 3, 0xFFE0A050.toInt()); b.vline(x + 4, baseY - 9, baseY - 7, P.OUTLINE) }
            1 -> { b.disc(x + 4, baseY - 3, 3, P.OUTLINE); b.disc(x + 4, baseY - 3, 2, 0xFFF2CF5B.toInt()) }
            else -> { b.outlined(x, baseY - 5, x + 9, baseY, 0xFFE3A24E.toInt(), P.OUTLINE); b.set(x + 3, baseY - 3, P.OUTLINE); b.set(x + 6, baseY - 3, P.OUTLINE) }
        }
    }

    private fun cake(b: PixelBuffer, x: Int, baseY: Int) {
        b.outlined(x, baseY - 10, x + 24, baseY, 0xFFF1E3C3.toInt(), P.OUTLINE)
        b.box(x + 1, baseY - 9, x + 23, baseY - 7, 0xFFE07A93.toInt())
        b.hline(x + 1, x + 23, baseY - 4, 0xFF8E5435.toInt())
        for (dx in 3..21 step 6) b.set(x + dx, baseY - 11, P.RED)
    }

    private fun sweet(b: PixelBuffer, x: Int, baseY: Int, kind: Int) {
        val c = intArrayOf(0xFF6B3E26.toInt(), 0xFFE07A93.toInt(), 0xFFF2CF5B.toInt(), 0xFF7FE0C2.toInt())[kind]
        b.disc(x + 4, baseY - 3, 3, P.OUTLINE); b.disc(x + 4, baseY - 3, 2, c)
        b.set(x + 4, baseY - 6, P.WHITE)
    }

    private fun cupIcon(b: PixelBuffer, x: Int, y: Int) {
        b.outlined(x, y, x + 8, y + 8, 0xFFDDE7E0.toInt(), 0xFFDDE7E0.toInt())
        b.box(x + 1, y + 1, x + 7, y + 7, CHALK)
        b.vline(x + 10, y + 2, y + 5, 0xFFDDE7E0.toInt())
        b.set(x + 3, y - 2, 0xFFDDE7E0.toInt()); b.set(x + 5, y - 3, 0xFFDDE7E0.toInt())
    }

    private fun croissantIcon(b: PixelBuffer, x: Int, y: Int) {
        b.line(x, y + 6, x + 5, y + 1, P.YELLOW); b.line(x + 5, y + 1, x + 10, y + 6, P.YELLOW)
        b.hline(x + 2, x + 8, y + 6, P.YELLOW)
    }

    override fun lights(env: SceneEnv): List<Light> = buildList {
        add(Light.Glow(40, 12, 70, 0.7f)); add(Light.Glow(120, 12, 80, 0.75f)); add(Light.Glow(200, 12, 70, 0.7f))
        add(Light.Emissive(16, TOP + 8, 114, 205))
        add(Light.Emissive(186, 90, 194, 94))
        if (env.period == DayPeriod.DAY || env.period == DayPeriod.MORNING) add(Light.Emissive(198, 72, 234, 149))
    }

    private companion object {
        val WALLS = intArrayOf(0xFFF3E1C2.toInt(), 0xFFE9D7C8.toInt(), 0xFFEFE6CF.toInt())
        val SIGNS = intArrayOf(0xFFB0573E.toInt(), 0xFF3F6E5A.toInt(), 0xFF8A4F7D.toInt())
        val FLOORS_A = intArrayOf(0xFFE8DCC6.toInt(), 0xFFE2D6CC.toInt(), 0xFFEDE3CF.toInt())
        val FLOORS_B = intArrayOf(0xFFC9B79B.toInt(), 0xFFBFAF9F.toInt(), 0xFFCFBF9E.toInt())
        val TABLECLOTH = intArrayOf(0xFFC9544F.toInt(), 0xFF4F7FC9.toInt(), 0xFF6B8E5A.toInt())
        const val CHALK = 0xFF2F4A3C.toInt()
        const val MACHINE = 0xFF6F7682.toInt()
        const val VITRINE = 0xFFD6EEF4.toInt()
        const val COUNTER_TOP = 0xFFE8D6B4.toInt()
        const val REGISTER = 0xFF3A3F55.toInt()
        const val GLASS = 0xFFB8DDEE.toInt()
        const val LAMP = 0xFFE8873A.toInt()
        const val STEAM = 0xFFDCE6E8.toInt()
        const val COFFEE = 0xFF6B3E26.toInt()
        /** Tampo do balcão. */
        const val TOP = 172

        val PADARIA = listOf(
            "##   #  ##   #  ##  ###  # ",
            "# # # # # # # # # #  #  # #",
            "##  ### # # ### ##   #  ###",
            "#   # # # # # # # #  #  # #",
            "#   # # ##  # # # # ### # #",
        )
    }
}

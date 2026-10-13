package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.PharmacyAmbient
import com.hoodie.app.pixel.npc.venue.PharmacyVenue
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Farmácia: prateleiras de parede, gôndola central, balcão com caixa e estoque atrás, cruz verde
 * luminosa e porta de vidro. Os produtos são caixinhas genéricas de cores pastel (nada que
 * identifique remédio ou condição). Vãos nas prateleiras, reposição e caixa vêm da sessão
 * ([PharmacyAmbient]); a caixinha que o Hoodie examina sai da prateleira B (ITEM_HELD).
 */
class PharmacyScene : PixelScene(SceneId.PHARMACY) {
    override val spots = mapOf(
        SpotId.SHELF_A to Spot(PharmacyVenue.HOODIE_SHELF_A_X, PharmacyVenue.HOODIE_SHELF_A_Y),
        SpotId.SHELF_B to Spot(PharmacyVenue.HOODIE_SHELF_B_X, PharmacyVenue.HOODIE_SHELF_B_Y),
        SpotId.CHECKOUT to Spot(PharmacyVenue.HOODIE_CHECKOUT_X, PharmacyVenue.HOODIE_CHECKOUT_Y),
        SpotId.CENTER to Spot(30, 300),
        SpotId.DOOR to Spot(114, 160),
    )
    override val defaultSpot = SpotId.SHELF_A
    override val hasAnimatedDoor = true
    override val backgroundVariants = 3

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        val v = env.variant.mod(3)
        b.box(0, 0, 239, 150, WALLS[v])
        b.box(0, 0, 239, 6, PixelBuffer.mix(WALLS[v], P.OUTLINE, 0.12f))
        b.box(0, 146, 239, 150, PixelBuffer.mix(WALLS[v], P.OUTLINE, 0.18f))
        // Letreiro com a cruz (sem nenhum produto específico).
        b.outlined(8, 8, 92, 30, GREEN, P.OUTLINE)
        b.glyph(FARMACIA, 24, 15, P.WHITE)
        cross(b, 14, 13, P.WHITE, 3)
        // Prateleiras de parede (a esquerda e a direita da prateleira são W1 e W2 dos NPCs).
        shelfUnit(b, 4, 40, 90, 148)
        // Estoque atrás do balcão.
        shelfUnit(b, 150, 64, 238, 132)
        // Piso claro.
        val a = FLOOR_A[v]; val c = FLOOR_B[v]
        for (y in 151..319 step 16) for (x in 0..239 step 16) b.box(x, y, minOf(x + 15, 239), minOf(y + 15, 319), if ((x / 16 + y / 16) % 2 == 0) a else c)
        b.box(0, 150, 239, 152, P.OUTLINE)
    }

    private fun shelfUnit(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int) {
        b.outlined(x0, y0, x1, y1, P.WOOD_LIGHT, P.OUTLINE)
        for (y in y0 + 18..y1 step 18) b.outlined(x0, y - 2, x1, y, P.WOOD_DARK, P.OUTLINE)
        // Caixinhas de produto em cada nível.
        for ((row, y) in (y0 + 16..y1 step 18).withIndex()) {
            var x = x0 + 3
            var i = row
            while (x + 7 < x1) { box(b, x, y, BOXES[i % BOXES.size], 6 + (i * 3) % 4); x += 9; i++ }
        }
    }

    /** Caixinha genérica: cor pastel com faixa branca. */
    private fun box(b: PixelBuffer, x: Int, baseY: Int, color: Int, h: Int) {
        b.outlined(x, baseY - h - 2, x + 6, baseY, color, P.OUTLINE)
        b.hline(x + 1, x + 5, baseY - h, P.WHITE)
    }

    private fun cross(b: PixelBuffer, x: Int, y: Int, color: Int, arm: Int) {
        b.box(x + arm, y, x + 2 * arm - 1, y + 3 * arm - 1, color)
        b.box(x, y + arm, x + 3 * arm - 1, y + 2 * arm - 1, color)
    }

    override fun props(): List<Prop> = listOf(
        // Porta de vidro: abre para o Hoodie (doorFrame) e para os clientes.
        Prop(0) { b, env, _ ->
            val ambient = env.venueAmbient as? PharmacyAmbient
            val open = maxOf(env.doorFrame, if (ambient?.doorOpen == true) SceneEnv.DOOR_OPEN else 0)
            b.outlined(94, 68, 134, 151, P.METAL_DARK, P.OUTLINE)
            b.box(96, 70, 132, 149, PixelBuffer.mix(SceneArt.sky(env.period).bottom, P.WHITE, 0.25f))
            val gap = open * 6
            b.outlined(96 - gap, 70, 114 - gap, 149, GLASS, P.METAL_DARK)
            b.outlined(114 + gap, 70, 132 + gap, 149, GLASS, P.METAL_DARK)
            cross(b, 108, 54, P.OUTLINE, 2); cross(b, 109, 55, GREEN, 1)
        },
        // Cruz luminosa piscando devagar acima do estoque.
        Prop(0) { b, _, t ->
            val on = (t / 900) % 3 != 2L
            b.outlined(184, 34, 206, 56, P.OUTLINE, P.OUTLINE)
            cross(b, 186, 36, if (on) GREEN_LIGHT else GREEN, 6)
        },
        // Vãos nas prateleiras de parede (o cliente levou) e caixa de reposição no chão.
        Prop(0) { b, env, _ ->
            val ambient = env.venueAmbient as? PharmacyAmbient ?: return@Prop
            if (PharmacyVenue.W_SHELF_1 in ambient.shelfGaps) gap(b, 12, 112)
            if (PharmacyVenue.W_SHELF_2 in ambient.shelfGaps) gap(b, 66, 112)
            if (SceneFlag.ITEM_HELD in env.flags) gap(b, 39, 94)
        },
        // Gôndola central (frente na altura dos pés em 272): olhada dos dois lados.
        Prop(272) { b, env, _ ->
            val ambient = env.venueAmbient as? PharmacyAmbient
            b.outlined(56, 214, 120, 272, P.WOOD_LIGHT, P.OUTLINE)
            for (y in intArrayOf(232, 250, 268)) b.outlined(56, y - 2, 120, y, P.WOOD_DARK, P.OUTLINE)
            for ((row, y) in intArrayOf(230, 248, 266).withIndex()) {
                var x = 59; var i = row + 2
                while (x + 7 < 118) {
                    val skip = (ambient?.shelfGaps?.contains(PharmacyVenue.GONDOLA_L) == true && row == 1 && x < 66) ||
                        (ambient?.shelfGaps?.contains(PharmacyVenue.GONDOLA_R) == true && row == 1 && x > 106) ||
                        (SceneFlag.ITEM_HELD in env.flags && row == 2 && x in 84..92)
                    if (!skip) box(b, x, y, BOXES[i % BOXES.size], 5 + (i * 3) % 4)
                    x += 9; i++
                }
            }
            b.outlined(52, 208, 124, 214, GREEN, P.OUTLINE)
        },
        // Balcão com portinhola à esquerda (o atendente passa por ela), caixa e sacolas.
        Prop(208) { b, env, t ->
            val ambient = env.venueAmbient as? PharmacyAmbient
            val left = PharmacyVenue.COUNTER_LEFT_X
            b.outlined(left, 172, 238, 178, COUNTER_TOP, P.OUTLINE)
            b.outlined(left, 178, 238, 208, P.WHITE, P.OUTLINE)
            b.box(left + 2, 186, 236, 190, GREEN)
            val active = ambient?.registerActive == true || SceneFlag.CHECKOUT_ACTIVE in env.flags
            b.outlined(196, 158, 220, 172, P.SCREEN_OFF, P.OUTLINE)
            b.box(199, 161, 217, 166, if (active) P.CODE_1 else P.SCREEN)
            if (active && (t / 300) % 2 == 0L) b.hline(201, 215, 163, P.OUTLINE)
            for (i in 0 until 2) b.outlined(160 + i * 12, 162 - i, 170 + i * 12, 172, P.WHITE, P.OUTLINE)
        },
        // Caixa de reposição ao lado da prateleira em reposição.
        Prop(280) { b, env, _ ->
            val shelf = (env.venueAmbient as? PharmacyAmbient)?.restocking ?: return@Prop
            val x = when (shelf) { PharmacyVenue.W_SHELF_1 -> 30; PharmacyVenue.W_SHELF_2 -> 88; PharmacyVenue.GONDOLA_L -> 20; else -> 144 }
            val y = if (shelf.startsWith("w_")) 198 else 272
            b.outlined(x, y - 8, x + 12, y, 0xFFC9A46A.toInt(), P.OUTLINE)
            b.hline(x + 1, x + 11, y - 5, 0xFFB08850.toInt())
        },
        Prop(316) { b, _, _ -> SceneArt.plant(b, 226, 316, big = true) },
    )

    private fun gap(b: PixelBuffer, x: Int, baseY: Int) {
        b.box(x, baseY - 9, x + 7, baseY - 1, P.WOOD_LIGHT)
    }

    override fun lights(env: SceneEnv): List<Light> = buildList {
        add(Light.Glow(60, 0, 100, 0.8f)); add(Light.Glow(180, 0, 100, 0.8f))
        add(Light.Emissive(186, 36, 203, 53))
        add(Light.Emissive(8, 8, 92, 30))
        if (env.period == DayPeriod.DAY || env.period == DayPeriod.MORNING) add(Light.Emissive(96, 70, 132, 149))
    }

    private companion object {
        val WALLS = intArrayOf(0xFFEFF5F1.toInt(), 0xFFF2F2EC.toInt(), 0xFFEAF1F6.toInt())
        val FLOOR_A = intArrayOf(0xFFF4F4F0.toInt(), 0xFFF2EFE8.toInt(), 0xFFEFF3F4.toInt())
        val FLOOR_B = intArrayOf(0xFFDCE6E0.toInt(), 0xFFE2DCD2.toInt(), 0xFFD9E2E8.toInt())
        const val GREEN = 0xFF3FA36B.toInt()
        const val GREEN_LIGHT = 0xFF7FE0A6.toInt()
        const val GLASS = 0xFFB8DDEE.toInt()
        const val COUNTER_TOP = 0xFFDDE7E0.toInt()
        val BOXES = intArrayOf(0xFFF2B5C8.toInt(), 0xFFB5D8F2.toInt(), 0xFFC8F2B5.toInt(), 0xFFF2E3B5.toInt(), 0xFFD8C8F2.toInt())

        val FARMACIA = listOf(
            "### ### ##  #   # ### ### ### ###",
            "#   # # # # ## ## # # #    #  # #",
            "##  ### ##  # # # ### #    #  ###",
            "#   # # # # #   # # # #    #  # #",
            "#   # # # # #   # # # ### ### # #",
        )
    }
}

package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.MallAmbient
import com.hoodie.app.pixel.npc.venue.MallVenue
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Shopping: um corredor principal (sem tentar o shopping inteiro). Piso de cima com a vitrine da
 * loja B e guarda-corpo de vidro; embaixo, a loja A (vitrine e porta por onde o Hoodie entra) e a
 * loja C (porta usada pelos visitantes); escada rolante no meio (animação ambiental, ninguém sobe
 * nela nesta etapa), banco de três lugares, plantas e placas.
 */
class MallScene : PixelScene(SceneId.MALL) {
    override val spots = mapOf(
        SpotId.STOREFRONT_A to Spot(MallVenue.HOODIE_STOREFRONT_A_X, MallVenue.HOODIE_STOREFRONT_Y),
        SpotId.STOREFRONT_C to Spot(MallVenue.HOODIE_STOREFRONT_C_X, MallVenue.HOODIE_STOREFRONT_Y),
        SpotId.STORE_ENTRANCE to Spot(MallVenue.HOODIE_STORE_DOOR_X, MallVenue.HOODIE_STORE_DOOR_Y),
        SpotId.BENCH to Spot(MallVenue.HOODIE_BENCH_X, MallVenue.BENCH_Y),
        SpotId.CENTER to Spot(120, 240),
        SpotId.PATH_A to Spot(40, 302),
        SpotId.PATH_B to Spot(200, 304),
    )
    override val defaultSpot = SpotId.STOREFRONT_A
    override val backgroundVariants = 3

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        val v = env.variant.mod(3)
        // Piso de cima: parede, vitrine da loja B e o vão com o guarda-corpo.
        b.box(0, 0, 239, 70, UPPER_WALL[v])
        b.outlined(8, 8, 104, 56, P.WOOD_DARK, P.OUTLINE)
        b.box(10, 18, 102, 54, SHOP_GLASS)
        b.outlined(8, 2, 104, 14, SIGN_B[v], P.OUTLINE); b.glyph(MODA, 40, 5, P.WHITE)
        for (i in 0 until 3) mannequin(b, 26 + i * 30, 54, TEES[(i + v) % TEES.size])
        // Claraboia à direita, no alto.
        b.outlined(150, 0, 238, 20, 0xFFBFE0F2.toInt(), P.OUTLINE)
        for (x in 150..238 step 22) b.vline(x, 0, 20, P.METAL)
        // Laje entre os andares e o guarda-corpo de vidro.
        b.box(0, 60, 239, 68, P.METAL_DARK); b.hline(0, 239, 60, P.OUTLINE); b.hline(0, 239, 68, P.OUTLINE)
        b.box(110, 40, 239, 59, PixelBuffer.mix(SHOP_GLASS, P.WHITE, 0.2f)); b.hline(110, 239, 40, P.METAL)
        for (x in 112..238 step 18) b.vline(x, 40, 59, P.METAL)
        // Térreo: parede, loja A (vitrine + porta) e loja C (porta + vitrine).
        b.box(0, 69, 239, 160, LOWER_WALL[v])
        b.box(0, 155, 239, 160, PixelBuffer.mix(LOWER_WALL[v], P.OUTLINE, 0.25f))
        b.outlined(4, 72, 98, 82, SIGN_A[v], P.OUTLINE); b.glyph(CALCADOS, 18, 75, P.WHITE)
        b.outlined(6, 84, 60, 156, P.WOOD_DARK, P.OUTLINE); b.box(8, 86, 58, 154, SHOP_GLASS)
        for (i in 0 until 3) { shoe(b, 12 + i * 16, 120, TEES[(i + 1) % TEES.size]); shoe(b, 14 + i * 16, 146, TEES[(i + 3) % TEES.size]) }
        b.hline(8, 58, 122, P.METAL); b.hline(8, 58, 148, P.METAL)
        b.outlined(172, 72, 238, 82, SIGN_C[v], P.OUTLINE); b.glyph(PRESENTES, 180, 75, P.WHITE)
        b.outlined(184, 84, 236, 156, P.WOOD_DARK, P.OUTLINE); b.box(186, 86, 234, 154, SHOP_GLASS)
        for (i in 0 until 3) gift(b, 192 + i * 14, 150, TEES[(i + 2) % TEES.size])
        // Piso polido com reflexos.
        val floorA = FLOOR_A[v]; val floorB = FLOOR_B[v]
        for (y in 161..319 step 20) for (x in 0..239 step 20) b.box(x, y, minOf(x + 19, 239), minOf(y + 19, 319), if ((x / 20 + y / 20) % 2 == 0) floorA else floorB)
        for (i in 0 until 14) b.hline((i * 53) % 220, (i * 53) % 220 + 6, 170 + (i * 29) % 140, PixelBuffer.mix(floorA, P.WHITE, 0.4f))
        b.box(0, 160, 239, 161, P.OUTLINE)
    }

    override fun props(): List<Prop> = listOf(
        // Escada rolante: degraus subindo em loop, corrimão e base.
        Prop(0) { b, env, t ->
            val phase = (env.venueAmbient as? MallAmbient)?.escalatorPhase ?: (Math.floorMod(t, MallAmbient.ESCALATOR_MS) / MallAmbient.ESCALATOR_MS.toFloat())
            escalator(b, phase)
        },
        // Porta da loja A aberta (atrás de quem passa)...
        Prop(0) { b, _, _ ->
            b.outlined(62, 84, 96, 158, P.METAL_DARK, P.OUTLINE)
            b.box(64, 86, 94, 157, PixelBuffer.mix(SHOP_GLASS, P.OUTLINE, 0.35f))
            b.outlined(64, 86, 68, 158, GLASS_DOOR, P.METAL_DARK); b.outlined(90, 86, 94, 158, GLASS_DOOR, P.METAL_DARK)
        },
        // ...e fechada na frente do Hoodie enquanto ele está lá dentro.
        Prop(170) { b, env, _ ->
            if (SceneFlag.INSIDE_STORE !in env.flags) return@Prop
            // A porta vai até o piso na frente dele (pés em y 166): não sobra nenhum pedaço do Hoodie.
            b.outlined(62, 84, 96, 168, P.METAL_DARK, P.OUTLINE)
            b.outlined(64, 86, 94, 168, GLASS_DOOR, P.METAL_DARK)
            b.box(65, 87, 93, 167, PixelBuffer.mix(GLASS_DOOR, P.OUTLINE, 0.3f))
            b.vline(79, 86, 168, P.METAL_DARK)
            b.line(68, 92, 74, 104, P.WHITE)
        },
        // Porta da loja C: abre quando um visitante entra ou sai.
        Prop(0) { b, env, _ ->
            val open = (env.venueAmbient as? MallAmbient)?.storeDoorOpen == true
            b.outlined(160, 84, 184, 158, P.METAL_DARK, P.OUTLINE)
            b.box(162, 86, 182, 157, PixelBuffer.mix(SHOP_GLASS, P.OUTLINE, 0.35f))
            if (open) { b.outlined(162, 86, 165, 158, GLASS_DOOR, P.METAL_DARK); b.outlined(179, 86, 182, 158, GLASS_DOOR, P.METAL_DARK) }
            else { b.outlined(162, 86, 182, 158, GLASS_DOOR, P.METAL_DARK); b.vline(172, 86, 158, P.METAL_DARK) }
            val inside = (env.venueAmbient as? MallAmbient)?.shoppersInside ?: 0
            // Silhueta de quem está comprando lá dentro, atrás do vidro da vitrine.
            for (i in 0 until inside.coerceAtMost(2)) b.box(196 + i * 18, 120, 202 + i * 18, 152, PixelBuffer.mix(SHOP_GLASS, P.OUTLINE, 0.25f))
        },
        // Placas penduradas piscando devagar.
        Prop(0) { b, _, t ->
            val on = (t / 1_100) % 4 != 3L
            b.vline(60, 20, 30, P.OUTLINE); b.vline(80, 20, 30, P.OUTLINE)
            b.outlined(56, 30, 84, 38, if (on) 0xFFF2CF5B.toInt() else 0xFFC9A04A.toInt(), P.OUTLINE)
            b.glyph(SALE, 62, 32, P.RED)
        },
        // Banco de três lugares: encosto atrás de quem senta, assento e pés na frente.
        Prop(276) { b, _, _ ->
            b.outlined(70, 256, 170, 276, BENCH_WOOD, P.OUTLINE)
            for (y in intArrayOf(261, 267, 273)) b.hline(72, 168, y, PixelBuffer.mix(BENCH_WOOD, P.OUTLINE, 0.25f))
        },
        Prop(296) { b, _, _ ->
            b.outlined(66, 278, 174, 286, PixelBuffer.mix(BENCH_WOOD, P.WHITE, 0.15f), P.OUTLINE)
            for (x in intArrayOf(74, 162)) b.box(x, 287, x + 3, 296, P.METAL_DARK)
        },
        Prop(312) { b, _, _ -> SceneArt.plant(b, 18, 312, big = true); SceneArt.plant(b, 222, 312, big = true) },
    )

    private fun escalator(b: PixelBuffer, phase: Float) {
        // Faixa diagonal do térreo (x 106, y 158) ao piso de cima (x 156, y 66).
        for (k in 0..40) {
            val x = 106 + k * 50 / 40
            val y = 158 - k * 92 / 40
            b.box(x, y - 2, x + 12, y + 8, if (k % 4 == 0) P.METAL_DARK else P.METAL)
        }
        // Degraus: linhas que sobem com o tempo.
        for (i in 0 until 8) {
            val k = ((i + phase) / 8f * 40).toInt()
            val x = 106 + k * 50 / 40; val y = 158 - k * 92 / 40
            b.hline(x + 1, x + 11, y + 2, P.OUTLINE)
        }
        // Corrimãos.
        b.line(102, 150, 152, 58, P.OUTLINE); b.line(120, 162, 170, 70, P.OUTLINE)
        b.line(103, 150, 153, 58, 0xFF2E3046.toInt()); b.line(121, 162, 171, 70, 0xFF2E3046.toInt())
        b.outlined(100, 156, 126, 164, P.METAL_DARK, P.OUTLINE)
    }

    private fun mannequin(b: PixelBuffer, cx: Int, baseY: Int, color: Int) {
        b.disc(cx, baseY - 28, 3, P.CREAM)
        b.outlined(cx - 5, baseY - 24, cx + 5, baseY - 8, color, P.OUTLINE)
        b.vline(cx, baseY - 8, baseY - 1, P.METAL_DARK)
    }

    private fun shoe(b: PixelBuffer, x: Int, baseY: Int, color: Int) {
        b.outlined(x, baseY - 5, x + 10, baseY, color, P.OUTLINE); b.box(x, baseY - 8, x + 4, baseY - 5, color)
    }

    private fun gift(b: PixelBuffer, x: Int, baseY: Int, color: Int) {
        b.outlined(x, baseY - 10, x + 10, baseY, color, P.OUTLINE); b.vline(x + 5, baseY - 10, baseY, P.YELLOW); b.hline(x, x + 10, baseY - 6, P.YELLOW)
    }

    override fun lights(env: SceneEnv): List<Light> = buildList {
        add(Light.Glow(60, 64, 90, 0.7f)); add(Light.Glow(180, 64, 90, 0.7f))
        add(Light.Emissive(8, 86, 58, 154)); add(Light.Emissive(186, 86, 234, 154)); add(Light.Emissive(10, 18, 102, 54))
        if (env.period == DayPeriod.DAY || env.period == DayPeriod.MORNING) add(Light.Emissive(150, 0, 238, 20))
    }

    private companion object {
        val UPPER_WALL = intArrayOf(0xFFEDE6F2.toInt(), 0xFFE6EEF0.toInt(), 0xFFF2EDE4.toInt())
        val LOWER_WALL = intArrayOf(0xFFF4EFE8.toInt(), 0xFFEAF0F2.toInt(), 0xFFF0EAF2.toInt())
        val SIGN_A = intArrayOf(0xFF4F7FC9.toInt(), 0xFFC9544F.toInt(), 0xFF3F8F6B.toInt())
        val SIGN_B = intArrayOf(0xFFE07A93.toInt(), 0xFF8A4F7D.toInt(), 0xFF4F7FC9.toInt())
        val SIGN_C = intArrayOf(0xFF3F8F6B.toInt(), 0xFFE3A24E.toInt(), 0xFFC9544F.toInt())
        val FLOOR_A = intArrayOf(0xFFE9E6EE.toInt(), 0xFFE6ECEE.toInt(), 0xFFEEE8E0.toInt())
        val FLOOR_B = intArrayOf(0xFFD6D2DE.toInt(), 0xFFD2DCE0.toInt(), 0xFFDDD4C8.toInt())
        val TEES = intArrayOf(0xFFE07A93.toInt(), 0xFF7FE0C2.toInt(), 0xFFF2CF5B.toInt(), 0xFF86A9E8.toInt(), 0xFFC9544F.toInt())
        const val SHOP_GLASS = 0xFFD8ECF4.toInt()
        const val GLASS_DOOR = 0xFFB8DDEE.toInt()
        const val BENCH_WOOD = 0xFFC08A5A.toInt()

        val MODA = listOf("#   #  ##  ##   #  ", "## ## #  # # # # # ", "# # # #  # # # ### ", "#   #  ##  ##  # # ")
        val CALCADOS = listOf(" ##  #  #    ##  #  ##   ##   ## ", "#   # # #   #   # # # # #  # #   ", "#   ### #   #   ### # # #  #  #  ", " ## # # ###  ## # # ##   ##  ##  ")
        val PRESENTES = listOf("##  ##  ### ### ### #  # ### ### ###", "# # # # #   #   #   ## #  #  #   #  ", "##  ##  ##  ### ##  # ##  #  ##  ###", "#   # # ###   # ### #  #  #  ###   #")
        val SALE = listOf("##  #  #   ###", "#  # # #   ## ", " # ### #   #  ", "## # # ### ###")
    }
}

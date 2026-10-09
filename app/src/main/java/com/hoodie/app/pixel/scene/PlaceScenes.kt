package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.npc.restaurant.RestaurantDrinkAmount
import com.hoodie.app.pixel.npc.restaurant.RestaurantFoodAmount
import com.hoodie.app.pixel.npc.restaurant.RestaurantMealState

class RestaurantScene : PixelScene(SceneId.RESTAURANT) {
    override val spots = mapOf(
        SpotId.TABLE to Spot(120, 268),
        SpotId.DOOR to Spot(222, 196),
        SpotId.CENTER to Spot(150, 214),
    )
    override val defaultSpot = SpotId.TABLE

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        b.box(0, 0, 239, 100, 0xFFB9584A.toInt())
        for (x in 0..239 step 12) b.vline(x, 0, 100, 0xFFAD4F42.toInt())
        b.box(0, 100, 239, 151, P.FURNITURE)
        for (x in 0..239 step 20) b.outlined(x + 2, 106, x + 17, 146, P.FURNITURE_LIGHT, P.FURNITURE)
        b.hline(0, 239, 100, P.OUTLINE); b.box(0, 151, 239, 153, P.OUTLINE)
        for (y in 154..319 step 16) for (x in 0..239 step 16) {
            b.box(x, y, x + 15, minOf(y + 15, 319), if ((x / 16 + y / 16) % 2 == 0) 0xFFE8E0D0.toInt() else 0xFFC9BBA4.toInt())
        }
        // Quadro de menu.
        b.outlined(64, 14, 172, 72, 0xFF2F4A3C.toInt(), P.OUTLINE)
        b.box(66, 16, 170, 17, P.FURNITURE_LIGHT)
        b.glyph(listOf("#   # ### #  # #  #", "## ## #   ## # #  #", "# # # ##  # ## #  #", "#   # #   #  # #  #", "#   # ### #  #  ## "), 100, 22, P.WHITE)
        for ((i, y) in (34..64 step 7).withIndex()) {
            b.hline(74, 120 + i * 4, y, 0xFFDDE7E0.toInt()); b.box(156, y - 1, 162, y, P.YELLOW)
        }
        SceneArt.window(b, 188, 30, 228, 92, env.period)
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, _ ->
            val on = env.period != DayPeriod.DAY
            for (lx in intArrayOf(40, 200)) {
                b.vline(lx, 0, 22, P.OUTLINE)
                b.outlined(lx - 9, 22, lx + 9, 30, 0xFFE8873A.toInt(), P.OUTLINE)
                b.box(lx - 4, 31, lx + 4, 32, if (on) P.LAMP_LIGHT else P.CREAM)
            }
        },
        Prop(170) { b, _, t ->
            b.outlined(0, 116, 52, 172, P.FURNITURE, P.OUTLINE)
            b.outlined(0, 110, 56, 118, 0xFFE2D6C0.toInt(), P.OUTLINE)
            b.outlined(20, 92, 38, 110, 0xFF3A3F55.toInt(), P.OUTLINE)
            b.set(24, 96, if ((t / 700) % 2 == 0L) P.CODE_1 else P.RED)
            SceneArt.mug(b, 42, 103)
        },
        // Mesa do cliente: tampo/prato na profundidade do assento; pernas e cadeiras atrás.
        Prop(224) { b, env, t -> drawGuestTableTop(b, env, t) },
        Prop(190) { b, _, _ -> SceneArt.plant(b, 14, 190, big = true) },
        Prop(220) { b, _, _ -> drawGuestChair(b) },
        Prop(260) { b, _, _ -> drawGuestTableFront(b) },
        Prop(292) { b, env, _ ->
            b.outlined(60, 246, 180, 258, P.WHITE, P.OUTLINE)
            b.outlined(62, 257, 178, 286, P.CREAM, P.OUTLINE)
            for (x in 64..176 step 8) b.box(x, 262, x + 3, 284, 0xFFE7A9A0.toInt())
            // Prato chega depois do WAIT_FOOD (evento FOOD_SERVED) e some ao terminar.
            if (SceneFlag.FOOD_SERVED in env.flags && SceneFlag.FOOD_DONE !in env.flags) drawFood(b, env.variant)
            else if (SceneFlag.FOOD_DONE in env.flags) b.outlined(106, 246, 134, 252, P.WHITE, P.OUTLINE)
            b.outlined(150, 234, 158, 250, 0xFFBFE6F6.toInt(), P.OUTLINE)
            b.box(151, 240, 157, 249, 0xFFF29B4A.toInt())
        },
    )

    private fun drawGuestChair(b: PixelBuffer) {
        // Encosto atrás do gato e assento sob o ponto de contato do NPC.
        b.outlined(196, 216, 215, 238, 0xFF8C5638.toInt(), P.OUTLINE)
        b.box(199, 219, 212, 234, 0xFFB87349.toInt())
        b.outlined(194, 235, 217, 240, 0xFFD08A57.toInt(), P.OUTLINE)
        b.vline(198, 240, 252, P.OUTLINE); b.vline(213, 240, 252, P.OUTLINE)
        b.hline(195, 216, 251, P.OUTLINE)
    }

    private fun drawGuestTableTop(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        // Tampo pequeno na direita; o centro do prato fica ao alcance da mão do gato.
        b.outlined(176, 224, 228, 236, 0xFFF1DAB4.toInt(), P.OUTLINE)
        b.box(178, 225, 226, 231, 0xFFFFE8C4.toInt())
        b.vline(180, 237, 240, 0xFF6B3E2C.toInt()); b.vline(224, 237, 240, 0xFF6B3E2C.toInt())
        b.outlined(178, 235, 226, 240, 0xFF8E5435.toInt(), P.OUTLINE)
        val state = env.restaurantGuestTable
        drawGuestPlate(b, state?.foodAmount ?: RestaurantFoodAmount.EMPTY, timeMs)
        drawGuestGlass(b, state?.drinkAmount ?: RestaurantDrinkAmount.FULL)
        if (state?.mealState == RestaurantMealState.SERVED || state?.mealState == RestaurantMealState.EATING) {
            val steamFrame = (timeMs / 360L).toInt() % 3
            for (i in 0..1) {
                val x = 199 + i * 5
                val y = 214 - ((steamFrame + i) % 3) * 2
                b.set(x, y, 0xFFDCE6E8.toInt())
            }
        }
        if (state?.menuOpen == true) {
            b.outlined(209, 225, 219, 230, 0xFFE9E0C8.toInt(), P.OUTLINE)
            b.hline(211, 217, 227, 0xFF8B6B4D.toInt())
        }
        // Saleiro e guardanapo, detalhes discretos e fixos da mesa.
        b.outlined(222, 225, 225, 229, P.WHITE, P.OUTLINE)
        b.box(223, 223, 224, 224, P.YELLOW)
        b.box(178, 227, 184, 229, P.WHITE)
    }

    private fun drawGuestPlate(b: PixelBuffer, amount: RestaurantFoodAmount, timeMs: Long) {
        b.outlined(200, 232, 217, 236, P.WHITE, P.OUTLINE)
        when (amount) {
            RestaurantFoodAmount.FULL -> {
                b.box(203, 230, 214, 232, 0xFFF2CF5B.toInt())
                b.box(205, 229, 207, 230, P.RED); b.box(210, 228, 212, 230, P.LEAF)
            }
            RestaurantFoodAmount.PARTIAL -> {
                b.box(205, 230, 212, 232, 0xFFF2CF5B.toInt()); b.set(209, 229, P.LEAF)
            }
            RestaurantFoodAmount.LOW -> b.box(207, 233, 210, 234, 0xFFF2CF5B.toInt())
            RestaurantFoodAmount.EMPTY -> if (timeMs % 5_000L < 1_000L) b.set(208, 233, 0xFFE8E0D0.toInt())
        }
    }

    private fun drawGuestGlass(b: PixelBuffer, amount: RestaurantDrinkAmount) {
        b.outlined(213, 229, 218, 237, 0xFFDCECF4.toInt(), P.OUTLINE)
        val top = when (amount) {
            RestaurantDrinkAmount.FULL -> 231
            RestaurantDrinkAmount.HALF -> 233
            RestaurantDrinkAmount.EMPTY -> 236
        }
        if (amount != RestaurantDrinkAmount.EMPTY) b.box(215, top, 216, 235, 0xFFF29B4A.toInt())
        b.set(214, 230, P.WHITE)
    }

    private fun drawGuestTableFront(b: PixelBuffer) {
        // Borda frontal passa à frente do NPC, escondendo apenas pernas/parte inferior.
        b.outlined(176, 240, 228, 246, 0xFFB87349.toInt(), P.OUTLINE)
        b.hline(180, 224, 242, 0xFFE0A06B.toInt())
    }

    /** Pratos variam por dia: 🍜 🍔 🍕 🥗 🍛. */
    private fun drawFood(b: PixelBuffer, variant: Int) {
        val cx = 120; val y = 250
        when (variant % 5) {
            0 -> { // lámen
                b.outlined(cx - 13, y - 8, cx + 13, y + 2, 0xFFE8E0F0.toInt(), P.OUTLINE)
                b.box(cx - 11, y - 9, cx + 11, y - 7, 0xFFF2CF5B.toInt())
                b.box(cx - 6, y - 10, cx - 2, y - 8, 0xFFF29B9B.toInt()); b.box(cx + 3, y - 10, cx + 6, y - 9, P.LEAF)
                b.line(cx + 6, y - 22, cx + 2, y - 8, P.FURNITURE); b.line(cx + 9, y - 22, cx + 4, y - 8, P.FURNITURE)
            }
            1 -> { // hambúrguer
                b.outlined(cx - 14, y - 2, cx + 14, y + 2, P.WHITE, P.OUTLINE)
                b.outlined(cx - 9, y - 14, cx + 9, y - 9, 0xFFE0A050.toInt(), P.OUTLINE)
                b.box(cx - 9, y - 8, cx + 9, y - 7, P.LEAF); b.box(cx - 9, y - 6, cx + 9, y - 5, 0xFF6B3E26.toInt())
                b.outlined(cx - 9, y - 5, cx + 9, y - 2, 0xFFE0A050.toInt(), P.OUTLINE)
            }
            2 -> { // pizza
                b.outlined(cx - 15, y - 4, cx + 15, y + 2, P.WHITE, P.OUTLINE)
                b.disc(cx, y - 4, 11, P.OUTLINE); b.disc(cx, y - 4, 10, 0xFFE0A050.toInt()); b.disc(cx, y - 4, 8, 0xFFF2CF5B.toInt())
                for ((dx, dy) in listOf(-4 to -6, 3 to -3, -2 to 1, 5 to -8)) b.box(cx + dx, y + dy, cx + dx + 1, y + dy + 1, P.RED)
            }
            3 -> { // salada
                b.outlined(cx - 13, y - 7, cx + 13, y + 2, P.WHITE, P.OUTLINE)
                for ((dx, c) in listOf(-8 to P.LEAF, -3 to 0xFF7CC98A.toInt(), 3 to P.LEAF, 8 to 0xFF7CC98A.toInt())) b.disc(cx + dx, y - 8, 4, c)
                b.box(cx - 1, y - 11, cx + 1, y - 9, P.RED); b.box(cx + 5, y - 10, cx + 6, y - 9, P.YELLOW)
            }
            else -> { // arroz com curry
                b.outlined(cx - 15, y - 4, cx + 15, y + 2, P.WHITE, P.OUTLINE)
                b.disc(cx - 4, y - 5, 7, P.WHITE); b.disc(cx + 5, y - 3, 6, 0xFFC98A3A.toInt())
            }
        }
    }

    override fun lights(env: SceneEnv): List<Light> = listOf(
        Light.Glow(40, 34, 80), Light.Glow(200, 34, 80), Light.Emissive(186, 28, 230, 94), Light.Glow(120, 250, 60, 0.4f),
    )
}

class GymScene : PixelScene(SceneId.GYM) {
    override val spots = mapOf(
        SpotId.TREADMILL to Spot(55, 254),
        SpotId.WEIGHTS to Spot(188, 236),
        SpotId.WATER to Spot(48, 186),
        SpotId.MAT to Spot(140, 298),
        SpotId.CENTER to Spot(132, 236),
        SpotId.DOOR to Spot(226, 160),
    )
    override val defaultSpot = SpotId.TREADMILL

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        b.box(0, 0, 239, 141, 0xFFDADDE2.toInt())
        b.box(0, 92, 239, 99, 0xFFE8873A.toInt()); b.hline(0, 239, 100, 0xFFB8682C.toInt())
        b.outlined(60, 16, 200, 86, 0xFFBFD9EA.toInt(), P.OUTLINE)
        for (k in 0..3) b.line(70 + k * 36, 80, 92 + k * 36, 20, 0xFFE2F0F8.toInt())
        for (x in intArrayOf(106, 154)) b.vline(x, 17, 85, P.METAL)
        b.box(0, 141, 239, 143, P.OUTLINE)
        b.box(0, 144, 239, 319, 0xFF4A4F5C.toInt())
        for (i in 0 until 160) b.set((i * 97) % 240, 144 + (i * 53) % 176, 0xFF565C6B.toInt())
        b.outlined(98, 286, 182, 302, 0xFF7A5BC4.toInt(), P.OUTLINE)
        b.hline(100, 180, 288, 0xFF9479D8.toInt())
        b.glyph(listOf("### # # #   #", "#   # # ## ##", "# # ### # # #", "# #   # #   #", "###  ## #   #"), 112, 104, P.WHITE)
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, _, t ->
            // Ventilador de parede girando.
            b.disc(24, 36, 13, P.OUTLINE); b.disc(24, 36, 12, P.METAL)
            val k = (t / 90 % 2).toInt()
            if (k == 0) { b.box(14, 35, 34, 37, P.METAL_DARK); b.box(23, 26, 25, 46, P.METAL_DARK) }
            else { b.line(16, 28, 32, 44, P.METAL_DARK); b.line(16, 44, 32, 28, P.METAL_DARK) }
            b.disc(24, 36, 2, P.OUTLINE)
        },
        Prop(176) { b, _, _ ->
            b.outlined(12, 122, 34, 178, P.WHITE, P.OUTLINE)
            b.outlined(14, 100, 32, 122, 0xFF8AD6F2.toInt(), P.OUTLINE)
            b.box(17, 104, 19, 118, 0xFFC4ECF9.toInt())
            b.box(20, 136, 25, 140, P.METAL_DARK)
        },
        Prop(186) { b, _, _ ->
            b.outlined(150, 150, 234, 188, P.METAL_DARK, P.OUTLINE)
            for (row in intArrayOf(160, 176)) {
                b.hline(152, 232, row + 4, P.OUTLINE)
                for (x in 156..226 step 14) {
                    b.box(x + 2, row + 1, x + 8, row + 2, P.METAL)
                    b.outlined(x, row - 2, x + 2, row + 5, 0xFF2C2F3A.toInt(), P.OUTLINE)
                    b.outlined(x + 8, row - 2, x + 10, row + 5, 0xFF2C2F3A.toInt(), P.OUTLINE)
                }
            }
        },
        Prop(212) { b, _, _ ->
            // Bicicleta ergométrica.
            b.disc(122, 200, 9, P.OUTLINE); b.disc(122, 200, 7, P.METAL_DARK)
            b.line(122, 200, 134, 176, P.METAL); b.line(122, 200, 112, 178, P.METAL)
            b.outlined(106, 174, 118, 178, 0xFF2C2F3A.toInt(), P.OUTLINE)
            b.outlined(130, 170, 140, 176, 0xFF2C2F3A.toInt(), P.OUTLINE)
            b.box(110, 208, 136, 212, P.METAL_DARK)
        },
        Prop(204) { b, _, t ->
            b.box(20, 194, 23, 252, P.METAL); b.box(87, 194, 90, 252, P.METAL)
            b.outlined(16, 184, 94, 198, 0xFF2C2F3A.toInt(), P.OUTLINE)
            b.box(42, 187, 68, 194, 0xFF1D3B2C.toInt())
            val secs = (t / 1000) % 60
            b.glyph(digits(secs.toInt()), 46, 188, P.CODE_1)
        },
        Prop(240) { b, _, t ->
            b.outlined(14, 244, 96, 262, 0xFF2C2F3A.toInt(), P.OUTLINE)
            b.box(17, 247, 93, 255, 0xFF1E2027.toInt())
            val off = ((t / 30) % 8).toInt()
            for (x in 17 + off..93 step 8) b.vline(x, 247, 255, 0xFF3A3D4A.toInt())
        },
        Prop(264) { b, _, _ -> b.outlined(12, 258, 98, 266, 0xFF3A3D4A.toInt(), P.OUTLINE) },
        Prop(256) { b, _, _ ->
            b.outlined(150, 238, 226, 250, 0xFF2F3E66.toInt(), P.OUTLINE)
            b.hline(152, 224, 240, 0xFF46598E.toInt())
            b.box(156, 251, 160, 262, P.METAL_DARK); b.box(216, 251, 220, 262, P.METAL_DARK)
        },
    )

    private fun digits(n: Int): List<String> {
        val font = mapOf(
            '0' to listOf("###", "# #", "# #", "# #", "###"), '1' to listOf(" # ", "## ", " # ", " # ", "###"),
            '2' to listOf("###", "  #", "###", "#  ", "###"), '3' to listOf("###", "  #", "###", "  #", "###"),
            '4' to listOf("# #", "# #", "###", "  #", "  #"), '5' to listOf("###", "#  ", "###", "  #", "###"),
            '6' to listOf("###", "#  ", "###", "# #", "###"), '7' to listOf("###", "  #", "  #", "  #", "  #"),
            '8' to listOf("###", "# #", "###", "# #", "###"), '9' to listOf("###", "# #", "###", "  #", "###"),
        )
        val text = "%02d:%02d".format(n / 10 + 12, n)
        return (0 until 5).map { row ->
            text.map { ch -> if (ch == ':') (if (row == 1 || row == 3) "#" else " ") else font.getValue(ch)[row] }.joinToString(" ")
        }
    }

    override fun lights(env: SceneEnv): List<Light> = listOf(
        Light.Glow(80, 0, 120, 0.7f), Light.Glow(180, 0, 120, 0.7f), Light.Emissive(42, 187, 68, 194),
    )
}

/** Lugar desconhecido: propositalmente misterioso. */
class UnknownScene : PixelScene(SceneId.UNKNOWN) {
    override val spots = mapOf(SpotId.CENTER to Spot(120, 272))
    override val usesLighting = false

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        b.fill(0xFF1E2238.toInt())
        b.dither(0, 60, 239, 120, 0xFF262B48.toInt())
        b.dither(0, 121, 239, 250, 0xFF2A3050.toInt(), 1)
        var x = 0; var i = 0
        while (x < 240) {
            val w = 18 + (i * 11) % 20; val h = 40 + (i * 29) % 80
            b.dither(x, 250 - h, x + w, 250, 0xFF343C66.toInt(), i)
            x += w + 6; i++
        }
        b.box(0, 251, 239, 319, 0xFF2B2F45.toInt())
        b.dither(0, 251, 239, 262, 0xFF343A55.toInt())
        b.hline(0, 239, 251, 0xFF3E4566.toInt())
    }

    override fun props(): List<Prop> = listOf(
        Prop(250) { b, _, _ ->
            b.box(189, 160, 191, 252, P.METAL_DARK)
            b.box(184, 156, 196, 160, P.LAMP_LIGHT); b.hline(184, 196, 155, P.OUTLINE)
        },
        Prop(0) { b, _, t ->
            val bob = if ((t / 600) % 2 == 0L) 0 else 2
            val q = listOf(" #### ", "##  ##", "    ##", "   ## ", "  ##  ", "  ##  ", "      ", "  ##  ")
            for (r in q.indices) for (c in q[r].indices) if (q[r][c] == '#') {
                b.box(102 + c * 6, 118 + bob + r * 6, 107 + c * 6, 123 + bob + r * 6, P.YELLOW)
            }
        },
    )

    }

/** Coringa para família, loja, evento, lugar temporário. */
class GenericIndoorScene : PixelScene(SceneId.GENERIC_INDOOR) {
    override val spots = mapOf(SpotId.CENTER to Spot(96, 256), SpotId.SOFA to Spot(160, 214), SpotId.DOOR to Spot(30, 160))
    override val hasAnimatedDoor = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        SceneArt.wallStripes(b, 0, 141, 0xFFB7CFA4.toInt(), 0xFFA8C295.toInt(), 10)
        SceneArt.baseboard(b, 141)
        SceneArt.woodFloor(b, 143, 319, 0xFFC79A6B.toInt(), 0xFFA97E54.toInt())
        SceneArt.window(b, 136, 26, 196, 84, env.period, curtains = 0xFFE8C46A.toInt())
        for ((x, c) in listOf(40 to 0xFFC9544F.toInt(), 76 to 0xFF4F7FC9.toInt())) {
            b.outlined(x, 34, x + 24, 60, P.FURNITURE, P.OUTLINE); b.box(x + 3, 37, x + 21, 57, c)
            b.disc(x + 12, 47, 5, P.CREAM)
        }
        SceneArt.rug(b, 60, 230, 200, 290, 0xFFC08A5A.toInt(), 0xFFE2B98A.toInt())
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, _ -> SceneArt.clock(b, 214, 40, 8, env.clockMinute) },
        Prop(0) { b, env, _ -> SceneArt.door(b, 14, 70, 44, 141, 0xFF6B4A33.toInt(), env.doorFrame) },
        Prop(190) { b, _, _ ->
            b.outlined(122, 160, 200, 190, 0xFF6F7FC4.toInt(), P.OUTLINE)
            b.hline(124, 198, 162, 0xFF8E9DDA.toInt())
        },
        Prop(222) { b, _, _ ->
            b.outlined(120, 206, 202, 222, 0xFF6F7FC4.toInt(), P.OUTLINE)
            b.outlined(114, 176, 126, 222, 0xFF6F7FC4.toInt(), P.OUTLINE)
            b.outlined(196, 176, 208, 222, 0xFF6F7FC4.toInt(), P.OUTLINE)
        },
        Prop(280) { b, _, _ ->
            b.outlined(54, 262, 140, 272, P.FURNITURE_LIGHT, P.OUTLINE)
            b.box(60, 273, 64, 286, P.FURNITURE); b.box(130, 273, 134, 286, P.FURNITURE)
            SceneArt.mug(b, 70, 254); SceneArt.mug(b, 116, 254)
            b.outlined(86, 256, 108, 262, P.WHITE, P.OUTLINE); b.box(90, 254, 104, 256, 0xFFE0A050.toInt())
        },
        Prop(300) { b, _, _ -> SceneArt.plant(b, 222, 300, big = true) },
    )

    override fun lights(env: SceneEnv): List<Light> = listOf(Light.Emissive(134, 24, 198, 86), Light.Glow(120, 40, 110, 0.6f))
}

/** Coringa para parque, passeio, lugar aberto. */
class GenericOutdoorScene : PixelScene(SceneId.GENERIC_OUTDOOR) {
    override val spots = mapOf(
        SpotId.PATH_A to Spot(56, 272), SpotId.PATH_B to Spot(184, 272),
        SpotId.CENTER to Spot(120, 270), SpotId.WALK to Spot(120, 270),
    )

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        SceneArt.skyBands(b, 0, 0, 239, 160, env.period)
        val hill = if (env.period == DayPeriod.NIGHT) 0xFF2F4F45.toInt() else 0xFF6FB37A.toInt()
        b.disc(50, 190, 70, hill); b.disc(190, 200, 80, PixelBuffer.mix(hill, P.OUTLINE, 0.12f))
        val grass = if (env.period == DayPeriod.NIGHT) 0xFF3E6B48.toInt() else 0xFF7CC46B.toInt()
        b.box(0, 168, 239, 319, grass)
        b.dither(0, 168, 239, 319, PixelBuffer.mix(grass, P.OUTLINE, 0.12f))
        b.box(0, 254, 239, 286, 0xFFD8B98A.toInt())
        b.hline(0, 239, 254, 0xFFB89868.toInt()); b.hline(0, 239, 286, 0xFFB89868.toInt())
        for (i in 0 until 30) {
            val fx = (i * 83) % 240; val fy = 180 + (i * 37) % 60
            b.set(fx, fy, if (i % 3 == 0) P.YELLOW else if (i % 3 == 1) 0xFFF29BC0.toInt() else P.WHITE)
        }
    }

    override fun props(): List<Prop> = listOf(
        Prop(200) { b, _, _ ->
            for (x in intArrayOf(34, 206)) {
                b.box(x - 3, 150, x + 3, 200, P.FURNITURE)
                b.disc(x, 136, 27, P.OUTLINE); b.disc(x, 136, 26, P.LEAF); b.disc(x - 8, 128, 12, 0xFF6CBF82.toInt())
                b.disc(x + 10, 146, 9, P.LEAF_DARK)
            }
        },
        Prop(240) { b, _, _ ->
            b.outlined(96, 222, 150, 230, P.FURNITURE_LIGHT, P.OUTLINE)
            b.outlined(96, 210, 150, 216, P.FURNITURE_LIGHT, P.OUTLINE)
            b.box(100, 231, 103, 240, P.METAL_DARK); b.box(143, 231, 146, 240, P.METAL_DARK)
        },
        Prop(0) { b, _, t ->
            // Passarinhos.
            val x = 240 - ((t / 40) % 300).toInt()
            val wing = if ((t / 200) % 2 == 0L) -1 else 1
            for (k in 0..1) {
                val bx = x + k * 14; val by = 50 + k * 6
                b.set(bx - 2, by + wing, P.OUTLINE); b.set(bx - 1, by, P.OUTLINE); b.set(bx, by + 1, P.OUTLINE)
                b.set(bx + 1, by, P.OUTLINE); b.set(bx + 2, by + wing, P.OUTLINE)
            }
        },
    )

    override fun lights(env: SceneEnv): List<Light> = listOf(Light.Glow(120, 200, 140, 0.35f))
}

object SceneRegistry {
    private val scenes: Map<SceneId, PixelScene> by lazy {
        listOf(
            HomeScene(), OfficeScene(), StreetScene(), TransitScene(), RestaurantScene(), GymScene(),
            BicycleScene(), GenericRideScene(),
            SchoolScene(), ShoppingScene(), StoreScene(), FamilyScene(), LeisureScene(),
            UnknownScene(), GenericIndoorScene(), GenericOutdoorScene(),
        ).associateBy { it.id }
    }

    operator fun get(id: SceneId): PixelScene = layered[id] ?: scenes.getValue(id)

    /** Carro, trem, metrô e ônibus: cenas em camadas (assets-source/scenes/transport, docs/transport-art-bible.md). */
    private val LAYERED = mapOf(SceneId.CAR to "car", SceneId.TRAIN to "train", SceneId.METRO to "metro", SceneId.BUS to "bus")

    private val layered: Map<SceneId, PixelScene> by lazy {
        LAYERED.mapValues { (id, name) ->
            val art = com.hoodie.app.pixel.art.SceneArtStore.get(name)
                ?: error("Arte da cena $name ausente em resources/${com.hoodie.app.pixel.art.SceneArtStore.DIR}")
            if (id == SceneId.CAR) CarSceneV3(art) else InteriorSceneV3(id, art, joltPhase = id == SceneId.BUS)
        }
    }
}

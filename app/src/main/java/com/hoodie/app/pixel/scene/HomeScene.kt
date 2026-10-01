package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Cenário principal: quarto/sala/cozinha em um só ambiente. */
class HomeScene : PixelScene(SceneId.HOME) {

    override val spots = mapOf(
        SpotId.BED to Spot(43, 186),
        SpotId.SOFA to Spot(164, 192),
        SpotId.DESK to Spot(166, 270),
        SpotId.KITCHEN to Spot(66, 274),
        SpotId.WINDOW to Spot(118, 146),
        SpotId.CENTER to Spot(112, 236),
        SpotId.DOOR to Spot(216, 148),
    )

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        SceneArt.wallStripes(b, 0, 131, 0xFFE9D8B4.toInt(), 0xFFDDC8A0.toInt())
        b.box(0, 0, 239, 3, 0xFFC9AE84.toInt())
        SceneArt.baseboard(b, 131)
        SceneArt.woodFloor(b, 133, 319)
        SceneArt.window(b, 100, 24, 148, 78, env.period, curtains = 0xFFD06A5E.toInt())
        SceneArt.poster(b, 170, 22, 192, 58)
        SceneArt.door(b, 202, 60, 230, 131)
        SceneArt.rug(b, 62, 198, 176, 236, 0xFF8E5A86.toInt(), 0xFFC08AB4.toInt())
        // Prateleira com livros.
        b.outlined(12, 64, 72, 68, P.FURNITURE_LIGHT, P.OUTLINE)
        val books = intArrayOf(0xFFC9544F.toInt(), 0xFF4F7FC9.toInt(), 0xFFF2CF5B.toInt(), 0xFF4FA36A.toInt(), 0xFF8E5A86.toInt())
        var x = 16
        books.forEachIndexed { i, c -> val h = 10 + (i * 3) % 5; b.outlined(x, 64 - h, x + 5, 64, c, P.OUTLINE); x += 6 }
        b.outlined(x + 6, 56, x + 14, 64, 0xFFE9EDF7.toInt(), P.OUTLINE) // porta-retrato
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, _ -> SceneArt.clock(b, 40, 34, 9, env.clockMinute) },
        Prop(118) { b, _, _ ->
            // Cama: cabeceira, colchão e travesseiro.
            b.outlined(6, 98, 80, 130, P.FURNITURE, P.OUTLINE)
            b.box(9, 101, 77, 103, P.FURNITURE_LIGHT)
            b.outlined(8, 120, 78, 180, P.WHITE, P.OUTLINE)
            b.outlined(20, 122, 66, 138, 0xFFFFFFFF.toInt(), P.OUTLINE)
            b.hline(22, 64, 136, 0xFFDCE3EE.toInt())
        },
        Prop(140) { b, env, _ ->
            b.outlined(84, 116, 100, 140, P.FURNITURE, P.OUTLINE)
            b.hline(86, 98, 126, P.FURNITURE_LIGHT); b.set(92, 130, P.YELLOW)
            b.box(91, 106, 93, 116, P.METAL_DARK)
            val lampOn = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING
            b.outlined(85, 96, 99, 106, if (lampOn) P.LAMP_LIGHT else P.CREAM, P.OUTLINE)
        },
        Prop(136) { b, _, _ -> SceneArt.plant(b, 160, 136, big = true) },
        Prop(168) { b, _, _ ->
            val green = 0xFF5C8F7A.toInt()
            b.outlined(128, 146, 200, 174, green, P.OUTLINE)
            b.vline(152, 150, 172, PixelBuffer.mix(green, P.OUTLINE, 0.3f))
            b.vline(176, 150, 172, PixelBuffer.mix(green, P.OUTLINE, 0.3f))
            b.hline(130, 198, 148, PixelBuffer.mix(green, P.WHITE, 0.25f))
        },
        Prop(0) { b, env, _ ->
            // Arandela de parede.
            val on = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING
            b.box(162, 82, 164, 90, P.METAL_DARK)
            b.outlined(157, 72, 169, 82, if (on) P.LAMP_LIGHT else P.CREAM, P.OUTLINE)
        },
        Prop(198) { b, _, _ ->
            val green = 0xFF5C8F7A.toInt(); val light = 0xFF79AD95.toInt()
            b.outlined(128, 182, 200, 198, green, P.OUTLINE)
            b.hline(130, 198, 184, light)
            b.outlined(120, 160, 134, 198, green, P.OUTLINE); b.hline(122, 132, 162, light)
            b.outlined(194, 160, 208, 198, green, P.OUTLINE); b.hline(196, 206, 162, light)
        },
        Prop(194) { b, env, t ->
            b.outlined(210, 174, 236, 196, P.FURNITURE, P.OUTLINE)
            b.hline(212, 234, 184, P.FURNITURE_LIGHT)
            b.outlined(206, 144, 238, 172, 0xFF2A2D3E.toInt(), P.OUTLINE)
            val screen = if (env.tvOn) {
                val k = (t / 220 % 5).toInt()
                intArrayOf(0xFF6FC0EE.toInt(), 0xFF7FE0C2.toInt(), 0xFFF2C76B.toInt(), 0xFF6FC0EE.toInt(), 0xFFE58AAE.toInt())[k]
            } else P.SCREEN_OFF
            b.box(209, 147, 235, 168, screen)
            if (env.tvOn) {
                b.box(213, 158, 219, 166, PixelBuffer.mix(screen, P.WHITE, 0.4f))
                b.box(224, 151, 231, 157, PixelBuffer.mix(screen, P.OUTLINE, 0.3f))
            }
            b.line(216, 143, 210, 134, P.OUTLINE); b.line(228, 143, 234, 134, P.OUTLINE)
        },
        // Cobertor: na frente do Hoodie quando ele dorme.
        Prop(188) { b, _, _ ->
            val blanket = 0xFF5E7BC4.toInt()
            b.outlined(8, 142, 78, 184, blanket, P.OUTLINE)
            b.box(9, 143, 77, 148, 0xFF8EA6E0.toInt()); b.hline(9, 77, 149, P.OUTLINE)
            for (y in 154..180 step 8) for (x in 14..74 step 10) b.set(x + (y / 8 % 2) * 5, y, 0xFF8EA6E0.toInt())
            b.box(10, 184, 13, 189, P.FURNITURE); b.box(73, 184, 76, 189, P.FURNITURE)
        },
        Prop(230) { b, _, _ ->
            b.outlined(150, 212, 182, 248, 0xFF4E5A7A.toInt(), P.OUTLINE)
            b.box(153, 215, 179, 218, 0xFF67759B.toInt())
        },
        Prop(292) { b, env, t ->
            // Cozinha: geladeira, bancada, fogão e cafeteira.
            b.outlined(4, 196, 34, 292, 0xFFE6EEF2.toInt(), P.OUTLINE)
            b.hline(5, 33, 232, P.OUTLINE); b.box(28, 206, 29, 222, P.METAL); b.box(28, 240, 29, 262, P.METAL)
            b.outlined(34, 250, 102, 259, 0xFFD9D0C0.toInt(), P.OUTLINE)
            b.outlined(34, 258, 102, 292, 0xFF6F8FB0.toInt(), P.OUTLINE)
            b.vline(68, 259, 291, P.OUTLINE)
            b.box(62, 272, 64, 274, P.METAL); b.box(72, 272, 74, 274, P.METAL)
            b.box(40, 251, 58, 254, 0xFF3A3D4A.toInt())
            b.outlined(42, 240, 56, 251, P.METAL_DARK, P.OUTLINE); b.hline(41, 57, 240, P.OUTLINE)
            b.outlined(84, 234, 98, 250, 0xFF3A3F55.toInt(), P.OUTLINE)
            b.set(87, 238, if ((t / 600) % 2 == 0L) P.RED else 0xFF7A2A2A.toInt())
            SceneArt.mug(b, 88, 243)
            // Mesa de trabalho/jogos com monitor.
            b.outlined(136, 248, 234, 258, P.WOOD_LIGHT, P.OUTLINE)
            b.outlined(136, 257, 234, 292, P.WOOD, P.OUTLINE)
            b.outlined(200, 262, 228, 286, P.WOOD_DARK, P.OUTLINE); b.box(212, 272, 216, 274, P.YELLOW)
            b.outlined(152, 249, 180, 253, 0xFFDCE2EE.toInt(), P.OUTLINE)
            SceneArt.monitor(b, 192, 214, 228, 242, t, env.screenOn, env.variant)
        },
    )

    override fun lights(env: SceneEnv): List<Light> = buildList {
        add(Light.Emissive(98, 22, 150, 80))
        if (env.screenOn) add(Light.Emissive(194, 216, 226, 239))
        add(Light.Glow(163, 80, 70))
        add(Light.Glow(92, 100, 40))
        if (env.tvOn) { add(Light.Emissive(206, 144, 238, 172)); add(Light.Glow(212, 160, 54, 0.6f)) }
        if (env.screenOn) add(Light.Glow(208, 230, 46, 0.5f))
    }
}

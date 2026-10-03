package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Rua: o Hoodie caminha "no lugar" e o mundo corre em paralaxe. Não representa o
 * trajeto real — a localização só diz que ele saiu e ainda não chegou.
 */
class StreetScene : PixelScene(SceneId.STREET) {
    override val spots = mapOf(SpotId.WALK to Spot(120, 252), SpotId.CENTER to Spot(120, 252))
    override val defaultSpot = SpotId.WALK
    override val walkInPlace = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        SceneArt.skyBands(b, 0, 0, 239, 200, env.period)
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, t ->
            SceneArt.city(b, 0, 239, 150, env.period, offset = (t / 140).toInt(), seed = 3)
            // Prédios próximos, maiores.
            val near = PixelBuffer.mix(SceneArt.sky(env.period).city, P.OUTLINE, 0.25f)
            val off = ((t / 60) % 240).toInt()
            for (i in -1..3) {
                val x = i * 80 - off + 20
                val h = 70 + (i * 37 % 3 + 3) % 3 * 18
                b.outlined(x, 200 - h, x + 56, 200, near, P.OUTLINE)
                val lit = if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) 0xFFFFD86B.toInt() else 0xFF9DB7DE.toInt()
                for (wy in 200 - h + 6 until 194 step 10) for (wx in x + 6 until x + 50 step 12) {
                    b.box(wx, wy, wx + 5, wy + 5, if ((wx * 7 + wy + i) % 4 == 0) near else lit)
                }
            }
        },
        Prop(1) { b, _, t ->
            b.box(0, 200, 239, 262, 0xFFB7B2A8.toInt())
            val off = ((t / 25) % 24).toInt()
            for (x in -off until 240 step 24) b.vline(x, 200, 262, 0xFFA29D93.toInt())
            b.hline(0, 239, 230, 0xFFA29D93.toInt())
            b.box(0, 262, 239, 267, 0xFF8E8A82.toInt()); b.hline(0, 239, 262, P.OUTLINE)
            b.box(0, 268, 239, 319, 0xFF3D4150.toInt())
            val dash = ((t / 25) % 40).toInt()
            for (x in -dash until 240 step 40) b.box(x, 292, x + 18, 294, 0xFFE9E3C8.toInt())
        },
        Prop(2) { b, env, t ->
            val off = ((t / 25) % 160).toInt()
            for (i in -1..2) {
                val x = i * 160 - off + 40
                // Árvore.
                b.box(x - 2, 180, x + 2, 214, P.FURNITURE)
                b.disc(x, 170, 17, P.OUTLINE); b.disc(x, 170, 16, P.LEAF); b.disc(x - 5, 165, 7, 0xFF6CBF82.toInt())
                // Poste.
                val lx = x + 80
                b.box(lx - 1, 140, lx + 1, 214, P.METAL_DARK)
                b.box(lx - 1, 140, lx + 10, 142, P.METAL_DARK)
                val on = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING
                b.outlined(lx + 6, 142, lx + 14, 147, if (on) P.LAMP_LIGHT else P.CREAM, P.OUTLINE)
            }
        },
        Prop(310) { b, env, t ->
            // Carro passando de tempos em tempos.
            val cycle = 5200L
            val p = t % cycle
            if (p < 2600) {
                val x = 260 - (p * 400 / 2600).toInt()
                val color = intArrayOf(P.RED, 0xFF4F7FC9.toInt(), P.YELLOW)[((t / cycle) % 3).toInt()]
                b.outlined(x, 286, x + 52, 304, color, P.OUTLINE)
                b.outlined(x + 10, 276, x + 38, 288, color, P.OUTLINE)
                b.box(x + 13, 279, x + 23, 286, 0xFF9DD3F0.toInt()); b.box(x + 26, 279, x + 35, 286, 0xFF9DD3F0.toInt())
                b.disc(x + 12, 305, 5, P.OUTLINE); b.disc(x + 40, 305, 5, P.OUTLINE)
                b.disc(x + 12, 305, 2, P.METAL); b.disc(x + 40, 305, 2, P.METAL)
                if (env.period == DayPeriod.NIGHT) b.box(x, 292, x + 2, 295, P.LAMP_LIGHT)
            }
        },
    )

    override fun lights(env: SceneEnv): List<Light> = listOf(Light.Glow(120, 230, 120, 0.45f))
}

/** Interior neutro: usado quando a detecção não sabe qual veículo é. */
class TransitScene : PixelScene(SceneId.TRANSIT) {
    override val spots = mapOf(SpotId.SEAT to Spot(120, 258), SpotId.CENTER to Spot(120, 258))
    override val defaultSpot = SpotId.SEAT
    override val walkInPlace = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        b.box(0, 0, 239, 24, 0xFFD8DDE3.toInt())
        b.box(0, 25, 239, 172, 0xFF657887.toInt())
        b.box(0, 173, 239, 319, 0xFF6C7180.toInt())
        for (y in 180..319 step 10) b.hline(0, 239, y, 0xFF62677A.toInt())
        b.box(0, 172, 239, 174, P.OUTLINE)
        b.box(0, 26, 239, 28, P.METAL); b.hline(0, 239, 29, P.METAL_DARK)
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, t ->
            val speed = TransportMotion.speed(env.transportAmbient, .7f)
            for (wx in intArrayOf(10, 86, 162)) {
                b.box(wx - 2, 38, wx + 70, 122, P.OUTLINE)
                b.box(wx - 1, 39, wx + 69, 121, 0xFFCDD3DB.toInt())
                SceneArt.skyBands(b, wx + 2, 42, wx + 66, 118, env.period)
                SceneArt.city(b, wx + 2, wx + 66, 118, env.period, offset = (t * speed / .7f / 6).toInt() + wx * 3, seed = wx)
                val pole = ((t * speed / .7f / 4) % 300).toInt()
                val px = wx + 66 - (pole - wx * 2).mod(300)
                if (px in wx + 2..wx + 66) b.box(px, 42, px + 2, 118, P.METAL_DARK)
            }
            // Painel neutro sem ícones ou acessórios que afirmem ônibus, trem ou metrô.
            b.outlined(82, 12, 158, 27, 0xFF46566A.toInt(), P.OUTLINE)
            b.box(88, 17, 152, 22, 0xFFC6D6E2.toInt())
        },
        Prop(200) { b, _, _ ->
            val seat = 0xFF2F5E9E.toInt()
            b.outlined(92, 176, 148, 240, seat, P.OUTLINE)
            b.box(95, 179, 145, 184, 0xFF4C7EC2.toInt())
        },
        Prop(262) { b, _, _ ->
            b.outlined(88, 244, 152, 262, 0xFF3C6FB0.toInt(), P.OUTLINE)
            b.hline(90, 150, 246, 0xFF5D8ED0.toInt())
        },
        Prop(318) { b, env, t ->
            val seat = 0xFF2F5E9E.toInt()
            b.outlined(4, 248, 70, 318, seat, P.OUTLINE); b.box(7, 251, 67, 256, 0xFF4C7EC2.toInt())
            b.outlined(170, 248, 236, 318, seat, P.OUTLINE); b.box(173, 251, 233, 256, 0xFF4C7EC2.toInt())
            val sway = TransportMotion.offset(t, env.transportAmbient?.vibration)
            b.box(34 + sway, 236, 38 + sway, 248, P.METAL); b.box(200 + sway, 236, 204 + sway, 248, P.METAL)
        },
    )

    override fun lights(env: SceneEnv): List<Light> = listOf(
        Light.Emissive(10, 40, 80, 120), Light.Emissive(86, 40, 156, 120), Light.Emissive(162, 40, 232, 120),
        Light.Glow(120, 10, 140, 0.6f),
    )
}

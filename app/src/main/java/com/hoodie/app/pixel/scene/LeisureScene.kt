package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Passeio ao ar livre em 3 variantes estáveis (`variant % 3`):
 * 0 parque com lago, 1 praça com chafariz, 2 área verde com colinas e flores.
 * Todas compartilham caminho, banco, mirante, poste e passarinhos.
 * Props dinâmicos: chafariz/patos/borboleta animados e o enquadramento da câmera
 * (CAMERA_ACTIVE) mirando o ponto de interesse do mirante.
 */
class LeisureScene : PixelScene(SceneId.LEISURE) {
    override val spots = mapOf(
        SpotId.PATH_A to Spot(52, 272),
        SpotId.PATH_B to Spot(188, 272),
        SpotId.WALK to Spot(120, 272),
        SpotId.BENCH to Spot(122, 228),
        SpotId.VIEWPOINT to Spot(196, 226),
        SpotId.CENTER to Spot(120, 272),
    )
    override val defaultSpot = SpotId.WALK
    override val backgroundVariants = 3

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        val night = env.period == DayPeriod.NIGHT
        SceneArt.skyBands(b, 0, 0, 239, 170, env.period)
        val grass = if (night) 0xFF3E6B48.toInt() else 0xFF7CC46B.toInt()
        when (env.variant.mod(3)) {
            0 -> { // Parque com lago.
                val far = if (night) 0xFF2F4F45.toInt() else 0xFF5E9E6A.toInt()
                for (i in 0 until 8) b.disc(i * 34 + 10, 150, 18 + (i * 7) % 9, far)
                b.box(0, 150, 239, 319, grass)
                b.dither(0, 150, 239, 319, PixelBuffer.mix(grass, P.OUTLINE, 0.12f))
                val water = if (night) 0xFF22406A.toInt() else 0xFF6FB6E0.toInt()
                // Lago achatado (elipse em perspectiva).
                for (dy in -14..14) {
                    val half = (44 * kotlin.math.sqrt(1.0 - (dy / 14.5).let { it * it })).toInt()
                    b.hline(70 - half - 1, 70 + half + 1, 192 + dy, P.OUTLINE)
                    if (dy in -13..13) b.hline(70 - half, 70 + half, 192 + dy, water)
                }
                b.hline(46, 64, 184, PixelBuffer.mix(water, P.WHITE, 0.4f)); b.hline(80, 94, 198, PixelBuffer.mix(water, P.WHITE, 0.4f))
            }
            1 -> { // Praça: prédios ao fundo e piso de pedra portuguesa.
                SceneArt.city(b, 0, 239, 150, env.period, seed = 7)
                b.box(0, 150, 239, 319, if (night) 0xFF8E8678.toInt() else 0xFFD8CDB6.toInt())
                val stone = if (night) 0xFF5E584E.toInt() else 0xFF9A8E78.toInt()
                for (y in 156..319 step 10) for (x in (if (y / 10 % 2 == 0) 0 else 5)..239 step 10) b.set(x, y, stone)
                for (x in intArrayOf(0, 200)) {
                    b.box(x, 150, x + 39, 196, grass); b.hline(x, x + 39, 150, PixelBuffer.mix(grass, P.OUTLINE, 0.3f))
                }
            }
            else -> { // Área verde: colinas e campo de flores.
                val hill = if (night) 0xFF2F4F45.toInt() else 0xFF6FB37A.toInt()
                b.disc(50, 190, 70, hill); b.disc(190, 200, 80, PixelBuffer.mix(hill, P.OUTLINE, 0.12f))
                b.box(0, 168, 239, 319, grass)
                b.dither(0, 168, 239, 319, PixelBuffer.mix(grass, P.OUTLINE, 0.12f))
                for (i in 0 until 40) {
                    val fx = (i * 83) % 240; val fy = 176 + (i * 37) % 70
                    b.set(fx, fy, if (i % 3 == 0) P.YELLOW else if (i % 3 == 1) 0xFFF29BC0.toInt() else P.WHITE)
                }
            }
        }
        // Caminho de terra comum às três variantes.
        b.box(0, 254, 239, 290, 0xFFD8B98A.toInt())
        b.hline(0, 239, 254, 0xFFB89868.toInt()); b.hline(0, 239, 290, 0xFFB89868.toInt())
        for (x in 6..239 step 23) b.set(x, 270 + x % 9, 0xFFB89868.toInt())
    }

    override fun props(): List<Prop> = listOf(
        // Árvores ao fundo (posição muda por variante).
        Prop(200) { b, env, _ ->
            val xs = when (env.variant.mod(3)) { 0 -> intArrayOf(150, 214); 1 -> intArrayOf(18, 220); else -> intArrayOf(30, 206) }
            for (x in xs) tree(b, x, 200)
        },
        // Elemento característico da variante.
        Prop(206) { b, env, t ->
            when (env.variant.mod(3)) {
                0 -> { // Patos nadando no lago.
                    val dx = ((t / 160) % 40).toInt().let { if (it < 20) it else 40 - it }
                    for ((k, base) in intArrayOf(48, 72).withIndex()) {
                        val x = base + dx - k * 6
                        b.disc(x, 190 + k * 6, 3, P.OUTLINE); b.disc(x, 190 + k * 6, 2, P.WHITE)
                        b.disc(x + 3, 187 + k * 6, 2, P.OUTLINE); b.set(x + 3, 187 + k * 6, P.WHITE); b.set(x + 5, 187 + k * 6, P.YELLOW)
                    }
                }
                1 -> { // Chafariz.
                    b.outlined(86, 188, 154, 206, 0xFFB8B0A0.toInt(), P.OUTLINE)
                    b.box(90, 190, 150, 196, 0xFF6FB6E0.toInt())
                    b.outlined(114, 168, 126, 190, 0xFFB8B0A0.toInt(), P.OUTLINE)
                    val h = 6 + ((t / 180) % 3).toInt() * 2
                    b.vline(120, 168 - h, 168, 0xFF9CD3EE.toInt())
                    for (k in 0..3) {
                        val drop = ((t / 90 + k * 3) % 12).toInt()
                        b.set(116 - drop / 2, 162 - h + drop * 2, 0xFF9CD3EE.toInt()); b.set(124 + drop / 2, 162 - h + drop * 2, 0xFF9CD3EE.toInt())
                    }
                }
                else -> { // Borboleta.
                    val bx = 60 + ((t / 50) % 120).toInt()
                    val by = 196 + ((t / 300) % 4).toInt() - 2
                    val wing = if ((t / 140) % 2 == 0L) 0xFFF2CF5B.toInt() else 0xFFE58AAE.toInt()
                    b.set(bx, by, P.OUTLINE); b.box(bx - 2, by - 1, bx - 1, by, wing); b.box(bx + 1, by - 1, bx + 2, by, wing)
                }
            }
        },
        // Mirante: grade de madeira atrás do Hoodie.
        Prop(214) { b, _, _ ->
            b.hline(170, 236, 196, P.FURNITURE); b.hline(170, 236, 206, P.FURNITURE)
            for (x in 172..236 step 12) b.outlined(x, 192, x + 2, 214, P.FURNITURE_LIGHT, P.OUTLINE)
        },
        // Banco: encosto atrás, assento na frente das pernas do Hoodie sentado.
        Prop(214) { b, _, _ ->
            b.outlined(92, 200, 152, 206, P.FURNITURE_LIGHT, P.OUTLINE)
            b.outlined(92, 208, 152, 214, P.FURNITURE_LIGHT, P.OUTLINE)
        },
        Prop(234) { b, _, _ ->
            b.outlined(90, 222, 154, 228, P.WOOD_LIGHT, P.OUTLINE)
            b.box(96, 229, 99, 240, P.METAL_DARK); b.box(145, 229, 148, 240, P.METAL_DARK)
        },
        // Poste de luz.
        Prop(250) { b, env, _ ->
            val on = env.period == DayPeriod.EVENING || env.period == DayPeriod.NIGHT
            b.box(22, 170, 24, 250, 0xFF3A3F55.toInt())
            b.outlined(16, 160, 30, 170, if (on) P.LAMP_LIGHT else P.CREAM, P.OUTLINE)
            b.box(18, 248, 28, 250, 0xFF3A3F55.toInt())
        },
        // Passarinhos atravessando o céu.
        Prop(0) { b, _, t ->
            val x = 240 - ((t / 40) % 300).toInt()
            val wing = if ((t / 200) % 2 == 0L) -1 else 1
            for (k in 0..1) {
                val bx = x + k * 14; val by = 46 + k * 6
                b.set(bx - 2, by + wing, P.OUTLINE); b.set(bx - 1, by, P.OUTLINE); b.set(bx, by + 1, P.OUTLINE)
                b.set(bx + 1, by, P.OUTLINE); b.set(bx + 2, by + wing, P.OUTLINE)
            }
        },
        // Enquadramento da câmera: cantos piscando em volta do ponto de interesse.
        Prop(0) { b, env, t ->
            if (SceneFlag.CAMERA_ACTIVE in env.flags) {
                val c = if ((t / 160) % 2 == 0L) P.WHITE else P.YELLOW
                val x0 = 150; val y0 = 112; val x1 = 230; val y1 = 168
                for ((cx, cy, sx, sy) in listOf(Corner(x0, y0, 1, 1), Corner(x1, y0, -1, 1), Corner(x0, y1, 1, -1), Corner(x1, y1, -1, -1))) {
                    b.hline(cx, cx + 6 * sx, cy, c); b.vline(cx, cy, cy + 6 * sy, c)
                }
                b.disc((x0 + x1) / 2, (y0 + y1) / 2, 1, P.RED)
            }
        },
    )

    private data class Corner(val x: Int, val y: Int, val sx: Int, val sy: Int)

    private fun tree(b: PixelBuffer, x: Int, base: Int) {
        b.box(x - 3, base - 50, x + 3, base, P.FURNITURE)
        b.disc(x, base - 64, 27, P.OUTLINE); b.disc(x, base - 64, 26, P.LEAF); b.disc(x - 8, base - 72, 12, 0xFF6CBF82.toInt())
        b.disc(x + 10, base - 54, 9, P.LEAF_DARK)
    }

    override fun lights(env: SceneEnv): List<Light> = listOf(
        Light.Glow(23, 166, 70, 0.8f),
        Light.Glow(120, 230, 140, 0.3f),
    )
}

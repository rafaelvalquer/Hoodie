package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Carro: o Hoodie vai sentado e a cidade corre atrás (paralaxe). O carro fica parado na
 * tela; o movimento vem do cenário, das faixas da rua e das rodas girando. Como nas outras
 * cenas de deslocamento, não representa o trajeto real — só "está indo de carro".
 */
class CarScene : PixelScene(SceneId.CAR) {
    override val spots = mapOf(SpotId.SEAT to Spot(SEAT_X, SEAT_Y), SpotId.CENTER to Spot(SEAT_X, SEAT_Y))
    override val defaultSpot = SpotId.SEAT
    override val walkInPlace = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        SceneArt.skyBands(b, 0, 0, 239, 205, env.period)
    }

    override fun props(): List<Prop> = listOf(
        // Cidade ao fundo, devagar; calçada e rua rápidas.
        Prop(0) { b, env, t ->
            SceneArt.city(b, 0, 239, 205, env.period, offset = (t / 90).toInt(), seed = 7)
            b.box(0, 205, 239, 214, 0xFFB7B2A8.toInt())
            val tile = ((t / 12) % 20).toInt()
            for (x in -tile until 240 step 20) b.vline(x, 205, 214, 0xFFA29D93.toInt())
            b.hline(0, 239, 214, P.OUTLINE)
            b.box(0, 215, 239, 319, 0xFF3D4150.toInt())
            val dash = ((t / 8) % 48).toInt()
            for (x in -dash until 240 step 48) b.box(x, 300, x + 24, 303, 0xFFE9E3C8.toInt())
            // Postes passando rápido (perto da câmera).
            val pole = ((t / 10) % 200).toInt()
            for (i in 0..1) {
                val px = 239 - pole + i * 200 - 100
                if (px in -4..243) {
                    b.box(px, 120, px + 2, 214, P.METAL_DARK)
                    val on = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING
                    b.outlined(px - 6, 116, px + 8, 121, if (on) P.LAMP_LIGHT else P.CREAM, P.OUTLINE)
                }
            }
        },
        // Cabine por trás do Hoodie: teto, vidros e o encosto do banco.
        Prop(100) { b, env, t ->
            val bounce = bounce(t)
            val body = CAR_BODY
            // Teto e colunas.
            b.outlined(54, 150 + bounce, 194, 160 + bounce, body, P.OUTLINE)
            b.outlined(48, 158 + bounce, 200, 214 + bounce, body, P.OUTLINE)
            // Vidros (dia: céu refletido; noite: escuro).
            val glass = if (env.period == DayPeriod.NIGHT) 0xFF2A3350.toInt() else 0xFF9DD3F0.toInt()
            b.box(56, 162 + bounce, 116, 206 + bounce, glass)
            b.box(124, 162 + bounce, 192, 206 + bounce, glass)
            b.hline(58, 80, 166 + bounce, 0xFFD6EEF9.toInt())
            // Encosto do banco atrás do Hoodie.
            b.outlined(96, 178 + bounce, 146, 214 + bounce, 0xFF3A3F55.toInt(), P.OUTLINE)
        },
        // Porta e lateral na frente do Hoodie: ele "senta" dentro do carro.
        Prop(300) { b, env, t ->
            val bounce = bounce(t)
            val body = CAR_BODY
            val shade = PixelBuffer.mix(body, P.OUTLINE, 0.25f)
            // Coluna do meio e moldura inferior das janelas (por cima do Hoodie).
            b.box(117, 160 + bounce, 123, 214 + bounce, body); b.vline(117, 160 + bounce, 214 + bounce, P.OUTLINE); b.vline(123, 160 + bounce, 214 + bounce, P.OUTLINE)
            // Lateral (portas) e para-choques.
            b.outlined(34, 214 + bounce, 214, 262 + bounce, body, P.OUTLINE)
            b.box(36, 248 + bounce, 212, 260 + bounce, shade)
            b.vline(120, 216 + bounce, 260 + bounce, P.OUTLINE)
            b.box(100, 226 + bounce, 110, 228 + bounce, P.METAL); b.box(186, 226 + bounce, 196, 228 + bounce, P.METAL) // maçanetas
            // Farol (frente à esquerda: o carro vai para a esquerda, o mundo corre para a direita).
            val night = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING
            b.outlined(30, 222 + bounce, 40, 232 + bounce, if (night) P.LAMP_LIGHT else P.CREAM, P.OUTLINE)
            b.outlined(208, 222 + bounce, 216, 232 + bounce, P.RED, P.OUTLINE)
            // Rodas girando.
            for (wx in intArrayOf(76, 172)) wheel(b, wx, 266, t)
        },
    )

    override fun lights(env: SceneEnv): List<Light> = listOf(
        Light.Glow(20, 228, 70, 0.7f),
        Light.Emissive(56, 162, 192, 206),
    )

    private fun bounce(t: Long) = if ((t / 320) % 4 == 0L) 1 else 0

    private fun wheel(b: PixelBuffer, cx: Int, cy: Int, t: Long) {
        b.disc(cx, cy, 15, P.OUTLINE)
        b.disc(cx, cy, 12, 0xFF2B2E3A.toInt())
        b.disc(cx, cy, 6, P.METAL)
        // Raios em 4 posições: a roda parece girar.
        when (((t / 70) % 4).toInt()) {
            0 -> { b.hline(cx - 10, cx + 10, cy, P.METAL_DARK) }
            1 -> { b.line(cx - 7, cy - 7, cx + 7, cy + 7, P.METAL_DARK) }
            2 -> { b.vline(cx, cy - 10, cy + 10, P.METAL_DARK) }
            else -> { b.line(cx - 7, cy + 7, cx + 7, cy - 7, P.METAL_DARK) }
        }
        b.disc(cx, cy, 2, P.OUTLINE)
    }

    companion object {
        const val SEAT_X = 120
        /** Pés do Hoodie escondidos pela porta: só cabeça e tronco aparecem na janela. */
        const val SEAT_Y = 246
        val CAR_BODY = 0xFF4F7FC9.toInt()
    }
}

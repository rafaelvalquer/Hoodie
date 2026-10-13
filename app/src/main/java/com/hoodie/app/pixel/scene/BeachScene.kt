package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.BeachAmbientState
import com.hoodie.app.pixel.npc.venue.BeachVenue
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * Praia em três faixas de profundidade: fundo (céu, nuvens, horizonte e mar), meio (areia molhada,
 * guarda-sol dos banhistas, quadra da bola) e frente (areia próxima, cadeira e guarda-sol do
 * Hoodie, carrinho de sorvete). Ondas, espuma, nuvens, gaivotas e guarda-sóis animam em ciclos
 * baratos com poucas tonalidades; a bola vem da sessão ([BeachAmbientState]).
 */
class BeachScene : PixelScene(SceneId.BEACH) {
    override val spots = mapOf(
        SpotId.SHORE to Spot(BeachVenue.HOODIE_SHORE_X, BeachVenue.HOODIE_SHORE_Y),
        SpotId.UMBRELLA to Spot(BeachVenue.HOODIE_UMBRELLA_X, BeachVenue.HOODIE_UMBRELLA_Y),
        SpotId.ICE_CREAM to Spot(BeachVenue.HOODIE_ICE_CREAM_X, BeachVenue.HOODIE_ICE_CREAM_Y),
        SpotId.PATH_A to Spot(176, 188),
        SpotId.PATH_B to Spot(136, 304),
        SpotId.CENTER to Spot(196, 248),
    )
    override val defaultSpot = SpotId.SHORE
    override val backgroundVariants = 3

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        val night = env.period == DayPeriod.NIGHT
        SceneArt.skyBands(b, 0, 0, 239, HORIZON, env.period)
        // Mar em três tons (fundo escuro, meio, raso) — as cristas animam por cima.
        val deep = if (night) 0xFF1D2F5C.toInt() else 0xFF2F6FB8.toInt()
        val mid = if (night) 0xFF25407A.toInt() else 0xFF3F8FD0.toInt()
        val shallow = if (night) 0xFF2F5590.toInt() else 0xFF66B8E0.toInt()
        b.box(0, HORIZON, 239, 112, deep)
        b.box(0, 113, 239, 136, mid)
        b.box(0, 137, 239, BeachVenue.SEA_Y, shallow)
        b.hline(0, 239, HORIZON, PixelBuffer.mix(deep, P.WHITE, 0.25f))
        // Areia molhada e seca.
        b.box(0, BeachVenue.SEA_Y + 1, 239, 172, if (night) 0xFF8A7A60.toInt() else WET_SAND)
        b.box(0, 173, 239, 319, if (night) 0xFFA8946E.toInt() else SAND)
        b.dither(0, 173, 239, 319, if (night) 0xFF9A8662.toInt() else SAND_DARK)
        for (i in 0 until 40) b.set((i * 61 + 13) % 240, 180 + (i * 37) % 136, if (i % 4 == 0) P.WHITE else SAND_DARK)
        // Detalhes por variante: castelo de areia, toalha, conchas.
        when (env.variant.mod(3)) {
            0 -> castle(b, 152, 318)
            1 -> towel(b, 112, 284)
            else -> for (i in 0 until 6) shell(b, 110 + i * 18, 296 - (i % 2) * 14)
        }
    }

    override fun props(): List<Prop> = listOf(
        // Nuvens deslizando devagar e gaivotas cruzando de vez em quando (fundo).
        Prop(0) { b, env, t ->
            val a = env.venueAmbient as? BeachAmbientState
            val off = (a?.cloudOffset ?: ((t / 90) % 480).toFloat()).toInt()
            for ((i, y) in intArrayOf(18, 34, 12).withIndex()) {
                val x = Math.floorMod(i * 160 + off, 480) - 120
                if (env.period != DayPeriod.NIGHT) SceneArt.cloud(b, x, y)
            }
            val gull = a?.seagullPhase ?: ((t % BeachAmbientState.GULL_MS) / BeachAmbientState.GULL_MS.toFloat())
            if (gull < 0.4f && env.period != DayPeriod.NIGHT) {
                val k = gull / 0.4f
                for (g in 0..1) {
                    val gx = (-20 + k * 290).toInt() - g * 18
                    val gy = 46 + g * 9 + (sin(k * 6 * PI + g) * 4).toInt()
                    val wing = if ((t / 180 + g) % 2 == 0L) -1 else 1
                    b.set(gx - 2, gy + wing, P.OUTLINE); b.set(gx - 1, gy, P.OUTLINE); b.set(gx, gy + 1, P.OUTLINE)
                    b.set(gx + 1, gy, P.OUTLINE); b.set(gx + 2, gy + wing, P.OUTLINE)
                }
            }
        },
        // Cristas das ondas e a espuma que avança e recua na areia molhada.
        Prop(0) { b, env, t ->
            val phase = (env.venueAmbient as? BeachAmbientState)?.wavePhase ?: ((t % BeachAmbientState.WAVE_MS) / BeachAmbientState.WAVE_MS.toFloat())
            val crest = if (env.period == DayPeriod.NIGHT) 0xFF5A7AB0.toInt() else 0xFFB8E4F6.toInt()
            for ((row, y) in intArrayOf(104, 118, 130, 144).withIndex()) {
                val shift = ((phase * 48).toInt() + row * 11) % 24
                var x = -24 + shift
                while (x < 240) { b.hline(x, x + 7 + row, y, crest); x += 24 + row * 2 }
            }
            val reach = (sin(phase * 2 * PI) * 5).toInt()
            val foamY = BeachVenue.SEA_Y + 6 + reach
            b.box(0, BeachVenue.SEA_Y + 1, 239, foamY - 1, if (env.period == DayPeriod.NIGHT) 0xFF2F5590.toInt() else 0xFF7CC4E4.toInt())
            for (x in 0..239) if ((x / 3 + (phase * 20).toInt()) % 5 != 0) b.set(x, foamY, P.WHITE)
        },
        // Guarda-sol e cadeira da banhista (meio da cena).
        // Guarda-sol e cadeira da banhista (no alto, à direita, perto do mar).
        Prop(198) { b, env, _ ->
            val sway = (env.venueAmbient as? BeachAmbientState)?.umbrellaSway ?: 0
            umbrella(b, 224, 206, sway, 0xFF4F7FC9.toInt(), P.YELLOW)
            chairBack(b, 206, 204, 0xFF7FC4E0.toInt())
        },
        // Guarda-sol e cadeira do Hoodie (frente à direita): encosto atrás dele, pés da cadeira na frente.
        Prop(292) { b, env, _ ->
            val sway = (env.venueAmbient as? BeachAmbientState)?.umbrellaSway ?: 0
            umbrella(b, 218, 302, -sway, P.RED, P.WHITE)
            chairBack(b, BeachVenue.HOODIE_UMBRELLA_X, BeachVenue.HOODIE_UMBRELLA_Y, 0xFFF29BC0.toInt())
        },
        Prop(308) { b, _, _ -> chairFront(b, BeachVenue.HOODIE_UMBRELLA_X, BeachVenue.HOODIE_UMBRELLA_Y) },
        // Carrinho de sorvete: o tampo esconde o sorveteiro da cintura para baixo; à noite, fechado.
        Prop(314) { b, env, t -> cart(b, env, t) },
        // Bola: na mão de quem está com ela ou em arco entre os dois, com sombra na areia.
        Prop(319) { b, env, _ ->
            val a = env.venueAmbient as? BeachAmbientState ?: return@Prop
            if (!a.ballVisible) return@Prop
            if (a.ballOwnerId == null) {
                val shadowY = 238 + (a.ballY - 182).coerceAtLeast(0) / 4
                b.hline(a.ballX - 2, a.ballX + 2, shadowY, PixelBuffer.mix(SAND, P.OUTLINE, 0.35f))
            }
            b.disc(a.ballX, a.ballY, 3, P.OUTLINE)
            b.disc(a.ballX, a.ballY, 2, P.WHITE)
            b.set(a.ballX - 1, a.ballY, P.RED); b.set(a.ballX + 1, a.ballY - 1, 0xFF4F7FC9.toInt())
        },
    )

    private fun umbrella(b: PixelBuffer, poleX: Int, baseY: Int, sway: Int, a: Int, c: Int) {
        val top = baseY - 86
        b.line(poleX, baseY, poleX + sway, top + 8, P.METAL_DARK)
        val cx = poleX + sway
        for (dy in 0..10) {
            val half = 4 + dy * 2
            for (dx in -half..half) {
                val stripe = ((dx + half) / 6) % 2 == 0
                b.set(cx + dx, top + dy, if (stripe) a else c)
            }
        }
        b.hline(cx - 24, cx + 24, top + 11, P.OUTLINE)
        for (dx in intArrayOf(-24, -12, 0, 12, 24)) b.set(cx + dx, top + 12, P.OUTLINE)
        b.set(cx, top - 1, P.OUTLINE)
    }

    private fun chairBack(b: PixelBuffer, x: Int, seatY: Int, color: Int) {
        b.outlined(x - 10, seatY - 26, x + 10, seatY - 2, color, P.OUTLINE)
        for (y in seatY - 22..seatY - 4 step 4) b.hline(x - 8, x + 8, y, PixelBuffer.mix(color, P.WHITE, 0.4f))
        b.outlined(x - 11, seatY - 3, x + 11, seatY + 1, PixelBuffer.mix(color, P.OUTLINE, 0.2f), P.OUTLINE)
    }

    private fun chairFront(b: PixelBuffer, x: Int, seatY: Int) {
        b.line(x - 10, seatY + 1, x - 13, seatY + 9, P.METAL_DARK)
        b.line(x + 10, seatY + 1, x + 13, seatY + 9, P.METAL_DARK)
    }

    private fun cart(b: PixelBuffer, env: SceneEnv, t: Long) {
        val open = (env.venueAmbient as? BeachAmbientState)?.vendorOpen ?: (env.period != DayPeriod.NIGHT)
        val x0 = BeachVenue.CART_X0; val x1 = BeachVenue.CART_X1; val top = BeachVenue.CART_TOP
        // Guarda-solzinho do carrinho.
        if (open) {
            b.vline(x1 - 6, top - 46, top, P.METAL_DARK)
            for (dy in 0..6) { val h = 4 + dy * 2; b.hline(x1 - 6 - h, x1 - 6 + h, top - 50 + dy, if (dy % 2 == 0) 0xFFF29BC0.toInt() else P.WHITE) }
        }
        b.outlined(x0, top, x1, top + 6, 0xFFE8E0F0.toInt(), P.OUTLINE)
        b.outlined(x0, top + 6, x1, BeachVenue.CART_Y1, if (open) 0xFFF6F3EA.toInt() else 0xFFD8D2C6.toInt(), P.OUTLINE)
        b.box(x0 + 2, top + 12, x1 - 2, top + 16, 0xFFF29BC0.toInt())
        // Casquinha desenhada na lateral e o sininho.
        b.line(x0 + 10, top + 30, x0 + 14, top + 22, P.OUTLINE); b.line(x0 + 18, top + 30, x0 + 14, top + 22, P.OUTLINE)
        b.disc(x0 + 14, top + 20, 3, 0xFFF29BC0.toInt())
        if (open && (t / 1_400) % 6 == 0L) b.set(x0 + 4, top - 2, P.YELLOW)
        for (wx in intArrayOf(x0 + 6, x1 - 6)) { b.disc(wx, BeachVenue.CART_Y1 + 2, 4, P.OUTLINE); b.disc(wx, BeachVenue.CART_Y1 + 2, 2, P.METAL) }
    }

    private fun castle(b: PixelBuffer, x: Int, baseY: Int) {
        val c = 0xFFD9B878.toInt()
        b.outlined(x, baseY - 12, x + 22, baseY, c, P.OUTLINE)
        b.outlined(x + 6, baseY - 22, x + 16, baseY - 12, c, P.OUTLINE)
        for (dx in intArrayOf(0, 8, 16)) b.box(x + dx, baseY - 14, x + dx + 3, baseY - 12, c)
        b.vline(x + 11, baseY - 30, baseY - 22, P.OUTLINE); b.box(x + 12, baseY - 30, x + 15, baseY - 28, P.RED)
    }

    private fun towel(b: PixelBuffer, x: Int, y: Int) {
        b.outlined(x, y, x + 34, y + 14, 0xFF7FC4E0.toInt(), P.OUTLINE)
        for (dx in 4..30 step 8) b.vline(x + dx, y + 1, y + 13, P.WHITE)
    }

    private fun shell(b: PixelBuffer, x: Int, y: Int) {
        b.set(x, y, 0xFFF2B5C8.toInt()); b.set(x + 1, y, 0xFFF2B5C8.toInt()); b.set(x, y - 1, P.WHITE)
    }

    override fun lights(env: SceneEnv): List<Light> = buildList {
        // Luar sobre o mar; nada de lâmpadas na areia.
        add(Light.Glow(180, 20, 150, 0.45f))
    }

    private companion object {
        const val HORIZON = 96
        const val SAND = 0xFFF0D9A4.toInt()
        const val SAND_DARK = 0xFFE2C68A.toInt()
        const val WET_SAND = 0xFFD8BC86.toInt()
    }
}

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

    // ── Paleta extra da rua ──
    private val sidewalkBase = 0xFFB7B2A8.toInt()
    private val sidewalkLine = 0xFFA29D93.toInt()
    private val sidewalkLight = 0xFFC4BFB5.toInt()
    private val curbTop = 0xFF8E8A82.toInt()
    private val road = 0xFF3D4150.toInt()
    private val roadDark = 0xFF333746.toInt()
    private val laneWhite = 0xFFE9E3C8.toInt()
    private val glass = 0xFF9DD3F0.toInt()
    private val glassDark = 0xFF6FAED4.toInt()
    private val drainColor = 0xFF4A4840.toInt()
    private val crosswalkWhite = 0xFFF2EDD8.toInt()
    private val benchWood = 0xFF9B6B42.toInt()
    private val trashGreen = 0xFF4D7355.toInt()
    private val hydrantRed = 0xFFCC4444.toInt()
    private val hydrantDark = 0xFF993333.toInt()
    private val busSignBlue = 0xFF3368A8.toInt()

    // Cores de carroceria (5 variações)
    private val carColors = intArrayOf(
        P.RED, 0xFF4F7FC9.toInt(), P.YELLOW, 0xFF5DAA6E.toInt(), 0xFFD4D0C6.toInt()
    )

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        SceneArt.skyBands(b, 0, 0, 239, 200, env.period)
    }

    override fun props(): List<Prop> = listOf(
        // ── Prop 0: Cidade de fundo com paralaxe ──
        Prop(0) { b, env, t ->
            StreetSkyline.drawFar(b, 0, 239, 150, t / 140, env.period)
            val near = PixelBuffer.mix(SceneArt.sky(env.period).city, P.OUTLINE, 0.25f)
            StreetSkyline.drawNear(b, 0, 239, 200, t / 60, near, P.OUTLINE, env.period)
        },

        // ── Prop 1: Calçada, meio-fio e asfalto ──
        Prop(1) { b, env, t ->
            val off = ((t / 25) % 24).toInt()
            // Calçada com textura de lajotas
            b.box(0, 200, 239, 262, sidewalkBase)
            for (x in -off until 240 step 24) b.vline(x, 200, 262, sidewalkLine)
            // Linha horizontal de junção no meio da calçada
            b.hline(0, 239, 230, sidewalkLine)
            // Detalhe sutil de textura: pontilhado em cada lajota
            for (x in -off + 6 until 240 step 24) {
                for (y in 204..258 step 12) {
                    if (x in 0..239) b.set(x, y, sidewalkLight)
                    if (x + 12 in 0..239) b.set(x + 12, y + 6, sidewalkLight)
                }
            }
            // Ralos na sarjeta (a cada 160 px, sincronizado com árvores/postes)
            val offDrain = Math.floorMod(t / 25, 160L).toInt()
            for (i in -1..2) {
                val dx = i * 160 - offDrain + 60
                if (dx in -9..239) {
                    b.box(dx, 258, dx + 8, 262, drainColor)
                    b.hline(dx + 1, dx + 7, 259, P.OUTLINE)
                    b.hline(dx + 1, dx + 7, 261, P.OUTLINE)
                }
            }
            // Faixa de pedestre (aparece a cada 320 px no ciclo)
            // Cópias a cada 320 px: a emenda do ciclo cai sempre fora da tela.
            val offCross = Math.floorMod(t / 25, 320L).toInt()
            for (i in -1..1) {
                val cx = i * 320 + 160 - offCross
                if (cx > -30 && cx < 260) for (sy in 270..314 step 8) b.box(cx, sy, cx + 22, sy + 4, crosswalkWhite)
            }
            // Meio-fio
            b.box(0, 262, 239, 264, curbTop)
            b.hline(0, 239, 265, P.OUTLINE)
            b.box(0, 266, 239, 267, PixelBuffer.mix(curbTop, P.OUTLINE, 0.4f))
            // Asfalto com textura
            b.box(0, 268, 239, 319, road)
            // Manchas escuras irregulares no asfalto
            val roadPatch = ((t / 25) % 200).toInt()
            for (i in -1..2) {
                val rpx = i * 200 - roadPatch + 80
                if (rpx in -13..239) b.box(rpx, 300, rpx + 12, 303, roadDark)
                val rpx2 = i * 200 - roadPatch + 30
                if (rpx2 in -9..239) b.box(rpx2, 278, rpx2 + 8, 280, roadDark)
            }
            // Faixa contínua de borda (topo da pista)
            b.hline(0, 239, 269, laneWhite)
            // Faixa tracejada central
            val dash = ((t / 25) % 40).toInt()
            for (x in -dash until 240 step 40) b.box(x, 292, x + 18, 294, laneWhite)
        },

        // ── Prop 2: Árvores melhoradas, postes, hidrantes, banco, lixeira, ponto de ônibus ──
        Prop(2) { b, env, t ->
            val scroll = t / 25
            val off = Math.floorMod(scroll, 160L).toInt()
            // Identidade presa ao trecho do mundo (não ao índice do laço): quando `off` volta a zero
            // o objeto que estava num ponto da calçada continua o mesmo.
            val tile = Math.floorDiv(scroll, 160L)
            val night = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING
            for (i in -1..2) {
                val x = i * 160 - off + 40
                val alt = Math.floorMod(tile + i, 2L).toInt()

                // ── Árvore melhorada ──
                // Sombra no chão
                b.box(x - 10, 213, x + 10, 216, PixelBuffer.mix(sidewalkBase, P.OUTLINE, 0.15f))
                // Tronco com detalhe
                b.box(x - 2, 178, x + 2, 214, P.FURNITURE)
                b.vline(x - 1, 182, 210, P.FURNITURE_LIGHT) // reflexo no tronco
                b.vline(x + 2, 185, 212, PixelBuffer.mix(P.FURNITURE, P.OUTLINE, 0.3f)) // sombra
                // Copa: múltiplos discos sobrepostos para formato orgânico
                val treeSize = if (alt == 0) 0 else 2 // variação de tamanho
                b.disc(x, 168, 18 + treeSize, P.OUTLINE)       // contorno
                b.disc(x, 168, 17 + treeSize, P.LEAF)           // base verde
                b.disc(x - 7, 163, 9 + treeSize, P.OUTLINE)     // contorno do tufo esquerdo
                b.disc(x - 7, 163, 8 + treeSize, 0xFF6CBF82.toInt()) // tufo claro esquerdo
                b.disc(x + 6, 165, 8 + treeSize, P.OUTLINE)     // contorno do tufo direito
                b.disc(x + 6, 165, 7 + treeSize, P.LEAF)        // tufo direito
                b.disc(x - 2, 158, 7 + treeSize, P.OUTLINE)     // topo
                b.disc(x - 2, 158, 6 + treeSize, 0xFF6CBF82.toInt()) // topo claro
                b.disc(x + 4, 170, 6, P.LEAF_DARK)              // sombra interna

                // ── Poste melhorado ──
                val lx = x + 80
                // Haste
                b.box(lx - 1, 145, lx + 1, 214, P.METAL_DARK)
                // Braço curvado
                b.box(lx - 1, 145, lx + 8, 146, P.METAL_DARK)
                b.box(lx + 7, 143, lx + 9, 146, P.METAL_DARK)
                b.box(lx + 9, 140, lx + 11, 144, P.METAL_DARK)
                // Luminária
                b.outlined(lx + 7, 137, lx + 15, 141, if (night) P.LAMP_LIGHT else P.CREAM, P.OUTLINE)
                // Cone de luz à noite
                if (night) {
                    b.dither(lx + 2, 147, lx + 20, 200, 0x30FFE9A8.toInt(), lx)
                    b.dither(lx + 5, 148, lx + 17, 190, 0x20FFE9A8.toInt(), lx + 1)
                }

                // ── Hidrante (a cada segunda posição) ──
                if (alt == 0) {
                    val hx = x + 24
                    b.outlined(hx - 3, 205, hx + 3, 214, hydrantRed, P.OUTLINE)
                    b.box(hx - 4, 202, hx + 4, 206, hydrantRed)
                    b.hline(hx - 3, hx + 3, 203, hydrantDark)
                    b.disc(hx, 200, 3, P.OUTLINE)
                    b.disc(hx, 200, 2, hydrantRed)
                }

                // ── Banco (a cada segunda posição, alternando com hidrante) ──
                if (alt == 1) {
                    val bx = x + 24
                    // Pernas
                    b.box(bx - 10, 247, bx - 8, 258, P.METAL_DARK)
                    b.box(bx + 8, 247, bx + 10, 258, P.METAL_DARK)
                    // Assento
                    b.outlined(bx - 12, 244, bx + 12, 248, benchWood, P.OUTLINE)
                    b.hline(bx - 11, bx + 11, 245, PixelBuffer.mix(benchWood, P.WHITE, 0.2f))
                    // Encosto
                    b.outlined(bx - 11, 238, bx + 11, 244, benchWood, P.OUTLINE)
                    b.hline(bx - 10, bx + 10, 240, PixelBuffer.mix(benchWood, P.WHITE, 0.15f))
                }

                // ── Lixeira (perto de cada poste) ──
                val tx = lx + 16
                b.outlined(tx - 3, 206, tx + 3, 214, trashGreen, P.OUTLINE)
                b.box(tx - 4, 203, tx + 4, 207, P.METAL_DARK)
                b.hline(tx - 3, tx + 3, 204, P.METAL)
            }

            // ── Ponto de ônibus (aparece uma vez no ciclo de 320) ──
            val offBus = Math.floorMod(t / 25, 320L).toInt()
            for (i in -1..1) {
                val bsx = i * 320 + 280 - offBus
                if (bsx > -20 && bsx < 260) {
                    // Poste do ponto
                    b.box(bsx - 1, 170, bsx + 1, 214, P.METAL_DARK)
                    // Placa
                    b.outlined(bsx - 8, 170, bsx + 8, 184, busSignBlue, P.OUTLINE)
                    // Ícone de ônibus simplificado na placa
                    b.box(bsx - 4, 174, bsx + 4, 180, P.WHITE)
                    b.box(bsx - 3, 175, bsx + 3, 179, busSignBlue)
                    b.set(bsx - 2, 178, P.WHITE); b.set(bsx + 2, 178, P.WHITE) // janelas
                    // Telhado de cobertura
                    b.outlined(bsx - 14, 185, bsx + 14, 188, P.METAL, P.OUTLINE)
                    b.box(bsx + 12, 188, bsx + 14, 214, P.METAL_DARK)
                }

                // ── Semáforo (aparece uma vez no ciclo de 320, em posição diferente do ponto) ──
                val tlx = i * 320 + 140 - offBus
                if (tlx > -12 && tlx < 250) {
                    b.box(tlx - 1, 152, tlx + 1, 214, P.METAL_DARK)
                    b.outlined(tlx - 4, 152, tlx + 4, 172, P.OUTLINE, P.OUTLINE)
                    b.box(tlx - 3, 153, tlx + 3, 171, 0xFF3A3A3A.toInt())
                    // Luz vermelha/verde/amarela baseada no tempo
                    val phase = ((t / 2500) % 3).toInt()
                    val redOn = if (phase == 0) 0xFFE83030.toInt() else 0xFF5A2020.toInt()
                    val yelOn = if (phase == 1) P.YELLOW else 0xFF5A5020.toInt()
                    val grnOn = if (phase == 2) 0xFF30E840.toInt() else 0xFF205A20.toInt()
                    b.disc(tlx, 157, 2, redOn)
                    b.disc(tlx, 162, 2, yelOn)
                    b.disc(tlx, 167, 2, grnOn)
                }
            }
        },

        // ── Prop 310: Carros, um de cada vez, alternando as pistas ──
        Prop(310) { b, env, t ->
            val night = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING
            drawHatchback(b, t, night)
            drawSedan(b, t, night)
        },
    )

    /**
     * Uma passagem de carro: posição [x] da carroceria e cor escolhida pela passagem inteira
     * (nunca por `t`), para o carro não trocar de cor/modelo enquanto cruza a tela.
     */
    internal class CarPass(val x: Int, val color: Int, val progressMs: Long)

    /** Sedan na pista de baixo (direita → esquerda); visível em [0, SEDAN_MS) de cada ciclo. */
    internal fun sedanPass(t: Long): CarPass? {
        val p = Math.floorMod(t, CAR_CYCLE_MS)
        if (p >= SEDAN_MS) return null
        val pass = Math.floorDiv(t, CAR_CYCLE_MS)
        return CarPass(250 - (p * 360 / SEDAN_MS).toInt(), carColors[Math.floorMod(pass * 3, 5L).toInt()], p)
    }

    /** Hatch na pista de cima (esquerda → direita), meio ciclo depois do sedan: nunca juntos. */
    internal fun hatchPass(t: Long): CarPass? {
        val shifted = t - CAR_CYCLE_MS / 2
        val p = Math.floorMod(shifted, CAR_CYCLE_MS)
        if (p >= HATCH_MS) return null
        val pass = Math.floorDiv(shifted, CAR_CYCLE_MS)
        return CarPass(-90 + (p * 340 / HATCH_MS).toInt(), carColors[Math.floorMod(pass * 3 + 2, 5L).toInt()], p)
    }

    /** Cabine em degraus: [left]/[right] dão as bordas em cada linha (0 = teto). */
    private inline fun cabin(b: PixelBuffer, y0: Int, rows: Int, color: Int, left: (Int) -> Int, right: (Int) -> Int) {
        for (r in 0 until rows) {
            val l = left(r); val rt = right(r)
            b.hline(l, rt, y0 + r, if (r == 0) P.OUTLINE else color)
            b.set(l, y0 + r, P.OUTLINE); b.set(rt, y0 + r, P.OUTLINE)
        }
    }

    private fun wheel(b: PixelBuffer, cx: Int, cy: Int, r: Int, angle: Double) {
        b.disc(cx, cy, r, P.OUTLINE)       // pneu
        b.disc(cx, cy, r - 3, P.METAL)     // aro
        b.disc(cx, cy, 1, P.OUTLINE)       // centro
        for (k in 0..2) {
            val a = angle + k * 2.094 // 120° entre raios
            b.line(cx, cy, cx + (kotlin.math.cos(a) * (r - 3)).toInt(), cy + (kotlin.math.sin(a) * (r - 3)).toInt(), P.OUTLINE)
        }
    }

    // ── Sedan (pista de baixo, frente à esquerda, y ≈ 274-318) ──
    private fun drawSedan(b: PixelBuffer, t: Long, night: Boolean) {
        val car = sedanPass(t) ?: return
        val x = car.x
        val color = car.color
        val colorDark = PixelBuffer.mix(color, P.OUTLINE, 0.25f)
        val tint = PixelBuffer.mix(glass, P.OUTLINE, 0.4f)

        // Cabine: para-brisa inclinado na frente (esquerda), vidro traseiro mais curto.
        cabin(b, 274, 16, color, { r -> x + 26 - r * 2 / 3 }, { r -> x + 76 + r / 2 })
        for (y in 277..288) {
            val r = y - 274
            b.hline(x + 29 - r * 2 / 3, x + 48, y, glass)
            b.hline(x + 52, x + 73 + r / 2, y, glass)
        }
        b.box(x + 49, 276, x + 51, 289, P.OUTLINE) // pilar B
        b.box(x + 34, 279, x + 42, 287, tint)      // motorista
        // Carroceria
        b.outlined(x, 289, x + 100, 309, color, P.OUTLINE)
        b.hline(x + 1, x + 99, 290, PixelBuffer.mix(color, P.WHITE, 0.2f)) // reflexo
        b.hline(x + 1, x + 99, 308, colorDark)
        b.vline(x + 50, 291, 306, colorDark) // porta
        b.box(x + 56, 294, x + 59, 295, P.METAL) // maçaneta
        b.box(x - 3, 296, x, 307, P.METAL_DARK)          // para-choque dianteiro
        b.box(x + 100, 296, x + 103, 307, P.METAL_DARK)  // traseiro
        b.box(x, 293, x + 4, 297, if (night) P.LAMP_LIGHT else P.CREAM) // farol
        b.box(x + 96, 293, x + 100, 297, P.RED)                          // lanterna
        if (night) b.box(x - 26, 301, x - 4, 306, PixelBuffer.mix(P.LAMP_LIGHT, road, 0.7f))
        // Rodas: indo para a esquerda giram no sentido anti-horário.
        val angle = -car.progressMs / 55.0
        wheel(b, x + 22, 310, 9, angle)
        wheel(b, x + 78, 310, 9, angle)
    }

    // ── Hatchback (pista de cima, frente à direita, y ≈ 268-310) ──
    private fun drawHatchback(b: PixelBuffer, t: Long, night: Boolean) {
        val car = hatchPass(t) ?: return
        val x = car.x
        val color = car.color
        val colorDark = PixelBuffer.mix(color, P.OUTLINE, 0.25f)
        val tint = PixelBuffer.mix(glass, P.OUTLINE, 0.4f)

        // Cabine: traseira quase reta (esquerda), para-brisa inclinado na frente (direita).
        cabin(b, 268, 16, color, { r -> x + 8 - r / 5 }, { r -> x + 54 + r * 2 / 3 })
        for (y in 271..282) {
            val r = y - 268
            b.hline(x + 11 - r / 5, x + 32, y, glass)
            b.hline(x + 36, x + 51 + r * 2 / 3, y, glass)
        }
        b.box(x + 33, 270, x + 35, 283, P.OUTLINE) // pilar
        b.box(x + 40, 273, x + 47, 281, tint)      // motorista
        // Carroceria
        b.outlined(x, 283, x + 80, 301, color, P.OUTLINE)
        b.hline(x + 1, x + 79, 284, PixelBuffer.mix(color, P.WHITE, 0.2f))
        b.hline(x + 1, x + 79, 300, colorDark)
        b.vline(x + 38, 285, 298, colorDark) // porta
        b.box(x + 43, 288, x + 46, 289, P.METAL)
        b.box(x + 80, 290, x + 83, 300, P.METAL_DARK) // para-choque dianteiro
        b.box(x - 3, 290, x, 300, P.METAL_DARK)       // traseiro
        b.box(x + 76, 287, x + 80, 291, if (night) P.LAMP_LIGHT else P.CREAM) // farol
        b.box(x, 287, x + 3, 291, P.RED)                                      // lanterna
        if (night) b.box(x + 84, 294, x + 106, 299, PixelBuffer.mix(P.LAMP_LIGHT, road, 0.7f))
        // Rodas: indo para a direita giram no sentido horário.
        val angle = car.progressMs / 55.0
        wheel(b, x + 17, 302, 8, angle)
        wheel(b, x + 63, 302, 8, angle)
    }

    internal companion object {
        const val CAR_CYCLE_MS = 9_600L
        const val SEDAN_MS = 3_000L
        const val HATCH_MS = 3_400L
    }

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

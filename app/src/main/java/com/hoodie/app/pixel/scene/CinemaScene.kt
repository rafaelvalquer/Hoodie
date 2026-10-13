package com.hoodie.app.pixel.scene

import com.hoodie.app.pixel.npc.venue.CinemaAmbient
import com.hoodie.app.pixel.npc.venue.CinemaLightingController
import com.hoodie.app.pixel.npc.venue.CinemaPhase
import com.hoodie.app.pixel.npc.venue.CinemaVenue
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Cinema em corte: o saguão no térreo (bilheteria, porta, escada central e bomboniere com
 * máquina de pipoca) e a sala no andar de cima, vista a partir da tela — a plateia de frente
 * para nós. A luz da sala ([CinemaLightingController]) baixa suave, banha a plateia com as cores
 * do filme e volta a acender no fim; o saguão continua claro. Duas composições no mesmo
 * ambiente, ligadas pela escada (sem precisar de um fade global entre cenas).
 */
class CinemaScene : PixelScene(SceneId.CINEMA) {
    override val spots = mapOf(
        SpotId.CINEMA_SEAT to Spot(CinemaVenue.HOODIE_SEAT_X, CinemaVenue.ROW_1),
        SpotId.POPCORN to Spot(CinemaVenue.HOODIE_POPCORN_X, CinemaVenue.HOODIE_POPCORN_Y),
        SpotId.BOX_OFFICE to Spot(CinemaVenue.HOODIE_BOX_OFFICE_X, CinemaVenue.HOODIE_BOX_OFFICE_Y),
        SpotId.CENTER to Spot(56, 302),
        SpotId.DOOR to Spot(76, 232),
    )
    override val defaultSpot = SpotId.CINEMA_SEAT
    override val hasAnimatedDoor = true
    override val backgroundVariants = 3

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        val v = env.variant.mod(3)
        // ── Sala (andar de cima) ──
        b.box(0, 0, 239, HALL_BOTTOM, HALL_WALL[v])
        for (x in 0..239 step 24) b.vline(x, 0, 70, PixelBuffer.mix(HALL_WALL[v], P.OUTLINE, 0.25f))
        // Cabine de projeção no fundo.
        b.outlined(110, 4, 158, 20, 0xFF14141E.toInt(), P.OUTLINE)
        b.box(124, 9, 144, 15, 0xFF2A2A3A.toInt()); b.disc(134, 12, 2, P.LAMP_LIGHT)
        // Degraus da arquibancada (uma faixa de piso por fileira).
        for ((i, row) in intArrayOf(CinemaVenue.ROW_3, CinemaVenue.ROW_2, CinemaVenue.ROW_1).withIndex()) {
            val top = row - 28
            b.box(0, top, 239, row + 8, PixelBuffer.mix(HALL_FLOOR, P.OUTLINE, 0.12f * i))
            b.hline(0, 239, top, P.OUTLINE)
        }
        // Corredor central com degraus iluminados.
        b.box(CinemaVenue.AISLE_X - 8, 46, CinemaVenue.AISLE_X + 8, HALL_BOTTOM, PixelBuffer.mix(CARPET, P.OUTLINE, 0.2f))
        for (y in 50..HALL_BOTTOM step 10) b.hline(CinemaVenue.AISLE_X - 8, CinemaVenue.AISLE_X + 8, y, 0xFFE8B84A.toInt())
        // ── Laje e escada central ──
        b.box(0, HALL_BOTTOM + 1, 239, 176, 0xFF2A2230.toInt()); b.hline(0, 239, HALL_BOTTOM + 1, P.OUTLINE)
        // ── Saguão (térreo) ──
        b.box(0, 177, 239, 232, LOBBY_WALL[v])
        b.box(0, 228, 239, 232, PixelBuffer.mix(LOBBY_WALL[v], P.OUTLINE, 0.25f))
        // Bilheteria.
        b.outlined(4, 180, 52, 228, P.WOOD_DARK, P.OUTLINE)
        b.box(8, 192, 48, 214, 0xFFBFD8E6.toInt())
        b.disc(28, 204, 5, 0xFF8A72A6.toInt()); b.box(22, 208, 34, 214, 0xFF46649A.toInt())
        b.outlined(4, 180, 52, 189, SIGN[v], P.OUTLINE); b.glyph(TICKETS, 10, 182, P.WHITE)
        b.box(8, 214, 48, 218, P.WOOD)
        // Pôsteres de filmes (formas abstratas).
        poster(b, 98, 184, 0xFF3F6E9E.toInt(), 0xFFF2CF5B.toInt())
        poster(b, 162, 184, 0xFF9E3F5A.toInt(), 0xFF7FE0C2.toInt())
        // Escada até a sala.
        b.box(112, 166, 158, 240, CARPET)
        for (y in 170..238 step 8) { b.hline(112, 158, y, PixelBuffer.mix(CARPET, P.OUTLINE, 0.35f)); b.hline(112, 158, y + 1, PixelBuffer.mix(CARPET, P.WHITE, 0.15f)) }
        b.vline(110, 166, 240, P.METAL); b.vline(160, 166, 240, P.METAL)
        // Menu da bomboniere e máquina de pipoca.
        b.outlined(178, 180, 236, 194, 0xFF2F2F3E.toInt(), P.OUTLINE); b.glyph(PIPOCA, 186, 184, P.YELLOW)
        b.outlined(206, 196, 236, 238, 0xFFC9544F.toInt(), P.OUTLINE)
        b.box(209, 200, 233, 226, 0xFFF6F3EA.toInt())
        b.box(209, 218, 233, 226, 0xFFFFF1B8.toInt())
        // Carpete do saguão.
        b.box(0, 233, 239, 319, CARPET)
        for (i in 0 until 60) b.set((i * 41 + 7) % 240, 236 + (i * 23) % 82, 0xFFE8B84A.toInt())
    }

    override fun props(): List<Prop> = buildList {
        // Fileiras: encosto atrás de quem senta (baseline acima do quadril) e a borda da fileira
        // na frente (esconde as pernas de quem está sentado nela).
        for (row in intArrayOf(CinemaVenue.ROW_3, CinemaVenue.ROW_2, CinemaVenue.ROW_1)) {
            add(Prop(row - 3) { b, _, _ -> seatBacks(b, row) })
            add(Prop(row + 6) { b, _, _ -> rowFront(b, row) })
        }
        // Porta do saguão: abre para o Hoodie (doorFrame) e para o público.
        add(Prop(0) { b, env, _ ->
            val open = maxOf(env.doorFrame, if ((env.venueAmbient as? CinemaAmbient)?.doorOpen == true) SceneEnv.DOOR_OPEN else 0)
            b.outlined(58, 184, 94, 232, P.METAL_DARK, P.OUTLINE)
            b.box(60, 186, 92, 231, 0xFF1E2238.toInt())
            val gap = open * 5
            b.outlined(60 - gap, 186, 76 - gap, 231, GLASS, P.METAL_DARK)
            b.outlined(76 + gap, 186, 92 + gap, 231, GLASS, P.METAL_DARK)
        })
        // Letreiro "SALA 1" piscando acima da escada.
        add(Prop(0) { b, env, t ->
            val phase = (env.venueAmbient as? CinemaAmbient)?.phase ?: CinemaLightingController.phaseAt(t)
            val on = phase != CinemaPhase.SCREENING || (t / 600) % 2 == 0L
            b.outlined(114, 166, 156, 175, 0xFF14141E.toInt(), P.OUTLINE)
            b.glyph(SALA, 120, 168, if (on) 0xFFF2CF5B.toInt() else 0xFF8A6A2A.toInt())
        })
        // Pipoca pulando na máquina (bem mais quando a atendente está enchendo um balde).
        add(Prop(0) { b, env, t ->
            val popping = (env.venueAmbient as? CinemaAmbient)?.popping == true
            val n = if (popping) 7 else 2
            for (i in 0 until n) {
                val k = ((t / 90) + i * 13) % 26
                b.set(211 + (i * 5) % 21, 217 - k.toInt() % 16, 0xFFFFF1B8.toInt())
            }
        })
        // Balcão da bomboniere (na frente da atendente, atrás de quem compra).
        add(Prop(288) { b, _, _ ->
            val x0 = CinemaVenue.COUNTER_X0; val top = CinemaVenue.COUNTER_TOP
            b.outlined(x0, top, 238, top + 6, 0xFFE8D6B4.toInt(), P.OUTLINE)
            b.outlined(x0, top + 6, 238, 288, 0xFF6F2A3A.toInt(), P.OUTLINE)
            b.box(x0 + 4, top + 14, 236, top + 18, 0xFFE8B84A.toInt())
            // Baldes de pipoca expostos e a registradora.
            for (i in 0 until 3) { val x = x0 + 8 + i * 12; b.outlined(x, top - 9, x + 8, top, P.WHITE, P.OUTLINE); b.vline(x + 4, top - 8, top - 1, P.RED) }
            b.outlined(204, top - 12, 224, top, 0xFF2A2D3E.toInt(), P.OUTLINE); b.box(207, top - 9, 221, top - 6, P.CODE_1)
        })
        add(Prop(318) { b, _, _ -> SceneArt.plant(b, 98, 318, big = true) })
    }

    private fun seatBacks(b: PixelBuffer, row: Int) {
        for (x in CinemaVenue.SEAT_XS_LEFT + CinemaVenue.SEAT_XS_RIGHT) {
            b.outlined(x - 11, row - 26, x + 11, row - 2, SEAT, P.OUTLINE)
            b.hline(x - 9, x + 9, row - 24, PixelBuffer.mix(SEAT, P.WHITE, 0.25f))
            b.outlined(x - 12, row - 3, x + 12, row + 1, PixelBuffer.mix(SEAT, P.OUTLINE, 0.2f), P.OUTLINE)
        }
    }

    private fun rowFront(b: PixelBuffer, row: Int) {
        for ((x0, x1) in listOf(30 to 114, 136 to 216)) {
            b.outlined(x0, row + 2, x1, row + 8, 0xFF3A2430.toInt(), P.OUTLINE)
            b.hline(x0 + 1, x1 - 1, row + 3, PixelBuffer.mix(SEAT, P.OUTLINE, 0.3f))
        }
    }

    private fun poster(b: PixelBuffer, x: Int, y: Int, bg: Int, accent: Int) {
        b.outlined(x, y, x + 12, y + 30, bg, P.OUTLINE)
        b.disc(x + 6, y + 10, 3, accent); b.hline(x + 2, x + 10, y + 22, accent); b.hline(x + 3, x + 9, y + 25, P.WHITE)
    }

    /**
     * Luz da sala por cima de tudo (incluindo o Hoodie e a plateia): escurece em degraus,
     * mistura a cor do filme e desenha o feixe do projetor durante a sessão. O saguão não muda.
     */
    override fun drawPostLighting(buffer: PixelBuffer, env: SceneEnv, timeMs: Long) {
        val a = env.venueAmbient as? CinemaAmbient ?: CinemaAmbient.atTime(timeMs)
        if (a.light >= 0.999f) return
        // Oito degraus de luz (estética pixel), arredondados: nunca mais escuro do que o controlador pede.
        val level = (Math.round(a.light * 8).coerceIn(4, 8)) * 32
        val screening = a.phase == CinemaPhase.SCREENING
        val sr = (a.screenColor shr 16) and 0xFF; val sg = (a.screenColor shr 8) and 0xFF; val sb = a.screenColor and 0xFF
        val px = buffer.pixels
        for (y in 0..HALL_BOTTOM) {
            val row = y * buffer.width
            for (x in 0 until buffer.width) {
                val c = px[row + x]
                var r = (((c shr 16) and 0xFF) * level) shr 8
                var g = (((c shr 8) and 0xFF) * level) shr 8
                var bl = ((c and 0xFF) * level) shr 8
                if (screening) { r = (r * 7 + sr) shr 3; g = (g * 7 + sg) shr 3; bl = (bl * 7 + sb) shr 3 }
                px[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        if (!screening) return
        // Feixe do projetor: raios esparsos da cabine em direção à tela (para o nosso lado).
        for (ray in -3..3) {
            for (s in 0..60 step 3) {
                val x = 134 + ray * s * 2 / 5; val y = 20 + s * 2
                if (y > HALL_BOTTOM || x !in 0..239) break
                if ((s + ray + (timeMs / 120).toInt()) % 4 == 0) {
                    val i = y * buffer.width + x
                    px[i] = PixelBuffer.mix(px[i], a.screenColor, 0.35f)
                }
            }
        }
    }

    override fun lights(env: SceneEnv): List<Light> = listOf(
        Light.Glow(30, 200, 60, 0.6f), Light.Glow(210, 200, 70, 0.7f), Light.Emissive(178, 180, 236, 194), Light.Emissive(114, 166, 156, 175),
    )

    private companion object {
        const val HALL_BOTTOM = 162
        val HALL_WALL = intArrayOf(0xFF5A2236.toInt(), 0xFF2E3458.toInt(), 0xFF3E2A4E.toInt())
        val LOBBY_WALL = intArrayOf(0xFFF2E3C3.toInt(), 0xFFE8E0D0.toInt(), 0xFFF0DCD0.toInt())
        val SIGN = intArrayOf(0xFF9E3F5A.toInt(), 0xFF3F6E9E.toInt(), 0xFF6B4A8E.toInt())
        const val HALL_FLOOR = 0xFF4A3040.toInt()
        const val CARPET = 0xFF8E2E3A.toInt()
        const val SEAT = 0xFFB8323E.toInt()
        const val GLASS = 0xFFB8DDEE.toInt()

        val TICKETS = listOf("### # ### # # ### ###", " #  # #   ##  ##   # ", " #  # #   # # #    # ", " #  # ### # # ###  # ")
        val PIPOCA = listOf("##  # ##   #   ##  #  ", "# # # # # # # #   # # ", "##  # ##  # # #   ###", "#   # #    #   ## # #")
        val SALA = listOf(" ## #  #    #   #", "#  # # #   # # ##", " # ### #   ###  #", "## # # ### # # ###")
    }
}

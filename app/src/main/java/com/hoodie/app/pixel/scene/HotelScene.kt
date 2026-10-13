package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.ElevatorDoor
import com.hoodie.app.pixel.npc.venue.HotelAmbient
import com.hoodie.app.pixel.npc.venue.HotelVenue
import com.hoodie.app.pixel.npc.venue.LuggageAttachment
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Hotel: saguão e recepção (sem quartos nesta etapa). Balcão com computador e campainha, quadro de
 * chaves, porta de vidro, dois elevadores (o do Hoodie e o dos hóspedes), sofá, poltrona e mesa
 * de centro. As folhas das portas dos elevadores ficam na frente da cabine: quem está dentro some
 * atrás delas. Mala no chão e porta do elevador dos hóspedes vêm da sessão ([HotelAmbient]).
 */
class HotelScene : PixelScene(SceneId.HOTEL) {
    override val spots = mapOf(
        SpotId.RECEPTION to Spot(HotelVenue.HOODIE_RECEPTION_X, HotelVenue.HOODIE_RECEPTION_Y),
        SpotId.LOBBY_SOFA to Spot(HotelVenue.HOODIE_SOFA_X, HotelVenue.SOFA_Y),
        SpotId.ELEVATOR to Spot(HotelVenue.HOODIE_ELEVATOR_X, HotelVenue.ELEVATOR_Y),
        SpotId.CENTER to Spot(130, 252),
        SpotId.DOOR to Spot(130, 160),
    )
    override val defaultSpot = SpotId.CENTER
    override val hasAnimatedDoor = true
    override val backgroundVariants = 3

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        val v = env.variant.mod(3)
        b.box(0, 0, 239, 150, WALL[v])
        // Lambri na metade de baixo da parede.
        b.box(0, 100, 239, 150, PixelBuffer.mix(WALL[v], P.WOOD_DARK, 0.45f))
        b.hline(0, 239, 100, P.WOOD_LIGHT); b.hline(0, 239, 101, P.OUTLINE)
        for (x in 0..239 step 20) b.vline(x, 104, 150, PixelBuffer.mix(WALL[v], P.OUTLINE, 0.4f))
        // Letreiro.
        b.outlined(84, 8, 156, 26, 0xFF1E2238.toInt(), 0xFFE8B84A.toInt())
        b.glyph(HOTEL, 98, 12, 0xFFE8B84A.toInt())
        // Quadro de chaves atrás da recepção.
        b.outlined(4, 60, 38, 96, P.WOOD_DARK, P.OUTLINE)
        for (r in 0 until 3) for (k in 0 until 4) {
            val x = 8 + k * 8; val y = 66 + r * 10
            b.set(x, y, P.METAL); b.vline(x, y + 1, y + 4, 0xFFE8B84A.toInt())
        }
        // Quadros decorativos.
        b.outlined(48, 40, 76, 62, 0xFFE8B84A.toInt(), P.OUTLINE); b.box(50, 42, 74, 60, 0xFF7FB8A6.toInt()); b.disc(62, 52, 5, 0xFFF2CF5B.toInt())
        // Elevadores: molduras e indicadores de andar.
        for (x0 in intArrayOf(152, 196)) {
            b.outlined(x0, 74, x0 + 36, 150, 0xFFC9A04A.toInt(), P.OUTLINE)
            b.outlined(x0 + 10, 64, x0 + 26, 72, 0xFF1E2238.toInt(), P.OUTLINE)
            b.box(x0 + 38, 104, x0 + 40, 110, P.METAL); b.set(x0 + 39, 106, P.CODE_1)
        }
        // Piso de mármore.
        val a = FLOOR_A[v]; val c = FLOOR_B[v]
        for (y in 151..319 step 18) for (x in 0..239 step 18) b.box(x, y, minOf(x + 17, 239), minOf(y + 17, 319), if ((x / 18 + y / 18) % 2 == 0) a else c)
        b.box(0, 150, 239, 152, P.OUTLINE)
        // Tapete do saguão.
        b.outlined(98, 246, 226, 312, RUG[v], P.OUTLINE)
        b.outlined(104, 252, 220, 306, PixelBuffer.mix(RUG[v], P.WHITE, 0.15f), PixelBuffer.mix(RUG[v], P.OUTLINE, 0.3f))
    }

    override fun props(): List<Prop> = listOf(
        // Lustre.
        Prop(0) { b, env, t ->
            b.vline(120, 0, 30, P.OUTLINE)
            b.outlined(108, 30, 132, 36, 0xFFE8B84A.toInt(), P.OUTLINE)
            for (i in 0 until 4) b.set(110 + i * 7, 38 + ((t / 500 + i) % 2).toInt(), if (env.period == DayPeriod.DAY) P.WHITE else P.LAMP_LIGHT)
        },
        // Porta de entrada (vidro): abre para o Hoodie e para quem chega da rua.
        Prop(0) { b, env, _ ->
            val open = maxOf(env.doorFrame, if ((env.venueAmbient as? HotelAmbient)?.doorOpen == true) SceneEnv.DOOR_OPEN else 0)
            b.outlined(112, 68, 148, 151, 0xFFC9A04A.toInt(), P.OUTLINE)
            b.box(114, 70, 146, 149, PixelBuffer.mix(SceneArt.sky(env.period).bottom, P.WHITE, 0.2f))
            val gap = open * 5
            b.outlined(114 - gap, 70, 130 - gap, 149, GLASS, P.METAL_DARK)
            b.outlined(130 + gap, 70, 146 + gap, 149, GLASS, P.METAL_DARK)
        },
        // Cabines dos elevadores (atrás de quem entra).
        Prop(0) { b, _, _ -> for (x0 in intArrayOf(152, 196)) { b.box(x0 + 2, 76, x0 + 34, 150, CABIN); b.hline(x0 + 4, x0 + 32, 80, P.LAMP_LIGHT) } },
        // Folhas das portas, na frente da cabine (escondem quem está lá dentro).
        Prop(170) { b, env, t ->
            // Elevador do Hoodie: aberto do botão até a saída; fechado com ele dentro.
            val hoodieOpen = when {
                SceneFlag.INSIDE_ELEVATOR in env.flags -> 0f
                SceneFlag.ELEVATOR_OPEN in env.flags -> 1f
                else -> 0f
            }
            doors(b, 152, hoodieOpen)
            val npc = (env.venueAmbient as? HotelAmbient)?.elevator
            doors(b, 196, npc?.openness ?: 0f)
            // Indicadores: seta acesa quando a cabine está no andar.
            if (hoodieOpen > 0f) b.hline(164, 176, 67, P.CODE_1)
            if (npc != null && npc.door != ElevatorDoor.CLOSED) b.hline(208, 220, 67, P.CODE_1)
            if ((t / 700) % 2 == 0L) b.set(170, 69, P.RED)
        },
        // Balcão da recepção (na frente da recepcionista, atrás de quem é atendido).
        Prop(214) { b, env, t ->
            val busy = (env.venueAmbient as? HotelAmbient)?.computerBusy == true
            val top = HotelVenue.DESK_TOP
            b.outlined(6, top, 112, top + 6, 0xFFE8D6B4.toInt(), P.OUTLINE)
            b.outlined(6, top + 6, 112, 214, DESK[env.variant.mod(3)], P.OUTLINE)
            b.box(10, top + 14, 108, top + 16, 0xFFE8B84A.toInt())
            // Computador e campainha sobre o tampo.
            b.outlined(76, top - 18, 100, top - 2, 0xFF2A2D3E.toInt(), P.OUTLINE)
            b.box(79, top - 15, 97, top - 6, if (busy) P.CODE_1 else P.SCREEN)
            if (busy && (t / 250) % 2 == 0L) b.hline(81, 95, top - 11, P.OUTLINE)
            b.vline(88, top - 2, top, P.OUTLINE)
            b.disc(30, top - 2, 3, 0xFFE8B84A.toInt()); b.hline(26, 34, top, P.OUTLINE)
        },
        // Mala no chão: do hóspede durante o check-in e a do Hoodie quando ele a apoia.
        Prop(226) { b, env, _ ->
            (env.venueAmbient as? HotelAmbient)?.suitcaseOnFloor?.let { (x, y) -> suitcase(b, x, y) }
            if (SceneFlag.SUITCASE_DOWN in env.flags) suitcase(b, HotelVenue.HOODIE_RECEPTION_X + LuggageAttachment.FLOOR_DX, HotelVenue.HOODIE_RECEPTION_Y)
        },
        // Sofá (encosto atrás de quem senta, assento na frente) e poltrona.
        Prop(276) { b, env, _ ->
            val c = SOFA[env.variant.mod(3)]
            b.outlined(18, 254, 102, 278, c, P.OUTLINE); b.hline(20, 100, 256, PixelBuffer.mix(c, P.WHITE, 0.3f))
            b.outlined(184, 258, 210, 280, c, P.OUTLINE)
        },
        Prop(298) { b, env, _ ->
            val c = SOFA[env.variant.mod(3)]
            b.outlined(14, 280, 106, 292, PixelBuffer.mix(c, P.WHITE, 0.12f), P.OUTLINE)
            b.outlined(14, 268, 22, 292, c, P.OUTLINE); b.outlined(98, 268, 106, 292, c, P.OUTLINE)
            b.outlined(180, 282, 214, 294, PixelBuffer.mix(c, P.WHITE, 0.12f), P.OUTLINE)
        },
        // Mesa de centro com revistas e vaso.
        Prop(290) { b, _, _ ->
            b.outlined(112, 270, 150, 278, P.WOOD, P.OUTLINE)
            b.box(116, 279, 118, 288, P.WOOD_DARK); b.box(144, 279, 146, 288, P.WOOD_DARK)
            b.outlined(118, 266, 130, 270, 0xFF4F7FC9.toInt(), P.OUTLINE)
            b.outlined(136, 258, 142, 270, 0xFFF6F3EA.toInt(), P.OUTLINE); b.disc(139, 255, 3, 0xFFE07A93.toInt())
        },
        Prop(316) { b, _, _ -> SceneArt.plant(b, 224, 316, big = true) },
    )

    private fun doors(b: PixelBuffer, x0: Int, openness: Float) {
        val half = 16
        val slide = (half * openness.coerceIn(0f, 1f)).toInt()
        // Folha esquerda e direita deslizando para as laterais.
        b.outlined(x0 + 2, 76, x0 + 2 + half - slide, 162, DOOR_METAL, P.OUTLINE)
        b.outlined(x0 + 34 - half + slide, 76, x0 + 34, 162, DOOR_METAL, P.OUTLINE)
        b.vline(x0 + 6, 80, 158, PixelBuffer.mix(DOOR_METAL, P.WHITE, 0.3f))
    }

    private fun suitcase(b: PixelBuffer, x: Int, y: Int) {
        b.outlined(x - 5, y - 16, x + 5, y, 0xFF4F7FC9.toInt(), P.OUTLINE)
        b.vline(x, y - 14, y - 2, 0xFF3A5E9A.toInt())
        b.hline(x - 2, x + 2, y - 19, P.OUTLINE); b.vline(x - 2, y - 19, y - 16, P.OUTLINE); b.vline(x + 2, y - 19, y - 16, P.OUTLINE)
    }

    override fun lights(env: SceneEnv): List<Light> = buildList {
        add(Light.Glow(120, 36, 110, 0.85f)); add(Light.Emissive(84, 8, 156, 26))
        add(Light.Emissive(154, 76, 186, 150)); add(Light.Emissive(198, 76, 230, 150))
        if (env.period == DayPeriod.DAY || env.period == DayPeriod.MORNING) add(Light.Emissive(114, 70, 146, 149))
    }

    private companion object {
        val WALL = intArrayOf(0xFFE9DCC6.toInt(), 0xFFDCE4E8.toInt(), 0xFFE6D8E2.toInt())
        val FLOOR_A = intArrayOf(0xFFF2EEE6.toInt(), 0xFFEEF0F2.toInt(), 0xFFF0EAEE.toInt())
        val FLOOR_B = intArrayOf(0xFFDCD4C4.toInt(), 0xFFD4DADE.toInt(), 0xFFDCD0D8.toInt())
        val RUG = intArrayOf(0xFF8E2E3A.toInt(), 0xFF2E4A7A.toInt(), 0xFF3F6E5A.toInt())
        val DESK = intArrayOf(0xFF6B3E2C.toInt(), 0xFF3A3F55.toInt(), 0xFF5A3A4E.toInt())
        val SOFA = intArrayOf(0xFF4F7FC9.toInt(), 0xFFC9544F.toInt(), 0xFF6B8E5A.toInt())
        const val GLASS = 0xFFB8DDEE.toInt()
        const val CABIN = 0xFF4A4038.toInt()
        const val DOOR_METAL = 0xFFB8BCC8.toInt()

        val HOTEL = listOf("# #  ##  ### ### #  ", "# # #  #  #  #   #  ", "### #  #  #  ##  #  ", "# # #  #  #  #   #  ", "# #  ##   #  ### ###")
    }
}

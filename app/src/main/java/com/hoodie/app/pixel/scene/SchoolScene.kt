package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Sala de estudo: quadro-negro, janela, relógio, estante, pôster, carteira e cadeira.
 * Props dinâmicos: livro aberto na mesa (BOOK_OPEN) com a página que vira (PAGE_TURNED)
 * e a luminária, acesa à noite.
 */
class SchoolScene : PixelScene(SceneId.SCHOOL) {
    override val spots = mapOf(
        SpotId.DESK to Spot(110, 270),
        SpotId.BOOKS to Spot(198, 216),
        SpotId.BOARD to Spot(150, 200),
        SpotId.WINDOW to Spot(76, 196),
        SpotId.DOOR to Spot(27, 162),
        SpotId.CENTER to Spot(140, 232),
    )
    override val defaultSpot = SpotId.DESK
    override val hasAnimatedDoor = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        // Parede de sala de aula: verde-claro com faixa inferior.
        b.box(0, 0, 239, 151, 0xFFCFE0C3.toInt())
        b.box(0, 104, 239, 151, 0xFFB5CBA6.toInt())
        b.hline(0, 239, 104, 0xFF98B087.toInt())
        SceneArt.baseboard(b, 151, P.WOOD_DARK)
        // Piso de taco claro.
        SceneArt.woodFloor(b, 153, 319, 0xFFD8B486.toInt(), 0xFFBF9868.toInt())
        SceneArt.window(b, 52, 24, 100, 90, env.period, curtains = 0xFFE0D6A8.toInt())
        // Quadro-negro com moldura, giz e bandeja.
        b.outlined(108, 20, 202, 86, 0xFF2F4A3C.toInt(), P.OUTLINE)
        b.outlined(106, 18, 204, 88, P.WOOD, P.OUTLINE)
        b.box(110, 22, 200, 84, 0xFF2F4A3C.toInt())
        b.glyph(listOf(
            " #  ##   ##",
            "# # # # #  ",
            "### ##  #  ",
            "# # # # #  ",
            "# # ##   ##",
        ), 118, 30, 0xFFE8EDE0.toInt())
        b.line(118, 50, 160, 46, 0xFFE8EDE0.toInt()); b.line(118, 58, 150, 58, 0xFFF2CF5B.toInt())
        b.line(166, 52, 192, 72, 0xFFE8EDE0.toInt()); b.line(166, 72, 192, 52, 0xFFE8EDE0.toInt())
        b.box(112, 88, 200, 90, P.WOOD_DARK); b.box(130, 86, 138, 87, P.WHITE); b.box(150, 86, 154, 87, P.YELLOW)
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, _ -> SceneArt.clock(b, 222, 30, 8, env.clockMinute) },
        Prop(0) { b, env, _ -> SceneArt.door(b, 12, 72, 42, 151, 0xFF6B7C5A.toInt(), env.doorFrame) },
        // Pôster educacional (planeta + régua).
        Prop(0) { b, _, _ ->
            b.outlined(210, 46, 236, 98, 0xFFF1E3C3.toInt(), P.OUTLINE)
            b.disc(223, 62, 7, P.OUTLINE); b.disc(223, 62, 6, 0xFF4F7FC9.toInt()); b.disc(221, 60, 2, P.LEAF)
            b.hline(214, 232, 78, P.RED); for (x in 214..232 step 3) b.vline(x, 76, 78, P.RED)
            b.hline(214, 230, 86, P.METAL_DARK); b.hline(214, 226, 90, P.METAL_DARK)
        },
        // Estante com livros (atrás do Hoodie quando ele está em BOOKS).
        Prop(176) { b, _, _ ->
            b.outlined(186, 104, 236, 176, P.FURNITURE, P.OUTLINE)
            for (shelf in intArrayOf(126, 150)) b.hline(187, 235, shelf, P.OUTLINE)
            val spines = intArrayOf(0xFFC9544F.toInt(), 0xFF4F7FC9.toInt(), 0xFFF2CF5B.toInt(), 0xFF4FA36A.toInt(), 0xFF9C6BC4.toInt())
            for ((row, top) in intArrayOf(106, 128, 152).withIndex()) {
                var x = 189
                var k = row
                while (x < 232) {
                    val w = 3 + k % 3
                    b.outlined(x, top + (k % 2), x + w, top + 21, spines[k % spines.size], P.OUTLINE)
                    x += w + 1; k++
                }
            }
        },
        Prop(190) { b, _, _ -> SceneArt.plant(b, 168, 190) },
        // Cadeira da carteira (atrás do Hoodie sentado).
        Prop(232) { b, env, _ ->
            SceneArt.deskChair(b, 94, 212, 126, 248, 0xFF4F7FC9.toInt(), 0xFF7B9FDA.toInt(), SceneFlag.CHAIR_OCCUPIED in env.flags)
        },
        // Carteira com livro, caderno, lápis, garrafa, mochila e luminária (na frente do Hoodie).
        Prop(292) { b, env, _ ->
            b.outlined(56, 248, 186, 258, P.WOOD_LIGHT, P.OUTLINE)
            b.outlined(60, 257, 182, 292, P.WOOD, P.OUTLINE)
            b.box(64, 262, 178, 263, P.WOOD_DARK)
            drawBook(b, env)
            // Caderno pautado + lápis.
            b.outlined(126, 249, 146, 255, P.WHITE, P.OUTLINE)
            b.hline(128, 144, 251, 0xFF9FB8E0.toInt()); b.hline(128, 144, 253, 0xFF9FB8E0.toInt())
            b.line(140, 248, 146, 252, P.YELLOW)
            // Garrafa de água.
            b.outlined(64, 236, 70, 254, 0xFF8AD6F2.toInt(), P.OUTLINE); b.box(65, 234, 69, 236, 0xFF2F6FD0.toInt())
            // Luminária de mesa.
            val on = env.period == DayPeriod.EVENING || env.period == DayPeriod.NIGHT
            b.vline(160, 232, 248, P.METAL_DARK); b.box(156, 247, 166, 249, P.METAL_DARK)
            b.outlined(152, 226, 168, 234, if (on) P.LAMP_LIGHT else 0xFFE8873A.toInt(), P.OUTLINE)
            // Mochila encostada na carteira.
            b.outlined(186, 262, 204, 292, 0xFFC9544F.toInt(), P.OUTLINE)
            b.outlined(189, 272, 201, 284, 0xFFA8443F.toInt(), P.OUTLINE)
            b.hline(190, 200, 266, P.OUTLINE)
        },
    )

    /** Fechado na mesa; aberto quando o Hoodie começa a estudar; a página vira com PAGE_TURN. */
    private fun drawBook(b: PixelBuffer, env: SceneEnv) {
        if (SceneFlag.BOOK_OPEN in env.flags) {
            b.outlined(84, 249, 104, 255, P.WHITE, P.OUTLINE)
            b.outlined(104, 249, 124, 255, P.WHITE, P.OUTLINE)
            val left = SceneFlag.PAGE_TURNED in env.flags
            for (y in intArrayOf(251, 253)) {
                b.hline(86, 102, y, if (left) 0xFFB8C4D8.toInt() else 0xFF8E9BB3.toInt())
                b.hline(106, 122, y, if (left) 0xFF8E9BB3.toInt() else 0xFFB8C4D8.toInt())
            }
        } else {
            b.outlined(90, 248, 116, 255, 0xFFC8484A.toInt(), P.OUTLINE)
            b.box(91, 254, 115, 255, 0xFFF4EBD8.toInt())
        }
    }

    override fun lights(env: SceneEnv): List<Light> = listOf(
        Light.Emissive(50, 22, 102, 92),
        Light.Glow(120, 0, 120, 0.55f),
        Light.Glow(160, 236, 54, 0.8f),
    )
}

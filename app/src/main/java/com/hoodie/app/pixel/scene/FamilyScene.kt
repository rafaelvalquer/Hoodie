package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Sala da família: papel de parede, retratos, sofá, poltrona da outra pessoa (à esquerda,
 * com a xícara dela), mesinha com petiscos, TV e luminária. Não há NPC desenhado.
 * Props dinâmicos: o prato perde um petisco enquanto o Hoodie come (SNACK_IN_HAND),
 * a TV liga quando o diretor pede e a xícara da poltrona solta vapor.
 */
class FamilyScene : PixelScene(SceneId.FAMILY) {
    override val spots = mapOf(
        SpotId.FAMILY_SOFA to Spot(150, 202),
        SpotId.FAMILY_TABLE to Spot(116, 272),
        SpotId.WINDOW to Spot(120, 160),
        SpotId.DOOR to Spot(214, 160),
        SpotId.CENTER to Spot(120, 236),
    )
    override val defaultSpot = SpotId.FAMILY_SOFA
    override val hasAnimatedDoor = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        // Papel de parede florido e rodapé.
        b.box(0, 0, 239, 141, 0xFFE8CFC0.toInt())
        for (y in 6..136 step 14) for (x in (if (y / 14 % 2 == 0) 4 else 11)..239 step 14) {
            b.set(x, y, 0xFFD39A8E.toInt()); b.set(x - 1, y + 1, 0xFFD39A8E.toInt()); b.set(x + 1, y + 1, 0xFFD39A8E.toInt())
            b.set(x, y + 2, 0xFF9FB58A.toInt())
        }
        b.box(0, 100, 239, 141, 0xFFC99A82.toInt()); b.hline(0, 239, 100, P.FURNITURE_LIGHT)
        SceneArt.baseboard(b, 141, P.FURNITURE)
        SceneArt.woodFloor(b, 143, 319, 0xFFB07A52.toInt(), 0xFF8C5C3A.toInt())
        SceneArt.window(b, 96, 22, 144, 76, env.period, curtains = 0xFF8EB07A.toInt())
        SceneArt.rug(b, 54, 214, 196, 256, 0xFFB0584A.toInt(), 0xFFE0A070.toInt())
        // Retratos de família (silhuetas simples) e um quadro de paisagem.
        for ((x, c) in listOf(20 to 0xFFF2CF5B.toInt(), 46 to 0xFF86A9E8.toInt())) {
            b.outlined(x, 30, x + 20, 54, P.FURNITURE, P.OUTLINE)
            b.box(x + 2, 32, x + 18, 52, PixelBuffer.mix(c, P.WHITE, 0.4f))
            b.disc(x + 10, 39, 4, c); b.box(x + 5, 45, x + 15, 52, c)
        }
        b.outlined(160, 24, 190, 50, P.FURNITURE, P.OUTLINE)
        b.box(162, 26, 188, 38, 0xFF9CD3EE.toInt()); b.box(162, 39, 188, 48, 0xFF7CC46B.toInt()); b.disc(182, 31, 3, P.YELLOW)
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, _ -> SceneArt.clock(b, 72, 66, 8, env.clockMinute) },
        Prop(0) { b, env, _ -> SceneArt.door(b, 200, 64, 228, 141, 0xFF9A6A48.toInt(), env.doorFrame) },
        // Luminária de pé ao lado do sofá.
        Prop(194) { b, env, _ -> SceneArt.floorLamp(b, 100, 194, env.period == DayPeriod.EVENING || env.period == DayPeriod.NIGHT) },
        // Encosto do sofá (atrás do Hoodie sentado).
        Prop(176) { b, _, _ ->
            val c = 0xFF7A6AA8.toInt()
            b.outlined(112, 150, 196, 180, c, P.OUTLINE)
            b.vline(140, 154, 178, PixelBuffer.mix(c, P.OUTLINE, 0.3f)); b.vline(168, 154, 178, PixelBuffer.mix(c, P.OUTLINE, 0.3f))
            b.hline(114, 194, 152, PixelBuffer.mix(c, P.WHITE, 0.25f))
            b.outlined(176, 158, 190, 170, 0xFFF2CF5B.toInt(), P.OUTLINE) // almofada
        },
        // Assento e braços do sofá (na frente das pernas).
        Prop(206) { b, _, _ ->
            val c = 0xFF7A6AA8.toInt(); val light = 0xFF9C8CC8.toInt()
            b.outlined(112, 188, 196, 206, c, P.OUTLINE); b.hline(114, 194, 190, light)
            b.outlined(104, 166, 118, 206, c, P.OUTLINE); b.hline(106, 116, 168, light)
            b.outlined(190, 166, 204, 206, c, P.OUTLINE); b.hline(192, 202, 168, light)
        },
        // Poltrona da outra pessoa, saindo pela esquerda, com xícara fumegante.
        Prop(208) { b, _, t ->
            val c = 0xFF5C8F7A.toInt()
            b.outlined(-10, 152, 34, 208, c, P.OUTLINE)
            b.outlined(-10, 186, 44, 208, c, P.OUTLINE); b.hline(-8, 42, 188, 0xFF79AD95.toInt())
            b.outlined(32, 166, 46, 208, c, P.OUTLINE)
            SceneArt.mug(b, 52, 194)
            b.outlined(48, 201, 64, 210, P.FURNITURE, P.OUTLINE)
            val k = ((t / 260) % 3).toInt()
            b.set(54 + k % 2, 190 - k, 0xFFDCE3EE.toInt()); b.set(56 - k % 2, 186 - k, 0xFFDCE3EE.toInt())
        },
        // TV num rack à direita.
        Prop(150) { b, env, t ->
            b.outlined(206, 128, 238, 150, P.FURNITURE, P.OUTLINE); b.hline(208, 236, 138, P.FURNITURE_LIGHT)
            b.outlined(204, 98, 238, 126, 0xFF2A2D3E.toInt(), P.OUTLINE)
            val screen = if (env.tvOn) intArrayOf(0xFF7FE0C2.toInt(), 0xFF6FC0EE.toInt(), 0xFFF2C76B.toInt())[(t / 300 % 3).toInt()] else P.SCREEN_OFF
            b.box(207, 101, 235, 123, screen)
        },
        // Mesinha de centro com o prato de petiscos (na frente do Hoodie no sofá).
        Prop(262) { b, env, _ ->
            b.outlined(76, 236, 172, 246, P.WOOD_LIGHT, P.OUTLINE)
            b.outlined(76, 245, 172, 262, P.WOOD, P.OUTLINE)
            b.box(80, 247, 84, 262, P.WOOD_DARK); b.box(164, 247, 168, 262, P.WOOD_DARK)
            // Prato: três biscoitos; um some enquanto o Hoodie está com o petisco.
            b.outlined(102, 232, 136, 238, P.WHITE, P.OUTLINE)
            val cookies = if (SceneFlag.SNACK_IN_HAND in env.flags) listOf(110, 128) else listOf(110, 119, 128)
            for (cx in cookies) { b.disc(cx, 231, 4, P.OUTLINE); b.disc(cx, 231, 3, 0xFFD9A05B.toInt()); b.set(cx - 1, 230, 0xFF6B3E26.toInt()) }
            // Jarra de suco e copo.
            b.outlined(146, 220, 156, 238, 0xFFBFE6F6.toInt(), P.OUTLINE); b.box(147, 226, 155, 237, 0xFFF29B4A.toInt())
            b.outlined(160, 228, 166, 238, 0xFFBFE6F6.toInt(), P.OUTLINE); b.box(161, 232, 165, 237, 0xFFF29B4A.toInt())
        },
        Prop(300) { b, _, _ -> SceneArt.plant(b, 222, 300, big = true) },
    )

    override fun lights(env: SceneEnv): List<Light> = buildList {
        add(Light.Emissive(94, 20, 146, 78))
        add(Light.Glow(100, 144, 70, 0.8f))
        add(Light.Glow(120, 230, 80, 0.4f))
        if (env.tvOn) { add(Light.Emissive(207, 101, 235, 123)); add(Light.Glow(220, 112, 50, 0.6f)) }
    }
}

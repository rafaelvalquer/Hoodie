package com.hoodie.app.pixel.scene

import com.hoodie.app.pixel.renderer.PixelBuffer

class OfficeScene : PixelScene(SceneId.OFFICE) {

    override val spots = mapOf(
        SpotId.DESK to Spot(110, 270),
        SpotId.COFFEE to Spot(102, 206),
        SpotId.WINDOW to Spot(60, 178),
        SpotId.CENTER to Spot(150, 222),
        SpotId.DOOR to Spot(215, 168),
    )
    override val defaultSpot = SpotId.DESK

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        val wall = 0xFFCBD5E0.toInt()
        b.box(0, 0, 239, 151, wall)
        for (x in 0..239 step 40) b.vline(x, 4, 147, 0xFFB9C4D2.toInt())
        b.box(0, 0, 239, 3, 0xFFA8B4C6.toInt())
        SceneArt.baseboard(b, 151, P.METAL_DARK)
        // Carpete em placas.
        b.box(0, 153, 239, 319, 0xFF7C889E.toInt())
        for (y in 153..319 step 16) for (x in 0..239 step 16) {
            if ((x / 16 + y / 16) % 2 == 0) b.box(x, y, x + 15, minOf(y + 15, 319), 0xFF74809A.toInt())
        }
        b.box(0, 153, 239, 155, 0xFF5E697F.toInt())
        SceneArt.window(b, 14, 20, 104, 92, env.period)
        // Persianas.
        for (y in 22..36 step 3) b.hline(14, 104, y, 0xFFE6EBF2.toInt())
        // Quadro branco com rabiscos.
        b.outlined(114, 28, 162, 74, P.WHITE, P.OUTLINE)
        b.line(120, 40, 140, 36, 0xFF4F7FC9.toInt()); b.line(120, 48, 150, 46, P.RED)
        b.box(124, 56, 136, 66, 0xFFF2CF5B.toInt()); b.line(142, 66, 156, 54, 0xFF4FA36A.toInt())
        b.box(114, 74, 162, 76, P.METAL_DARK)
        SceneArt.door(b, 200, 74, 230, 151, 0xFF6F7C96.toInt())
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, _ -> SceneArt.clock(b, 183, 40, 8, env.clockMinute) },
        Prop(152) { b, _, _ ->
            b.outlined(166, 100, 194, 152, P.METAL, P.OUTLINE)
            for (y in intArrayOf(117, 134)) b.hline(167, 193, y, P.OUTLINE)
            for (y in intArrayOf(108, 125, 142)) b.box(176, y, 184, y + 1, P.METAL_DARK)
        },
        Prop(168) { b, _, _ -> SceneArt.plant(b, 26, 168, big = true) },
        Prop(198) { b, _, t ->
            b.outlined(50, 168, 84, 198, P.METAL, P.OUTLINE)
            b.hline(51, 83, 176, P.METAL_DARK)
            b.outlined(54, 140, 76, 168, 0xFF3A3F55.toInt(), P.OUTLINE)
            b.box(58, 144, 72, 150, 0xFF23263A.toInt())
            b.set(60, 147, if ((t / 700) % 2 == 0L) P.CODE_1 else P.RED)
            SceneArt.mug(b, 61, 158)
        },
        Prop(232) { b, _, _ ->
            b.outlined(94, 212, 126, 248, 0xFF3F4A66.toInt(), P.OUTLINE)
            b.box(97, 215, 123, 218, 0xFF566389.toInt())
        },
        Prop(292) { b, env, t ->
            b.outlined(58, 248, 202, 258, 0xFFE2D6C0.toInt(), P.OUTLINE)
            b.outlined(58, 257, 202, 292, 0xFFB8AA90.toInt(), P.OUTLINE)
            b.outlined(160, 262, 196, 288, 0xFFA2937A.toInt(), P.OUTLINE)
            b.box(174, 272, 182, 273, P.METAL_DARK)
            b.outlined(96, 249, 124, 253, 0xFFDCE2EE.toInt(), P.OUTLINE)
            b.outlined(128, 249, 134, 254, 0xFFDCE2EE.toInt(), P.OUTLINE)
            SceneArt.monitor(b, 136, 206, 190, 244, t, env.screenOn, env.variant)
            SceneArt.mug(b, 70, 240)
            b.outlined(178, 232, 198, 248, P.WHITE, P.OUTLINE); b.hline(180, 196, 236, 0xFFB8C4E8.toInt())
            b.hline(180, 192, 240, 0xFFB8C4E8.toInt())
        },
    )

    override fun lights(env: SceneEnv): List<Light> = listOf(
        Light.Emissive(14, 20, 104, 92),
        Light.Emissive(138, 208, 188, 240),
        Light.Glow(80, 0, 110, 0.65f),
        Light.Glow(190, 0, 110, 0.65f),
    )
}

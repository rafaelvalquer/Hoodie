package com.hoodie.app.pixel.scene

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector

class OfficeScene : PixelScene(SceneId.OFFICE) {

    private val officeLights = listOf(
        Light.Emissive(14, 20, 104, 92),
        Light.Emissive(138, 208, 188, 240),
        Light.Glow(80, 0, 110, 0.65f),
        Light.Glow(190, 0, 110, 0.65f),
    )

    override fun ambientNpcs(env: SceneEnv): List<AmbientNpcSlot> = OfficeNpcDirector.plan(env)

    override val spots = mapOf(
        SpotId.DESK to Spot(110, 270),
        SpotId.COFFEE to Spot(102, 206),
        SpotId.WINDOW to Spot(60, 178),
        SpotId.CENTER to Spot(150, 222),
        SpotId.DOOR to Spot(215, 168),
    )
    override val defaultSpot = SpotId.DESK
    override val hasAnimatedDoor = true

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
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, _ -> SceneArt.clock(b, 183, 40, 8, env.clockMinute) },
        Prop(0) { b, env, _ -> SceneArt.door(b, 200, 74, 230, 151, 0xFF6F7C96.toInt(), env.doorFrame) },
        Prop(152) { b, _, t ->
            b.outlined(166, 100, 194, 152, P.METAL, P.OUTLINE)
            for (y in intArrayOf(117, 134)) b.hline(167, 193, y, P.OUTLINE)
            for (y in intArrayOf(108, 125, 142)) b.box(176, y, 184, y + 1, P.METAL_DARK)
            // Impressora compacta sobre o armário; o LED pisca em ritmo lento.
            b.outlined(169, 91, 191, 101, 0xFF566389.toInt(), P.OUTLINE)
            b.hline(172, 187, 94, 0xFFBFC9D7.toInt())
            b.box(173, 88, 184, 92, P.WHITE)
            b.set(188, 95, if ((t / 1_300) % 2 == 0L) P.CODE_1 else P.RED)
        },
        // Mesas compactas dos colegas; o monitor e a cadeira dão contexto ao trabalho.
        Prop(192) { b, _, _ ->
            b.outlined(26, 183, 42, 198, 0xFF3F4A66.toInt(), P.OUTLINE)
            b.box(29, 185, 39, 194, 0xFF566389.toInt())
            b.outlined(25, 197, 43, 201, 0xFF3F4A66.toInt(), P.OUTLINE)
            b.vline(28, 200, 204, P.OUTLINE); b.vline(40, 200, 204, P.OUTLINE)
        },
        Prop(208) { b, _, t ->
            b.outlined(5, 183, 55, 190, 0xFFB8AA90.toInt(), P.OUTLINE)
            b.box(9, 190, 12, 201, 0xFF8B785F.toInt()); b.box(48, 190, 51, 201, 0xFF8B785F.toInt())
            SceneArt.monitor(b, 17, 162, 42, 181, t, true, 1)
            b.outlined(22, 181, 38, 183, 0xFF353B50.toInt(), P.OUTLINE)
        },
        Prop(208) { b, _, t ->
            b.outlined(187, 183, 237, 190, 0xFFB8AA90.toInt(), P.OUTLINE)
            b.box(191, 190, 194, 201, 0xFF8B785F.toInt()); b.box(230, 190, 233, 201, 0xFF8B785F.toInt())
            SceneArt.monitor(b, 199, 162, 224, 181, t, true, 2)
            b.outlined(204, 181, 220, 183, 0xFF353B50.toInt(), P.OUTLINE)
        },
        Prop(192) { b, _, _ ->
            b.outlined(198, 183, 214, 198, 0xFF3F4A66.toInt(), P.OUTLINE)
            b.box(201, 185, 211, 194, 0xFF566389.toInt())
            b.outlined(197, 197, 215, 201, 0xFF3F4A66.toInt(), P.OUTLINE)
            b.vline(200, 200, 204, P.OUTLINE); b.vline(212, 200, 204, P.OUTLINE)
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
        Prop(232) { b, env, _ ->
            SceneArt.deskChair(b, 94, 212, 126, 248, 0xFF3F4A66.toInt(), 0xFF566389.toInt(), SceneFlag.CHAIR_OCCUPIED in env.flags)
        },
        Prop(292) { b, env, t ->
            b.outlined(58, 248, 202, 258, 0xFFE2D6C0.toInt(), P.OUTLINE)
            b.outlined(58, 257, 202, 292, 0xFFB8AA90.toInt(), P.OUTLINE)
            b.outlined(160, 262, 196, 288, 0xFFA2937A.toInt(), P.OUTLINE)
            b.box(174, 272, 182, 273, P.METAL_DARK)
            b.outlined(96, 249, 124, 253, 0xFFDCE2EE.toInt(), P.OUTLINE)
            b.outlined(128, 249, 134, 254, 0xFFDCE2EE.toInt(), P.OUTLINE)
            SceneArt.monitor(b, 136, 206, 190, 244, t, env.screenOn, env.variant)
            // A caneca some da mesa quando o Hoodie a pega (evento MUG_PICKUP).
            if (SceneFlag.MUG_IN_HAND !in env.flags) SceneArt.mug(b, 70, 240)
            b.outlined(178, 232, 198, 248, P.WHITE, P.OUTLINE); b.hline(180, 196, 236, 0xFFB8C4E8.toInt())
            b.hline(180, 192, 240, 0xFFB8C4E8.toInt())
        },
    )

    override fun lights(env: SceneEnv): List<Light> = officeLights
}

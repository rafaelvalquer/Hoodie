package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.transport.TransportAmbientProfile
import com.hoodie.app.pixel.transport.TransportVibration

/** Small, deterministic body/prop offsets driven by each transport's ambient profile. */
internal object TransportMotion {
    fun speed(ambient: TransportAmbientProfile?, fallback: Float): Float = when {
        ambient == null -> fallback
        ambient.parallax -> ambient.outsideSpeed
        else -> 0f
    }

    fun offset(timeMs: Long, vibration: TransportVibration?): Int = when (vibration) {
        null, TransportVibration.NONE -> 0
        TransportVibration.LOW -> if ((timeMs / 520) % 2L == 0L) 0 else 1
        TransportVibration.MEDIUM -> when ((timeMs / 190) % 4L) { 0L -> 0; 1L -> 1; 2L -> 0; else -> -1 }
    }
}

/** Interior transport scenes share rendering primitives but have distinct silhouettes and motion cues. */
enum class InteriorKind { BUS, TRAIN, METRO }

open class InteriorTransportScene(id: SceneId, private val kind: InteriorKind) : PixelScene(id) {
    override val spots = mapOf(SpotId.SEAT to Spot(120, 260), SpotId.CENTER to Spot(120, 260))
    override val defaultSpot = SpotId.SEAT
    override val walkInPlace = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        when (kind) {
            InteriorKind.BUS -> {
                b.box(0, 0, 239, 34, 0xFFD9E0E7.toInt())
                b.box(0, 35, 239, 163, 0xFF557C73.toInt())
                b.box(0, 164, 239, 319, 0xFF666B78.toInt())
                b.box(0, 160, 239, 166, 0xFF34394A.toInt())
                b.box(0, 298, 239, 302, 0xFFEACD65.toInt())
            }
            InteriorKind.TRAIN -> {
                b.box(0, 0, 239, 24, 0xFFD7E2EB.toInt())
                b.box(0, 25, 239, 150, 0xFF819E9E.toInt())
                b.box(0, 151, 239, 319, 0xFF77808D.toInt())
                b.box(0, 145, 239, 153, 0xFF394352.toInt())
                b.hline(0, 239, 297, 0xFF394352.toInt())
            }
            InteriorKind.METRO -> {
                b.box(0, 0, 239, 319, 0xFF292D42.toInt())
                b.box(0, 160, 239, 319, 0xFF555A6C.toInt())
                b.box(0, 154, 239, 162, 0xFF1C2032.toInt())
                b.hline(0, 239, 292, 0xFFB58CF0.toInt())
                b.hline(0, 239, 295, 0xFF6FE0E8.toInt())
            }
        }
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, time ->
            when (kind) {
                InteriorKind.BUS -> {
                    for (x in intArrayOf(8, 88, 168)) {
                        window(b, x, 42, 64, 94, env, time, TransportMotion.speed(env.transportAmbient, .55f), 0xFF78AFA4.toInt())
                        b.vline(x + 67, 38, 164, 0xFF6C727D.toInt())
                        val sway = TransportMotion.offset(time + x * 73L, env.transportAmbient?.vibration)
                        b.vline(x + 3 + sway, 30, 39, 0xFF34394A.toInt())
                        b.disc(x + 3 + sway, 42, 3, 0xFF34394A.toInt())
                    }
                    b.outlined(84, 14, 156, 29, 0xFF2E3C4C.toInt(), 0xFF1F2335.toInt())
                    b.box(89, 18, 151, 25, 0xFFBEEAA0.toInt()) // próxima parada
                    b.box(14, 173, 74, 244, 0xFF35709B.toInt())
                    b.box(166, 173, 226, 244, 0xFF35709B.toInt())
                }
                InteriorKind.TRAIN -> {
                    window(b, 12, 38, 216, 105, env, time, TransportMotion.speed(env.transportAmbient, .9f), 0xFF8BA9AD.toInt())
                    b.outlined(8, 30, 231, 35, 0xFFB8C6D1.toInt(), 0xFF34394A.toInt()) // rack superior
                    for (x in 28..210 step 36) {
                        val sway = TransportMotion.offset(time + x * 47L, env.transportAmbient?.vibration)
                        b.vline(x + sway, 144, 168, 0xFF363E4E.toInt())
                        b.box(x - 1 + sway, 163, x + 2 + sway, 167, 0xFFB58CF0.toInt())
                    }
                    b.box(20, 176, 94, 244, 0xFF526E9A.toInt())
                    b.box(146, 176, 220, 244, 0xFF526E9A.toInt())
                    b.box(99, 14, 141, 27, 0xFF33495D.toInt())
                    b.box(103, 18, 137, 23, 0xFFFFD98A.toInt()) // painel de estação
                }
                InteriorKind.METRO -> {
                    val station = (time / 9000) % 2L == 0L
                    for (x in intArrayOf(10, 89, 168)) {
                        b.outlined(x, 42, x + 63, 132, 0xFF171B2D.toInt(), 0xFF111526.toInt())
                        b.box(x + 2, 44, x + 61, 130, if (station) 0xFF4F6172.toInt() else 0xFF090C16.toInt())
                        val speed = TransportMotion.speed(env.transportAmbient, 1f)
                        val flash = ((time * speed / 120f).toLong()) % 80
                        if (!station) for (i in 0..2) {
                            val lx = x + ((flash + i * 27) % 66).toInt()
                            b.box(lx, 46, lx + 2, 128, if (i % 2 == 0) 0xFFE7E9FF.toInt() else 0xFF9DE4F0.toInt())
                        } else {
                            b.box(x + 5, 102, x + 58, 106, 0xFFB58CF0.toInt())
                            b.box(x + 13, 69, x + 23, 88, 0xFF8799A4.toInt()) // plataforma/coluna
                        }
                    }
                    b.outlined(83, 12, 157, 31, 0xFF30364C.toInt(), 0xFF101425.toInt())
                    b.box(89, 18, 151, 23, 0xFF9FE6EA.toInt()) // linha/estação
                    val sway = TransportMotion.offset(time, env.transportAmbient?.vibration)
                    b.vline(33 + sway, 31, 169, 0xFFB9BFD0.toInt()); b.vline(207 + sway, 31, 169, 0xFFB9BFD0.toInt())
                    for (x in 20..220 step 40) b.vline(x + sway, 31, 38, 0xFFB9BFD0.toInt())
                    b.box(26, 177, 87, 243, 0xFF46516D.toInt()); b.box(153, 177, 214, 243, 0xFF46516D.toInt())
                }
            }
        },
        Prop(262) { b, _, _ ->
            b.outlined(88, 245, 152, 263, 0xFF3C6FB0.toInt(), 0xFF202337.toInt())
            b.hline(90, 150, 247, 0xFF5D8ED0.toInt())
        },
    )

    private fun window(b: PixelBuffer, x: Int, y: Int, width: Int, height: Int, env: SceneEnv, time: Long, speed: Float, tint: Int) {
        b.outlined(x, y, x + width, y + height, 0xFF202438.toInt(), 0xFF202438.toInt())
        b.box(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF9CC7EE.toInt())
        if (env.period == DayPeriod.NIGHT) b.box(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF26304D.toInt())
        val offset = ((time * speed / .7f / 9f).toInt() % (width + 44))
        for (i in -1..2) {
            val bx = x + i * 44 - offset
            if (bx > x && bx + 18 < x + width) {
                val h = 20 + ((i * 29 + 58) % 48)
                b.box(bx, y + height - h, bx + 14, y + height - 2, tint)
                for (wy in y + height - h + 5 until y + height - 5 step 10) b.box(bx + 3, wy, bx + 5, wy + 3, 0xFFFFE5A0.toInt())
            }
        }
    }
}

class BusScene : InteriorTransportScene(SceneId.BUS, InteriorKind.BUS)
class TrainScene : InteriorTransportScene(SceneId.TRAIN, InteriorKind.TRAIN)
class MetroScene : InteriorTransportScene(SceneId.METRO, InteriorKind.METRO)

/** Street cycling scene: the bicycle, cycle-lane markings and roadside pass in parallax. */
class BicycleScene : PixelScene(SceneId.BICYCLE) {
    override val spots = mapOf(SpotId.SEAT to Spot(120, 258), SpotId.CENTER to Spot(120, 258), SpotId.WALK to Spot(120, 258))
    override val defaultSpot = SpotId.WALK
    override val walkInPlace = true

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) = SceneArt.skyBands(b, 0, 0, 239, 205, env.period)

    override fun props() = listOf(
        Prop(0) { b, env, t ->
            val speed = TransportMotion.speed(env.transportAmbient, .65f)
            val motion = (t * speed / .65f).toLong()
            val vibration = TransportMotion.offset(t, env.transportAmbient?.vibration)
            SceneArt.city(b, 0, 239, 202, env.period, (motion / 100).toInt(), seed = 17)
            b.box(0, 203 + vibration, 239, 226 + vibration, 0xFF92908A.toInt()); b.hline(0, 239, 203 + vibration, 0xFF5F5D5A.toInt())
            val dash = ((motion / 28) % 36).toInt()
            for (x in -dash until 240 step 36) b.box(x, 212 + vibration, x + 16, 214 + vibration, 0xFFE9D75B.toInt())
            b.box(0, 227 + vibration, 239, 319 + vibration, 0xFF3D4150.toInt())
            val line = ((motion / 18) % 54).toInt()
            for (x in -line until 240 step 54) b.box(x, 275 + vibration, x + 24, 277 + vibration, 0xFFF4F1EA.toInt())
            if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) {
                b.disc(34, 149, 2, 0xFFFFE59A.toInt()); b.disc(199, 177, 2, 0xFFFFE59A.toInt())
            }
        },
        Prop(290) { b, env, t ->
            // Frame and spinning wheels stay visible under the rider's pedal loop.
            for (cx in intArrayOf(98, 142)) {
                b.disc(cx, 280, 16, 0xFF242738.toInt()); b.disc(cx, 280, 13, 0xFFB9C7D2.toInt()); b.disc(cx, 280, 2, 0xFF242738.toInt())
                val spoke = ((t / 140) % 4).toInt()
                if (spoke % 2 == 0) b.hline(cx - 10, cx + 10, 280, 0xFF5B6472.toInt()) else b.vline(cx, 270, 290, 0xFF5B6472.toInt())
            }
            b.line(98, 280, 119, 257, 0xFFE5604F.toInt()); b.line(119, 257, 142, 280, 0xFFE5604F.toInt())
            b.line(98, 280, 130, 280, 0xFFE5604F.toInt()); b.line(130, 280, 119, 257, 0xFFE5604F.toInt())
            b.hline(112, 121, 253, 0xFF242738.toInt()); b.line(139, 260, 147, 254, 0xFF242738.toInt())
        },
    )
}

/** Neutral personal ride silhouette: deliberately avoids identifying an unknown vehicle. */
class GenericRideScene : PixelScene(SceneId.GENERIC_RIDE) {
    override val spots = mapOf(SpotId.SEAT to Spot(120, 258), SpotId.CENTER to Spot(120, 258), SpotId.WALK to Spot(120, 258))
    override val defaultSpot = SpotId.WALK
    override val walkInPlace = true
    override fun drawBackground(b: PixelBuffer, env: SceneEnv) = SceneArt.skyBands(b, 0, 0, 239, 205, env.period)
    override fun props() = listOf(
        Prop(0) { b, env, t ->
            val speed = TransportMotion.speed(env.transportAmbient, .6f)
            val motion = (t * speed / .6f).toLong()
            SceneArt.city(b, 0, 239, 205, env.period, (motion / 120).toInt(), seed = 23)
            b.box(0, 206, 239, 232, 0xFFB7B2A8.toInt()); b.box(0, 233, 239, 319, 0xFF3D4150.toInt())
            val dash = ((motion / 24) % 42).toInt()
            for (x in -dash until 240 step 42) b.box(x, 285, x + 18, 287, 0xFFE9E3C8.toInt())
            if (env.period == DayPeriod.NIGHT) for (x in 22..220 step 66) b.box(x, 138, x + 3, 142, 0xFFFFE59A.toInt())
        },
        Prop(290) { b, env, t ->
            val bob = TransportMotion.offset(t, env.transportAmbient?.vibration)
            b.hline(97, 143, 281 + bob, 0xFF242738.toInt())
            for (x in intArrayOf(103, 137)) { b.disc(x, 278 + bob, 5, 0xFF242738.toInt()); b.disc(x, 278 + bob, 2, 0xFFB9C7D2.toInt()) }
            b.outlined(102, 265 + bob, 138, 271 + bob, 0xFFE6B84F.toInt(), 0xFF242738.toInt())
            b.vline(119, 258 + bob, 265 + bob, 0xFF242738.toInt()); b.hline(112, 126, 258 + bob, 0xFF242738.toInt())
        },
    )
}

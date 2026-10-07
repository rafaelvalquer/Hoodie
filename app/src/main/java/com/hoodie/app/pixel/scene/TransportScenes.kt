package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.config.HoodieConfig
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

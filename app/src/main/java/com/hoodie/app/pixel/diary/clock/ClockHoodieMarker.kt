package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.HoodiePalette

/**
 * Hoodie mini do relógio (9×9 a pé; 11×11 de veículo): idle com respiração, piscada e
 * andar. Os quadros são pintados uma vez e reaproveitados (zero alocação por frame).
 */
object ClockHoodieMarker {
    enum class Frame { IDLE, BLINK, WALK_A, WALK_B }
    enum class Ride { FOOT, BICYCLE, VEHICLE }

    private val HEAD = listOf(
        ".O.....O.",
        "OIO...OIO",
        "OHHOOOHHO",
        "OHFFFFFHO",
        "OFEFFFEFO",
        "OFFFNFFFO",
        ".OFFFFFO.",
    )
    private val BODY_IDLE = listOf(".OHHHHHO.", "..O...O..")
    private val BODY_WALK_A = listOf(".OHHHHHO.", "..O..O...")
    private val BODY_WALK_B = listOf(".OHHHHHO.", "...O..O..")

    fun rideOf(mode: MovementMode?): Ride = when {
        mode == MovementMode.BICYCLE -> Ride.BICYCLE
        mode != null && mode.isVehicle -> Ride.VEHICLE
        else -> Ride.FOOT
    }

    fun frameAt(timeMs: Long, moving: Boolean): Frame = when {
        moving -> if ((timeMs / 150) % 2 == 0L) Frame.WALK_A else Frame.WALK_B
        timeMs % 3_200 < 160 -> Frame.BLINK
        else -> Frame.IDLE
    }

    /** Sobe 1 px a cada ~0,6 s parado (respiração). */
    fun breath(timeMs: Long, moving: Boolean): Int = if (!moving && (timeMs / 600) % 2 == 1L) -1 else 0

    private val cache = HashMap<Triple<Frame, Ride, Int>, PixelBuffer>()

    fun sprite(frame: Frame, ride: Ride, mode: MovementMode?): PixelBuffer {
        val color = if (ride == Ride.VEHICLE) DayClockPalette.mode(mode) else 0
        return synchronized(cache) { cache.getOrPut(Triple(frame, ride, color)) { paint(frame, ride, color) } }
    }

    private fun paint(frame: Frame, ride: Ride, vehicleColor: Int): PixelBuffer {
        val w = if (ride == Ride.FOOT) 9 else 11
        val h = if (ride == Ride.FOOT) 9 else 11
        val b = PixelBuffer(w, h)
        val dx = (w - 9) / 2
        grid(b, HEAD, dx, 0, blink = frame == Frame.BLINK)
        when (ride) {
            Ride.FOOT -> grid(b, when (frame) { Frame.WALK_A -> BODY_WALK_A; Frame.WALK_B -> BODY_WALK_B; else -> BODY_IDLE }, dx, 7, false)
            Ride.BICYCLE -> {
                grid(b, listOf(".OHHHHHO."), dx, 7, false)
                val spin = frame == Frame.WALK_B
                // Rodas 3×3 em losango; o cubo pisca para parecer girar.
                listOf(1, 7).forEach { cx ->
                    b.set(cx + 1, 8, DayClockPalette.OUTLINE)
                    b.set(cx, 9, DayClockPalette.OUTLINE); b.set(cx + 2, 9, DayClockPalette.OUTLINE)
                    b.set(cx + 1, 10, DayClockPalette.OUTLINE)
                    if (spin) b.set(cx + 1, 9, HoodiePalette.STRING)
                }
                b.hline(4, 6, 9, DayClockPalette.OUTLINE)
            }
            Ride.VEHICLE -> {
                b.hline(1, 9, 6, DayClockPalette.OUTLINE)
                b.box(0, 7, 10, 8, vehicleColor)
                b.set(0, 7, DayClockPalette.OUTLINE); b.set(10, 7, DayClockPalette.OUTLINE)
                b.set(0, 8, DayClockPalette.OUTLINE); b.set(10, 8, DayClockPalette.OUTLINE)
                b.hline(0, 10, 9, DayClockPalette.OUTLINE)
                val wheel = if (frame == Frame.WALK_B) HoodiePalette.STRING else DayClockPalette.OUTLINE
                b.set(2, 10, wheel); b.set(8, 10, wheel)
            }
        }
        return b
    }

    private fun grid(b: PixelBuffer, rows: List<String>, dx: Int, dy: Int, blink: Boolean) {
        rows.forEachIndexed { y, line ->
            line.forEachIndexed { x, p ->
                val c = when (p) {
                    'O' -> HoodiePalette.OUTLINE
                    'I' -> HoodiePalette.INNER_EAR
                    'H' -> HoodiePalette.HOOD
                    'F' -> HoodiePalette.FUR
                    'E' -> if (blink) HoodiePalette.FUR_SHADE else HoodiePalette.EYE
                    'N' -> HoodiePalette.NOSE
                    else -> 0
                }
                if (c != 0) b.set(dx + x, dy + y, c)
            }
        }
    }
}

package com.hoodie.app.pixel.scene

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Point

enum class BusSeatType { SINGLE, PREFERENTIAL, DOUBLE, WHEELCHAIR }

data class SeatSlot(
    val id: String,
    val anchor: Point,
    val type: BusSeatType,
    val occupied: Boolean,
)

/** Two-pass bus seat: upholstery/backrest behind the passenger and only the cushion lip in front. */
class BusSeatProp(
    val id: String,
    private val centerX: Int,
    private val hipY: Int,
    val type: BusSeatType,
    private val occupied: Boolean,
) {
    private val width get() = when (type) {
        BusSeatType.SINGLE -> 38
        BusSeatType.PREFERENTIAL -> 42
        BusSeatType.DOUBLE -> 62
        BusSeatType.WHEELCHAIR -> 54
    }
    private val x0 get() = centerX - width / 2
    val slots: List<SeatSlot> get() = when (type) {
        BusSeatType.DOUBLE -> listOf(
            SeatSlot("$id-left", Point(centerX - 13, hipY), type, occupied),
            SeatSlot("$id-right", Point(centerX + 13, hipY), type, false),
        )
        BusSeatType.WHEELCHAIR -> listOf(SeatSlot(id, Point(centerX, hipY), type, occupied))
        else -> listOf(SeatSlot(id, Point(centerX, hipY), type, occupied))
    }

    fun drawRear(b: PixelBuffer) {
        if (type == BusSeatType.WHEELCHAIR) {
            b.vline(centerX - 20, hipY - 48, hipY + 5, P.METAL_DARK)
            b.hline(centerX - 20, centerX + 20, hipY + 5, P.METAL)
            b.disc(centerX - 16, hipY + 9, 5, P.OUTLINE)
            b.disc(centerX + 16, hipY + 9, 5, P.OUTLINE)
            b.box(centerX - 2, hipY - 40, centerX + 2, hipY - 31, P.YELLOW)
            b.glyph(listOf("#..", "###", "#..", "#.#"), centerX + 8, hipY - 37, P.WHITE)
            return
        }
        val top = hipY - 47
        b.outlined(x0, top, x0 + width - 1, hipY - 1, 0xFF315A90.toInt(), P.OUTLINE)
        val preferred = type == BusSeatType.PREFERENTIAL
        val dark = if (preferred) 0xFFBA8F2D.toInt() else 0xFF244779.toInt()
        val light = if (preferred) 0xFFF2CF5B.toInt() else 0xFF4A79B6.toInt()
        for (y in top + 3 until hipY - 3 step 4) for (x in x0 + 3 until x0 + width - 3 step 4) {
            b.box(x, y, x + 3, y + 3, if (((x - x0) + (y - top)) % 8 == 0) light else dark)
        }
        // Headrest, grab loop and exposed chrome supports.
        b.outlined(centerX - 9, top - 4, centerX + 9, top + 7, light, P.OUTLINE)
        b.vline(x0 - 2, top + 9, hipY + 8, P.METAL)
        b.vline(x0 + width + 1, top + 9, hipY + 8, P.METAL)
        b.box(centerX - 2, top - 15, centerX + 2, top - 8, P.METAL)
        b.vline(centerX - 4, top - 16, top - 11, P.METAL)
        b.vline(centerX + 4, top - 16, top - 11, P.METAL)
        if (preferred) {
            b.disc(centerX, top + 17, 5, P.YELLOW)
            b.glyph(listOf(".#.", "###", ".#.", ".#.", "#.#"), centerX - 2, top + 14, P.OUTLINE)
        }
    }

    fun drawFront(b: PixelBuffer) {
        if (type == BusSeatType.WHEELCHAIR) return
        b.outlined(x0 - 2, hipY + 2, x0 + width + 1, hipY + 8, 0xFF315A90.toInt(), P.OUTLINE)
        b.hline(x0 + 1, x0 + width - 2, hipY + 3, if (type == BusSeatType.PREFERENTIAL) 0xFFFFDB66.toInt() else 0xFF6B96CA.toInt())
        b.vline(x0 + 4, hipY + 9, hipY + 17, P.METAL_DARK)
        b.vline(x0 + width - 5, hipY + 9, hipY + 17, P.METAL_DARK)
    }
}

package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.character.CharacterLimbPainter
import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.HoodiePainter.Part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shape
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shapeUnion
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.recolor

/** Procedural HoodieArmsPainter; preserves drawing order and semantic part ownership. */
internal object HoodieArmsPainter {
    fun armShape(arm: Arm): ArmShape = when (arm) {
        Arm.DOWN -> ArmShape(listOf(R(6, 35, 12, 53)), R(6, 52, 11, 57), 8, 55)
        Arm.SWING_FRONT -> ArmShape(listOf(R(7, 35, 13, 52)), R(8, 51, 13, 56), 10, 54)
        Arm.SWING_BACK -> ArmShape(listOf(R(5, 35, 11, 53)), R(4, 52, 9, 57), 6, 55)
        Arm.FORWARD_UP -> ArmShape(listOf(R(8, 35, 14, 45), R(12, 41, 20, 47)), R(17, 40, 22, 46), 19, 43)
        Arm.FORWARD_DOWN -> ArmShape(listOf(R(8, 35, 14, 46), R(12, 43, 20, 49)), R(17, 43, 22, 49), 19, 46)
        Arm.HOLD_CHEST -> ArmShape(listOf(R(8, 35, 14, 45), R(12, 39, 19, 45)), R(16, 37, 21, 42), 18, 39)
        Arm.HOLD_MOUTH -> ArmShape(listOf(R(8, 35, 14, 44), R(12, 30, 18, 42)), R(15, 26, 20, 31), 17, 28)
        Arm.CHIN -> ArmShape(listOf(R(8, 35, 14, 44), R(13, 32, 19, 43)), R(16, 29, 21, 34), 18, 31)
        Arm.HEAD -> ArmShape(listOf(R(4, 24, 10, 38), R(6, 12, 12, 26)), R(10, 7, 16, 12), 13, 9)
        Arm.UP -> ArmShape(listOf(R(2, 18, 8, 38)), R(1, 13, 6, 18), 3, 15)
        Arm.WAVE -> ArmShape(listOf(R(5, 30, 11, 38), R(1, 19, 7, 32)), R(1, 14, 6, 19), 3, 16)
    }

    fun drawArm(b: PixelBuffer, arm: ArmShape, up: Int, mirror: Boolean, sleeve: Int = HoodiePalette.HOOD, fur: Int = HoodiePalette.FUR) {
        part(b, if (mirror) Part.ARM_RIGHT else Part.ARM_LEFT) {
            arm.parts.forEach { seg ->
                val r = seg.dy(up).let { if (mirror) it.mirror() else it }
                CharacterLimbPainter.drawShape(b, r, sleeve, HoodiePalette.OUTLINE)
                CharacterLimbPainter.recolor(b, r.x0, r.y1 - 1, r.x1, r.y1 - 1, sleeve, HoodiePalette.HOOD_SHADE)
            }
        }
        val paw = arm.paw.dy(up).let { if (mirror) it.mirror() else it }
        part(b, if (mirror) Part.HAND_RIGHT else Part.HAND_LEFT) { CharacterLimbPainter.drawShape(b, paw, fur, HoodiePalette.OUTLINE) }
    }

    fun redrawPaws(b: PixelBuffer, left: ArmShape, right: ArmShape, up: Int) {
        part(b, Part.HAND_LEFT) { CharacterLimbPainter.drawShape(b, left.paw.dy(up), HoodiePalette.FUR, HoodiePalette.OUTLINE) }
        part(b, Part.HAND_RIGHT) { CharacterLimbPainter.drawShape(b, right.paw.dy(up).mirror(), HoodiePalette.FUR, HoodiePalette.OUTLINE) }
    }

    fun sideArm(b: PixelBuffer, swing: Int, up: Int, sleeve: Int, fur: Int, far: Boolean): Point = part(b, if (far) Part.ARM_RIGHT else Part.ARM_LEFT) {
        // Balanço amplo: a pata sai da silhueta do corpo para a frente e para trás.
        val ex = 20 + swing
        val px = 19 + swing * 2
        CharacterLimbPainter.drawShapes(b, listOf(R(19, 35, 24, 42, 2), R(ex, 40, ex + 4, 46, 1), R(px, 45, px + 4, 49, 1)).map { it.dy(up) }, sleeve, HoodiePalette.OUTLINE)
        // Borda de trás da manga mais escura: separa o braço do corpo.
        val back = if (sleeve == HoodiePalette.HOOD_LIGHT) HoodiePalette.HOOD else HoodiePalette.HOOD_DARK
        CharacterLimbPainter.recolor(b, 23, 36 + up, 24, 42 + up, sleeve, back)
        CharacterLimbPainter.recolor(b, ex + 3, 41 + up, ex + 4, 46 + up, sleeve, back)
        CharacterLimbPainter.recolor(b, px, 48 + up, px + 4, 49 + up, sleeve, HoodiePalette.HOOD_SHADE)
        part(b, if (far) Part.HAND_RIGHT else Part.HAND_LEFT) { CharacterLimbPainter.drawShape(b, R(px - 1, 49, px + 4, 53, 2).dy(up), fur, HoodiePalette.OUTLINE) }
        Point(px + 1, 51 + up)
    }
}

package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.renderer.PixelBuffer

/** Paleta oficial do Hoodie (baseada na concept art: gato azul de moletom, contorno escuro). */
object HoodiePalette {
    const val OUTLINE = 0xFF1A1C33.toInt()
    const val FUR = 0xFF86A9E8.toInt()
    const val FUR_SHADE = 0xFF6586CE.toInt()
    const val FUR_LIGHT = 0xFFB0C9F6.toInt()
    const val INNER_EAR = 0xFF4E69B0.toInt()
    const val HOOD = 0xFFB9CBEF.toInt()
    const val HOOD_SHADE = 0xFF92A9DB.toInt()
    const val HOOD_DARK = 0xFF6E84BE.toInt()
    const val HOOD_LIGHT = 0xFFDAE5FA.toInt()
    const val EYE = 0xFF0F1124.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val NOSE = 0xFF283063.toInt()
    const val TONGUE = 0xFFE07A93.toInt()
    const val BLUSH = 0xFFEFA3C8.toInt()
    const val STRING = 0xFFF1F5FF.toInt()
    const val BACKPACK = 0xFFDB7A3E.toInt()
    const val BACKPACK_DARK = 0xFFAA5329.toInt()
}

/**
 * Gera o sprite 48×72 de uma [HoodiePose]. Cada parte é uma forma com contorno
 * próprio de 1px, desenhada de trás para frente — isso garante o outline escuro
 * consistente exigido pelo padrão visual, inclusive entre braço e corpo.
 */
object HoodiePainter {
    const val WIDTH = 48
    const val HEIGHT = 72

    private val cache = HashMap<HoodiePose, PixelBuffer>()

    fun sprite(pose: HoodiePose): PixelBuffer = synchronized(cache) { cache.getOrPut(pose) { paint(pose) } }

    private data class R(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val r: Int = 2, val round: Boolean = false) {
        fun dy(d: Int) = copy(y0 = y0 + d, y1 = y1 + d)
        fun mirror() = copy(x0 = WIDTH - 1 - x1, x1 = WIDTH - 1 - x0)
    }

    private class ArmShape(val parts: List<R>, val paw: R, val hx: Int, val hy: Int)

    // Geometria do braço ESQUERDO (da tela). O direito é espelhado.
    private fun armShape(arm: Arm): ArmShape = when (arm) {
        Arm.DOWN -> ArmShape(listOf(R(6, 35, 12, 53)), R(6, 52, 11, 57), 8, 55)
        Arm.SWING_FRONT -> ArmShape(listOf(R(7, 35, 13, 52)), R(8, 51, 13, 56), 10, 54)
        Arm.SWING_BACK -> ArmShape(listOf(R(5, 35, 11, 53)), R(4, 52, 9, 57), 6, 55)
        Arm.FORWARD_UP -> ArmShape(listOf(R(8, 35, 14, 45), R(12, 41, 20, 47)), R(17, 40, 22, 46), 19, 43)
        Arm.FORWARD_DOWN -> ArmShape(listOf(R(8, 35, 14, 46), R(12, 43, 20, 49)), R(17, 43, 22, 49), 19, 46)
        Arm.HOLD_CHEST -> ArmShape(listOf(R(8, 35, 14, 45), R(12, 39, 19, 45)), R(16, 37, 21, 42), 18, 39)
        Arm.HOLD_MOUTH -> ArmShape(listOf(R(8, 35, 14, 44), R(12, 30, 18, 42)), R(15, 26, 20, 31), 17, 28)
        Arm.CHIN -> ArmShape(listOf(R(8, 35, 14, 44), R(13, 32, 19, 43)), R(16, 29, 21, 34), 18, 31)
        Arm.UP -> ArmShape(listOf(R(2, 18, 8, 38)), R(1, 13, 6, 18), 3, 15)
        Arm.WAVE -> ArmShape(listOf(R(5, 30, 11, 38), R(1, 19, 7, 32)), R(1, 14, 6, 19), 3, 16)
    }

    private fun paint(p: HoodiePose): PixelBuffer {
        val b = PixelBuffer(WIDTH, HEIGHT)
        val sit = p.legs == Legs.SIT
        val up = (if (sit) 5 else 0) + p.bob

        if (p.headOnly) {
            drawHead(b, p, up)
            return b
        }
        if (p.backpack) shape(b, R(8, 37, 39, 56, 3).dy(up), HoodiePalette.BACKPACK)
        if (!sit) drawLegs(b, p.legs)
        // Capuz caído atrás do pescoço.
        shape(b, R(9, 28, 38, 39, 5).dy(up), HoodiePalette.HOOD_SHADE)
        drawBody(b, up, p.backpack)
        if (sit) drawLegs(b, p.legs)

        val left = armShape(p.leftArm)
        val right = armShape(p.rightArm)
        drawArm(b, left, up, mirror = false)
        drawArm(b, right, up, mirror = true)

        drawHead(b, p, up)
        drawItem(b, p, left, right, up)
        return b
    }

    private fun drawBody(b: PixelBuffer, up: Int, backpack: Boolean) {
        val body = R(11, 33, 36, 60, 4).dy(up)
        shape(b, body, HoodiePalette.HOOD)
        recolor(b, 31, body.y0 + 2, 35, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        recolor(b, 12, body.y0 + 3, 13, body.y1 - 5, HoodiePalette.HOOD, HoodiePalette.HOOD_LIGHT)
        // Barra canelada.
        recolor(b, 12, body.y1 - 3, 35, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        recolor(b, 12, body.y1 - 3, 35, body.y1 - 1, HoodiePalette.HOOD_LIGHT, HoodiePalette.HOOD_SHADE)
        for (x in 13..34 step 3) b.set(x, body.y1 - 2, HoodiePalette.HOOD_DARK)
        // Bolso canguru.
        shape(b, R(16, 46, 31, 55, 2).dy(up), HoodiePalette.HOOD_SHADE, HoodiePalette.HOOD_DARK)
        b.hline(18, 29, 48 + up, HoodiePalette.HOOD_DARK)
        // Cordões do capuz.
        for (x in intArrayOf(20, 27)) {
            b.vline(x, 36 + up, 43 + up, HoodiePalette.STRING)
            b.set(x, 44 + up, HoodiePalette.HOOD_DARK)
        }
        if (backpack) {
            for (x in intArrayOf(14, 32)) {
                b.box(x, 34 + up, x + 1, 47 + up, HoodiePalette.BACKPACK_DARK)
                b.set(x, 47 + up, HoodiePalette.OUTLINE); b.set(x + 1, 47 + up, HoodiePalette.OUTLINE)
            }
        }
    }

    private fun drawLegs(b: PixelBuffer, legs: Legs) {
        if (legs == Legs.SIT) {
            foot(b, R(12, 63, 21, 70, 3))
            foot(b, R(12, 63, 21, 70, 3).mirror())
            return
        }
        val standLeg = R(15, 57, 21, 68, 1); val standFoot = R(13, 65, 21, 70, 2)
        val stepLeg = R(15, 56, 21, 66, 1); val stepFoot = R(13, 63, 21, 68, 2)
        val runLeg = R(14, 55, 20, 63, 1); val runFoot = R(12, 60, 20, 65, 2)
        val (l, r) = when (legs) {
            Legs.STEP_LEFT -> (stepLeg to stepFoot) to (standLeg to standFoot)
            Legs.STEP_RIGHT -> (standLeg to standFoot) to (stepLeg to stepFoot)
            Legs.RUN_A -> (runLeg to runFoot) to (standLeg to standFoot)
            Legs.RUN_B -> (standLeg to standFoot) to (runLeg to runFoot)
            else -> (standLeg to standFoot) to (standLeg to standFoot)
        }
        shape(b, l.first, HoodiePalette.FUR); foot(b, l.second)
        shape(b, r.first.mirror(), HoodiePalette.FUR); foot(b, r.second.mirror())
    }

    private fun foot(b: PixelBuffer, f: R) {
        shape(b, f, HoodiePalette.FUR)
        recolor(b, f.x0, f.y1 - 1, f.x1, f.y1 - 1, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        val mid = (f.x0 + f.x1) / 2
        b.set(mid - 1, f.y1 - 1, HoodiePalette.OUTLINE); b.set(mid + 1, f.y1 - 1, HoodiePalette.OUTLINE)
    }

    private fun drawArm(b: PixelBuffer, arm: ArmShape, up: Int, mirror: Boolean) {
        arm.parts.forEach { part ->
            val r = part.dy(up).let { if (mirror) it.mirror() else it }
            shape(b, r, HoodiePalette.HOOD)
            recolor(b, r.x0, r.y1 - 1, r.x1, r.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        }
        val paw = arm.paw.dy(up).let { if (mirror) it.mirror() else it }
        shape(b, paw, HoodiePalette.FUR)
    }

    private fun drawHead(b: PixelBuffer, p: HoodiePose, up: Int) {
        // Orelhas (antes da cabeça, para o contorno da cabeça passar na base).
        ear(b, mirror = false, dx = if (p.earTwitch) -1 else 0, dy = up + if (p.earTwitch) 1 else 0)
        ear(b, mirror = true, dx = 0, dy = up)
        val head = R(7, 9, 40, 33, 8, round = true).dy(up)
        shape(b, head, HoodiePalette.FUR)
        recolor(b, head.x0 + 1, head.y1 - 2, head.x1 - 1, head.y1 - 1, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        recolor(b, 35, head.y0 + 4, 39, head.y1 - 3, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        b.hline(13, 19, head.y0 + 2, HoodiePalette.FUR_LIGHT)
        b.hline(11, 14, head.y0 + 3, HoodiePalette.FUR_LIGHT)
        // Bochechas fofas.
        b.set(head.x0, 26 + up, HoodiePalette.FUR); b.set(head.x1, 26 + up, HoodiePalette.FUR_SHADE)

        drawEyes(b, p.eyes, up)
        // Focinho: nariz + boca em "w".
        b.hline(23, 24, 23 + up, HoodiePalette.NOSE)
        when (p.mouth) {
            Mouth.SMILE -> {
                b.set(21, 24 + up, HoodiePalette.NOSE); b.set(22, 25 + up, HoodiePalette.NOSE)
                b.hline(23, 24, 24 + up, HoodiePalette.NOSE)
                b.set(25, 25 + up, HoodiePalette.NOSE); b.set(26, 24 + up, HoodiePalette.NOSE)
            }
            Mouth.OPEN -> {
                b.box(22, 24 + up, 25, 27 + up, HoodiePalette.NOSE)
                b.hline(23, 24, 26 + up, HoodiePalette.TONGUE)
            }
            Mouth.FLAT -> b.hline(22, 25, 25 + up, HoodiePalette.NOSE)
            Mouth.CHEW -> {
                b.hline(22, 25, 25 + up, HoodiePalette.NOSE); b.set(21, 24 + up, HoodiePalette.NOSE); b.set(26, 24 + up, HoodiePalette.NOSE)
            }
        }
        if (p.blush || p.eyes == Eyes.HAPPY) {
            b.hline(11, 13, 24 + up, HoodiePalette.BLUSH); b.hline(34, 36, 24 + up, HoodiePalette.BLUSH)
        }
    }

    private fun ear(b: PixelBuffer, mirror: Boolean, dx: Int, dy: Int) {
        fun mx(x: Int) = if (mirror) WIDTH - 1 - x else x
        val top = 2; val bottom = 13
        // Contorno + preenchimento linha a linha.
        for (y in top..bottom) {
            val l = 10 - (y - top) * 2 / 11
            val r = 11 + (y - top) * 9 / 11
            for (x in l - 1..r + 1) b.set(mx(x) + dx, y + dy, HoodiePalette.OUTLINE)
        }
        b.hline(mx(10) + dx, mx(11) + dx, top - 1 + dy, HoodiePalette.OUTLINE)
        for (y in top..bottom) {
            val l = 10 - (y - top) * 2 / 11
            val r = 11 + (y - top) * 9 / 11
            for (x in l..r) b.set(mx(x) + dx, y + dy, HoodiePalette.FUR)
        }
        for (y in 6..12) {
            val l = 11 - (y - 6) / 6
            val r = 11 + (y - 6) * 5 / 7
            for (x in l..r) b.set(mx(x) + dx, y + dy, HoodiePalette.INNER_EAR)
        }
    }

    private fun drawEyes(b: PixelBuffer, eyes: Eyes, up: Int) {
        for (baseX in intArrayOf(15, 30)) {
            var x0 = baseX; var y0 = 18 + up
            when (eyes) {
                Eyes.LOOK_LEFT -> x0 -= 1
                Eyes.LOOK_RIGHT -> x0 += 1
                Eyes.LOOK_UP -> y0 -= 1
                Eyes.LOOK_DOWN -> y0 += 1
                else -> Unit
            }
            when (eyes) {
                Eyes.OPEN, Eyes.LOOK_LEFT, Eyes.LOOK_RIGHT, Eyes.LOOK_UP -> {
                    b.box(x0, y0, x0 + 2, y0 + 3, HoodiePalette.EYE)
                    b.set(x0, y0, HoodiePalette.WHITE)
                }
                Eyes.LOOK_DOWN -> {
                    b.box(x0, y0, x0 + 2, y0 + 2, HoodiePalette.EYE)
                    b.set(x0, y0, HoodiePalette.WHITE)
                }
                Eyes.CLOSED -> {
                    b.hline(x0 - 1, x0 + 3, y0 + 2, HoodiePalette.EYE)
                    b.set(x0 - 1, y0 + 1, HoodiePalette.EYE); b.set(x0 + 3, y0 + 1, HoodiePalette.EYE)
                }
                Eyes.HAPPY -> {
                    b.set(x0 - 1, y0 + 3, HoodiePalette.EYE); b.set(x0, y0 + 2, HoodiePalette.EYE)
                    b.set(x0 + 1, y0 + 1, HoodiePalette.EYE); b.set(x0 + 2, y0 + 2, HoodiePalette.EYE)
                    b.set(x0 + 3, y0 + 3, HoodiePalette.EYE)
                }
                Eyes.WIDE -> {
                    b.box(x0 - 1, y0 - 1, x0 + 3, y0 + 3, HoodiePalette.EYE)
                    b.box(x0 - 1, y0 - 1, x0, y0, HoodiePalette.WHITE)
                }
                Eyes.FOCUSED -> {
                    b.box(x0, y0 + 1, x0 + 2, y0 + 3, HoodiePalette.EYE)
                    b.hline(x0 - 1, x0 + 3, y0, HoodiePalette.FUR_SHADE)
                }
                Eyes.SLEEPY -> {
                    b.box(x0, y0 + 2, x0 + 2, y0 + 3, HoodiePalette.EYE)
                    b.hline(x0 - 1, x0 + 3, y0 + 1, HoodiePalette.EYE)
                }
            }
        }
    }

    private fun drawItem(b: PixelBuffer, p: HoodiePose, left: ArmShape, right: ArmShape, up: Int) {
        val o = HoodiePalette.OUTLINE
        val rx = WIDTH - 1 - right.hx; val ry = right.hy + up
        when (p.item) {
            Item.NONE -> Unit
            Item.MUG -> {
                b.outlined(rx + 1, ry - 6, rx + 6, ry, 0xFFF4F1EA.toInt(), o)
                b.hline(rx + 2, rx + 5, ry - 5, 0xFF6B3E26.toInt())
                b.vline(rx + 7, ry - 4, ry - 2, o)
                b.set(rx + 3, ry - 2, 0xFFE07A93.toInt()) // coraçãozinho na caneca
            }
            Item.PHONE -> {
                b.outlined(rx - 2, ry - 7, rx + 2, ry + 1, 0xFF2E3350.toInt(), o)
                b.box(rx - 1, ry - 6, rx + 1, ry - 1, 0xFF9FE3F0.toInt())
            }
            Item.BOTTLE -> {
                b.outlined(rx, ry - 8, rx + 4, ry + 1, 0xFF8AD6F2.toInt(), o)
                b.box(rx + 1, ry - 9, rx + 3, ry - 8, 0xFF2F6FD0.toInt())
            }
            Item.FORK -> {
                b.vline(rx + 2, ry - 7, ry + 1, 0xFFD0D4DE.toInt())
                b.set(rx + 1, ry - 7, 0xFFD0D4DE.toInt()); b.set(rx + 3, ry - 7, 0xFFD0D4DE.toInt())
            }
            Item.PAN -> {
                b.outlined(rx + 2, ry - 3, rx + 12, ry + 1, 0xFF3A3D4A.toInt(), o)
                b.hline(rx + 4, rx + 10, ry - 2, 0xFFF2D25C.toInt())
            }
            Item.BROOM -> {
                b.box(rx + 1, ry - 16, rx + 2, ry + 15, 0xFF8B5A2B.toInt())
                b.outlined(rx - 3, ry + 15, rx + 6, ry + 21, 0xFFE5C25A.toInt(), o)
                for (x in rx - 2..rx + 5 step 2) b.vline(x, ry + 17, ry + 20, 0xFFC49A35.toInt())
            }
            Item.DUMBBELL -> {
                val hands = if (p.itemInBothHands) listOf(left.hx to left.hy + up, rx to ry) else listOf(rx to ry)
                hands.forEach { (hx, hy) ->
                    b.hline(hx - 5, hx + 5, hy, 0xFF8A8F9E.toInt())
                    b.outlined(hx - 7, hy - 3, hx - 4, hy + 3, 0xFF3A3D4A.toInt(), o)
                    b.outlined(hx + 4, hy - 3, hx + 7, hy + 3, 0xFF3A3D4A.toInt(), o)
                }
            }
            Item.BOOK -> {
                b.outlined(15, 35 + up, 32, 46 + up, 0xFFC8484A.toInt(), o)
                b.box(16, 36 + up, 31, 37 + up, 0xFFF4EBD8.toInt())
                b.vline(23, 36 + up, 45 + up, 0xFF8E2E33.toInt())
                b.vline(24, 36 + up, 45 + up, 0xFF8E2E33.toInt())
                redrawPaws(b, left, right, up)
            }
            Item.CONTROLLER -> {
                b.outlined(15, 38 + up, 32, 45 + up, 0xFF3B3F55.toInt(), o)
                b.set(18, 41 + up, 0xFFDDE2F0.toInt()); b.set(17, 41 + up, 0xFFDDE2F0.toInt()); b.set(19, 41 + up, 0xFFDDE2F0.toInt())
                b.set(18, 40 + up, 0xFFDDE2F0.toInt()); b.set(18, 42 + up, 0xFFDDE2F0.toInt())
                b.set(28, 40 + up, 0xFFE05A5A.toInt()); b.set(29, 42 + up, 0xFF5AC8E0.toInt())
                redrawPaws(b, left, right, up)
            }
        }
    }

    private fun redrawPaws(b: PixelBuffer, left: ArmShape, right: ArmShape, up: Int) {
        shape(b, left.paw.dy(up), HoodiePalette.FUR)
        shape(b, right.paw.dy(up).mirror(), HoodiePalette.FUR)
    }

    /** Retângulo de cantos chanfrados com contorno de 1px. */
    private fun shape(b: PixelBuffer, r: R, fill: Int, outline: Int = HoodiePalette.OUTLINE) {
        fun inside(x: Int, y: Int): Boolean {
            if (x < r.x0 || x > r.x1 || y < r.y0 || y > r.y1) return false
            val dx = minOf(x - r.x0, r.x1 - x)
            val dy = minOf(y - r.y0, r.y1 - y)
            if (!r.round) return dx + dy >= r.r
            if (dx >= r.r || dy >= r.r) return true
            val ex = r.r - dx - 0.5; val ey = r.r - dy - 0.5
            return ex * ex + ey * ey <= r.r * r.r
        }
        for (y in r.y0 - 1..r.y1 + 1) for (x in r.x0 - 1..r.x1 + 1) {
            if (inside(x, y)) b.set(x, y, fill)
            else if (inside(x - 1, y) || inside(x + 1, y) || inside(x, y - 1) || inside(x, y + 1)) b.set(x, y, outline)
        }
    }

    private fun recolor(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, from: Int, to: Int) {
        for (y in y0..y1) for (x in x0..x1) if (b[x, y] == from) b.set(x, y, to)
    }
}

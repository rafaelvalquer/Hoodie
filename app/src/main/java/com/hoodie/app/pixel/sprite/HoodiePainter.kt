package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.renderer.PixelBuffer

/** Paleta oficial do Hoodie (baseada na concept art: gato azul de moletom, contorno escuro). 17 cores. */
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

    val ALL = listOf(OUTLINE, FUR, FUR_SHADE, FUR_LIGHT, INNER_EAR, HOOD, HOOD_SHADE, HOOD_DARK, HOOD_LIGHT, EYE, WHITE, NOSE, TONGUE, BLUSH, STRING, BACKPACK, BACKPACK_DARK)
}

/** Sprite procedural + âncoras do frame. */
data class PaintedSprite(val image: PixelBuffer, val anchors: SpriteAnchors)

/**
 * Pintor procedural do Hoodie (fallback/debug do SpriteProvider). Gera o sprite
 * 48×72 de uma [HoodiePose] em três vistas (frente, costas, lado esquerdo). Cada
 * parte é uma forma com contorno próprio de 1px, desenhada de trás para frente.
 */
object HoodiePainter {
    const val WIDTH = 48
    const val HEIGHT = 72
    /** Os pés tocam o chão sempre neste ponto (o que evita "pé deslizando"). */
    val FEET = Point(24, 71)

    private val cache = HashMap<HoodiePose, PaintedSprite>()

    fun sprite(pose: HoodiePose): PixelBuffer = painted(pose).image

    fun painted(pose: HoodiePose): PaintedSprite = synchronized(cache) { cache.getOrPut(pose) { paint(pose) } }

    private data class R(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val r: Int = 2, val round: Boolean = false) {
        fun dy(d: Int) = copy(y0 = y0 + d, y1 = y1 + d)
        fun dx(d: Int) = copy(x0 = x0 + d, x1 = x1 + d)
        fun mirror() = copy(x0 = WIDTH - 1 - x1, x1 = WIDTH - 1 - x0)
        fun inside(x: Int, y: Int): Boolean {
            if (x < x0 || x > x1 || y < y0 || y > y1) return false
            val dx = minOf(x - x0, x1 - x)
            val dy = minOf(y - y0, y1 - y)
            if (!round) return dx + dy >= r
            if (dx >= r || dy >= r) return true
            val ex = r - dx - 0.5; val ey = r - dy - 0.5
            return ex * ex + ey * ey <= r * r
        }
    }

    private class ArmShape(val parts: List<R>, val paw: R, val hx: Int, val hy: Int)

    // Geometria do braço ESQUERDO (da tela), vista frontal. O direito é espelhado.
    private fun armShape(arm: Arm): ArmShape = when (arm) {
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

    /** Caminhada em 8 fases: quanto cada pé sobe e quanto avança (vista lateral). */
    private val STRIDE_LIFT = intArrayOf(0, 0, 0, 0, 0, 1, 2, 1)
    private val STRIDE_X = intArrayOf(-5, -3, -1, 2, 5, 3, 0, -3)
    private val ARM_SWING = intArrayOf(3, 2, 0, -2, -3, -2, 0, 2)

    private fun paint(p: HoodiePose): PaintedSprite {
        val b = PixelBuffer(WIDTH, HEIGHT)
        val sit = p.legs == Legs.SIT
        val up = (if (sit) 5 else 0) + p.bob
        val anchors = when {
            p.headOnly -> { drawHeadFront(b, p, up); headOnlyAnchors(p, up) }
            p.facing == Facing.SIDE -> paintSide(b, p, up)
            p.facing == Facing.BACK -> paintBack(b, p, up)
            else -> paintFront(b, p, up)
        }
        if (p.lift <= 0) return PaintedSprite(b, anchors)
        // Pulo: todo o desenho sobe; o ponto dos pés continua no chão.
        val lifted = PixelBuffer(WIDTH, HEIGHT).also { it.blit(b, 0, -p.lift) }
        fun Point.up() = Point(x, y - p.lift)
        return PaintedSprite(
            lifted,
            anchors.copy(rightHand = anchors.rightHand.up(), leftHand = anchors.leftHand.up(), head = anchors.head.up(), back = anchors.back.up()),
        )
    }

    private fun headOnlyAnchors(p: HoodiePose, up: Int): SpriteAnchors {
        val head = Point(24 + p.headTilt * 2, 8 + up)
        return SpriteAnchors(head, head, head, Point(24, 30 + up), FEET)
    }

    // ───────────────────────── FRENTE ─────────────────────────

    private fun paintFront(b: PixelBuffer, p: HoodiePose, up: Int): SpriteAnchors {
        val sit = p.legs == Legs.SIT
        if (p.backpack) shape(b, R(8, 37, 39, 56, 3).dy(up + p.backpackDy), HoodiePalette.BACKPACK)
        if (!sit) drawLegsFront(b, p)
        shape(b, R(9, 28, 38, 39, 5).dy(up), HoodiePalette.HOOD_SHADE)
        drawBodyFront(b, up, p)
        if (sit) drawLegsFront(b, p)

        val left = armShape(p.leftArm)
        val right = armShape(p.rightArm)
        drawArm(b, left, up, mirror = false)
        drawArm(b, right, up, mirror = true)

        drawHeadFront(b, p, up + p.headDy)
        // Pata na cabeça (coçar) fica por cima da cabeça.
        if (p.leftArm == Arm.HEAD) shape(b, left.paw.dy(up), HoodiePalette.FUR)
        if (p.rightArm == Arm.HEAD) shape(b, right.paw.dy(up).mirror(), HoodiePalette.FUR)

        val anchors = SpriteAnchors(
            rightHand = Point(WIDTH - 1 - right.hx, right.hy + up),
            leftHand = Point(left.hx, left.hy + up),
            head = Point(24, 8 + up + p.headDy),
            back = Point(24, 46 + up),
            feet = FEET,
        )
        drawItemFront(b, p, left, right, up, anchors)
        return anchors
    }

    private fun drawBodyFront(b: PixelBuffer, up: Int, p: HoodiePose) {
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
        drawStrings(b, intArrayOf(20, 27), up, p.stringSwing)
        if (p.backpack) {
            for (x in intArrayOf(14, 32)) {
                b.box(x, 34 + up, x + 1, 47 + up, HoodiePalette.BACKPACK_DARK)
                b.set(x, 47 + up, HoodiePalette.OUTLINE); b.set(x + 1, 47 + up, HoodiePalette.OUTLINE)
            }
        }
    }

    /** Cordões do capuz: metade de baixo balança (follow-through). */
    private fun drawStrings(b: PixelBuffer, xs: IntArray, up: Int, swing: Int) {
        for (x in xs) {
            b.vline(x, 36 + up, 39 + up, HoodiePalette.STRING)
            val lx = x + swing.coerceIn(-1, 1)
            b.vline(lx, 40 + up, 42 + up, HoodiePalette.STRING)
            val tip = x + swing.coerceIn(-2, 2)
            b.set(tip, 43 + up, HoodiePalette.STRING)
            b.set(tip, 44 + up, HoodiePalette.HOOD_DARK)
        }
    }

    private fun drawLegsFront(b: PixelBuffer, p: HoodiePose) {
        if (p.legs == Legs.SIT) {
            foot(b, R(12, 63, 21, 70, 3))
            foot(b, R(12, 63, 21, 70, 3).mirror())
            return
        }
        fun leg(lift: Int, forward: Int = 0) = R(15 + forward, 57 - lift, 21 + forward, 68 - lift, 1) to R(13 + forward, 65 - lift, 21 + forward, 70 - lift, 2)
        val (l, r) = when (p.legs) {
            Legs.STEP_LEFT -> leg(1) to leg(0)
            Legs.STEP_RIGHT -> leg(0) to leg(1)
            Legs.RUN_A -> leg(5, -1) to leg(0)
            Legs.RUN_B -> leg(0) to leg(5, -1)
            Legs.WALK -> {
                val s = p.stride.mod(8)
                leg(STRIDE_LIFT[s] * 2) to leg(STRIDE_LIFT[(s + 4) % 8] * 2)
            }
            else -> leg(0) to leg(0)
        }
        shape(b, l.first, HoodiePalette.FUR); foot(b, l.second)
        shape(b, r.first.mirror(), HoodiePalette.FUR); foot(b, r.second.mirror())
    }

    private fun foot(b: PixelBuffer, f: R, color: Int = HoodiePalette.FUR) {
        shape(b, f, color)
        recolor(b, f.x0, f.y1 - 1, f.x1, f.y1 - 1, color, HoodiePalette.FUR_SHADE)
        val mid = (f.x0 + f.x1) / 2
        b.set(mid - 1, f.y1 - 1, HoodiePalette.OUTLINE); b.set(mid + 1, f.y1 - 1, HoodiePalette.OUTLINE)
    }

    private fun drawArm(b: PixelBuffer, arm: ArmShape, up: Int, mirror: Boolean, sleeve: Int = HoodiePalette.HOOD, fur: Int = HoodiePalette.FUR) {
        arm.parts.forEach { part ->
            val r = part.dy(up).let { if (mirror) it.mirror() else it }
            shape(b, r, sleeve)
            recolor(b, r.x0, r.y1 - 1, r.x1, r.y1 - 1, sleeve, HoodiePalette.HOOD_SHADE)
        }
        val paw = arm.paw.dy(up).let { if (mirror) it.mirror() else it }
        shape(b, paw, fur)
    }

    private fun drawHeadFront(b: PixelBuffer, p: HoodiePose, up: Int) {
        val tx = if (p.headOnly) p.headTilt * 2 else 0
        drawEars(b, p.ears, up, tx)
        val head = R(7 + tx, 9, 40 + tx, 33, 8, round = true).dy(up)
        shape(b, head, HoodiePalette.FUR)
        recolor(b, head.x0 + 1, head.y1 - 2, head.x1 - 1, head.y1 - 1, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        recolor(b, 35 + tx, head.y0 + 4, 39 + tx, head.y1 - 3, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        b.hline(13 + tx, 19 + tx, head.y0 + 2, HoodiePalette.FUR_LIGHT)
        b.hline(11 + tx, 14 + tx, head.y0 + 3, HoodiePalette.FUR_LIGHT)
        b.set(head.x0, 26 + up, HoodiePalette.FUR); b.set(head.x1, 26 + up, HoodiePalette.FUR_SHADE)

        drawEyes(b, p.eyes, up, intArrayOf(15 + tx, 30 + tx))
        drawMouth(b, p.mouth, 23 + tx, up)
        if (p.blush || p.eyes == Eyes.HAPPY) {
            b.hline(11 + tx, 13 + tx, 24 + up, HoodiePalette.BLUSH); b.hline(34 + tx, 36 + tx, 24 + up, HoodiePalette.BLUSH)
        }
    }

    /** Focinho: nariz + boca em "w" centrados em [nx] (nariz ocupa nx..nx+1). */
    private fun drawMouth(b: PixelBuffer, mouth: Mouth, nx: Int, up: Int) {
        b.hline(nx, nx + 1, 23 + up, HoodiePalette.NOSE)
        when (mouth) {
            Mouth.SMILE -> {
                b.set(nx - 2, 24 + up, HoodiePalette.NOSE); b.set(nx - 1, 25 + up, HoodiePalette.NOSE)
                b.hline(nx, nx + 1, 24 + up, HoodiePalette.NOSE)
                b.set(nx + 2, 25 + up, HoodiePalette.NOSE); b.set(nx + 3, 24 + up, HoodiePalette.NOSE)
            }
            Mouth.OPEN -> {
                b.box(nx - 1, 24 + up, nx + 2, 27 + up, HoodiePalette.NOSE)
                b.hline(nx, nx + 1, 26 + up, HoodiePalette.TONGUE)
            }
            Mouth.FLAT -> b.hline(nx - 1, nx + 2, 25 + up, HoodiePalette.NOSE)
            Mouth.CHEW -> {
                b.hline(nx - 1, nx + 2, 25 + up, HoodiePalette.NOSE); b.set(nx - 2, 24 + up, HoodiePalette.NOSE); b.set(nx + 3, 24 + up, HoodiePalette.NOSE)
            }
        }
    }

    /** Desloca cada orelha conforme o humor (alerta, relaxada, caída, contraindo). */
    private fun drawEars(b: PixelBuffer, ears: Ears, up: Int, tx: Int) {
        val (ldx, ldy, rdx, rdy) = when (ears) {
            Ears.NORMAL -> listOf(0, 0, 0, 0)
            Ears.ALERT -> listOf(0, -1, 0, -1)
            Ears.RELAXED -> listOf(-1, 1, 1, 1)
            Ears.DOWN -> listOf(-2, 3, 2, 3)
            Ears.TWITCH_LEFT -> listOf(-1, 1, 0, 0)
            Ears.TWITCH_RIGHT -> listOf(0, 0, 1, 1)
        }
        ear(b, mirror = false, dx = ldx + tx, dy = up + ldy)
        ear(b, mirror = true, dx = rdx + tx, dy = up + rdy)
    }

    private fun ear(b: PixelBuffer, mirror: Boolean, dx: Int, dy: Int, fill: Int = HoodiePalette.FUR, inner: Int? = HoodiePalette.INNER_EAR) {
        fun mx(x: Int) = if (mirror) WIDTH - 1 - x else x
        val top = 2; val bottom = 13
        for (y in top..bottom) {
            val l = 10 - (y - top) * 2 / 11
            val r = 11 + (y - top) * 9 / 11
            for (x in l - 1..r + 1) b.set(mx(x) + dx, y + dy, HoodiePalette.OUTLINE)
        }
        b.hline(mx(10) + dx, mx(11) + dx, top - 1 + dy, HoodiePalette.OUTLINE)
        for (y in top..bottom) {
            val l = 10 - (y - top) * 2 / 11
            val r = 11 + (y - top) * 9 / 11
            for (x in l..r) b.set(mx(x) + dx, y + dy, fill)
        }
        if (inner != null) for (y in 6..12) {
            val l = 11 - (y - 6) / 6
            val r = 11 + (y - 6) * 5 / 7
            for (x in l..r) b.set(mx(x) + dx, y + dy, inner)
        }
    }

    private fun drawEyes(b: PixelBuffer, eyes: Eyes, up: Int, xs: IntArray) {
        for (baseX in xs) {
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
                Eyes.HALF -> {
                    b.box(x0, y0 + 2, x0 + 2, y0 + 3, HoodiePalette.EYE)
                    b.hline(x0 - 1, x0 + 3, y0 + 1, HoodiePalette.FUR_SHADE)
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

    private fun drawItemFront(b: PixelBuffer, p: HoodiePose, left: ArmShape, right: ArmShape, up: Int, a: SpriteAnchors) {
        val o = HoodiePalette.OUTLINE
        when (p.item) {
            Item.NONE -> Unit
            Item.BOOK, Item.MENU -> {
                val cover = if (p.item == Item.MENU) 0xFF2F4A3C.toInt() else 0xFFC8484A.toInt()
                b.outlined(15, 35 + up, 32, 46 + up, cover, o)
                b.box(16, 36 + up, 31, 37 + up, 0xFFF4EBD8.toInt())
                if (p.item == Item.MENU) for (y in 39..44 step 2) b.hline(17, 30, y + up, 0xFFDDE7E0.toInt())
                else { b.vline(23, 36 + up, 45 + up, 0xFF8E2E33.toInt()); b.vline(24, 36 + up, 45 + up, 0xFF8E2E33.toInt()) }
                redrawPaws(b, left, right, up)
            }
            Item.CONTROLLER -> {
                b.outlined(15, 38 + up, 32, 45 + up, 0xFF3B3F55.toInt(), o)
                b.set(18, 41 + up, 0xFFDDE2F0.toInt()); b.set(17, 41 + up, 0xFFDDE2F0.toInt()); b.set(19, 41 + up, 0xFFDDE2F0.toInt())
                b.set(18, 40 + up, 0xFFDDE2F0.toInt()); b.set(18, 42 + up, 0xFFDDE2F0.toInt())
                b.set(28, 40 + up, 0xFFE05A5A.toInt()); b.set(29, 42 + up, 0xFF5AC8E0.toInt())
                redrawPaws(b, left, right, up)
            }
            Item.DUMBBELL -> {
                val hands = if (p.itemInBothHands) listOf(a.leftHand, a.rightHand) else listOf(a.rightHand)
                hands.forEach { drawItemAt(b, Item.DUMBBELL, it) }
            }
            else -> drawItemAt(b, p.item, a.rightHand)
        }
    }

    /** Item de uma mão desenhado na âncora (usado também pelos sprite sheets sem item embutido). */
    fun drawItemAt(b: PixelBuffer, item: Item, hand: Point) {
        val o = HoodiePalette.OUTLINE
        val rx = hand.x; val ry = hand.y
        when (item) {
            Item.MUG -> {
                b.outlined(rx + 1, ry - 6, rx + 6, ry, 0xFFF4F1EA.toInt(), o)
                b.hline(rx + 2, rx + 5, ry - 5, 0xFF6B3E26.toInt())
                b.vline(rx + 7, ry - 4, ry - 2, o)
                b.set(rx + 3, ry - 2, 0xFFE07A93.toInt())
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
                b.hline(rx - 5, rx + 5, ry, 0xFF8A8F9E.toInt())
                b.outlined(rx - 7, ry - 3, rx - 4, ry + 3, 0xFF3A3D4A.toInt(), o)
                b.outlined(rx + 4, ry - 3, rx + 7, ry + 3, 0xFF3A3D4A.toInt(), o)
            }
            else -> Unit
        }
    }

    private fun redrawPaws(b: PixelBuffer, left: ArmShape, right: ArmShape, up: Int) {
        shape(b, left.paw.dy(up), HoodiePalette.FUR)
        shape(b, right.paw.dy(up).mirror(), HoodiePalette.FUR)
    }

    // ───────────────────────── COSTAS ─────────────────────────

    private fun paintBack(b: PixelBuffer, p: HoodiePose, up: Int): SpriteAnchors {
        drawLegsFront(b, p)
        // Rabo saindo por baixo do moletom.
        shape(b, R(31, 54, 35, 63, 2).dy(up), HoodiePalette.FUR)
        shape(b, R(33, 60, 39, 64, 2).dy(up), HoodiePalette.FUR)
        val body = R(11, 33, 36, 60, 4).dy(up)
        shape(b, body, HoodiePalette.HOOD)
        recolor(b, 12, body.y0 + 2, 15, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        recolor(b, 12, body.y1 - 3, 35, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        for (x in 13..34 step 3) b.set(x, body.y1 - 2, HoodiePalette.HOOD_DARK)
        // Capuz caído nas costas.
        shape(b, R(14, 30, 33, 45, 6, round = true).dy(up), HoodiePalette.HOOD_SHADE, HoodiePalette.HOOD_DARK)
        b.hline(18, 29, 32 + up, HoodiePalette.HOOD_DARK)
        if (p.backpack) {
            shape(b, R(13, 35, 34, 57, 3).dy(up + p.backpackDy), HoodiePalette.BACKPACK)
            b.hline(15, 32, 43 + up + p.backpackDy, HoodiePalette.BACKPACK_DARK)
            b.box(21, 47 + up + p.backpackDy, 26, 52 + up + p.backpackDy, HoodiePalette.BACKPACK_DARK)
        }
        val left = armShape(p.leftArm)
        val right = armShape(p.rightArm)
        drawArm(b, left, up, mirror = false)
        drawArm(b, right, up, mirror = true)
        // Cabeça de costas: orelhas sem a parte interna, sem rosto.
        val hu = up + p.headDy
        val (ldy, rdy) = when (p.ears) { Ears.ALERT -> -1 to -1; Ears.DOWN -> 3 to 3; Ears.RELAXED -> 1 to 1; Ears.TWITCH_LEFT -> 1 to 0; Ears.TWITCH_RIGHT -> 0 to 1; else -> 0 to 0 }
        ear(b, mirror = false, dx = 0, dy = hu + ldy, inner = HoodiePalette.FUR_SHADE)
        ear(b, mirror = true, dx = 0, dy = hu + rdy, inner = HoodiePalette.FUR_SHADE)
        val head = R(7, 9, 40, 33, 8, round = true).dy(hu)
        shape(b, head, HoodiePalette.FUR)
        recolor(b, 8, head.y0 + 3, 12, head.y1 - 3, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        recolor(b, head.x0 + 1, head.y1 - 3, head.x1 - 1, head.y1 - 1, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        b.hline(22, 26, head.y0 + 3, HoodiePalette.FUR_LIGHT)
        return SpriteAnchors(
            rightHand = Point(WIDTH - 1 - right.hx, right.hy + up), leftHand = Point(left.hx, left.hy + up),
            head = Point(24, 8 + hu), back = Point(24, 46 + up), feet = FEET,
        )
    }

    // ───────────────────────── LADO (olhando para a esquerda) ─────────────────────────

    private fun paintSide(b: PixelBuffer, p: HoodiePose, up: Int): SpriteAnchors {
        val sit = p.legs == Legs.SIT
        val s = p.stride.mod(8)
        val walking = p.legs == Legs.WALK
        val nearX = when { walking -> STRIDE_X[s]; p.legs == Legs.RUN_A -> -5; p.legs == Legs.RUN_B -> 5; else -> -1 }
        val farX = when { walking -> STRIDE_X[(s + 4) % 8]; p.legs == Legs.RUN_A -> 5; p.legs == Legs.RUN_B -> -5; else -> 2 }
        val nearLift = if (walking) STRIDE_LIFT[s] * 2 else if (p.legs == Legs.RUN_B) 4 else 0
        val farLift = if (walking) STRIDE_LIFT[(s + 4) % 8] * 2 else if (p.legs == Legs.RUN_A) 4 else 0
        val armSwing = if (walking) ARM_SWING[s] else 0

        // Rabo atrás de tudo: sai do quadril, curva para cima e balança com o passo.
        if (!sit) {
            val sway = if (walking) intArrayOf(0, 0, 1, 1, 0, 0, -1, -1)[s] else 0
            shapeUnion(b, listOf(R(30, 48, 35, 54, 2), R(34 + sway, 41, 38 + sway, 52, 2), R(36 + sway * 2, 36, 40 + sway * 2, 43, 2)).map { it.dy(up) }, HoodiePalette.FUR)
            recolor(b, 37, 36 + up, 41, 52 + up, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        }
        // Mochila nas costas (lado direito), atrás do corpo.
        if (p.backpack) {
            shape(b, R(29, 35, 39, 54, 3).dy(up + p.backpackDy), HoodiePalette.BACKPACK)
            b.vline(36, 39 + up + p.backpackDy, 51 + up + p.backpackDy, HoodiePalette.BACKPACK_DARK)
        }
        // Perna e braço do fundo, mais escuros.
        if (sit) {
            foot(b, R(13, 63, 24, 70, 3), HoodiePalette.FUR_SHADE)
        } else {
            sideLeg(b, farX, farLift, HoodiePalette.FUR_SHADE)
        }
        sideArm(b, -armSwing, up, HoodiePalette.HOOD_SHADE, HoodiePalette.FUR_SHADE)
        if (!sit) sideLeg(b, nearX, nearLift, HoodiePalette.FUR)
        // Capuz embolado nas costas + corpo com barriguinha na frente.
        shape(b, R(25, 28, 36, 42, 4).dy(up), HoodiePalette.HOOD_SHADE)
        val body = R(15, 33, 32, 55, 4).dy(up)
        shapeUnion(b, listOf(body, R(13, 41, 19, 53, 3).dy(up)), HoodiePalette.HOOD)
        recolor(b, 29, body.y0 + 2, 31, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        recolor(b, 14, body.y1 - 2, 31, body.y1 - 1, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE)
        for (x in 15..30 step 3) b.set(x, body.y1 - 1, HoodiePalette.HOOD_DARK)
        drawStrings(b, intArrayOf(16), up, p.stringSwing)
        if (p.backpack) b.box(25, 34 + up, 26, 45 + up, HoodiePalette.BACKPACK_DARK)
        if (sit) foot(b, R(10, 63, 21, 70, 3))

        // Braço da frente: ombro → cotovelo → pata na altura do quadril (balança oposto à perna).
        val forward = p.rightArm in setOf(Arm.FORWARD_UP, Arm.FORWARD_DOWN, Arm.HOLD_CHEST) || p.leftArm in setOf(Arm.FORWARD_UP, Arm.FORWARD_DOWN, Arm.HOLD_CHEST)
        val hand = if (forward) {
            drawArm(b, ArmShape(listOf(R(19, 35, 25, 44), R(10, 40, 22, 46)), R(6, 39, 11, 45), 8, 42), up, false)
            Point(8, 42 + up)
        } else {
            // Mais claro que o tronco: está mais perto da luz e de quem olha.
            sideArm(b, armSwing, up, HoodiePalette.HOOD_LIGHT, HoodiePalette.FUR)
        }

        // Cabeça de perfil: orelha do fundo, focinho, um olho.
        val hu = up + p.headDy
        val (edx, edy) = when (p.ears) { Ears.ALERT -> 0 to -1; Ears.DOWN -> 2 to 3; Ears.RELAXED -> 1 to 1; Ears.TWITCH_LEFT, Ears.TWITCH_RIGHT -> 1 to 1; else -> 0 to 0 }
        ear(b, mirror = false, dx = 19 + edx, dy = hu + 1 + edy, fill = HoodiePalette.FUR_SHADE, inner = null)
        ear(b, mirror = false, dx = 3 - edx, dy = hu + edy)
        shapeUnion(b, listOf(R(8, 9, 37, 33, 8, round = true).dy(hu), R(4, 19, 12, 30, 3).dy(hu)), HoodiePalette.FUR)
        recolor(b, 9, 31 + hu, 36, 32 + hu, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        recolor(b, 31, 13 + hu, 36, 30 + hu, HoodiePalette.FUR, HoodiePalette.FUR_SHADE)
        b.hline(14, 20, 11 + hu, HoodiePalette.FUR_LIGHT)
        drawEyes(b, p.eyes, hu, intArrayOf(12))
        b.hline(4, 5, 22 + hu, HoodiePalette.NOSE)
        when (p.mouth) {
            Mouth.OPEN -> { b.box(5, 25 + hu, 8, 27 + hu, HoodiePalette.NOSE); b.set(6, 26 + hu, HoodiePalette.TONGUE) }
            else -> { b.set(6, 25 + hu, HoodiePalette.NOSE); b.set(7, 26 + hu, HoodiePalette.NOSE); b.set(8, 25 + hu, HoodiePalette.NOSE) }
        }
        if (p.blush || p.eyes == Eyes.HAPPY) b.hline(13, 15, 25 + hu, HoodiePalette.BLUSH)

        if (p.item != Item.NONE && p.item != Item.BOOK && p.item != Item.CONTROLLER && p.item != Item.MENU) drawItemAt(b, p.item, hand)
        return SpriteAnchors(rightHand = hand, leftHand = Point(20 - armSwing, 52 + up), head = Point(20, 8 + hu), back = Point(33, 44 + up), feet = FEET)
    }

    /**
     * Perna de perfil: coxa sai do quadril (metade do deslocamento) e a canela vai até
     * o pé (deslocamento inteiro) — isso desenha o joelho. [lift] sobe canela e pé.
     */
    private fun sideLeg(b: PixelBuffer, offset: Int, lift: Int, color: Int) {
        val hx = 21 + offset / 2
        shapeUnion(b, listOf(R(hx, 50, hx + 5, 60 - lift / 2, 1), R(21 + offset, 58 - lift, 26 + offset, 67 - lift, 1)), color)
        foot(b, R(17 + offset, 65 - lift, 26 + offset, 70 - lift, 2), color)
    }

    /**
     * Braço de perfil em dois segmentos (manga até o cotovelo + antebraço) com a pata
     * na altura do quadril. [swing] > 0 leva a pata para trás. Retorna a âncora da mão.
     */
    private fun sideArm(b: PixelBuffer, swing: Int, up: Int, sleeve: Int, fur: Int): Point {
        // Balanço amplo: a pata sai da silhueta do corpo para a frente e para trás.
        val ex = 20 + swing
        val px = 19 + swing * 2
        shapeUnion(b, listOf(R(19, 35, 24, 42, 2), R(ex, 40, ex + 4, 46, 1), R(px, 45, px + 4, 49, 1)).map { it.dy(up) }, sleeve)
        // Borda de trás da manga mais escura: separa o braço do corpo.
        val back = if (sleeve == HoodiePalette.HOOD_LIGHT) HoodiePalette.HOOD else HoodiePalette.HOOD_DARK
        recolor(b, 23, 36 + up, 24, 42 + up, sleeve, back)
        recolor(b, ex + 3, 41 + up, ex + 4, 46 + up, sleeve, back)
        recolor(b, px, 48 + up, px + 4, 49 + up, sleeve, HoodiePalette.HOOD_SHADE)
        shape(b, R(px - 1, 49, px + 4, 53, 2).dy(up), fur)
        return Point(px + 1, 51 + up)
    }

    // ───────────────────────── Formas ─────────────────────────

    /** Retângulo de cantos chanfrados/arredondados com contorno de 1px. */
    private fun shape(b: PixelBuffer, r: R, fill: Int, outline: Int = HoodiePalette.OUTLINE) = shapeUnion(b, listOf(r), fill, outline)

    /** União de formas com um único contorno (ex.: cabeça + focinho de perfil). */
    private fun shapeUnion(b: PixelBuffer, rs: List<R>, fill: Int, outline: Int = HoodiePalette.OUTLINE) {
        fun inside(x: Int, y: Int) = rs.any { it.inside(x, y) }
        val x0 = rs.minOf { it.x0 } - 1; val x1 = rs.maxOf { it.x1 } + 1
        val y0 = rs.minOf { it.y0 } - 1; val y1 = rs.maxOf { it.y1 } + 1
        for (y in y0..y1) for (x in x0..x1) {
            if (inside(x, y)) b.set(x, y, fill)
            else if (inside(x - 1, y) || inside(x + 1, y) || inside(x, y - 1) || inside(x, y + 1)) b.set(x, y, outline)
        }
    }

    private fun recolor(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, from: Int, to: Int) {
        for (y in y0..y1) for (x in x0..x1) if (b[x, y] == from) b.set(x, y, to)
    }
}

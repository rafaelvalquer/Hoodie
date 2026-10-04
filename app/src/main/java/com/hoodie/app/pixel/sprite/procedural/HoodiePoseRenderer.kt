package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.character.CharacterGeometry
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.HoodiePainter.Part
import com.hoodie.app.pixel.sprite.procedural.HoodieBodyPainter.drawBodyFront
import com.hoodie.app.pixel.sprite.procedural.HoodieBodyPainter.drawBodyBack
import com.hoodie.app.pixel.sprite.procedural.HoodieBodyPainter.drawBodySide
import com.hoodie.app.pixel.sprite.procedural.HoodieHeadPainter.drawHeadFront
import com.hoodie.app.pixel.sprite.procedural.HoodieHeadPainter.drawHeadBack
import com.hoodie.app.pixel.sprite.procedural.HoodieHeadPainter.drawHeadSide
import com.hoodie.app.pixel.sprite.procedural.HoodieArmsPainter.armShape
import com.hoodie.app.pixel.sprite.procedural.HoodieArmsPainter.drawArm
import com.hoodie.app.pixel.sprite.procedural.HoodieArmsPainter.sideArm
import com.hoodie.app.pixel.sprite.procedural.HoodieLegsPainter.drawLegsFront
import com.hoodie.app.pixel.sprite.procedural.HoodieLegsPainter.foot
import com.hoodie.app.pixel.sprite.procedural.HoodieLegsPainter.sideLeg
import com.hoodie.app.pixel.sprite.procedural.HoodieAccessoryPainter.drawItemFront
import com.hoodie.app.pixel.sprite.procedural.HoodieAccessoryPainter.drawItemAt
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.part
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.shape
import com.hoodie.app.pixel.sprite.procedural.HoodieTailPainter.drawTailBack
import com.hoodie.app.pixel.sprite.procedural.HoodieTailPainter.drawTailSide

/** Procedural HoodiePoseRenderer; preserves drawing order and semantic part ownership. */
internal object HoodiePoseRenderer {
    private const val WIDTH = CharacterCanvas.WIDTH
    private const val HEIGHT = CharacterCanvas.HEIGHT
    private val FEET = CharacterCanvas.FEET

    fun paint(p: HoodiePose): PaintedSprite {
        val b = PixelBuffer(WIDTH, HEIGHT)
        val geometry = CharacterGeometry.resolveHoodie(p)
        val sit = p.legs == Legs.SIT
        val up = geometry.torsoOffsetY
        val anchors = when {
            p.headOnly -> { drawHeadFront(b, p, up); headOnlyAnchors(p, up) }
            p.facing == Facing.SIDE -> paintSide(b, p, up)
            p.facing == Facing.BACK -> paintBack(b, p, up)
            else -> paintFront(b, p, up)
        }
        if (geometry.lift <= 0) return PaintedSprite(b, anchors)
        // Pulo: todo o desenho sobe; o ponto dos pés continua no chão.
        val lifted = PixelBuffer(WIDTH, HEIGHT).also { it.blit(b, 0, -geometry.lift) }
        fun Point.up() = Point(x, y - geometry.lift)
        return PaintedSprite(
            lifted,
            anchors.copy(rightHand = anchors.rightHand.up(), leftHand = anchors.leftHand.up(), head = anchors.head.up(), back = anchors.back.up()),
        )
    }

    fun headOnlyAnchors(p: HoodiePose, up: Int): SpriteAnchors {
        val head = Point(24 + p.headTilt * 2, 8 + up)
        return SpriteAnchors(head, head, head, Point(24, 30 + up), FEET)
    }

    fun paintFront(b: PixelBuffer, p: HoodiePose, up: Int): SpriteAnchors {
        val sit = p.legs == Legs.SIT
        if (p.backpack) part(b, Part.BACKPACK) { shape(b, R(8, 37, 39, 56, 3).dy(up + p.backpackDy), HoodiePalette.BACKPACK) }
        if (!sit) drawLegsFront(b, p)
        part(b, Part.TORSO) {
            shape(b, R(9, 28, 38, 39, 5).dy(up), HoodiePalette.HOOD_SHADE)
            drawBodyFront(b, up, p)
        }
        if (sit) drawLegsFront(b, p)

        val left = armShape(p.leftArm)
        val right = armShape(p.rightArm)
        drawArm(b, left, up, mirror = false)
        drawArm(b, right, up, mirror = true)

        drawHeadFront(b, p, up + p.headDy)
        // Pata na cabeça (coçar) fica por cima da cabeça.
        if (p.leftArm == Arm.HEAD) part(b, Part.HAND_LEFT) { shape(b, left.paw.dy(up), HoodiePalette.FUR) }
        if (p.rightArm == Arm.HEAD) part(b, Part.HAND_RIGHT) { shape(b, right.paw.dy(up).mirror(), HoodiePalette.FUR) }

        val anchors = SpriteAnchors(
            rightHand = Point(WIDTH - 1 - right.hx, right.hy + up),
            leftHand = Point(left.hx, left.hy + up),
            head = Point(24, 8 + up + p.headDy),
            back = Point(24, 46 + up),
            feet = FEET,
        )
        part(b, Part.ACCESSORY) { drawItemFront(b, p, left, right, up, anchors) }
        return anchors
    }

    fun paintBack(b: PixelBuffer, p: HoodiePose, up: Int): SpriteAnchors {
        drawLegsFront(b, p)
        // Rabo saindo por baixo do moletom.
        // De costas o rabo também acompanha a passada (a leitura vem do capuz, costas e rabo).
        drawTailBack(b, p, up)
        drawBodyBack(b, up)
        if (p.backpack) part(b, Part.BACKPACK) {
            shape(b, R(13, 35, 34, 57, 3).dy(up + p.backpackDy), HoodiePalette.BACKPACK)
            b.hline(15, 32, 43 + up + p.backpackDy, HoodiePalette.BACKPACK_DARK)
            b.box(21, 47 + up + p.backpackDy, 26, 52 + up + p.backpackDy, HoodiePalette.BACKPACK_DARK)
        }
        val left = armShape(p.leftArm)
        val right = armShape(p.rightArm)
        drawArm(b, left, up, mirror = false)
        drawArm(b, right, up, mirror = true)
        // Cabeça de costas: orelhas sem a parte interna, sem rosto.
        val hu = drawHeadBack(b, p, up)
        return SpriteAnchors(
            rightHand = Point(WIDTH - 1 - right.hx, right.hy + up), leftHand = Point(left.hx, left.hy + up),
            head = Point(24, 8 + hu), back = Point(24, 46 + up), feet = FEET,
        )
    }

    fun paintSide(b: PixelBuffer, p: HoodiePose, up: Int): SpriteAnchors {
        val sit = p.legs == Legs.SIT
        val s = p.stride.mod(8)
        val walking = p.legs == Legs.WALK
        val nearX = when { walking -> STRIDE_X[s]; p.legs == Legs.RUN_A -> -5; p.legs == Legs.RUN_B -> 5; else -> -1 }
        val farX = when { walking -> STRIDE_X[(s + 4) % 8]; p.legs == Legs.RUN_A -> 5; p.legs == Legs.RUN_B -> -5; else -> 2 }
        val nearLift = if (walking) STRIDE_LIFT[s] * 2 else if (p.legs == Legs.RUN_B) 4 else 0
        val farLift = if (walking) STRIDE_LIFT[(s + 4) % 8] * 2 else if (p.legs == Legs.RUN_A) 4 else 0
        val armSwing = if (walking) ARM_SWING[s] else 0

        // Rabo atrás de tudo: sai do quadril, curva para cima e balança com o passo.
        drawTailSide(b, p, up)
        // Mochila nas costas (lado direito), atrás do corpo.
        if (p.backpack) part(b, Part.BACKPACK) {
            shape(b, R(29, 35, 39, 54, 3).dy(up + p.backpackDy), HoodiePalette.BACKPACK)
            b.vline(36, 39 + up + p.backpackDy, 51 + up + p.backpackDy, HoodiePalette.BACKPACK_DARK)
        }
        // Perna e braço do fundo, mais escuros.
        part(b, Part.LEG_RIGHT) {
            if (sit) foot(b, R(13, 63, 24, 70, 3), HoodiePalette.FUR_SHADE)
            else sideLeg(b, farX, farLift, HoodiePalette.FUR_SHADE)
        }
        sideArm(b, -armSwing, up, HoodiePalette.HOOD_SHADE, HoodiePalette.FUR_SHADE, far = true)
        if (!sit) part(b, Part.LEG_LEFT) { sideLeg(b, nearX, nearLift, HoodiePalette.FUR) }
        // Capuz embolado nas costas + corpo com barriguinha na frente.
        drawBodySide(b, p, up)
        if (sit) part(b, Part.LEG_LEFT) { foot(b, R(10, 63, 21, 70, 3)) }

        // Braço da frente: ombro → cotovelo → pata na altura do quadril (balança oposto à perna).
        val forward = p.rightArm in setOf(Arm.FORWARD_UP, Arm.FORWARD_DOWN, Arm.HOLD_CHEST) || p.leftArm in setOf(Arm.FORWARD_UP, Arm.FORWARD_DOWN, Arm.HOLD_CHEST)
        val hand = if (forward) {
            drawArm(b, ArmShape(listOf(R(19, 35, 25, 44), R(10, 40, 22, 46)), R(6, 39, 11, 45), 8, 42), up, false)
            Point(8, 42 + up)
        } else {
            // Mais claro que o tronco: está mais perto da luz e de quem olha.
            sideArm(b, armSwing, up, HoodiePalette.HOOD_LIGHT, HoodiePalette.FUR, far = false)
        }

        // Cabeça de perfil: orelha do fundo, focinho, um olho.
        val hu = drawHeadSide(b, p, up)
        if (p.item != Item.NONE && p.item != Item.BOOK && p.item != Item.CONTROLLER && p.item != Item.MENU) part(b, Part.ACCESSORY) { drawItemAt(b, p.item, hand) }
        return SpriteAnchors(rightHand = hand, leftHand = Point(20 - armSwing, 52 + up), head = Point(20, 8 + hu), back = Point(33, 44 + up), feet = FEET)
    }
}

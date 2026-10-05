package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.character.outfit.BackAccessoryPainter
import com.hoodie.app.pixel.character.outfit.OutfitContext
import com.hoodie.app.pixel.character.outfit.painter
import com.hoodie.app.pixel.character.species.HeadContext
import com.hoodie.app.pixel.character.species.LegStyle
import com.hoodie.app.pixel.character.species.TailContext
import com.hoodie.app.pixel.character.species.TailStyle
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Point
import kotlin.math.roundToInt

/**
 * Painter V3 dos personagens. Ordem de pintura (frente):
 *
 *     cauda (atrás) → mochila → pernas/pés → tronco+roupa → alças → braços/mãos
 *     → sombra do pescoço → cabeça (espécie) → item na mão
 *
 * De perfil o quadro é autorado olhando para a direita e espelhado no fim, como o Hoodie.
 */
object CharacterPainter {
    const val WIDTH = CharacterCanvas.WIDTH
    const val HEIGHT = CharacterCanvas.HEIGHT
    val FEET = CharacterCanvas.FEET

    fun render(request: CharacterRenderRequest): CharacterFrame = paint(request.style, request.pose, request.motion)

    /** O Hoodie continua chamando exatamente o renderer legado, sem recompor nenhum pixel. */
    fun paint(
        style: CharacterStyle,
        pose: CharacterPose,
        motion: CharacterRenderMotion = CharacterRenderMotion(),
    ): CharacterFrame {
        if (style.id == CharacterStyle.HOODIE.id) {
            val legacy = HoodiePainter.painted(pose)
            return CharacterFrame(legacy.image, CharacterAnchors.fromHoodie(legacy.anchors, pose.facing))
        }
        val frame = paintV3(style, pose, motion)
        // A pose lateral segue a mesma orientação canônica do Hoodie: perfil voltado à esquerda.
        return if (pose.facing == Facing.SIDE) frame.mirrored() else frame
    }

    private fun paintV3(style: CharacterStyle, pose: CharacterPose, motion: CharacterRenderMotion): CharacterFrame {
        val b = PixelBuffer(WIDTH, HEIGHT)
        val p = style.palette
        val facing = pose.facing
        val sway = motion.sway.coerceIn(-1, 1)
        val l = BodyLayout.resolve(style, pose, sway)
        val outfit = style.outfit.painter
        val gait = motion.gait ?: defaultGait(pose, motion)
        val bird = style.species.legStyle == LegStyle.BIRD
        val tailSwing = tailSwing(pose, motion)

        // 1. Volume da mochila/bolsa atrás.
        BackAccessoryPainter.drawBehind(b, p, style.backAccessory, l)
        // 2. Cauda atrás do corpo (frente e perfil), mas na frente da mochila.
        if (style.hasTail && facing != Facing.BACK) drawTail(b, style, pose, l, tailSwing)
        // 3. Braço de trás (perfil).
        val sideJoint = Point((l.shoulderLeft + l.shoulderRight) / 2 + 1, l.shoulderY + 3)
        if (facing == Facing.SIDE) {
            val farHand = sideHand(oppositeSwing(pose.leftArm), sideJoint, l, motion.armSwing).let { it.copy(x = it.x - 2) }
            CharacterBodyPainter.drawSleeve(b, style, outfit, sideJoint.x - 2, sideJoint.y, farHand, outward = -1, far = true)
            style.species.drawHand(b, p, farHand.x, farHand.y)
        }
        // 4. Pernas e pés.
        if (facing == Facing.SIDE) CharacterBodyPainter.drawLegsSide(b, style, outfit, l, gait)
        else CharacterBodyPainter.drawLegsFront(b, style, outfit, l, gait)
        // 5. Tronco: barriga de pena (pato) + roupa sobre a máscara de ombro→quadril.
        if (bird) {
            CharacterMask.oval(l.hipLeft, l.hipY - 6, l.hipRight, l.torsoBottom + 1).paint(b, Tones.fur(p), style.artProfile.shading)
        }
        val torso = CharacterBodyPainter.torsoMask(l, style.artProfile, motion.breath.coerceIn(0, 1), hemRaise = if (bird) 3 else 0)
        val outfitCtx = OutfitContext(b, style, l, torso)
        outfit.draw(outfitCtx, facing)
        BackAccessoryPainter.drawFront(outfitCtx, style.backAccessory)
        // 6. Braços e mãos.
        val hands = if (facing == Facing.SIDE) {
            val hand = sideHand(pose.rightArm, sideJoint, l, motion.armSwing)
            CharacterBodyPainter.drawSleeve(b, style, outfit, sideJoint.x, sideJoint.y, hand, outward = 1)
            style.species.drawHand(b, p, hand.x, hand.y)
            hand to hand
        } else {
            val aw = style.artProfile.proportions.armWidth
            val leftJoint = Point(l.shoulderLeft + aw / 2, l.shoulderY + 3)
            val rightJoint = Point(l.shoulderRight - aw / 2, l.shoulderY + 3)
            val left = frontHand(pose.leftArm, leftJoint, l, -1, motion.armSwing)
            val right = frontHand(pose.rightArm, rightJoint, l, 1, motion.armSwing)
            CharacterBodyPainter.drawSleeve(b, style, outfit, leftJoint.x, leftJoint.y, left, outward = -1)
            CharacterBodyPainter.drawSleeve(b, style, outfit, rightJoint.x, rightJoint.y, right, outward = 1)
            style.species.drawHand(b, p, left.x, left.y)
            style.species.drawHand(b, p, right.x, right.y)
            left to right
        }
        // 7. Pescoço e cabeça.
        CharacterBodyPainter.drawNeck(b, p, l, style.artProfile)
        style.species.drawHead(HeadContext(b, style, pose, l, earLag = motion.earLag))
        // 8. Item na mão (âncoras de mão).
        if (pose.item != Item.NONE) {
            HoodiePainter.drawItemAt(b, pose.item, if (pose.itemInBothHands) hands.first else hands.second)
            if (pose.itemInBothHands) HoodiePainter.drawItemAt(b, pose.item, hands.second)
        }
        // 9. De costas, a cauda fica por cima.
        if (style.hasTail && facing == Facing.BACK) drawTail(b, style, pose, l, tailSwing)

        val head = Point(l.headCx, (l.headTop + l.headBottom) / 2)
        val mouth = if (facing == Facing.SIDE) Point(l.headRight + 1, l.eyeY + 5) else Point(l.headCx, l.headBottom - 5)
        fun Point.safe() = Point(x.coerceIn(0, WIDTH - 1), y.coerceIn(0, HEIGHT - 1))
        return CharacterFrame(
            b,
            CharacterAnchors(
                feet = CharacterCanvas.FEET, head = head.safe(),
                leftHand = hands.first.safe(), rightHand = hands.second.safe(),
                mouth = mouth.safe(), back = Point(l.cx, l.shoulderY + 8).safe(),
            ),
        )
    }

    // ───── Braços ─────

    /** Mão de frente: [side] −1 esquerda, +1 direita do quadro. */
    private fun frontHand(arm: Arm, joint: Point, l: BodyLayout, side: Int, armSwing: Int): Point {
        val cx = l.cx
        val swing = armSwing.coerceIn(0, 4)
        val p = when (arm) {
            Arm.DOWN -> Point(joint.x + side * 2, l.hipY - 1)
            Arm.SWING_FRONT -> Point(joint.x + side, l.hipY - 2 - swing / 2)
            Arm.SWING_BACK -> Point(joint.x + side * 3, l.hipY - 1)
            Arm.HOLD_CHEST -> Point(cx + side * 4, l.shoulderY + 8)
            Arm.HOLD_MOUTH, Arm.CHIN -> Point(cx + side * 5, l.headBottom - 3)
            Arm.HEAD -> Point(cx + side * 9, l.headTop + 5)
            Arm.FORWARD_UP -> Point(cx + side * 8, l.shoulderY + 6)
            Arm.FORWARD_DOWN -> Point(cx + side * 9, l.shoulderY + 11)
            Arm.UP -> Point(joint.x + side * 2, l.headTop - 1)
            Arm.WAVE -> Point(joint.x + side * 6, l.headTop + 3)
        }
        return Point(p.x.coerceIn(3, WIDTH - 4), p.y.coerceIn(3, HEIGHT - 4))
    }

    /** Mão de perfil (autorado para a direita): + x é para a frente. */
    private fun sideHand(arm: Arm, joint: Point, l: BodyLayout, armSwing: Int): Point {
        val swing = armSwing.coerceIn(0, 4) * 2
        val p = when (arm) {
            Arm.DOWN -> Point(joint.x, l.hipY - 1)
            Arm.SWING_FRONT -> Point(joint.x + swing, l.hipY - 2)
            Arm.SWING_BACK -> Point(joint.x - swing, l.hipY - 1)
            Arm.HOLD_CHEST -> Point(joint.x + 5, l.shoulderY + 9)
            Arm.HOLD_MOUTH, Arm.CHIN -> Point(l.headRight - 4, l.headBottom - 2)
            Arm.HEAD -> Point(joint.x + 3, l.headTop + 4)
            Arm.FORWARD_UP -> Point(joint.x + 9, l.shoulderY + 6)
            Arm.FORWARD_DOWN -> Point(joint.x + 8, l.shoulderY + 12)
            Arm.UP -> Point(joint.x + 2, l.headTop - 1)
            Arm.WAVE -> Point(joint.x + 6, l.headTop + 3)
        }
        return Point(p.x.coerceIn(3, WIDTH - 4), p.y.coerceIn(3, HEIGHT - 4))
    }

    private fun oppositeSwing(arm: Arm) = when (arm) {
        Arm.SWING_FRONT -> Arm.SWING_BACK
        Arm.SWING_BACK -> Arm.SWING_FRONT
        else -> Arm.DOWN
    }

    // ───── Pernas e cauda ─────

    /** Passada padrão (sem sincronia com o mundo): tabela de 8 fases escalada pela espécie. */
    fun defaultGait(pose: CharacterPose, motion: CharacterRenderMotion): GaitSample {
        val walking = pose.legs == Legs.WALK || pose.legs == Legs.RUN_A || pose.legs == Legs.RUN_B
        if (!walking) return GaitSample(1, -1, contact = FootContact.BOTH)
        val phase = pose.stride.mod(8)
        val amp = motion.stepAmplitude.coerceIn(0, 4)
        // Amplitude 1 já abre a passada inteira da tabela; espécies mais elásticas vão além.
        fun scaled(v: Int) = (v * (amp + 2) / 3f).roundToInt()
        return GaitSample(
            nearX = scaled(WALK_SWING[phase]), farX = scaled(WALK_SWING[(phase + 4) % 8]),
            nearLift = scaled(WALK_LIFT[phase]), farLift = scaled(WALK_LIFT[(phase + 4) % 8]),
            contact = if (WALK_LIFT[phase] == 0) FootContact.LEFT else FootContact.RIGHT,
        )
    }

    private fun drawTail(b: PixelBuffer, style: CharacterStyle, pose: CharacterPose, l: BodyLayout, swing: Int) {
        val facing = pose.facing
        // De frente a cauda aparece por trás do quadril, à direita; de perfil sai para trás (esquerda).
        val short = style.species.tailStyle == TailStyle.SHORT || style.species.tailStyle == TailStyle.DUCK
        val (x, dir) = when {
            facing == Facing.SIDE -> l.hipLeft + 2 to -1
            // De costas, o pompom curto fica no meio das costas, sobre o quadril.
            facing == Facing.BACK && short -> l.cx - 3 to 1
            else -> (l.hipRight - 3).coerceAtMost(WIDTH - 2 - tailReach(style.species.tailStyle)) to 1
        }
        style.species.drawTail(TailContext(b, style, pose, x, l.hipY - 3, dir, swing))
    }

    private fun tailReach(style: TailStyle) = when (style) {
        TailStyle.RINGED -> 15
        TailStyle.LONG -> 13
        TailStyle.CAT -> 11
        TailStyle.SHORT -> 7
        TailStyle.DUCK -> 5
        TailStyle.NONE -> 0
    }

    private fun tailSwing(pose: CharacterPose, motion: CharacterRenderMotion): Int {
        val walked = pose.legs == Legs.WALK || pose.legs == Legs.RUN_A || pose.legs == Legs.RUN_B
        val base = motion.tailAmplitude?.let { pose.stringSwing * it / (if (walked) 2 else 1) }
            ?: pose.stringSwing.takeIf { it != 0 }
            ?: if (walked) WALK_SWING[pose.stride.mod(8)].coerceIn(-2, 2) else 0
        return base.coerceIn(-3, 3)
    }

    private val WALK_SWING = intArrayOf(-3, -2, 0, 2, 3, 2, 0, -2)
    /** Um pé permanece em contato durante a passada; o outro completa o arco de swing. */
    private val WALK_LIFT = intArrayOf(0, 1, 2, 1, 0, 0, 0, 0)
}

fun CharacterFrame.mirrored(): CharacterFrame {
    val source = this.image
    val mirroredImage = PixelBuffer(source.width, source.height).also { it.blit(source, 0, 0, flipX = true) }
    fun Point.mirror() = Point(mirroredImage.width - 1 - x, y)
    return copy(
        image = mirroredImage,
        anchors = anchors.copy(
            feet = Point(mirroredImage.width - 1 - anchors.feet.x, anchors.feet.y),
            head = anchors.head.mirror(), leftHand = anchors.rightHand.mirror(), rightHand = anchors.leftHand.mirror(),
            mouth = anchors.mouth.mirror(), back = anchors.back.mirror(),
        ),
    )
}

package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.character.outfit.OutfitPainter
import com.hoodie.app.pixel.character.outfit.OutfitStyle
import com.hoodie.app.pixel.character.species.EarStyle
import com.hoodie.app.pixel.character.species.TailStyle
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Point
import kotlin.math.max
import kotlin.math.roundToInt

/** Painter compartilhado de proporções, olhos, membros e anchors. */
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

        val b = PixelBuffer(WIDTH, HEIGHT)
        val palette = style.palette
        val walked = pose.legs == Legs.WALK || pose.legs == Legs.RUN_A || pose.legs == Legs.RUN_B
        val stride = pose.stride.mod(8)
        val geometry = CharacterGeometry.resolve(style, pose)
        val torsoUp = geometry.torsoOffsetY
        val lift = geometry.lift
        val bodyLeft = geometry.bodyLeft
        val bodyRight = geometry.bodyRight
        val bodyTop = geometry.bodyTop
        val bodyBottom = geometry.bodyBottom
        val headTop = geometry.headTop
        val headWidth = geometry.headWidth
        val headCenterX = CharacterCanvas.CENTER_X + pose.headTilt.coerceIn(-1, 1)
        val tailRootX = if (pose.facing == Facing.SIDE) bodyLeft + 3
        else tailRootX(style.species.tailStyle, bodyRight - 1)

        if (style.hasTail && pose.facing != Facing.FRONT) {
            style.species.drawTail(b, pose, palette, tailRootX, bodyTop + 16, tailAmplitude(pose, motion, walked, stride))
        }
        drawLegs(b, pose, palette, style.outfit, style.scale, style.species, bodyLeft, bodyRight, torsoUp, lift, motion)
        val neckTop = max(headTop + style.species.headHeight - 6, bodyTop - 7)
        CharacterBodyPainter.drawNeck(b, palette, CharacterCanvas.CENTER_X, neckTop, bodyTop + 5)
        OutfitPainter.drawTorso(b, style.outfit, palette, bodyLeft, bodyTop, bodyRight, bodyBottom, pose.facing)
        val hands = drawArms(b, pose, palette, style.species, bodyLeft, bodyRight, bodyTop, headTop, style.scale.armLength, motion.armSwing)
        if (pose.item != Item.NONE) {
            HoodiePainter.drawItemAt(b, pose.item, if (pose.itemInBothHands) hands.first else hands.second)
            if (pose.itemInBothHands) HoodiePainter.drawItemAt(b, pose.item, hands.second)
        }

        style.species.drawEars(b, pose, palette, headCenterX, headTop + if (style.species.earStyle == EarStyle.LONG) 5 else 0, style.scale.headScale)
        style.species.drawHead(b, pose, palette, headCenterX, headTop, style.scale.headScale)
        drawEyes(b, style, pose, headWidth, headCenterX, headTop)
        if (pose.facing != Facing.BACK) style.species.drawMuzzle(b, pose, palette, headCenterX, headTop, style.scale.headScale)
        CharacterMouthPainter.draw(b, style, pose, headTop, headCenterX)
        if (pose.blush && pose.facing != Facing.BACK) {
            b.set(CharacterCanvas.CENTER_X - headWidth / 3, headTop + 17, palette.accent)
            b.set(CharacterCanvas.CENTER_X + headWidth / 3, headTop + 17, palette.accent)
        }
        if (style.hasTail && pose.facing == Facing.FRONT) {
            style.species.drawTail(b, pose, palette, tailRootX, bodyTop + 16, tailAmplitude(pose, motion, walked, stride))
        }

        val head = Point(headCenterX, headTop + (style.species.headHeight / 2) + pose.headDy)
        val mouth = Point(
            if (pose.facing == Facing.SIDE) headCenterX + 11 else headCenterX,
            (headTop + style.species.headHeight - 3 + pose.headDy).coerceIn(0, HEIGHT - 1),
        )
        val safeHead = Point(head.x.coerceIn(0, WIDTH - 1), head.y.coerceIn(0, HEIGHT - 1))
        val safeLeftHand = Point(hands.first.x.coerceIn(0, WIDTH - 1), hands.first.y.coerceIn(0, HEIGHT - 1))
        val safeRightHand = Point(hands.second.x.coerceIn(0, WIDTH - 1), hands.second.y.coerceIn(0, HEIGHT - 1))
        val frame = CharacterFrame(
            b,
            CharacterAnchors(
                feet = CharacterCanvas.FEET, head = safeHead,
                leftHand = safeLeftHand, rightHand = safeRightHand,
                mouth = mouth, back = Point(CharacterCanvas.CENTER_X, (bodyTop + 13).coerceIn(0, HEIGHT - 1)),
            ),
        )
        // A pose lateral segue a mesma orientação canônica do Hoodie: perfil voltado à esquerda.
        return if (pose.facing == Facing.SIDE) frame.mirrored() else frame
    }

    private fun drawLegs(b: PixelBuffer, pose: CharacterPose, p: CharacterPalette, outfit: OutfitStyle, scale: CharacterScale, species: com.hoodie.app.pixel.character.species.SpeciesStyle, left: Int, right: Int, bob: Int, lift: Int, motion: CharacterRenderMotion) {
        val hipY = 53 + bob - lift
        val ground = CharacterCanvas.GROUND_Y - lift
        val len = scale.legLength.coerceIn(9, 16)
        val phase = pose.stride.mod(8)
        val walking = pose.legs == Legs.WALK || pose.legs == Legs.RUN_A || pose.legs == Legs.RUN_B
        val amplitude = motion.stepAmplitude.coerceIn(0, 4)
        val leftSwing = if (pose.legs == Legs.SIT) 5 else if (walking) scaleMotion(WALK_SWING[phase], amplitude) else 0
        val rightSwing = if (pose.legs == Legs.SIT) -5 else if (walking) scaleMotion(WALK_SWING[(phase + 4) % 8], amplitude) else 0
        val leftLift = if (walking) scaleMotion(WALK_LIFT[phase], amplitude) else 0
        val rightLift = if (walking) scaleMotion(WALK_LIFT[(phase + 4) % 8], amplitude) else 0
        val hipSpread = if (pose.facing == Facing.SIDE) max(2, scale.bodyWidth / 10) else max(5, scale.bodyWidth / 5)
        val hipLeft = CharacterCanvas.CENTER_X - hipSpread
        val hipRight = CharacterCanvas.CENTER_X + hipSpread
        val legColors = OutfitPainter.legColors(outfit, p)
        if (pose.facing == Facing.BACK) {
            b.line(hipLeft, hipY, hipLeft + leftSwing, ground - len / 3 - leftLift, p.outline)
            b.line(hipRight, hipY, hipRight + rightSwing, ground - len / 3 - rightLift, p.outline)
            b.line(hipLeft, hipY, hipLeft + leftSwing, ground - len / 3 - leftLift, legColors.front)
            b.line(hipRight, hipY, hipRight + rightSwing, ground - len / 3 - rightLift, legColors.back)
        } else {
            limb(b, hipLeft, hipY, hipLeft + leftSwing, ground - len / 3 - leftLift, legColors.front, p.outline, 4)
            limb(b, hipRight, hipY, hipRight + rightSwing, ground - len / 3 - rightLift, legColors.back, p.outline, 4)
        }
        if (pose.legs == Legs.SIT) {
            species.drawFoot(b, p, hipLeft + leftSwing, ground - species.footContactOffset, farSide = false)
            species.drawFoot(b, p, hipRight + rightSwing, ground - species.footContactOffset, farSide = true)
            OutfitPainter.drawFootwear(b, outfit, p, hipLeft + leftSwing, ground, farSide = false, facing = pose.facing)
            OutfitPainter.drawFootwear(b, outfit, p, hipRight + rightSwing, ground, farSide = true, facing = pose.facing)
        } else {
            species.drawFoot(b, p, hipLeft + leftSwing, ground - leftLift - species.footContactOffset, farSide = false)
            species.drawFoot(b, p, hipRight + rightSwing, ground - rightLift - species.footContactOffset, farSide = true)
            OutfitPainter.drawFootwear(b, outfit, p, hipLeft + leftSwing, ground - leftLift, farSide = false, facing = pose.facing)
            OutfitPainter.drawFootwear(b, outfit, p, hipRight + rightSwing, ground - rightLift, farSide = true, facing = pose.facing)
        }
    }

    private fun drawArms(
        b: PixelBuffer, pose: CharacterPose, p: CharacterPalette,
        species: com.hoodie.app.pixel.character.species.SpeciesStyle,
        bodyLeft: Int, bodyRight: Int, bodyTop: Int, headTop: Int, armLength: Int, armSwing: Int,
    ): Pair<Point, Point> {
        if (pose.facing == Facing.SIDE) {
            val target = armTarget(
                pose.rightArm, CharacterCanvas.CENTER_X + 2, bodyTop, headTop,
                Facing.FRONT, right = true, lift = pose.lift, armLength = armLength, armSwing = armSwing,
            ).copy(x = CharacterCanvas.CENTER_X + 4)
            OutfitPainter.drawSideSleeve(b, p, bodyRight - 2, bodyTop, target)
            species.drawHand(b, p, target.x, target.y)
            // Both hand anchors resolve to the visible near hand in a profile view.
            return target to target
        }
        val left = armTarget(pose.leftArm, CharacterCanvas.CENTER_X - (bodyRight - bodyLeft) / 2 + 2, bodyTop, headTop, pose.facing, false, pose.lift, armLength, armSwing)
        val right = armTarget(pose.rightArm, CharacterCanvas.CENTER_X + (bodyRight - bodyLeft) / 2 - 2, bodyTop, headTop, pose.facing, true, pose.lift, armLength, armSwing)
        OutfitPainter.drawSleeves(b, p, bodyLeft, bodyRight, bodyTop, left, right)
        species.drawHand(b, p, left.x, left.y)
        species.drawHand(b, p, right.x, right.y)
        return Point(left.x, left.y) to Point(right.x, right.y)
    }

    private fun armTarget(arm: Arm, shoulderX: Int, bodyTop: Int, headTop: Int, facing: Facing, right: Boolean, lift: Int, armLength: Int, armSwing: Int): Point {
        val sign = if (right) 1 else -1
        val swing = armSwing.coerceIn(0, 4) * 2
        return when (arm) {
            Arm.UP, Arm.WAVE -> Point(shoulderX + sign * 3, 15 + if (arm == Arm.WAVE) 3 else 0)
            Arm.HOLD_MOUTH, Arm.CHIN, Arm.HEAD -> Point(CharacterCanvas.CENTER_X + sign * 6, headTop + 17)
            Arm.HOLD_CHEST -> Point(CharacterCanvas.CENTER_X + sign * 5, bodyTop + 12)
            Arm.FORWARD_UP -> Point(CharacterCanvas.CENTER_X + sign * 12, bodyTop + 9)
            Arm.FORWARD_DOWN -> Point(CharacterCanvas.CENTER_X + sign * 14, bodyTop + 15)
            Arm.SWING_FRONT -> Point(shoulderX + sign * swing, bodyTop + armLength + 2)
            Arm.SWING_BACK -> Point(shoulderX - sign * swing, bodyTop + armLength + 3)
            Arm.DOWN -> Point(shoulderX + sign * 1, bodyTop + armLength + 2)
        }.let { point ->
            val shifted = point.copy(y = (point.y - lift.coerceIn(0, 8)).coerceAtLeast(2))
            if (facing == Facing.SIDE) shifted.copy(x = (shifted.x + 2).coerceAtMost(45)) else shifted
        }
    }

    private fun drawEyes(b: PixelBuffer, style: CharacterStyle, pose: CharacterPose, headWidth: Int, cx: Int, top: Int) {
        if (pose.facing == Facing.BACK) return
        val y = top + 10 + pose.headDy
        val dx = style.species.eyeSpacing
        val xs = if (pose.facing == Facing.SIDE) listOf(cx + 5) else listOf(cx - dx, cx + dx)
        CharacterEyePainter.drawCharacter(
            buffer = b, eyes = pose.eyes, xs = xs, y = y,
            ink = style.palette.outline, light = style.palette.shirt,
            brow = style.palette.furDark,
            style = style.eyeStyle,
        )
        style.species.drawEyeDecoration(b, style.palette, cx, y, headWidth)
    }

    private fun limb(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, fill: Int, outline: Int, width: Int) {
        CharacterLimbPainter.drawSegment(b, x0, y0, x1, y1, fill, outline, width)
    }

    private fun strideAmplitude(stride: Int) = WALK_SWING[stride].coerceIn(-2, 2)
    private fun tailRootX(style: TailStyle, preferredX: Int): Int {
        // Tail painters can swing up to four pixels beyond their longest fixed reach.
        val maxReach = when (style) {
            TailStyle.CAT -> 14
            TailStyle.LONG, TailStyle.RINGED -> 16
            TailStyle.SHORT -> 10
            TailStyle.DUCK, TailStyle.NONE -> 0
        }
        return minOf(preferredX, WIDTH - 2 - maxReach)
    }

    private fun tailAmplitude(pose: CharacterPose, motion: CharacterRenderMotion, walked: Boolean, stride: Int): Int =
        motion.tailAmplitude?.let {
            (if (walked) pose.stringSwing * it / 2 else pose.stringSwing * it).coerceIn(-4, 4)
        }
            ?: pose.stringSwing.takeIf { it != 0 }
            ?: if (walked) strideAmplitude(stride) else 0
    private fun scaleMotion(value: Int, amplitude: Int) = (value * amplitude / 2f).roundToInt()

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

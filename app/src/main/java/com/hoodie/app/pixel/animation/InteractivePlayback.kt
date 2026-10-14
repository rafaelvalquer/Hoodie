package com.hoodie.app.pixel.animation

import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.sprite.*

/** Temporary overlay on a frozen action frame. Neither prop events nor the action cursor advance. */
internal class InteractivePlayback(
    val response: InteractionResponse,
    val startedAt: Long,
    private val base: SpriteFrame,
    private val basePose: HoodiePose,
    private val baseRequest: SpriteRequest,
    private val provider: SpriteProvider,
) {
    private val holding = basePose.item != Item.NONE || base.itemOverlay != null
    // Existing Aseprite overrides can replace individual free-standing clips. Occupied/seated
    // poses retain the inherited procedural composition until their art supports those poses.
    private val replacements = if (!holding && !basePose.backpack && baseRequest.posture == Posture.STANDING && basePose.facing == Facing.FRONT) {
        response.animations.filter { animation ->
            provider.supports(animation, viewFor(animation, baseRequest.direction).facing) &&
                provider.frame(reactionRequest(animation, 0)).source != ProceduralSpriteProvider.name
        }.toSet()
    } else emptySet()
    private val timings = response.animations.associateWith { animation ->
        if (animation in replacements) provider.durations(animation, baseRequest.direction) else animation.clip.durations
    }
    private val naturalDuration = response.animations.sumOf { ClipTiming.total(timings.getValue(it)) }
    val durationMs = naturalDuration.coerceAtMost(response.maxDurationMs)
    private val cache = mutableMapOf<Pair<AnimationId, Int>, SpriteFrame>()
    var announced = false

    private fun reactionRequest(animation: AnimationId, index: Int) =
        SpriteRequest(animation, baseRequest.direction, index, baseRequest.posture, PoseOverlay.NONE)

    fun finished(now: Long) = now - startedAt >= durationMs

    fun sprite(now: Long): Pair<AnimationId, SpriteFrame> {
        var elapsed = ((now - startedAt).coerceAtLeast(0) * naturalDuration / durationMs).coerceAtMost(naturalDuration - 1)
        val animation = response.animations.first { id ->
            val duration = ClipTiming.total(timings.getValue(id))
            if (elapsed < duration) true else { elapsed -= duration; false }
        }
        val index = ClipTiming.indexAt(timings.getValue(animation), elapsed, false)
        val sprite = cache.getOrPut(animation to index) {
            if (animation in replacements) {
                val replacement = provider.frame(reactionRequest(animation, index))
                return@getOrPut replacement.copy(events = emptySet(), itemOverlay = base.itemOverlay, source = "${replacement.source}+interaction")
            }
            val reaction = animation.frames[index].pose
            val pose = basePose.copy(
                eyes = reaction.eyes, mouth = reaction.mouth, ears = reaction.ears,
                headDy = basePose.headDy + reaction.headDy, blush = reaction.blush,
                bob = basePose.bob + reaction.bob,
                lift = if (baseRequest.posture == Posture.STANDING) basePose.lift + reaction.lift else basePose.lift,
                stringSwing = basePose.stringSwing + reaction.stringSwing,
                // Supporting arms and held objects are untouched; idle arms may wave.
                leftArm = if (holding) basePose.leftArm else reaction.leftArm,
                rightArm = if (holding) basePose.rightArm else reaction.rightArm,
            )
            fun painted(p: HoodiePose): PaintedSprite {
                val painted = HoodiePainter.painted(p)
                val mirror = baseRequest.direction.flipX && p.facing == Facing.SIDE
                if (!mirror) return painted
                val image = com.hoodie.app.pixel.renderer.PixelBuffer(painted.image.width, painted.image.height).also { it.blit(painted.image, 0, 0, flipX = true) }
                return PaintedSprite(image, painted.anchors.mirror(image.width))
            }
            val plain = painted(basePose)
            val reacted = painted(pose)
            val image = CompositeSpriteProvider.overlayPatch(base.image, plain.image, reacted.image)
            fun shifted(point: Point, from: Point, to: Point) = Point(point.x + to.x - from.x, point.y + to.y - from.y)
            val anchors = base.anchors.copy(
                head = shifted(base.anchors.head, plain.anchors.head, reacted.anchors.head),
                rightHand = shifted(base.anchors.rightHand, plain.anchors.rightHand, reacted.anchors.rightHand),
                leftHand = shifted(base.anchors.leftHand, plain.anchors.leftHand, reacted.anchors.leftHand),
                back = shifted(base.anchors.back, plain.anchors.back, reacted.anchors.back),
            )
            base.copy(image = image, anchors = anchors, events = emptySet(), source = "${base.source}+interaction")
        }
        return animation to sprite
    }
}

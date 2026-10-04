package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterRenderMotion
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Ears
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth
import kotlin.math.abs

enum class NpcAnimation { IDLE, WALK, LOOK, TALK, SIT_PHONE, SIT_EAT, SIT_SLEEP, STAND }

data class SpeciesMotionProfile(
    val walkBob: Int,
    val stepAmplitude: Int,
    val headLag: Int,
    val tailAmplitude: Int,
    val armSwing: Int,
) {
    fun renderMotion() = CharacterRenderMotion(
        stepAmplitude = stepAmplitude,
        armSwing = armSwing,
        tailAmplitude = tailAmplitude,
    )
}

data class NpcBehaviorProfile(
    val animation: NpcAnimation,
    val motion: SpeciesMotionProfile,
)

data class NpcSpeechProfile(
    val lines: List<String>,
    val cycleMs: Long = 48_000,
    val visibleMs: Long = 1_500,
)

data class NpcPathPoint(
    val x: Int,
    val floorY: Int,
    val stop: NpcAnimation? = null,
    val holdMs: Long = 0,
)

/** Waypoints percorrem o plano da cena; os pontos de parada podem direcionar fala/olhar. */
data class NpcPath(val points: List<NpcPathPoint>, val millisPerPixel: Long = 35, val repeat: Boolean = true) {
    init { require(points.size >= 2); require(millisPerPixel > 0); require(points.all { it.holdMs >= 0 }) }
}

data class NpcMovement(val x: Int, val floorY: Int, val facingRight: Boolean, val animation: NpcAnimation, val localTimeMs: Long)

object SpeciesMotionProfiles {
    fun forCharacter(style: CharacterStyle): SpeciesMotionProfile = when (style.species.id) {
        "bulldog" -> SpeciesMotionProfile(walkBob = 1, stepAmplitude = 1, headLag = 1, tailAmplitude = 0, armSwing = 1)
        "rabbit" -> SpeciesMotionProfile(walkBob = 2, stepAmplitude = 2, headLag = 0, tailAmplitude = 1, armSwing = 2)
        "mouse" -> SpeciesMotionProfile(walkBob = 1, stepAmplitude = 2, headLag = 1, tailAmplitude = 2, armSwing = 2)
        "duck" -> SpeciesMotionProfile(walkBob = 1, stepAmplitude = 1, headLag = 1, tailAmplitude = 0, armSwing = 1)
        "raccoon" -> SpeciesMotionProfile(walkBob = 1, stepAmplitude = 1, headLag = 1, tailAmplitude = 1, armSwing = 1)
        "dog" -> SpeciesMotionProfile(walkBob = 1, stepAmplitude = 1, headLag = 1, tailAmplitude = 1, armSwing = 1)
        else -> SpeciesMotionProfile(walkBob = 1, stepAmplitude = 2, headLag = 0, tailAmplitude = 2, armSwing = 2)
    }
}

object NpcMotionController {
    fun movement(slot: AmbientNpcSlot, timeMs: Long): NpcMovement {
        val path = slot.path ?: return NpcMovement(
            slot.x, slot.floorY, facingRight = false, slot.definition.behaviorProfile.animation,
            Math.floorMod(timeMs + slot.seed * 733L, 12_000L),
        )
        val duration = path.points.first().holdMs + path.points.zipWithNext()
            .fold(0L) { total, (a, b) -> total + travelTime(a, b, path.millisPerPixel) + b.holdMs }
        val cycle = duration.coerceAtLeast(1L)
        var elapsed = if (path.repeat) Math.floorMod(timeMs + slot.seed * 977L, cycle) else timeMs.coerceIn(0, cycle - 1)
        val firstPoint = path.points.first()
        if (elapsed < firstPoint.holdMs) {
            return NpcMovement(firstPoint.x, firstPoint.floorY, facingRight = true, animation = firstPoint.stop ?: NpcAnimation.IDLE, localTimeMs = elapsed)
        }
        elapsed -= firstPoint.holdMs
        for ((from, to) in path.points.zipWithNext()) {
            val travel = travelTime(from, to, path.millisPerPixel)
            if (elapsed < travel) {
                val t = elapsed.toFloat() / travel
                return NpcMovement(
                    (from.x + (to.x - from.x) * t).toInt(),
                    (from.floorY + (to.floorY - from.floorY) * t).toInt(),
                    facingRight = to.x >= from.x, animation = NpcAnimation.WALK, localTimeMs = elapsed,
                )
            }
            elapsed -= travel
            if (elapsed < to.holdMs) {
                return NpcMovement(to.x, to.floorY, to.x >= from.x, to.stop ?: NpcAnimation.IDLE, elapsed)
            }
            elapsed -= to.holdMs
        }
        val last = path.points.last()
        val first = path.points.first()
        return NpcMovement(last.x, last.floorY, facingRight = first.x >= last.x, animation = last.stop ?: NpcAnimation.IDLE, localTimeMs = elapsed)
    }

    fun pose(animation: NpcAnimation, elapsedMs: Long, seed: Int, motion: SpeciesMotionProfile): CharacterPose {
        val tick = Math.floorMod((elapsedMs / 115L + seed).toInt(), 8)
        val walkOffsets = intArrayOf(-2, -2, -1, 1, 2, 2, 1, -1)
        return when (animation) {
            NpcAnimation.WALK -> walkPose(elapsedMs, seed, motion, walkOffsets)
            NpcAnimation.LOOK -> sampleClip(AnimationId.LOOK_AROUND, elapsedMs, seed, motion)
                .copy(facing = Facing.SIDE, ears = Ears.ALERT)
            NpcAnimation.TALK -> sampleClip(AnimationId.VISIT_CHAT, elapsedMs, seed, motion)
                .copy(legs = Legs.STAND, facing = Facing.SIDE)
            NpcAnimation.SIT_PHONE -> sampleClip(AnimationId.PHONE_SIT, elapsedMs, seed, motion)
                .copy(facing = Facing.SIDE)
            NpcAnimation.SIT_EAT -> sampleClip(AnimationId.EAT, elapsedMs, seed, motion)
                .copy(facing = Facing.SIDE)
            NpcAnimation.SIT_SLEEP -> sampleClip(AnimationId.NAP_SIT, elapsedMs, seed, motion)
                .copy(mouth = Mouth.FLAT, facing = Facing.FRONT)
            NpcAnimation.STAND -> sampleClip(AnimationId.IDLE, elapsedMs, seed, motion)
                .copy(eyes = if (tick == 0) Eyes.CLOSED else Eyes.OPEN, rightArm = Arm.HOLD_CHEST, facing = Facing.FRONT)
            NpcAnimation.IDLE -> sampleClip(AnimationId.IDLE, elapsedMs, seed, motion)
                .copy(eyes = if (shouldBlink(elapsedMs, seed)) Eyes.CLOSED else Eyes.OPEN, mouth = Mouth.SMILE)
        }
    }

    /** Reusa o ciclo e os tempos do Hoodie; só o peso e a amplitude variam por espécie. */
    private fun walkPose(
        elapsedMs: Long,
        seed: Int,
        motion: SpeciesMotionProfile,
        walkOffsets: IntArray,
    ): CharacterPose {
        val base = sampleClip(AnimationId.WALK, elapsedMs, seed, motion, applyWalkBob = true)
        val tick = base.stride.mod(walkOffsets.size)
        // One pixel is the shared gait baseline; values above one add species-specific bounce.
        val gaitBob = WALK_BOB[tick] * (motion.walkBob - 1).coerceAtLeast(0)
        return base.copy(
            // Walk clips may have no vertical offset at all; add an explicit contact/pass bob
            // so species profiles (especially the rabbit's elastic gait) affect the pixels.
            bob = (base.bob + gaitBob).coerceIn(-2, 2),
            eyes = if (tick == 7 && seed.mod(2) == 0) Eyes.LOOK_UP else Eyes.OPEN,
            mouth = Mouth.SMILE,
            facing = Facing.SIDE,
            ears = if (tick.mod(4) == 1) Ears.ALERT else Ears.NORMAL,
            headTilt = if (tick in 1..3) -motion.headLag.coerceAtMost(1) else 0,
            stringSwing = walkOffsets[tick],
        )
    }

    private fun sampleClip(
        animation: AnimationId,
        elapsedMs: Long,
        seed: Int,
        motion: SpeciesMotionProfile,
        applyWalkBob: Boolean = false,
    ): CharacterPose {
        val frames = animation.frames
        val phase = Math.floorMod(seed, frames.size)
        val phaseOffset = frames.take(phase).sumOf { it.durationMs }
        val base = animation.frameAt(elapsedMs + phaseOffset)
        return base.copy(
            bob = if (applyWalkBob) base.bob * motion.walkBob else base.bob,
            headDy = base.headDy * motion.headLag,
        )
    }

    private fun shouldBlink(elapsedMs: Long, seed: Int): Boolean =
        Math.floorMod(elapsedMs + seed * 257L, 3_900L) < 130L

    private val WALK_BOB = intArrayOf(0, 1, -1, 0, 0, 1, -1, 0)

    private fun travelTime(a: NpcPathPoint, b: NpcPathPoint, millisPerPixel: Long) =
        (maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) * millisPerPixel).coerceAtLeast(1)
}

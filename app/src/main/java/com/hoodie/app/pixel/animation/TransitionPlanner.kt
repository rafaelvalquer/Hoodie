package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.animation.AnimationStateMachine.Step
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.pixel.sprite.*
import kotlin.math.abs

/** A computed script; playback state stays in AnimationStateMachine. */
internal data class AnimationSequence(val steps: List<Step>)

/** Pure routing and posture decisions for visual-state and microaction transitions. */
internal class TransitionPlanner {
    private val postureOnly = setOf(AnimationId.SIT_DOWN, AnimationId.STAND_UP, AnimationId.SIT_TABLE, AnimationId.STAND_TABLE)

    fun plan(sc: PixelScene, current: VisualState, target: VisualState, x: Float, y: Float,
             posture: Posture, actionExit: List<AnimationId>, inLoop: Boolean): AnimationSequence {
        val plan = ArrayDeque<Step>()

        val sameScene = target.scene == sc.id
        val targetScene = SceneRegistry[target.scene]
        val targetSpot = targetScene.spot(target.spot)
        val atTarget = sameScene && abs(targetSpot.x - x) < 1 && abs(targetSpot.y - y) < 1
        val sameSpot = atTarget && target.spot == current.spot

        val exits = (if (inLoop) actionExit else emptyList()) +
            current.exit.filter { !(sameSpot && it in postureOnly) }
        exits.forEach { plan += Step.Play(it) }
        var simulatedPosture = posture
        exits.forEach { a -> a.frames.last().pose.legs.let { if (it == Legs.SIT) simulatedPosture = Posture.SITTING else if (it != Legs.INHERIT) simulatedPosture = Posture.STANDING } }
        if (!atTarget && simulatedPosture == Posture.SITTING) plan += Step.Play(AnimationId.STAND_UP)

        if (!sameScene) {
            val door = sc.spots[SpotId.DOOR]
            if (door != null && !sc.walkInPlace) {
                addWalk(plan, x, y, door, stop = false)
                plan += Step.OpenDoor
            }
            plan += Step.FadeOut
            plan += Step.Switch(target)
            plan += Step.FadeIn
            target.approach.forEach { plan += Step.Play(it) }
            if (!targetScene.walkInPlace) {
                val from = targetScene.spots[SpotId.DOOR] ?: targetSpot
                addWalk(plan, from.x.toFloat(), from.y.toFloat(), targetSpot, stop = true)
            }
        } else {
            plan += Step.Apply(target)
            if (!atTarget) {
                target.approach.forEach { plan += Step.Play(it) }
                addWalk(plan, x, y, targetSpot, stop = true)
            }
        }
        target.enter.filter { !(sameSpot && it in postureOnly) }.forEach { plan += Step.Play(it) }
        plan += Step.Loop
        return AnimationSequence(plan.toList())
    }

    fun actionPlan(sc: PixelScene, previous: MicroAction?, next: MicroAction,
                   x: Float, y: Float, posture: Posture): AnimationSequence {
        val plan = ArrayDeque<Step>()
        previous?.exit?.forEach { plan += Step.Play(it) }
        val target = next.spot?.takeIf { it in sc.spots }?.let(sc::spot)
        if (target != null && (abs(target.x - x) >= 1 || abs(target.y - y) >= 1)) {
            if (posture == Posture.SITTING && previous?.exit?.none { it.posture == Legs.STAND || it == AnimationId.STAND_UP } != false) plan += Step.Play(AnimationId.STAND_UP)
            addWalk(plan, x, y, target, stop = true)
        }
        next.enter.forEach { plan += Step.Play(it) }
        plan += Step.Loop
        return AnimationSequence(plan.toList())
    }

    private fun addWalk(plan: ArrayDeque<Step>, fromX: Float, fromY: Float, to: Spot, stop: Boolean) {
        val dx = to.x - fromX; val dy = to.y - fromY
        if (abs(dx) < 1 && abs(dy) < 1) return
        // Descendo anda primeiro em Y (sai da cama/sofá para a frente); senão primeiro em X.
        val first = if (dy > 0) Direction.of(0f, dy) else if (abs(dx) >= 1) Direction.of(dx, 0f) else Direction.of(0f, dy)
        plan += Step.Play(AnimationId.TURN, first)
        plan += Step.Walk(to)
        if (stop) plan += Step.Play(AnimationId.WALK_STOP, Direction.FRONT)
    }
}

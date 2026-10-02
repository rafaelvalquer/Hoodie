package com.hoodie.app.pixel

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationStateMachine.Step
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.pixel.sprite.Posture
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AnimationResolversTest {
    @Test fun playerKeepsLoopClockWhenTurningAndResetsOnlyOnRestartOrClipChange() {
        val player = AnimationPlayer()
        assertTrue(player.play(AnimationId.WALK, com.hoodie.app.pixel.sprite.Direction.FRONT, 100))
        player.lastFrameIndex = 3
        assertTrue(player.play(AnimationId.WALK, com.hoodie.app.pixel.sprite.Direction.BACK, 300))
        assertEquals(100L, player.startedAt)
        assertEquals(3, player.lastFrameIndex)
        assertFalse(player.play(AnimationId.WALK, com.hoodie.app.pixel.sprite.Direction.BACK, 400))
        assertTrue(player.play(AnimationId.WALK, com.hoodie.app.pixel.sprite.Direction.BACK, 500, restart = true))
        assertEquals(500L, player.startedAt)
        assertEquals(-1, player.lastFrameIndex)
        assertEquals(1, player.frameIndex(650, longArrayOf(100, 300)))
        assertTrue(player.play(AnimationId.WAVE, com.hoodie.app.pixel.sprite.Direction.FRONT, 700))
        assertEquals(700L, player.startedAt)
    }

    @Test fun reactionWaitsForBoundaryAndIsConsumedOnce() {
        val resolver = ReactionResolver()
        val animations = mutableListOf(AnimationId.WAVE, AnimationId.HAPPY)
        resolver.request(animations, 500)
        animations.clear()
        assertNull(resolver.takeReady(499))
        assertEquals(listOf(Step.Play(AnimationId.WAVE), Step.Play(AnimationId.HAPPY), Step.Loop), resolver.takeReady(500)!!.steps)
        assertNull(resolver.takeReady(501))
    }

    @Test fun newReactionReplacesQueueAndEmptyRequestKeepsPendingReaction() {
        val resolver = ReactionResolver()
        resolver.request(listOf(AnimationId.WAVE), 50)
        resolver.request(listOf(AnimationId.NOD), 100)
        resolver.request(emptyList(), 0)
        assertNull(resolver.takeReady(50))
        assertEquals(Step.Play(AnimationId.NOD), resolver.takeReady(100)!!.steps.first())
    }

    @Test fun interruptionUsesProviderFrameDurationsAndCycleTolerance() {
        val durations = longArrayOf(100, 300, 200)
        assertEquals(1_400L, InterruptResolver.boundary(AnimationId.PHONE_SCROLL, durations, 1_000, 1_150))
        assertEquals(1_600L, InterruptResolver.boundary(AnimationId.WORK_TYPING, durations, 1_000, 1_150))
        assertEquals(1_150L, InterruptResolver.boundary(AnimationId.IDLE, durations, 1_000, 1_150))
        assertEquals(1_020L, InterruptResolver.loopBoundary(AnimationId.WORK_TYPING, durations, 1_000, 1_020))
        assertEquals(1_600L, InterruptResolver.loopBoundary(AnimationId.WORK_TYPING, durations, 1_000, 1_150))
    }

    @Test fun loopAvoidsRepeatingAndDurationUsesInclusiveConfiguredRange() {
        val first = MicroAction(AnimationId.WORK_TYPING, 1, 10, 20)
        val second = MicroAction(AnimationId.WORK_MOUSE, 2, 30, 30)
        val visual = VisualState(SceneId.OFFICE, SpotId.DESK, listOf(first, second))
        val selector = LoopSelector(Random(7))
        repeat(100) {
            assertEquals(second, selector.pick(visual, first))
            assertEquals(first, selector.pick(visual, second))
            assertTrue(selector.duration(first) in 10L..20L)
            assertEquals(30L, selector.duration(second))
        }
    }

    @Test fun sameDeskTransitionSkipsRedundantPostureAndDoorSteps() {
        val current = VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK)
        val scene = SceneRegistry[current.scene]
        val spot = scene.spot(current.spot)
        val target = current.copy(variant = current.variant + 1)
        val sequence = TransitionPlanner().plan(scene, current, target, spot.x.toFloat(), spot.y.toFloat(), Posture.SITTING, emptyList(), true)
        assertFalse(sequence.steps.any { it is Step.Walk || it == Step.OpenDoor || it == Step.FadeOut })
        assertFalse(sequence.steps.any { it is Step.Play && it.anim in setOf(AnimationId.STAND_UP, AnimationId.SIT_TABLE) })
        assertTrue(sequence.steps.contains(Step.Apply(target)))
        assertEquals(Step.Loop, sequence.steps.last())
    }
}

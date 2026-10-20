package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionGesture
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.renderer.EffectKind
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Segurar o dedo no Hoodie (carinho) solta corações da cabeça; o toque simples não. */
class PettingHeartsTest {
    private val petting = InteractionResponse("pet", listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE), null, 0, 3_000, InteractionGesture.LONG_PRESS)
    private val tap = InteractionResponse("tap", listOf(AnimationId.SMILE), null, 0, 2_500, InteractionGesture.TAP)

    private fun play(response: InteractionResponse): List<RenderFrame> {
        val m = AnimationStateMachine(Random(1))
        m.place(VisualState(SceneId.HOME, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1, 60_000, 60_000))), 1)
        m.frame(2, 600, DayPeriod.DAY)
        assertTrue(m.interact(response, SceneId.HOME, 3))
        return (5L..6_000L step 20).map { m.frame(it, 600, DayPeriod.DAY)!! }
    }

    private fun RenderFrame.hearts() = effects.any { it.first == EffectKind.HEARTS }

    @Test fun pettingShowsHeartsWhileItPlaysAndStopsAfter() {
        val frames = play(petting)
        val busy = frames.indexOfFirst { it.hearts() }
        assertTrue("carinho sem corações", busy >= 0)
        val last = frames.indexOfLast { it.hearts() }
        assertTrue("corações duram a reação", (last - busy) * 20 >= 2_000)
        assertFalse("corações depois do carinho", frames.last().hearts())
    }

    @Test fun aSimpleTapHasNoHearts() {
        assertFalse(play(tap).any { it.hearts() })
    }
}

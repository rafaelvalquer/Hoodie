package com.hoodie.app.engine.interaction

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.ClipTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InteractionSoundPlanTest {
    private fun response(gesture: InteractionGesture, vararg animations: AnimationId, max: Long = 3_000) =
        InteractionResponse("r", animations.toList(), null, 3_500, max, gesture)

    private fun total(a: AnimationId) = ClipTiming.total(a.clip.durations)

    @Test fun `toque toca um pop no inicio e nunca repete a mesma afinacao em seguida`() {
        val taps = (1L..9L).map { id -> InteractionSoundPlan.cues(response(InteractionGesture.TAP, AnimationId.NOTICE, AnimationId.WAVE), id) }
        taps.forEach { assertEquals(1, it.size); assertEquals(0L, it.single().offsetMs) }
        taps.map { it.single().sound }.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        assertEquals(setOf(InteractionSound.TAP_1, InteractionSound.TAP_2, InteractionSound.TAP_3), taps.map { it.single().sound }.toSet())
    }

    @Test fun `carinho toca cada etapa no inicio da sua animacao`() {
        val cues = InteractionSoundPlan.cues(response(InteractionGesture.LONG_PRESS, AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE), 4)
        assertEquals(listOf(0L, total(AnimationId.PET_HEAD), total(AnimationId.PET_HEAD) + total(AnimationId.PURR)), cues.map { it.offsetMs })
        assertTrue(cues[0].sound in setOf(InteractionSound.PET_HEAD_1, InteractionSound.PET_HEAD_2))
        assertEquals(listOf(InteractionSound.PURR, InteractionSound.NUZZLE), cues.drop(1).map { it.sound })
    }

    @Test fun `sequencia encurtada pelo limite acompanha a reproducao`() {
        val natural = total(AnimationId.PET_HEAD) + total(AnimationId.PURR) + total(AnimationId.NUZZLE)
        val cues = InteractionSoundPlan.cues(response(InteractionGesture.LONG_PRESS, AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE, max = natural / 2), 1)
        assertEquals(total(AnimationId.PET_HEAD) / 2, cues[1].offsetMs)
        assertTrue(cues.all { it.offsetMs < natural / 2 })
    }

    @Test fun `so as etapas presentes tocam`() {
        val cues = InteractionSoundPlan.cues(response(InteractionGesture.LONG_PRESS, AnimationId.PET_HEAD, AnimationId.NUZZLE), 2)
        assertEquals(2, cues.size)
        assertTrue(cues.none { it.sound == InteractionSound.PURR })
        assertEquals(emptyList<InteractionSoundCue>(), InteractionSoundPlan.cues(response(InteractionGesture.TAP), 1))
    }

    @Test fun `todo som tem o seu arquivo em res raw`() {
        InteractionSound.entries.forEach { sound ->
            val file = File("src/main/res/raw/hoodie_${sound.name.lowercase()}.wav")
            assertTrue("falta ${file.name}", file.isFile && file.length() > 1_000)
        }
    }
}

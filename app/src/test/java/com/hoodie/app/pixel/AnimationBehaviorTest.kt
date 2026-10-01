package com.hoodie.app.pixel

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.animation.AnimationStateMachine.Phase
import com.hoodie.app.pixel.animation.IdleDirector
import com.hoodie.app.pixel.animation.ReactionDirector
import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Posture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Transições, políticas de interrupção, eventos, idle e reações. */
class AnimationBehaviorTest {

    private class Run(val sm: AnimationStateMachine) {
        var t = 1L
        val frames = mutableListOf<RenderFrame>()
        fun until(ms: Long, stop: (RenderFrame) -> Boolean = { false }): RenderFrame {
            val end = t + ms
            var f = sm.frame(t, 600, DayPeriod.DAY)!!
            while (t < end) {
                t += 33
                f = sm.frame(t, 600, DayPeriod.DAY)!!
                frames += f
                if (stop(f)) break
            }
            return f
        }
        fun anims() = frames.map { it.animation }.distinctConsecutive()
    }


    private fun office() = VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK)

    @Test
    fun `escritorio para rua - levanta, anda, abre a porta, fade e anda de lado`() {
        val sm = AnimationStateMachine(Random(3))
        sm.setVisual(office(), 1)
        val run = Run(sm)
        run.until(2_000)
        assertEquals(Posture.SITTING, sm.posture)
        sm.setVisual(VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, commute = CommuteStyle.WALK), run.t)
        run.until(30_000) { it.scene.id == SceneId.STREET && sm.phase == Phase.LOOP }

        val anims = run.anims()
        val stand = anims.indexOf(AnimationId.STAND_UP)
        val walk = anims.indexOf(AnimationId.WALK)
        assertTrue("levanta antes de andar: $anims", stand in 0 until walk)
        assertTrue("antecipação (TURN) antes de andar: $anims", anims.subList(0, walk).contains(AnimationId.TURN))
        assertTrue("porta abriu", run.frames.any { it.scene.id == SceneId.OFFICE && it.env.doorFrame > 0 })
        assertTrue("fade", run.frames.any { it.fade < 1f })
        val last = run.frames.last()
        assertEquals(SceneId.STREET, last.scene.id)
        assertEquals(AnimationId.WALK_BACKPACK, last.animation)
        assertEquals(Direction.RIGHT, last.direction)
        // Quando anda para os lados, usa a vista lateral (nunca "de frente deslizando").
        run.frames.filter { it.animation == AnimationId.WALK && it.direction in setOf(Direction.LEFT, Direction.RIGHT) }.forEach {
            assertEquals("lado", com.hoodie.app.pixel.sprite.Facing.SIDE, it.direction.facing)
        }
    }

    @Test
    fun `digitar nao e cortado no meio do ciclo (FINISH_CYCLE)`() {
        val v = office().copy(actions = listOf(MicroAction(AnimationId.WORK_TYPING, 1, 60_000, 60_000)))
        val sm = AnimationStateMachine(Random(1))
        sm.setVisual(v, 1)
        val run = Run(sm)
        run.until(300) // no meio do ciclo (ciclo = 960 ms)
        assertEquals(AnimationId.WORK_TYPING, sm.animation)
        sm.setVisual(VisualDirector.resolve(HoodieActivity.COFFEE, UserContextType.WORK), run.t)
        run.until(500)
        assertEquals("ainda termina o ciclo", AnimationId.WORK_TYPING, sm.animation)
        run.until(3_000)
        assertTrue(run.anims().contains(AnimationId.STAND_UP))
    }

    @Test
    fun `acordar toca a sequencia completa (PLAY_EXIT)`() {
        val sm = AnimationStateMachine(Random(2))
        sm.setVisual(VisualDirector.resolve(HoodieActivity.SLEEPING, UserContextType.HOME), 1)
        val run = Run(sm)
        run.until(1_000)
        sm.setVisual(VisualDirector.resolve(HoodieActivity.WAKING_UP, UserContextType.HOME), run.t)
        run.until(15_000) { sm.phase == Phase.LOOP && it.animation != AnimationId.STRETCH && run.anims().contains(AnimationId.STRETCH) }
        val seq = run.anims().filter { it in setOf(AnimationId.WAKE_EYES, AnimationId.BED_SIT, AnimationId.YAWN, AnimationId.BED_EXIT, AnimationId.WALK, AnimationId.STRETCH) }
        assertEquals(listOf(AnimationId.WAKE_EYES, AnimationId.BED_SIT, AnimationId.YAWN, AnimationId.BED_EXIT, AnimationId.WALK, AnimationId.STRETCH), seq)
    }

    @Test
    fun `ir dormir - boceja, anda ate a cama, senta, deita e dorme`() {
        val sm = AnimationStateMachine(Random(5))
        sm.setVisual(VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.HOME), 1)
        val run = Run(sm)
        run.until(500)
        sm.setVisual(VisualDirector.resolve(HoodieActivity.SLEEPING, UserContextType.HOME), run.t)
        run.until(20_000) { it.animation == AnimationId.SLEEP }
        val seq = run.anims().filter { it in setOf(AnimationId.YAWN, AnimationId.WALK, AnimationId.BED_SIT, AnimationId.BED_LIE_DOWN, AnimationId.SLEEP) }
        assertEquals(listOf(AnimationId.YAWN, AnimationId.WALK, AnimationId.BED_SIT, AnimationId.BED_LIE_DOWN, AnimationId.SLEEP), seq)
        assertTrue(SceneFlag.IN_BED in sm.sceneFlags)
    }

    @Test
    fun `cafe e acao completa e a caneca some da mesa via eventos`() {
        val steam = listOf(com.hoodie.app.pixel.scene.EffectSpec(com.hoodie.app.pixel.renderer.EffectKind.STEAM, 2, -8, com.hoodie.app.pixel.sprite.Anchor.RIGHT_HAND))
        val coffee = MicroAction(AnimationId.DRINK, 1, 2_000, 2_000, effects = steam, enter = listOf(AnimationId.REACH_MUG), exit = listOf(AnimationId.PUT_MUG))
        val v = office().copy(actions = listOf(MicroAction(AnimationId.WORK_READ, 1, 1_000, 1_000), coffee))
        val sm = AnimationStateMachine(Random(9))
        sm.setVisual(v, 1)
        val run = Run(sm)
        var sawInHand = false; var sawPutBack = false
        run.until(20_000) { f ->
            if (SceneFlag.MUG_IN_HAND in f.env.flags) sawInHand = true
            if (sawInHand && SceneFlag.MUG_IN_HAND !in f.env.flags) sawPutBack = true
            sawPutBack
        }
        assertTrue(sawInHand); assertTrue(sawPutBack)
        val anims = run.anims()
        val reach = anims.indexOf(AnimationId.REACH_MUG); val drink = anims.indexOf(AnimationId.DRINK); val put = anims.indexOf(AnimationId.PUT_MUG)
        assertTrue("$anims", reach in 0 until drink && drink < put)
        // Vapor só enquanto bebe, ancorado na mão.
        assertTrue(run.frames.any { it.animation == AnimationId.DRINK && it.effects.isNotEmpty() })
    }

    @Test
    fun `restaurante - senta, olha o menu, espera e a comida aparece`() {
        val sm = AnimationStateMachine(Random(4))
        sm.setVisual(office(), 1)
        val run = Run(sm)
        run.until(500)
        sm.setVisual(VisualDirector.resolve(HoodieActivity.EATING, UserContextType.LUNCH), run.t)
        run.until(40_000) { it.scene.id == SceneId.RESTAURANT && sm.phase == Phase.LOOP }
        val anims = run.anims().dropWhile { it != AnimationId.SIT_TABLE }
        assertEquals(listOf(AnimationId.SIT_TABLE, AnimationId.LOOK_MENU, AnimationId.WAIT_FOOD), anims.take(3))
        val served = run.frames.indexOfFirst { SceneFlag.FOOD_SERVED in it.env.flags }
        val waitStart = run.frames.indexOfFirst { it.animation == AnimationId.WAIT_FOOD }
        assertTrue(served > waitStart)
    }

    @Test
    fun `microacoes nao se repetem em sequencia`() {
        val sm = AnimationStateMachine(Random(11))
        sm.setVisual(office(), 1)
        val run = Run(sm)
        val loops = mutableListOf<AnimationId>()
        var last: AnimationId? = null
        run.until(5 * 60_000) { f ->
            if (sm.phase == Phase.LOOP && f.animation != last && f.animation.group != com.hoodie.app.pixel.animation.AnimGroup.POSTURE) {
                last = f.animation; loops += f.animation
            }
            false
        }
        assertTrue(loops.size > 5)
        assertFalse("typing → typing: $loops", loops.zipWithNext().any { (a, b) -> a == b })
    }

    @Test
    fun `reacao entra por cima e a microacao volta`() {
        val v = office().copy(actions = listOf(MicroAction(AnimationId.WORK_READ, 1, 30_000, 30_000)))
        val sm = AnimationStateMachine(Random(1))
        sm.setVisual(v, 1)
        val run = Run(sm)
        run.until(500)
        sm.react(AnimationId.WAVE, run.t)
        run.until(3_000)
        val anims = run.anims()
        assertTrue(anims.indexOf(AnimationId.WAVE) > 0)
        assertEquals(AnimationId.WORK_READ, anims.last())
        // Sentado, o aceno herda a postura.
        assertEquals(Posture.SITTING, sm.posture)
    }

    @Test
    fun `piscadas variam entre 3 e 8 s, com piscadas duplas e sequencia meio-fechado`() {
        val idle = IdleDirector(Random(7))
        idle.reset(0)
        val starts = mutableListOf<Long>()
        val seq = mutableListOf<Eyes?>()
        var prev: Eyes? = null
        var t = 0L
        while (t < 10 * 60_000) {
            val b = idle.blink(t)
            if (prev == null && b != null) starts += t
            if (b != prev) seq += b
            prev = b; t += 10
        }
        val gaps = starts.zipWithNext { a, b -> b - a }
        assertTrue(gaps.any { it < 600 }) // dupla
        val singles = gaps.filter { it > 600 }
        assertTrue(singles.all { it in 3_000..8_400 })
        assertTrue(singles.distinct().size > 10)
        assertEquals(listOf(Eyes.HALF, Eyes.CLOSED, Eyes.HALF, null), seq.take(4))
    }

    @Test
    fun `reacao ao abrir o app segue os pesos`() {
        assertEquals(100, ReactionDirector.WEIGHTS.sum())
        val v = office()
        val rnd = Random(42)
        val n = 20_000
        val none = (1..n).count { ReactionDirector.onAppOpened(v, AnimationId.WORK_TYPING, rnd).isEmpty() }
        assertEquals(0.55, none / n.toDouble(), 0.02)
        assertEquals(listOf(AnimationId.NOTICE, AnimationId.WAVE), ReactionDirector.contextual(AnimationId.WORK_TYPING))
        assertEquals(listOf(AnimationId.GLANCE), ReactionDirector.contextual(AnimationId.GAMING))
        assertTrue(ReactionDirector.onAppOpened(v, AnimationId.SLEEP, rnd).isEmpty())
    }

    @Test
    fun `cafe muda com energia e humor`() {
        fun coffeeOf(energy: Int, mood: Int) = VisualDirector.resolve(HoodieActivity.COFFEE, UserContextType.WORK, energy = energy, mood = mood).actions.first().anim
        assertEquals(AnimationId.COFFEE_TIRED, coffeeOf(15, 70))
        assertEquals(AnimationId.COFFEE_HAPPY, coffeeOf(70, 90))
        assertEquals(AnimationId.DRINK, coffeeOf(70, 60))
    }

    @Test
    fun `cadeira ocupada quando senta na mesa`() {
        val sm = AnimationStateMachine(Random(1))
        sm.setVisual(VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.WORK).copy(spot = SpotId.WINDOW), 1)
        val run = Run(sm)
        run.until(300)
        assertFalse(SceneFlag.CHAIR_OCCUPIED in sm.sceneFlags)
        sm.setVisual(office(), run.t)
        run.until(20_000) { SceneFlag.CHAIR_OCCUPIED in it.env.flags }
        assertTrue(SceneFlag.CHAIR_OCCUPIED in sm.sceneFlags)
    }
}

private fun <T> List<T>.distinctConsecutive(): List<T> = fold(mutableListOf<T>()) { acc, x -> if (acc.lastOrNull() != x) acc += x; acc }

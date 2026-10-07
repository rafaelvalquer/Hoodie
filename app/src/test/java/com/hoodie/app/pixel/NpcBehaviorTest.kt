package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.AmbientScale
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcDirector
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.NpcRenderer
import com.hoodie.app.pixel.npc.NpcReactions
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comportamentos: sequências determinísticas, reações raras, entrar/sair, virar. */
class NpcBehaviorTest {
    /** Ônibus, trem e metrô trazem os passageiros pela própria cena (slots da arte em camadas). */
    private fun npcs(scene: SceneId, variant: Int) =
        if (scene in setOf(SceneId.BUS, SceneId.TRAIN, SceneId.METRO)) SceneRegistry[scene].ambientNpcs(SceneEnv(DayPeriod.DAY, 9 * 60, variant = variant))
        else NpcDirector.plan(scene, variant)

    private val scenes = listOf(SceneId.OFFICE, SceneId.BUS, SceneId.TRAIN, SceneId.METRO, SceneId.RESTAURANT, SceneId.SHOPPING, SceneId.LEISURE)

    @Test fun behaviorIsDeterministicBySceneSeedAndTime() {
        scenes.forEach { scene ->
            (0..1).forEach { variant ->
                npcs(scene, variant).forEach { slot ->
                    (0L..60_000L step 777L).forEach { t ->
                        assertEquals(NpcMotionController.movement(slot, t), NpcMotionController.movement(slot, t))
                        assertEquals(NpcMotionController.frame(slot, t), NpcMotionController.frame(slot, t))
                    }
                }
            }
        }
    }

    @Test fun animationDoesNotFlickerFrameToFrame() {
        scenes.forEach { scene ->
            npcs(scene, 0).forEach { slot ->
                var changes = 0
                var last: NpcAnimation? = null
                for (t in 0L until 30_000L step 33L) {
                    val a = NpcMotionController.movement(slot, t).animation
                    if (last != null && a != last) changes++
                    last = a
                }
                // Em 30 s: no máximo uma troca a cada ~0,3 s em média (sem sorteio por quadro).
                assertTrue("${slot.definition.id} in $scene changed animation $changes times", changes <= 100)
            }
        }
    }

    @Test fun planDescribedBehaviorsAreUsed() {
        // Passageiros dos interiores em camadas, por seed: cada espécie mantém o comportamento descrito.
        val byStyle = (0..40).flatMap { seed ->
            listOf(SceneId.BUS, SceneId.TRAIN, SceneId.METRO).flatMap { scene ->
                SceneRegistry[scene].ambientNpcs(SceneEnv(DayPeriod.DAY, 9 * 60, daySeed = seed))
            }
        }.associateBy { it.definition.characterStyle.id }
        val mouseAnims = (0L..20_000L step 100L).map { NpcMotionController.movement(byStyle.getValue("mouse_commuter"), it).animation }.toSet()
        assertTrue("mouse: phone ↔ window, $mouseAnims", NpcAnimation.SIT_PHONE in mouseAnims && NpcAnimation.LOOK_WINDOW in mouseAnims)
        val duckAnims = (0L..20_000L step 100L).map { NpcMotionController.movement(byStyle.getValue("duck_sleepy"), it).animation }.toSet()
        assertTrue("duck: sleep, head drop, small wake, $duckAnims",
            duckAnims.containsAll(listOf(NpcAnimation.SIT_SLEEP, NpcAnimation.SIT_HEAD_DROP, NpcAnimation.SIT_WAKE)))
        val dogAnims = (0L..60_000L step 100L).map { NpcMotionController.movement(byStyle.getValue("dog_worker"), it).animation }.toSet()
        assertTrue("dog: looks, phone and a short comment, $dogAnims", dogAnims.containsAll(listOf(NpcAnimation.SIT_LOOK, NpcAnimation.SIT_PHONE, NpcAnimation.TALK)))
    }

    @Test fun dialogueIsSparseAndNeverCompetesBetweenNpcs() {
        scenes.forEach { scene ->
            val speakers = npcs(scene, 0).filter { it.definition.speechProfile != null || it.restaurantBrain != null }
            assertTrue("$scene must have at most one speaking NPC", speakers.size <= 1)
        }

        listOf(SceneId.OFFICE, SceneId.BUS, SceneId.RESTAURANT).forEach { scene ->
            val speaker = npcs(scene, 0).single { it.definition.speechProfile != null || it.restaurantBrain != null }
            val talkStarts = mutableListOf<Long>()
            var wasTalking = false
            for (time in 0L..300_000L step 100L) {
                val restaurant = speaker.restaurantBrain
                val talking = if (restaurant != null) restaurant.speechLineAt(time) != null else
                    NpcMotionController.movement(speaker, time).animation == NpcAnimation.TALK && NpcRenderer.shouldSpeak(speaker, time)
                if (talking && !wasTalking) talkStarts += time
                wasTalking = talking
            }
            val minimumConversations = if (speaker.restaurantBrain != null) 1 else 2
            assertTrue("$scene should expose visible short conversations", talkStarts.size >= minimumConversations)
            talkStarts.zipWithNext().forEach { (first, next) ->
                assertTrue("$scene speech cooldown was only ${next - first}ms", next - first >= 40_000L)
            }
        }
    }

    @Test fun executiveEntersWalksLooksTalksWaitsAndExits() {
        val exec = npcs(SceneId.OFFICE, 0).first().copy(seed = 0)
        val timeline = (0L until 10_000L step 50L).map { NpcMotionController.movement(exec, it) }
        val phases = timeline.map { it.phase }.distinct()
        assertEquals(PathPhase.ENTER, phases.first())
        assertTrue("phases $phases", phases.containsAll(listOf(PathPhase.ENTER, PathPhase.STOP, PathPhase.WALK, PathPhase.EXIT)))
        val anims = timeline.map { it.animation }.distinct()
        val order = listOf(NpcAnimation.WALK, NpcAnimation.LOOK, NpcAnimation.TALK)
        assertTrue("order $anims", anims.indexOf(NpcAnimation.LOOK) < anims.indexOf(NpcAnimation.TALK) && anims.containsAll(order))
        assertTrue("waits after talking", timeline.any { it.phase == PathPhase.STOP && (it.animation == NpcAnimation.IDLE || it.animation == NpcAnimation.REACTION) })
    }

    @Test fun walkersTurnInsteadOfFlippingInstantly() {
        val cat = npcs(SceneId.OFFICE, 1).first()
        val anims = (0L until 12_000L step 20L).map { NpcMotionController.movement(cat, it) }
        assertTrue("colleague turns around", anims.any { it.animation == NpcAnimation.TURN_LEFT } && anims.any { it.animation == NpcAnimation.TURN_RIGHT })
        // Toda troca de direção andando passa por um TURN.
        anims.zipWithNext().forEach { (a, b) ->
            if (a.animation == NpcAnimation.WALK && b.animation == NpcAnimation.WALK) assertEquals("direction flip without turn", a.facingRight, b.facingRight)
        }
    }

    @Test fun reactionsAreRareButHappen() {
        var hits = 0; var total = 0
        (0 until 50).forEach { seed ->
            for (t in 0L until 600_000L step 500L) { total++; if (NpcReactions.at(seed, t) != null) hits++ }
        }
        val ratio = hits.toDouble() / total
        assertTrue("reactions should be rare but present ($ratio)", ratio in 0.002..0.03)
    }

    @Test fun depthScalesAreFromTheAllowedSet() {
        scenes.forEach { scene -> (0..1).forEach { v -> npcs(scene, v).forEach { assertTrue(it.scale in AmbientScale.allowed) } } }
        assertEquals(listOf(0.90f, 0.95f, 1.00f), AmbientScale.allowed)
    }

}

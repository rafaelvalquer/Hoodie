package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.leisure.LeisureNpcDirector
import com.hoodie.app.pixel.npc.leisure.LeisureNpcFrameState
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fixed 1.0 fallback and the active 2.0 session have different planning contracts. */
class LeisureNpcDirectorTest {
    private fun env(variant: Int = 0, daySeed: Int = 5, period: DayPeriod = DayPeriod.DAY) =
        SceneEnv(period, clockMinute = 14 * 60, variant = variant, daySeed = daySeed)

    @Test fun parqueTemVisitantesComAcoesDiferentesEmVezDePingPong() {
        for (variant in 0..2) {
            val slots = LeisureNpcDirector.planLegacy(env(variant))
            assertEquals(3, slots.size)
            val explorer = slots.single { it.definition.id == "rabbit_walker" }
            val photographer = slots.single { it.definition.id == "cat_colleague" }
            val reader = slots.single { it.definition.id == "mouse_commuter" }
            assertTrue(explorer.path!!.points.size >= 6)
            assertTrue(photographer.path!!.points.size >= 6)
            assertEquals(null, reader.path)

            val firstActions = (0L..120_000L step 100L).map { NpcMotionController.movement(explorer, it).animation }.toSet()
            val secondActions = (0L..120_000L step 100L).map { NpcMotionController.movement(photographer, it).animation }.toSet()
            val sittingActions = (0L..60_000L step 100L).map { NpcMotionController.movement(reader, it).animation }.toSet()

            assertTrue("$variant explorar: $firstActions", firstActions.containsAll(setOf(
                NpcAnimation.WALK, NpcAnimation.LOOK, NpcAnimation.STRETCH, NpcAnimation.STAND_PHONE, NpcAnimation.STAND_PHOTO,
            )))
            assertTrue("$variant fotografar: $secondActions", secondActions.containsAll(setOf(
                NpcAnimation.WALK, NpcAnimation.STAND_PHOTO, NpcAnimation.STAND_PHONE, NpcAnimation.TALK,
            )))
            assertTrue("$variant banco: $sittingActions", sittingActions.containsAll(setOf(
                NpcAnimation.SIT_LOOK, NpcAnimation.READ_DOCUMENT, NpcAnimation.SIT_DRINK, NpcAnimation.SIT_PHONE,
            )))
            assertTrue((0L..60_000L step 100L).all { NpcMotionController.movement(reader, it).seated })
        }
    }

    @Test fun percursosReiniciamForaDaTelaSemTeleporteVisivel() {
        val moving = LeisureNpcDirector.planLegacy(env()).filter { it.path != null }
        moving.forEach { slot ->
            val path = slot.path!!
            val first = path.points.first().x
            val last = path.points.last().x
            assertTrue("início deve estar fora de tela: $first", first < -48 || first > 288)
            assertTrue("fim deve estar fora de tela: $last", last < -48 || last > 288)
            val frames = (0L..140_000L step 33L).map { NpcMotionController.movement(slot, it) }
            assertTrue(frames.any { it.phase == PathPhase.WALK })
            assertTrue(frames.any { it.phase == PathPhase.STOP })
            frames.zipWithNext().forEach { (a, b) ->
                if (kotlin.math.abs(a.x - b.x) > 8) {
                    assertFalse("salto dentro da tela: $a -> $b", NpcMotionController.visible(a))
                    assertFalse("salto dentro da tela: $a -> $b", NpcMotionController.visible(b))
                }
            }
        }
    }

    @Test fun horariosVariantesESementesSaoPrevisiveis() {
        val day = LeisureNpcDirector.planLegacy(env())
        assertEquals(day, LeisureNpcDirector.planLegacy(env()))
        assertEquals(LeisureNpcDirector.planLegacy(env(variant = 0)), LeisureNpcDirector.planLegacy(env(variant = 3)))
        assertNotEquals(day, LeisureNpcDirector.planLegacy(env(daySeed = 6)))
        assertNotEquals(day, LeisureNpcDirector.planLegacy(env(variant = 1)))
        val night = LeisureNpcDirector.planLegacy(env(period = DayPeriod.NIGHT))
        assertEquals(2, night.size)
        assertFalse(night.any { it.definition.id == "mouse_commuter" })
        assertEquals(1, day.count { it.definition.speechProfile != null })
        assertEquals(1, night.count { it.definition.speechProfile != null })
    }

    @Test fun planoPublico2UsaSessoesIndependentesComEstadosDeterministicos() {
        fun states(environment: SceneEnv, fromScene: Boolean = false): List<List<LeisureNpcFrameState>> {
            val slots = if (fromScene) SceneRegistry[SceneId.LEISURE].ambientNpcs(environment)
                else LeisureNpcDirector.plan(environment)
            assertTrue("2.0 uses actors, not fixed paths", slots.all { it.leisureActor != null && it.path == null })
            return (0L..180_000L step 1_000).map { time -> slots.map { it.leisureActor!!.frameStateAt(time) } }
        }
        val day = states(env())
        assertEquals(3, day.first().size)
        // Each call owns new mutable actors; determinism compares their output, not object identity.
        assertEquals(day, states(env()))
        assertEquals(day, states(env(variant = 3)))
        assertNotEquals(day, states(env(daySeed = 6)))
        assertNotEquals(day, states(env(variant = 1)))
        val night = states(env(period = DayPeriod.NIGHT))
        assertEquals(2, night.first().size)
        assertFalse(night.flatten().any { it.npcId == "mouse_commuter" })
        assertEquals(day, states(env(), fromScene = true))
    }

    @Test fun fotoUsaCelularComPoseDiferenteDeConsultaDeMensagens() {
        val motion = SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.CAT_COLLEAGUE)
        val frames = (0L..3_800L step 100L).map { NpcPoseLibrary.frame(NpcAnimation.STAND_PHOTO, it, 0, motion).pose }
        assertTrue(frames.all { it.legs == Legs.STAND && it.item == Item.PHONE })
        assertTrue(frames.any { it.rightArm == Arm.FORWARD_UP })
        assertTrue(frames.any { it.rightArm == Arm.HOLD_CHEST })
        assertNotEquals(
            NpcPoseLibrary.frame(NpcAnimation.STAND_PHOTO, 1_000L, 0, motion).pose,
            NpcPoseLibrary.frame(NpcAnimation.STAND_PHONE, 1_000L, 0, motion).pose,
        )
    }
}

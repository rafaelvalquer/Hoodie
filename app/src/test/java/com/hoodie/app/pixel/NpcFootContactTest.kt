package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.FootContact
import com.hoodie.app.pixel.npc.AmbientScale
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDirector
import com.hoodie.app.pixel.npc.NpcGait
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pé plantado = não desliza: enquanto um pé está em contato, a posição dele NO MUNDO
 * (x do NPC + offset do pé escalado) não varia além da tolerância de arredondamento.
 */
class NpcFootContactTest {
    private val tolerance = 2

    @Test fun plantedFootStaysStillInTheWorldForEverySpecies() {
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            AmbientScale.allowed.forEach { scale ->
                var plantedNear: Int? = null
                var plantedFar: Int? = null
                var d = 0f
                while (d < 200f) {
                    val sample = NpcGait.sample(d, motion, scale)
                    val s = sample.gait
                    val worldNear = (d + s.nearX * scale).roundToInt()
                    val worldFar = (d + s.farX * scale).roundToInt()
                    if (sample.nearPlanted) {
                        plantedNear?.let { assertTrue("${style.id}@$scale near foot slid ${abs(worldNear - it)}px at d=$d", abs(worldNear - it) <= tolerance) }
                        if (plantedNear == null) plantedNear = worldNear
                    } else plantedNear = null
                    if (sample.farPlanted) {
                        plantedFar?.let { assertTrue("${style.id}@$scale far foot slid ${abs(worldFar - it)}px at d=$d", abs(worldFar - it) <= tolerance) }
                        if (plantedFar == null) plantedFar = worldFar
                    } else plantedFar = null
                    d += 0.5f
                }
            }
        }
    }

    @Test fun walkAlwaysHasAFootOnTheGround() {
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            var d = 0f
            while (d < 100f) {
                val c = NpcGait.sample(d, motion).gait.contact
                assertTrue("${style.id} has no foot planted at d=$d", c != FootContact.NONE)
                val sample = NpcGait.sample(d, motion)
                assertTrue("${style.id} planted foot must touch the ground", if (sample.nearPlanted) sample.gait.nearLift == 0 else sample.gait.farLift == 0)
                d += 0.25f
            }
        }
    }

    @Test fun pathWalkerFeetDoNotSlideAlongTheExecutivePath() {
        val exec = NpcDirector.plan(SceneId.OFFICE, 0).first().copy(seed = 0)
        val motion = exec.definition.behaviorProfile.motion
        var planted: Int? = null
        for (t in 0L until 10_000L step 8L) {
            val m = NpcMotionController.movement(exec, t)
            if (m.animation != NpcAnimation.WALK) { planted = null; continue }
            val sample = NpcGait.sample(m.walkedPx, motion, exec.scale)
            val g = sample.gait
            // Andando para a direita, "frente" é +x no mundo.
            val world = m.x + (g.nearX * exec.scale).roundToInt()
            if (sample.nearPlanted) {
                planted?.let { assertTrue("executive foot slid ${abs(world - it)}px at ${t}ms", abs(world - it) <= tolerance + 1) }
                if (planted == null) planted = world
            } else planted = null
        }
    }
}

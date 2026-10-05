package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Sem saltos impossíveis entre quadros consecutivos (~30 fps): cabeça, mãos, pés e
 * cauda andam no máximo alguns pixels por quadro — nada de pop de cabeça ou mão teleportada.
 */
class NpcMotionContinuityTest {
    private val stepMs = 33L
    private val continuous = listOf(
        NpcAnimation.IDLE, NpcAnimation.WALK, NpcAnimation.LOOK, NpcAnimation.TALK,
        NpcAnimation.SIT_PHONE, NpcAnimation.SIT_SLEEP, NpcAnimation.STAND, NpcAnimation.SIT,
    )

    /** Centro de massa dos pixels de uma faixa (cauda: quadro inteiro abaixo da cabeça). */
    private fun lowestRow(b: PixelBuffer): Int = (b.height - 1 downTo 0).first { y -> (0 until b.width).any { b[it, y] ushr 24 != 0 } }

    @Test fun anchorsMoveSmoothlyBetweenConsecutiveFrames() {
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            continuous.forEach { animation ->
                var prev: com.hoodie.app.pixel.character.CharacterFrame? = null
                var t = 0L
                while (t < 4_000L) {
                    val f = NpcPoseLibrary.frame(animation, t, 3, motion)
                    val frame = CharacterPainter.paint(style, f.pose, f.motion)
                    prev?.let { p ->
                        val head = abs(frame.anchors.head.y - p.anchors.head.y) + abs(frame.anchors.head.x - p.anchors.head.x)
                        assertTrue("${style.id}/$animation head popped ${head}px at ${t}ms", head <= 4)
                        listOf(frame.anchors.leftHand to p.anchors.leftHand, frame.anchors.rightHand to p.anchors.rightHand).forEach { (a, b) ->
                            val jump = abs(a.x - b.x) + abs(a.y - b.y)
                            // Gestos de mão (pegar/levantar) podem trocar de alvo, mas nunca atravessar o corpo.
                            assertTrue("${style.id}/$animation hand teleported ${jump}px at ${t}ms", jump <= 22)
                        }
                        assertTrue("${style.id}/$animation feet anchor moved", frame.anchors.feet == p.anchors.feet)
                        assertTrue("${style.id}/$animation lost ground contact at ${t}ms", lowestRow(frame.image) >= 70)
                    }
                    prev = frame
                    t += stepMs
                }
            }
        }
    }

    @Test fun walkingFeetAdvanceGraduallyWithoutPops() {
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            var prev: Pair<Int, Int>? = null
            var t = 0L
            while (t < 3_000L) {
                val g = NpcPoseLibrary.frame(NpcAnimation.WALK, t, 0, motion).motion.gait!!
                prev?.let { (n, f) ->
                    // Na troca stance→swing o pé pode inverter, mas o deslocamento por quadro é limitado.
                    assertTrue("${style.id} near foot jumped ${abs(g.nearX - n)} at ${t}ms", abs(g.nearX - n) <= 3)
                    assertTrue("${style.id} far foot jumped ${abs(g.farX - f)} at ${t}ms", abs(g.farX - f) <= 3)
                }
                prev = g.nearX to g.farX
                t += stepMs
            }
        }
    }

    @Test fun inspectorFrameStepChangesRenderedWalkPixels() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val motion = SpeciesMotionProfiles.forCharacter(style)
        val before = NpcPoseLibrary.frame(NpcAnimation.WALK, 352L, style.id.hashCode(), motion)
        val after = NpcPoseLibrary.frame(NpcAnimation.WALK, 480L, style.id.hashCode(), motion)
        val beforeImage = CharacterPainter.paint(style, before.pose.copy(facing = Facing.SIDE), before.motion).image
        val afterImage = CharacterPainter.paint(style, after.pose.copy(facing = Facing.SIDE), after.motion).image

        assertTrue("frame stepping must change the rendered walk pose", !beforeImage.pixels.contentEquals(afterImage.pixels))
    }

    @Test fun tailAndEarsFollowThroughWithinOnePixelPerFrame() {
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            var prev: com.hoodie.app.pixel.npc.NpcFrame? = null
            var t = 0L
            while (t < 3_000L) {
                val f = NpcPoseLibrary.frame(NpcAnimation.WALK, t, 0, motion)
                prev?.let { p ->
                    assertTrue("${style.id} tail snapped", abs(f.pose.stringSwing - p.pose.stringSwing) <= 2)
                    assertTrue("${style.id} ears snapped", abs(f.motion.earLag - p.motion.earLag) <= 3)
                }
                prev = f
                t += stepMs
            }
        }
    }
}

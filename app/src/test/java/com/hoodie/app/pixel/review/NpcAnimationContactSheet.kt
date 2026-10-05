package com.hoodie.app.pixel.review

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcGait
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.NpcRenderer
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.review.VisualReviewPose
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs

object NpcAnimationContactSheet {
    val phaseLabels = listOf("CONTACT", "DOWN", "PASS", "UP", "CONTACT", "DOWN", "PASS", "UP")
    val species: List<CharacterStyle> get() = NpcVisualReviewFrames.reviewSpecies

    fun walk(style: CharacterStyle, debug: Boolean): PixelBuffer {
        val motion = SpeciesMotionProfiles.forCharacter(style)
        val out = PixelBuffer(8 * 52 + 2, 92).also { it.fill(ReviewRaster.BG) }
        phaseLabels.forEachIndexed { i, label -> ReviewRaster.text(out, label, 2 + i * 52, 2) }
        val groundY = 82
        out.hline(1, out.width - 2, groundY, ReviewRaster.CYAN)
        (0 until 8).forEach { i ->
            val walked = motion.stepLength * (i / 4f)
            val data = NpcPoseLibrary.frame(NpcAnimation.WALK, 0, 0, motion, walkedPx = walked, scale = 1f)
            val frame = CharacterPainter.paint(style, data.pose, data.motion)
            val x = 2 + i * 52
            out.blit(frame.image, x, 9)
            if (debug) {
                val gait = NpcGait.sample(walked, motion, 1f).gait
                out.vline(x + 24, 9, groundY, ReviewRaster.PINK)
                out.box(x + 24 + gait.nearX - 1, groundY - gait.nearLift - 1, x + 24 + gait.nearX + 1, groundY - gait.nearLift + 1, ReviewRaster.GOLD)
                out.box(x + 24 + gait.farX - 1, groundY - gait.farLift - 1, x + 24 + gait.farX + 1, groundY - gait.farLift + 1, 0xFF84DA78.toInt())
            }
        }
        ReviewRaster.text(out, if (debug) "GROUND CENTER NEAR FAR" else "WALK CLEAN", 2, 88, scale = 1)
        return out
    }

    fun turn(style: CharacterStyle): PixelBuffer {
        // Amostra a aproximação, o limite, o centro frontal e o retorno comprimido.
        val times = listOf(0L, 80L, 119L, 120L, 180L, 239L, 240L, 280L, 359L)
        val labels = listOf("SIDE", "TURN IN", "BEFORE", "FRONT IN", "FRONT", "BEFORE", "SIDE IN", "TURN OUT", "SIDE")
        return timeline(style, NpcAnimation.TURN_LEFT, times, labels)
    }

    fun sit(style: CharacterStyle): PixelBuffer = timeline(
        style, NpcAnimation.SIT, listOf(0, 160, 320, 480, 640).map(Int::toLong),
        listOf("STAND", "BEND", "LOWER", "CONTACT", "SIT"),
    )

    fun talk(style: CharacterStyle): PixelBuffer = timeline(
        style, NpcAnimation.TALK, listOf(0, 200, 700, 1_100, 1_800, 2_200).map(Int::toLong),
        listOf("OPEN", "CLOSED", "NOD", "GESTURE", "SMILE", "NEUTRAL"),
    )

    private fun timeline(style: CharacterStyle, animation: NpcAnimation, times: List<Long>, labels: List<String>): PixelBuffer {
        val motion = SpeciesMotionProfiles.forCharacter(style)
        val out = PixelBuffer(times.size * 52 + 2, 92).also { it.fill(ReviewRaster.BG) }
        times.forEachIndexed { i, time ->
            ReviewRaster.text(out, labels[i], 2 + i * 52, 2)
            val data = NpcPoseLibrary.frame(animation, time, 0, motion)
            val facing = when (animation) {
                NpcAnimation.TURN_LEFT, NpcAnimation.TURN_RIGHT -> data.pose.facing
                else -> Facing.FRONT
            }
            val painted = CharacterPainter.paint(style, data.pose.copy(facing = facing), data.motion)
            val frame = if (animation == NpcAnimation.TURN_LEFT || animation == NpcAnimation.TURN_RIGHT) {
                NpcRenderer.turnPerspective(painted, style, time)
            } else painted
            out.blit(frame.image, 2 + i * 52, 9)
            out.hline(2 + i * 52, 49 + i * 52, 82, ReviewRaster.CYAN)
        }
        return out
    }
}

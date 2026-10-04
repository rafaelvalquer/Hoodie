package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs
import org.junit.Test

/** Folha de revisão V3: Hoodie × NPC em frente, perfil, costas, sentado e passada. */
class NpcArtV3SheetTest {
    private val bg = 0xFF2B2E4A.toInt()

    private fun cell(style: CharacterStyle, pose: CharacterPose): PixelBuffer =
        CharacterPainter.paint(style, pose, SpeciesMotionProfiles.forCharacter(style).renderMotion()).image

    @Test fun exportReviewSheet() {
        val poses = listOf(
            CharacterPose(),
            CharacterPose(facing = Facing.SIDE),
            CharacterPose(facing = Facing.BACK),
            CharacterPose(facing = Facing.SIDE, legs = Legs.SIT),
        ) + (0 until 8 step 2).map {
            CharacterPose(facing = Facing.SIDE, legs = Legs.WALK, stride = it,
                leftArm = if (it < 4) Arm.SWING_BACK else Arm.SWING_FRONT, rightArm = if (it < 4) Arm.SWING_FRONT else Arm.SWING_BACK)
        }
        val styles = listOf(CharacterStyle.HOODIE) + NpcCharacterRegistry.all
        val cols = poses.size
        val sheet = PixelBuffer(cols * 50 + 2, styles.size * 74 + 2).also { it.fill(bg) }
        styles.forEachIndexed { row, style ->
            val strip = PixelBuffer(cols * 50 + 2, 76).also { it.fill(bg) }
            poses.forEachIndexed { col, pose ->
                val img = cell(style, pose)
                sheet.blit(img, 2 + col * 50, 2 + row * 74)
                strip.blit(img, 2 + col * 50, 2)
            }
            PreviewExport.save("npc-v3/row-${style.id}", strip, scale = 5)
        }
        PreviewExport.save("npc-v3/review-sheet", sheet, scale = 3)
    }
}

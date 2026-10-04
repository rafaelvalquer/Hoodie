package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing

/** Fonte única para a imagem que o humano revisa e que o digest SHA protege. */
internal object NpcArtReviewFixture {
    val characterIds = listOf(
        "bulldog_exec", "dog_worker", "rabbit_analyst", "mouse_commuter",
        "duck_sleepy", "raccoon_window", "cat_colleague",
    )
    val additionalIdentityIds = listOf("dog_shopper", "rabbit_reader", "rabbit_walker", "cat_guest")

    fun idlePose(style: CharacterStyle) = NpcMotionController.pose(
        NpcAnimation.IDLE, elapsedMs = 400, seed = 0,
        motion = SpeciesMotionProfiles.forCharacter(style),
    ).copy(facing = Facing.FRONT)

    fun comparison(style: CharacterStyle, pose: CharacterPose): PixelBuffer {
        val hoodie = CharacterPainter.paint(CharacterStyle.HOODIE, pose)
        val npc = CharacterPainter.paint(style, pose, SpeciesMotionProfiles.forCharacter(style).renderMotion())
        return PixelBuffer(100, 72).also { image ->
            image.fill(BACKGROUND)
            image.blit(hoodie.image, 1, 0)
            image.blit(npc.image, 53, 0)
        }
    }

    fun idleComparison(style: CharacterStyle) = comparison(style, idlePose(style))

    private const val BACKGROUND = 0xFF2B2E4A.toInt()
}

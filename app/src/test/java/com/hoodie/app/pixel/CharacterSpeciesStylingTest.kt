package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterGeometry
import com.hoodie.app.pixel.character.EyeStyle
import com.hoodie.app.pixel.character.species.BulldogSpecies
import com.hoodie.app.pixel.character.species.CatSpecies
import com.hoodie.app.pixel.character.species.DogSpecies
import com.hoodie.app.pixel.character.species.RaccoonSpecies
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSpeciesStylingTest {
    @Test fun urbanMammalHeadsUseTheHoodieRoundedRectangleWhileOtherSpeciesKeepTheirSilhouette() {
        val rectangular = listOf(
            NpcCharacterRegistry.BULLDOG_EXEC,
            NpcCharacterRegistry.DOG_WORKER,
            NpcCharacterRegistry.CAT_COLLEAGUE,
            NpcCharacterRegistry.RACCOON_COMMUTER,
        )
        rectangular.forEach { style ->
            val head = PixelBuffer(48, 72)
            style.species.drawHead(head, CharacterPose(), style.palette, 24, 10, style.scale.headScale)
            val topWidth = (0 until head.width).count { head[it, 10] != 0 }
            val middleWidth = (0 until head.width).count { head[it, 22] != 0 }
            assertTrue("${style.id} should have a broad, chamfered forehead ($topWidth px)", topWidth >= 20)
            assertTrue("${style.id} forehead should be narrower than its broad center", topWidth < middleWidth)
            assertTrue("${style.id} center should approach Hoodie head width ($middleWidth px)", middleWidth >= 29)
        }

        val duck = PixelBuffer(48, 72)
        NpcCharacterRegistry.DUCK_SLEEPY.species.drawHead(
            duck, CharacterPose(), NpcCharacterRegistry.DUCK_SLEEPY.palette, 24, 10,
            NpcCharacterRegistry.DUCK_SLEEPY.scale.headScale,
        )
        val duckTop = (0 until duck.width).count { duck[it, 10] != 0 }
        val duckMiddle = (0 until duck.width).count { duck[it, 24] != 0 }
        assertTrue("Duck should keep its rounded head and distinct bill silhouette", duckTop < duckMiddle / 2)
        assertEquals(6, DogSpecies.eyeSpacing)
        assertEquals(6, CatSpecies.eyeSpacing)
    }

    @Test fun bulldogFaceUsesItsDeclaredPixelWidthAfterScaling() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val buffer = PixelBuffer(48, 72)
        BulldogSpecies.drawHead(buffer, CharacterPose(), style.palette, centerX = 24, topY = 2, scale = style.scale.headScale)
        val row = 17
        val occupiedX = (0 until buffer.width).filter { buffer.pixels[row * buffer.width + it] != 0 }

        val faceWidth = occupiedX.last() - occupiedX.first() + 1
        assertTrue("Bulldog face should stay close to the Hoodie’s 34px head width ($faceWidth px including outline)", faceWidth in 34..36)
        assertEquals(33, CharacterGeometry.resolve(style, CharacterPose()).headWidth)
    }

    @Test fun bulldogEyeTreatmentComesFromItsStyleAndCanBeOverriddenInTheLab() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        assertEquals(EyeStyle.HEAVY, style.eyeStyle)

        val heavy = CharacterPainter.paint(style, CharacterPose()).image.pixels
        val soft = CharacterPainter.paint(style.copy(eyeStyle = EyeStyle.SOFT), CharacterPose()).image.pixels

        assertFalse("changing eye style should override Bulldog's default without a species-id check", heavy.contentEquals(soft))
    }

    @Test fun speciesProvidesEyeSpacingAndRaccoonMaskDecoration() {
        assertEquals(6, com.hoodie.app.pixel.character.species.BulldogSpecies.eyeSpacing)
        assertEquals(6, CatSpecies.eyeSpacing)

        val raccoonMask = PixelBuffer(48, 72)
        RaccoonSpecies.drawEyeDecoration(raccoonMask, NpcCharacterRegistry.RACCOON_COMMUTER.palette, 24, 20, 29)
        assertTrue(raccoonMask.pixels[22 * 48 + 13] != 0)
        assertTrue(raccoonMask.pixels[22 * 48 + 35] != 0)
    }
}

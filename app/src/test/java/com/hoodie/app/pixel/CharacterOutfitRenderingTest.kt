package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.character.outfit.OutfitStyle
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterOutfitRenderingTest {
    @Test fun hoodieAndCasualOutfitsHaveDifferentConstructionDetails() {
        val cat = NpcCharacterRegistry.CAT_COLLEAGUE
        val hoodie = CharacterPainter.paint(cat.copy(outfit = OutfitStyle.Hoodie), CharacterPose()).image.pixels
        val casual = CharacterPainter.paint(cat.copy(outfit = OutfitStyle.Casual), CharacterPose()).image.pixels

        assertFalse("Hoodie outfit must have its own hood, cords and pocket", hoodie.contentEquals(casual))
    }

    @Test fun executiveSuitContinuesIntoMatchingTrouserLegs() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val frame = CharacterPainter.paint(executive, CharacterPose())

        assertEquals(executive.palette.outfit, frame.image.pixels[60 * CharacterPainter.WIDTH + 18])
    }

    @Test fun executiveSuitAddsPolishedShoesOverTheSharedPaws() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val frame = CharacterPainter.paint(executive, CharacterPose())
        val shoeHighlights = (67..70).sumOf { y ->
            (0 until frame.image.width).count { x -> frame.image.pixels[y * frame.image.width + x] == executive.palette.outfitLight }
        }

        assertEquals("both shoes should catch a restrained suit highlight", 2, shoeHighlights)
        val groundSolePixels = (0 until frame.image.width).count {
            frame.image.pixels[CharacterPainter.FEET.y * frame.image.width + it] == executive.palette.outline
        }
        org.junit.Assert.assertTrue("shoe soles should meet the shared ground line", groundSolePixels >= 10)
    }

    @Test fun polishedSuitShoesStayVisibleThroughEveryWalkingPhaseAndView() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(executive)
        val style = executive.copy(outfit = OutfitStyle.Suit)
        val motion = profile.renderMotion()

        Facing.entries.forEach { facing -> (0..7).forEach { stride ->
            val pose = CharacterPose(legs = Legs.WALK, stride = stride, facing = facing)
            val frame = CharacterPainter.paint(style, pose, motion).image
            val shoeHighlights = (64 until frame.height).sumOf { y ->
                (0 until frame.width).count { x -> frame.pixels[y * frame.width + x] == style.palette.outfitLight }
            }

            org.junit.Assert.assertTrue("shoe highlight should remain readable in $facing stride $stride", shoeHighlights >= 1)
        } }
    }

    @Test fun executiveSuitTieNarrowsFromTheKnotIntoAPixelPoint() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val frame = CharacterPainter.paint(executive, CharacterPose())
        val centerX = 23

        assertEquals(executive.palette.accent, frame.image.pixels[37 * CharacterPainter.WIDTH + centerX])
        assertEquals(executive.palette.accent, frame.image.pixels[50 * CharacterPainter.WIDTH + centerX - 1])
        assertEquals(executive.palette.accent, frame.image.pixels[51 * CharacterPainter.WIDTH + centerX])
    }

    @Test fun executiveSuitBackShowsTheJacketSeamWithoutTheFrontTieOrShirt() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val front = CharacterPainter.paint(executive, CharacterPose(facing = Facing.FRONT)).image
        val back = CharacterPainter.paint(executive, CharacterPose(facing = Facing.BACK)).image
        val torsoPixels = (30..52).flatMap { y ->
            (10..37).map { x -> back.pixels[y * CharacterPainter.WIDTH + x] }
        }

        assertFalse("the back view must not show the red tie", torsoPixels.contains(executive.palette.accent))
        assertFalse("the back view must not show the shirt panel", torsoPixels.contains(executive.palette.shirt))
        assertEquals("the tie remains visible from the front", executive.palette.accent, front.pixels[37 * CharacterPainter.WIDTH + 23])
        assertTrue("the jacket back keeps its center seam", torsoPixels.contains(executive.palette.outfitDark))
    }

    @Test fun rearFacingOutfitsDoNotLeakTheFrontShirtPanel() {
        val cat = NpcCharacterRegistry.CAT_COLLEAGUE

        listOf(
            OutfitStyle.Hoodie, OutfitStyle.Suit, OutfitStyle.Casual,
            OutfitStyle.Student, OutfitStyle.Sport, OutfitStyle.Commuter,
        ).forEach { outfit ->
            val style = cat.copy(outfit = outfit)
            val back = CharacterPainter.paint(style, CharacterPose(facing = Facing.BACK)).image
            val shirtInTorso = (30..52).sumOf { y ->
                (10..37).count { x -> back.pixels[y * CharacterPainter.WIDTH + x] == style.palette.shirt }
            }

            assertEquals("$outfit rear view should draw back fabric instead of a front shirt", 0, shirtInTorso)
        }
    }

    @Test fun sideFacingExecutiveShowsTheSuitProfileWithoutTheFrontTieOrShirt() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val side = CharacterPainter.paint(executive, CharacterPose(facing = Facing.SIDE)).image
        val torsoPixels = (30..52).flatMap { y ->
            (10..37).map { x -> side.pixels[y * CharacterPainter.WIDTH + x] }
        }

        assertFalse("profile view should not show the shirt panel", torsoPixels.contains(executive.palette.shirt))
        assertFalse("profile view should not show the full tie", torsoPixels.contains(executive.palette.accent))
        assertTrue("profile view keeps a suit highlight", torsoPixels.contains(executive.palette.outfitLight))
    }
}

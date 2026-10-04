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
        val l = com.hoodie.app.pixel.character.BodyLayout.resolve(executive, CharacterPose())
        val legRows = (l.torsoBottom + 1)..(l.ankleY - 1)
        val trouser = setOf(executive.palette.outfitLight, executive.palette.outfit, executive.palette.outfitDark)
        // As duas pernas abaixo do paletó são calça do terno, não pelo.
        listOf(l.hipLeft + 3, l.hipRight - 3).forEach { x ->
            assertTrue("trouser at x=$x", legRows.any { y -> frame.image[x, y] in trouser })
        }
    }

    @Test fun executiveSuitAddsPolishedShoesOverTheSharedPaws() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val frame = CharacterPainter.paint(executive, CharacterPose())
        val shoeHighlights = (67..70).sumOf { y ->
            (0 until frame.image.width).count { x -> frame.image.pixels[y * frame.image.width + x] == executive.palette.outfitLight }
        }

        assertTrue("both shoes should catch a restrained suit highlight ($shoeHighlights)", shoeHighlights in 2..6)
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
        val frame = CharacterPainter.paint(executive, CharacterPose()).image
        val accent = executive.palette.accent
        val rows = (0 until frame.height).map { y -> (0 until frame.width).count { frame[it, y] == accent } }
        val tieRows = rows.indices.filter { rows[it] > 0 }
        assertTrue("tie visible below the chin", tieRows.size >= 6)
        // Nó estreito, lâmina mais larga e ponta de um pixel.
        assertEquals("tie ends in a single pixel point", 1, rows[tieRows.last()])
        assertTrue("tie blade widens below the knot", rows[tieRows[tieRows.size / 2]] >= rows[tieRows.first()])
    }

    @Test fun executiveSuitBackShowsTheJacketSeamWithoutTheFrontTieOrShirt() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val front = CharacterPainter.paint(executive, CharacterPose(facing = Facing.FRONT)).image
        val back = CharacterPainter.paint(executive, CharacterPose(facing = Facing.BACK)).image
        val l = com.hoodie.app.pixel.character.BodyLayout.resolve(executive, CharacterPose(facing = Facing.BACK))
        val torsoPixels = (l.shoulderY + 1..l.torsoBottom).flatMap { y ->
            (l.shoulderLeft..l.shoulderRight).map { x -> back.pixels[y * CharacterPainter.WIDTH + x] }
        }

        assertFalse("the back view must not show the red tie", torsoPixels.contains(executive.palette.accent))
        assertFalse("the back view must not show the shirt panel", torsoPixels.contains(executive.palette.shirt))
        assertTrue("the tie remains visible from the front", front.pixels.contains(executive.palette.accent))
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
            val l = com.hoodie.app.pixel.character.BodyLayout.resolve(style, CharacterPose(facing = Facing.BACK))
            // Gola branca no topo das costas é tecido de trás; o painel frontal fica abaixo dela.
            val shirtInTorso = (l.shoulderY + 3..l.torsoBottom).sumOf { y ->
                (l.shoulderLeft..l.shoulderRight).count { x -> back.pixels[y * CharacterPainter.WIDTH + x] == style.palette.shirt }
            }

            assertEquals("$outfit rear view should draw back fabric instead of a front shirt", 0, shirtInTorso)
        }
    }

    @Test fun sideFacingExecutiveShowsTheSuitProfileWithoutTheFrontTieOrShirt() {
        val executive = NpcCharacterRegistry.BULLDOG_EXEC
        val side = CharacterPainter.paint(executive, CharacterPose(facing = Facing.SIDE)).image
        val front = CharacterPainter.paint(executive, CharacterPose(facing = Facing.FRONT)).image
        fun count(img: com.hoodie.app.pixel.renderer.PixelBuffer, c: Int) = img.pixels.count { it == c }

        // De perfil só aparece um filete de camisa/gravata na frente do paletó, não o painel.
        assertTrue("profile view should not show the shirt panel", count(side, executive.palette.shirt) * 3 < count(front, executive.palette.shirt))
        assertTrue("profile view should not show the full tie", count(side, executive.palette.accent) * 2 < count(front, executive.palette.accent))
        assertTrue("profile view keeps a suit highlight", side.pixels.contains(executive.palette.outfitLight))
    }
}

package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.BodyLayout
import com.hoodie.app.pixel.character.CharacterGeometry
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.EyeStyle
import com.hoodie.app.pixel.character.ProportionProfile
import com.hoodie.app.pixel.character.species.BulldogSpecies
import com.hoodie.app.pixel.character.species.CatSpecies
import com.hoodie.app.pixel.character.species.DogSpecies
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Silhuetas V3 por espécie: proporção vem do ArtProfile, nunca de `if (espécie)` no painter. */
class CharacterSpeciesStylingTest {
    private fun front(style: CharacterStyle): PixelBuffer = CharacterPainter.paint(style, CharacterPose()).image
    private fun rowWidth(b: PixelBuffer, y: Int): Int {
        val xs = (0 until b.width).filter { b[it, y] ushr 24 != 0 }
        return if (xs.isEmpty()) 0 else xs.last() - xs.first() + 1
    }

    @Test fun headWidthFollowsTheSpeciesProportionProfile() {
        NpcCharacterRegistry.all.forEach { style ->
            val layout = BodyLayout.resolve(style, CharacterPose())
            assertEquals("${style.id} head width", style.artProfile.proportions.headWidth, layout.headWidth)
            assertEquals(layout.headWidth, CharacterGeometry.resolve(style, CharacterPose()).headWidth)
        }
        // Bulldog é a cabeça mais larga; o cachorro comum é claramente mais estreito.
        assertTrue(BulldogSpecies.headWidth > DogSpecies.headWidth + 6)
        assertEquals(ProportionProfile.BULLDOG, BulldogSpecies.artProfile.proportions)
    }

    @Test fun bulldogHeadIsWiderThanItsShouldersAndHoodieSizedCatIsBalanced() {
        val bulldog = front(NpcCharacterRegistry.BULLDOG_EXEC)
        val l = BodyLayout.resolve(NpcCharacterRegistry.BULLDOG_EXEC, CharacterPose())
        val headRow = rowWidth(bulldog, l.eyeY + 4)
        val chestRow = rowWidth(bulldog, l.shoulderY + 8)
        assertTrue("Bulldog jowls ($headRow) should be wider than the chest ($chestRow)", headRow > chestRow)
        assertTrue("Bulldog head should span at least 36px ($headRow)", headRow >= 36)

        val cat = front(NpcCharacterRegistry.CAT_COLLEAGUE)
        val cl = BodyLayout.resolve(NpcCharacterRegistry.CAT_COLLEAGUE, CharacterPose())
        assertTrue("Cat head close to the Hoodie's 34px (${rowWidth(cat, cl.eyeY)})", rowWidth(cat, cl.eyeY) in 30..36)
    }

    @Test fun torsoHasShouldersAndTapersToTheHipInsteadOfBeingABox() {
        NpcCharacterRegistry.all.filter { it.species.id != "duck" }.forEach { style ->
            val pr = style.artProfile.proportions
            assertTrue("${style.id} shoulders should be at least as wide as hips", pr.shoulderWidth >= pr.hipWidth)
            val l = BodyLayout.resolve(style, CharacterPose())
            assertTrue("${style.id} hip below shoulders", l.hipY > l.shoulderY + 10)
            assertTrue("${style.id} head overlaps the collar (no long neck)", l.headBottom >= l.shoulderY)
        }
    }

    @Test fun duckKeepsARoundHeadDistinctFromTheMammals() {
        val duck = front(NpcCharacterRegistry.DUCK_SLEEPY)
        val l = BodyLayout.resolve(NpcCharacterRegistry.DUCK_SLEEPY, CharacterPose())
        val top = rowWidth(duck, l.headTop + 1)
        val middle = rowWidth(duck, (l.headTop + l.headBottom) / 2)
        assertTrue("Duck crown ($top) should be much narrower than its middle ($middle)", top * 3 < middle * 2)
    }

    @Test fun eyeStyleComesFromTheCharacterAndCanBeOverriddenInTheLab() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        assertEquals(EyeStyle.HEAVY, style.eyeStyle)
        val heavy = CharacterPainter.paint(style, CharacterPose()).image.pixels
        val soft = CharacterPainter.paint(style.copy(eyeStyle = EyeStyle.SOFT), CharacterPose()).image.pixels
        assertFalse("changing eye style should override Bulldog's default without a species-id check", heavy.contentEquals(soft))
        assertEquals(8, BulldogSpecies.eyeSpacing)
        assertEquals(7, CatSpecies.eyeSpacing)
    }

    @Test fun raccoonWearsADarkMaskAroundBothEyes() {
        val style = NpcCharacterRegistry.RACCOON_COMMUTER
        val b = front(style)
        val l = BodyLayout.resolve(style, CharacterPose())
        val dark = style.palette.furDark
        listOf(l.headCx - 10, l.headCx + 10).forEach { x ->
            assertTrue("mask pixels near x=$x", (l.eyeY - 2..l.eyeY + 3).any { y -> b[x, y] == dark })
        }
    }
}

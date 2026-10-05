package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.species.CatSpecies
import com.hoodie.app.pixel.character.species.DuckSpecies
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckSpeciesAnatomyTest {
    private val palette = CharacterPalette(
        outline = 0xFF1A1C33.toInt(),
        furLight = 0xFFFFE89A.toInt(), fur = 0xFFE9C95A.toInt(), furDark = 0xFFB18B35.toInt(),
        inner = 0xFF9F6D65.toInt(), outfitLight = 0xFFD9E5F1.toInt(), outfit = 0xFF7B739D.toInt(),
        outfitDark = 0xFF343A50.toInt(), shirt = 0xFFF3EBD9.toInt(), accent = 0xFFE9854B.toInt(),
    )

    @Test fun duckHasDistinctWebbedFeetAndWingTips() {
        val feet = PixelBuffer(24, 12)
        DuckSpecies.drawFoot(feet, palette, 8, 7, 11, farSide = false, facing = com.hoodie.app.pixel.sprite.Facing.FRONT)
        DuckSpecies.drawFoot(feet, palette, 16, 7, 11, farSide = true, facing = com.hoodie.app.pixel.sprite.Facing.FRONT)
        assertTrue("webbed feet use duck-orange palette", feet.pixels.count { it == palette.accent } >= 18)
        assertTrue("webbed feet keep an outline", feet.pixels.count { it == palette.outline } >= 12)

        val wing = PixelBuffer(12, 12)
        DuckSpecies.drawHand(wing, palette, 6, 7)
        assertTrue("wing feathers retain a distinct silhouette", wing.pixels.count { it == palette.furLight } >= 2)
        assertTrue("other species use the shared mammal hand", run {
            val mammalHand = PixelBuffer(12, 12)
            CatSpecies.drawHand(mammalHand, palette, 6, 7)
            !mammalHand.pixels.contentEquals(wing.pixels)
        })
    }
}

package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterLimbPainter
import com.hoodie.app.pixel.character.species.CatSpecies
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.procedural.HoodieArmsPainter
import com.hoodie.app.pixel.sprite.procedural.HoodieLegsPainter
import com.hoodie.app.pixel.sprite.procedural.R
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMammalFootRenderingTest {
    private val palette = CharacterPalette(
        outline = 0xFF1A1C33.toInt(),
        furLight = 0xFFFFE89A.toInt(), fur = 0xFFE9C95A.toInt(), furDark = 0xFFB18B35.toInt(),
        inner = 0xFF9F6D65.toInt(), outfitLight = 0xFFD9E5F1.toInt(), outfit = 0xFF7B739D.toInt(),
        outfitDark = 0xFF343A50.toInt(), shirt = 0xFFF3EBD9.toInt(), accent = 0xFFE9854B.toInt(),
    )

    @Test fun mammalPawsHaveAReadableRoundedSilhouetteToeSeparationsAndFarSideShade() {
        val near = PixelBuffer(24, 18)
        val far = PixelBuffer(24, 18)
        CatSpecies.drawFoot(near, palette, x = 8, y = 12, farSide = false)
        CatSpecies.drawFoot(far, palette, x = 8, y = 12, farSide = true)

        val occupiedRows = near.pixels.indices.filter { near.pixels[it] != 0 }.map { it / near.width }.distinct()
        assertTrue("paw should have a shaped 6px profile, not a flat sole", occupiedRows.size >= 5)
        assertTrue("paw should use outline and fur highlight", near.pixels.count { it == palette.outline } >= 8 && near.pixels.count { it == palette.furLight } >= 3)
        assertTrue("two toe separations should remain visible", near.pixels.count { it == palette.outline } >= 10)
        assertTrue("far paw should receive the species shadow color", far.pixels.count { it == palette.furDark } > near.pixels.count { it == palette.furDark })
    }

    @Test fun sharedMammalPawKeepsHoodiesRoundedSixPixelProfile() {
        val hoodie = PixelBuffer(40, 24)
        val npc = PixelBuffer(40, 24)
        HoodieLegsPainter.foot(hoodie, R(13, 6, 21, 11, 2))
        CatSpecies.drawFoot(npc, palette, x = 17, y = 11, farSide = false)

        val hoodieBounds = occupiedBounds(hoodie)
        val npcBounds = occupiedBounds(npc)
        assertTrue("shared mammal paws should keep Hoodie’s rounded profile depth", hoodieBounds.height == npcBounds.height)
        assertTrue("shared mammal paw width should stay close to Hoodie", kotlin.math.abs(hoodieBounds.width - npcBounds.width) <= 2)
    }

    @Test fun sharedMammalHandUsesTheHoodiesRoundedSilhouette() {
        val hoodie = PixelBuffer(24, 18)
        val npc = PixelBuffer(24, 18)
        val hoodiePaw = HoodieArmsPainter.armShape(Arm.DOWN).paw.dx(-1).dy(-47)
        CharacterLimbPainter.drawShape(hoodie, hoodiePaw, palette.fur, palette.outline)
        CatSpecies.drawHand(npc, palette, x = 8, y = 8)

        assertTrue(
            "shared mammal hand should match the Hoodie paw's rounded 6x6 silhouette",
            hoodie.pixels.indices.all { index -> (hoodie.pixels[index] ushr 24 != 0) == (npc.pixels[index] ushr 24 != 0) },
        )
    }

    private data class Bounds(val width: Int, val height: Int)

    private fun occupiedBounds(buffer: PixelBuffer): Bounds {
        val occupied = buffer.pixels.indices.filter { buffer.pixels[it] != 0 }
        val xs = occupied.map { it % buffer.width }
        val ys = occupied.map { it / buffer.width }
        return Bounds(xs.max() - xs.min() + 1, ys.max() - ys.min() + 1)
    }
}

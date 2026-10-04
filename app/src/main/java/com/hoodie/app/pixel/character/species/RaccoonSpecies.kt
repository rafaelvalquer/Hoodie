package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterScale
import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.renderer.PixelBuffer

object RaccoonSpecies : SpeciesStyle {
    override val id = "raccoon"
    override val headWidth = 29
    override val headHeight = 23
    override val bodyScale = CharacterScale.STANDARD
    override val earStyle = EarStyle.POINTED
    override val muzzleStyle = MuzzleStyle.MASKED
    override val tailStyle = TailStyle.RINGED
    override val headShape = HeadShape.RACCOON

    override fun drawEyeDecoration(buffer: PixelBuffer, palette: CharacterPalette, centerX: Int, eyeY: Int, headWidth: Int) {
        buffer.hline(centerX - headWidth / 2 + 3, centerX - 10, eyeY + 2, palette.outline)
        buffer.hline(centerX + 10, centerX + headWidth / 2 - 3, eyeY + 2, palette.outline)
    }
}

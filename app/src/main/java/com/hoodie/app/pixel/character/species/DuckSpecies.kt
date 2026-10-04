package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterScale
import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.renderer.PixelBuffer

object DuckSpecies : SpeciesStyle {
    override val id = "duck"
    override val headWidth = 26
    override val headHeight = 23
    override val bodyScale = CharacterScale.SMALL.copy(bodyWidth = 25, bodyHeight = 27, headScale = .9f)
    override val earStyle = EarStyle.WING
    override val muzzleStyle = MuzzleStyle.BEAK
    override val tailStyle = TailStyle.DUCK
    override val headShape = HeadShape.DUCK
    override val footContactOffset = 1

    override fun drawHand(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int) {
        // Asa curta com três penas, em vez de uma mão de mamífero.
        buffer.outlined(x - 3, y - 2, x + 3, y + 2, palette.fur, palette.outline)
        buffer.box(x - 2, y - 3, x - 1, y, palette.furLight)
        buffer.box(x, y - 4, x + 1, y, palette.fur)
        buffer.box(x + 2, y - 3, x + 3, y, palette.furDark)
    }

    override fun drawFoot(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int, farSide: Boolean) {
        // Pé palmado: base larga, dois dedos e membrana legível na escala do sprite.
        val offset = if (farSide) -1 else 1
        buffer.outlined(x - 5, y - 1, x + 5, y + 1, palette.accent, palette.outline)
        buffer.box(x - 4 + offset, y - 2, x - 2 + offset, y - 1, palette.accent)
        buffer.box(x + offset, y - 3, x + 2 + offset, y - 1, palette.accent)
        buffer.box(x + 3 + offset, y - 2, x + 4 + offset, y - 1, palette.accent)
        buffer.set(x - 2, y, palette.outfitLight)
    }
}

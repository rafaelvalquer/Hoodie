package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterScale
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Silhueta larga, focinho baixo pronunciado e orelhas laterais pequenas. */
object BulldogSpecies : SpeciesStyle {
    override val id = "bulldog"
    // The effective front silhouette (headWidth × WIDE.headScale) stays near 33 px.
    override val headWidth = 30
    override val headHeight = 27
    override val bodyScale = CharacterScale.WIDE.copy(bodyWidth = 28)
    override val earStyle = EarStyle.FLOPPY
    override val muzzleStyle = MuzzleStyle.BROAD
    override val tailStyle = TailStyle.SHORT
    override val headShape = HeadShape.BULLDOG
    override val eyeSpacing = 6

    override fun drawHead(buffer: PixelBuffer, pose: CharacterPose, palette: CharacterPalette, centerX: Int, topY: Int, scale: Float) {
        CharacterSpeciesPainter.drawBulldogHead(buffer, pose, palette, centerX, topY, scale)
    }

    override fun drawEars(buffer: PixelBuffer, pose: CharacterPose, palette: CharacterPalette, centerX: Int, topY: Int, scale: Float) {
        CharacterSpeciesPainter.drawBulldogEars(buffer, pose, palette, centerX, topY, scale)
    }

    override fun drawMuzzle(buffer: PixelBuffer, pose: CharacterPose, palette: CharacterPalette, centerX: Int, topY: Int, scale: Float) {
        CharacterSpeciesPainter.drawBulldogMuzzle(buffer, pose, palette, centerX, topY, scale)
    }
}

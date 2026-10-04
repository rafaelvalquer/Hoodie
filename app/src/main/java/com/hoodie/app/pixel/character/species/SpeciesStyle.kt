package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterBodyPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterScale
import com.hoodie.app.pixel.renderer.PixelBuffer

enum class EarStyle { POINTED, FLOPPY, LONG, ROUND, WING, NONE }
enum class MuzzleStyle { SHORT, BROAD, LONG, FINE, BEAK, MASKED }
enum class TailStyle { CAT, SHORT, LONG, RINGED, DUCK, NONE }
enum class HeadShape { CAT, BULLDOG, DOG, RABBIT, MOUSE, DUCK, RACCOON }

/** Espécie define somente silhueta facial, orelhas, focinho e cauda. */
interface SpeciesStyle {
    val id: String
    val headWidth: Int
    val headHeight: Int
    val bodyScale: CharacterScale
    val earStyle: EarStyle
    val muzzleStyle: MuzzleStyle
    val tailStyle: TailStyle
    val headShape: HeadShape
    /** Algumas espécies desenham a sola um pixel abaixo do centro do pé. */
    val footContactOffset: Int get() = 0
    /** Espaçamento ocular acompanha a largura facial sem identificar espécies no painter comum. */
    val eyeSpacing: Int get() = 6

    fun drawHead(buffer: PixelBuffer, pose: CharacterPose, palette: CharacterPalette, centerX: Int, topY: Int, scale: Float) =
        CharacterSpeciesPainter.drawHead(buffer, this, pose, palette, centerX, topY, scale)

    fun drawEars(buffer: PixelBuffer, pose: CharacterPose, palette: CharacterPalette, centerX: Int, topY: Int, scale: Float) =
        CharacterSpeciesPainter.drawEars(buffer, this, pose, palette, centerX, topY, scale)

    fun drawMuzzle(buffer: PixelBuffer, pose: CharacterPose, palette: CharacterPalette, centerX: Int, topY: Int, scale: Float) =
        CharacterSpeciesPainter.drawMuzzle(buffer, this, pose, palette, centerX, topY, scale)

    fun drawTail(buffer: PixelBuffer, pose: CharacterPose, palette: CharacterPalette, hipX: Int, hipY: Int, amplitude: Int) =
        CharacterSpeciesPainter.drawTail(buffer, this, pose, palette, hipX, hipY, amplitude)

    /** Detalhes de pelagem ao redor dos olhos, como a máscara natural do guaxinim. */
    fun drawEyeDecoration(buffer: PixelBuffer, palette: CharacterPalette, centerX: Int, eyeY: Int, headWidth: Int) = Unit

    /** Mão anatômica comum; espécies com asas ou patas especiais podem sobrescrever. */
    fun drawHand(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int) =
        CharacterBodyPainter.drawMammalHand(buffer, palette, x, y)

    /** Pé anatômico comum; espécies com pés palmados podem sobrescrever. */
    fun drawFoot(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int, farSide: Boolean) =
        CharacterBodyPainter.drawMammalFoot(buffer, palette, x, y, farSide)
}

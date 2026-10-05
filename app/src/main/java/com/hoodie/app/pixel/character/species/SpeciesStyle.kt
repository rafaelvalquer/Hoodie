package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.BodyLayout
import com.hoodie.app.pixel.character.CharacterArtProfile
import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterScale
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing

enum class EarStyle { POINTED, FLOPPY, LONG, ROUND, WING, NONE }
enum class MuzzleStyle { SHORT, BROAD, LONG, FINE, BEAK, MASKED }
enum class TailStyle { CAT, SHORT, LONG, RINGED, DUCK, NONE }
enum class HeadShape { CAT, BULLDOG, DOG, RABBIT, MOUSE, DUCK, RACCOON }

/** Pernas de mamífero (calça/pelo) ou de ave (finas, cor do bico). */
enum class LegStyle { MAMMAL, BIRD }

/** Tudo que a espécie precisa para desenhar a cabeça de um quadro. */
class HeadContext(
    val b: PixelBuffer,
    val style: CharacterStyle,
    val pose: CharacterPose,
    val layout: BodyLayout,
    /** Deslocamento das orelhas (follow-through do movimento secundário). */
    val earLag: Int = 0,
) {
    val p: CharacterPalette get() = style.palette
    val art: CharacterArtProfile get() = style.artProfile
    val facing: Facing get() = pose.facing
    val l get() = layout.headLeft
    val t get() = layout.headTop
    val r get() = layout.headRight
    val bot get() = layout.headBottom
    val cx get() = layout.headCx
    val eyeY get() = layout.eyeY
}

/** Contexto da cauda: raiz no quadril, direção do balanço e tamanho. */
class TailContext(
    val b: PixelBuffer,
    val style: CharacterStyle,
    val pose: CharacterPose,
    val rootX: Int,
    val rootY: Int,
    /** +1 cauda para a direita do canvas, −1 para a esquerda. */
    val direction: Int,
    /** Balanço (px) do movimento secundário. */
    val swing: Int,
) {
    val p: CharacterPalette get() = style.palette
}

/**
 * Espécie V3: silhueta da cabeça, rosto, orelhas, mãos/pés e cauda. Proporções e
 * rosto vêm do [artProfile]; corpo, roupa e animação continuam no painter comum.
 */
interface SpeciesStyle {
    val id: String
    val artProfile: CharacterArtProfile
    val earStyle: EarStyle
    val muzzleStyle: MuzzleStyle
    val tailStyle: TailStyle
    val headShape: HeadShape
    val legStyle: LegStyle get() = LegStyle.MAMMAL

    val headWidth: Int get() = artProfile.proportions.headWidth
    val headHeight: Int get() = artProfile.proportions.headHeight
    val eyeSpacing: Int get() = artProfile.face.eyeSpacing
    /** Escala legada (mantida para compatibilidade de [CharacterStyle.scale]). */
    val bodyScale: CharacterScale get() = CharacterScale.STANDARD
    /** Pixels reservados acima do topo da cabeça para orelhas altas. */
    val earClearance: Int get() = 0

    /** Cabeça completa: orelhas, silhueta, luz/sombra, marcas, olhos, focinho e boca. */
    fun drawHead(ctx: HeadContext)

    fun drawTail(ctx: TailContext) = SpeciesArt.tail(ctx, tailStyle)

    /** Mão anatômica (pata) na ponta da manga. */
    fun drawHand(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int) = SpeciesArt.paw(buffer, palette, x, y)

    /** Pé descalço; roupas com calçado desenham por cima. */
    fun drawFoot(buffer: PixelBuffer, palette: CharacterPalette, x: Int, y: Int, width: Int, farSide: Boolean, facing: Facing) =
        SpeciesArt.foot(buffer, palette, x, y, width, farSide, facing)
}

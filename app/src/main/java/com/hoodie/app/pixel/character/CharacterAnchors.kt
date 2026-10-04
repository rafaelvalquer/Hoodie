package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Point
import com.hoodie.app.pixel.sprite.SpriteAnchors

/** Âncoras em coordenadas do canvas 48×72, comuns a todos os personagens. */
data class CharacterAnchors(
    val feet: Point,
    val head: Point,
    val leftHand: Point,
    val rightHand: Point,
    val mouth: Point,
    val back: Point,
) {
    companion object {
        /** Adapta os anchors legados do Hoodie ao contrato comum sem tocar na imagem. */
        internal fun fromHoodie(anchors: SpriteAnchors, facing: Facing): CharacterAnchors = CharacterAnchors(
            feet = anchors.feet,
            head = anchors.head,
            leftHand = anchors.leftHand,
            rightHand = anchors.rightHand,
            mouth = Point(
                if (facing == Facing.SIDE) 7 else anchors.head.x,
                (anchors.head.y + 17).coerceIn(0, CharacterCanvas.HEIGHT - 1),
            ),
            back = anchors.back,
        )
    }
}

data class CharacterFrame(val image: PixelBuffer, val anchors: CharacterAnchors)

data class CharacterRenderRequest(
    val style: CharacterStyle,
    val pose: CharacterPose,
    val motion: CharacterRenderMotion = CharacterRenderMotion(),
)

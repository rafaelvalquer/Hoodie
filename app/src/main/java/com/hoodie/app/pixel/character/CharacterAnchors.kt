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
    val seatHip: Point? = null,
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
            seatHip = anchors.seatHip,
        )
    }
}

data class CharacterFrame(val image: PixelBuffer, val anchors: CharacterAnchors) {
    /** Row-wise opaque spans are stable for cached sprite frames and avoid scanning transparent padding on every draw. */
    internal val opaqueRowBounds: IntArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val bounds = IntArray(image.height * 2)
        for (y in 0 until image.height) {
            val row = y * image.width
            var left = image.width
            var right = -1
            for (x in 0 until image.width) {
                if (image.pixels[row + x] ushr 24 != 0) {
                    left = minOf(left, x)
                    right = x
                }
            }
            bounds[y * 2] = left
            bounds[y * 2 + 1] = right
        }
        bounds
    }
}

data class CharacterRenderRequest(
    val style: CharacterStyle,
    val pose: CharacterPose,
    val motion: CharacterRenderMotion = CharacterRenderMotion(),
)

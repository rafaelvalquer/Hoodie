package com.hoodie.app.pixel.review

import com.hoodie.app.pixel.character.CharacterComparisonRenderer
import com.hoodie.app.pixel.character.CharacterFrame
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.review.VisualReviewPose
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Núcleo de renderização compartilhado por matrizes, folhas de animação e revisão no Pixel Lab. */
object NpcVisualReviewRenderer {
    val facings = listOf(Facing.FRONT, Facing.SIDE, Facing.BACK)

    fun frame(style: CharacterStyle, pose: VisualReviewPose, facing: Facing): CharacterFrame {
        val data = NpcVisualReviewFrames.resolve(style, pose, facing)
        return CharacterPainter.paint(style, data.pose, data.motion)
    }

    fun hoodieFrame(pose: VisualReviewPose, facing: Facing): CharacterFrame {
        val data = NpcVisualReviewFrames.resolve(CharacterStyle.HOODIE, pose, facing)
        return CharacterComparisonRenderer.hoodie(data.pose)
    }

    fun facingLabel(facing: Facing) = when (facing) {
        Facing.FRONT -> "FRONT"
        Facing.SIDE -> "SIDE"
        Facing.BACK -> "BACK"
    }

    fun paste(out: PixelBuffer, frame: CharacterFrame, x: Int, y: Int, debug: Boolean = false) {
        out.blit(frame.image, x, y)
        val ground = y + frame.anchors.feet.y
        out.hline(x, x + CharacterPainter.WIDTH - 1, ground, ReviewRaster.CYAN)
        if (debug) {
            out.vline(x + frame.anchors.feet.x, y, ground, ReviewRaster.PINK)
            out.box(x + frame.anchors.leftHand.x - 1, y + frame.anchors.leftHand.y - 1, x + frame.anchors.leftHand.x + 1, y + frame.anchors.leftHand.y + 1, ReviewRaster.GOLD)
            out.box(x + frame.anchors.rightHand.x - 1, y + frame.anchors.rightHand.y - 1, x + frame.anchors.rightHand.x + 1, y + frame.anchors.rightHand.y + 1, ReviewRaster.GOLD)
            out.box(x + frame.anchors.head.x - 1, y + frame.anchors.head.y - 1, x + frame.anchors.head.x + 1, y + frame.anchors.head.y + 1, 0xFFE89A4A.toInt())
        }
    }
}

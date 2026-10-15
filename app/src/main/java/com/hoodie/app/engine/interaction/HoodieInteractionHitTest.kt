package com.hoodie.app.engine.interaction

import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.scene.PixelScene
import kotlin.math.floor
import kotlin.math.min

object HoodieInteractionHitTest {
    /** A gesture starts with an exact hit; small breathing/ear motion must not cancel a held finger. */
    fun nearHit(frame: RenderFrame?, x: Float, y: Float, width: Int, height: Int,
        toleranceLogicalPx: Int = HoodieInteractionConfig.DEFAULT_EXIT_TOLERANCE_LOGICAL_PX,
    ): Boolean {
        if (hit(frame, x, y, width, height)) return true
        val scale = floor(min(width.toFloat() / PixelScene.SCENE_W, height.toFloat() / PixelScene.SCENE_H)).toInt().coerceAtLeast(1)
        for (dy in -toleranceLogicalPx..toleranceLogicalPx) for (dx in -toleranceLogicalPx..toleranceLogicalPx) {
            if (hit(frame, x + dx * scale, y + dy * scale, width, height)) return true
        }
        return false
    }

    /** Same integer scale and centered draw area as PixelImage; no Compose frame state needed. */
    fun hit(frame: RenderFrame?, x: Float, y: Float, width: Int, height: Int): Boolean {
        if (frame == null || frame.fade < .99f || frame.interactionTransitioning || !frame.occupancy.present ||
            frame.occupancy.transition == com.hoodie.app.pixel.animation.HoodieOccupancy.Transition.LEAVING ||
            !x.isFinite() || !y.isFinite() || width <= 0 || height <= 0) return false
        val scale = floor(min(width.toFloat() / PixelScene.SCENE_W, height.toFloat() / PixelScene.SCENE_H)).toInt().coerceAtLeast(1)
        val logicalX = floor((x - (width - PixelScene.SCENE_W * scale) / 2f) / scale).toInt()
        val logicalY = floor((y - (height - PixelScene.SCENE_H * scale) / 2f) / scale).toInt()
        if (logicalX !in 0 until PixelScene.SCENE_W || logicalY !in 0 until PixelScene.SCENE_H) return false
        frame.interactionHitMask?.let { return it[logicalY * PixelScene.SCENE_W + logicalX].toInt() == 1 }
        val sprite = frame.sprite
        val sx = logicalX - frame.x + sprite.anchors.feet.x
        val sy = logicalY - frame.y + sprite.anchors.feet.y
        return sx in 0 until sprite.image.width && sy in 0 until sprite.image.height &&
            sprite.image.pixels[sy * sprite.image.width + sx].ushr(24) != 0
    }
}

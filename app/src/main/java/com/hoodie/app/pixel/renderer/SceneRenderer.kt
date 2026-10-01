package com.hoodie.app.pixel.renderer

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.HoodiePainter

/**
 * Compõe um frame: fundo (cacheado) → objetos atrás → Hoodie → objetos na frente
 * (ordenados por Y) → iluminação → efeitos → fade.
 */
class SceneRenderer {
    val buffer = PixelBuffer(PixelScene.SCENE_W, PixelScene.SCENE_H)

    private data class BgKey(val scene: SceneId, val period: DayPeriod)
    private val backgrounds = HashMap<BgKey, PixelBuffer>()

    private fun background(scene: PixelScene, env: SceneEnv) = backgrounds.getOrPut(BgKey(scene.id, env.period)) {
        PixelBuffer(scene.width, scene.height).also { scene.drawBackground(it, env) }
    }

    fun render(frame: RenderFrame, timeMs: Long): PixelBuffer {
        val scene = frame.scene
        buffer.copyFrom(background(scene, frame.env))

        val (behind, front) = scene.sortedProps.partition { it.baseline <= frame.y }
        behind.forEach { it.draw(buffer, frame.env, timeMs) }

        // O ponto dos pés do frame é alinhado ao chão: sprite sheets e procedural usam o mesmo contrato.
        val sprite = frame.sprite
        val left = frame.x - sprite.anchors.feet.x
        val top = frame.y - sprite.anchors.feet.y
        buffer.blit(sprite.image, left, top)
        sprite.itemOverlay?.let { item ->
            val hand = sprite.anchors.rightHand
            HoodiePainter.drawItemAt(buffer, item, com.hoodie.app.pixel.sprite.Point(left + hand.x, top + hand.y))
        }
        front.forEach { it.draw(buffer, frame.env, timeMs) }

        Lighting.apply(buffer, Lighting.map(scene, frame.env))
        frame.effects.forEach { (kind, pos) -> Effects.draw(buffer, kind, pos.first, pos.second, timeMs) }
        Lighting.fade(buffer, frame.fade)
        return buffer
    }

    /** Cena sem personagem (galeria de cenas / thumbnails). */
    fun renderEmpty(scene: PixelScene, env: SceneEnv, timeMs: Long): PixelBuffer {
        buffer.copyFrom(background(scene, env))
        scene.sortedProps.forEach { it.draw(buffer, env, timeMs) }
        Lighting.apply(buffer, Lighting.map(scene, env))
        return buffer
    }
}

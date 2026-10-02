package com.hoodie.app.pixel.debug

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
import com.hoodie.app.pixel.sprite.SpriteFrame
import com.hoodie.app.pixel.sprite.SpriteProvider
import com.hoodie.app.pixel.sprite.SpriteRequest

/**
 * Pixel Lab: comparar a mesma animação/direção/frame em [Procedural], [Final] (o
 * provider ativo — sheet onde existir) e [Overlay] (final com o procedural por cima
 * a 50%, para ver desalinhamento de pés, cabeça e mãos).
 */
enum class CompareMode { PROCEDURAL, FINAL, OVERLAY }

object SourceCompare {

    fun provider(mode: CompareMode, active: SpriteProvider): SpriteProvider = when (mode) {
        CompareMode.PROCEDURAL -> ProceduralSpriteProvider
        CompareMode.FINAL -> active
        CompareMode.OVERLAY -> object : SpriteProvider by active {
            override val name = "overlay"
            override fun supports(animation: AnimationId, facing: Facing) = true
            override fun frame(request: SpriteRequest): SpriteFrame {
                val f = active.frame(request)
                val p = ProceduralSpriteProvider.frame(request)
                return f.copy(image = blend(f.image, p.image), source = "${f.source}+procedural")
            }
        }
    }

    /** [top] a 50% sobre [base]. */
    fun blend(base: PixelBuffer, top: PixelBuffer): PixelBuffer {
        val out = PixelBuffer(base.width, base.height).also { base.pixels.copyInto(it.pixels) }
        for (y in 0 until minOf(base.height, top.height)) for (x in 0 until minOf(base.width, top.width)) {
            val c = top[x, y]
            if (c ushr 24 != 0) out.set(x, y, (c and 0xFFFFFF) or (0x80 shl 24))
        }
        return out
    }
}

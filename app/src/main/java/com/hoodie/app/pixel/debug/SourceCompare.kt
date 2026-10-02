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
 * a 50%, para ver desalinhamento de pés, cabeça e mãos) e [Difference] (só os pixels
 * em que o final difere do procedural, pintados — quanto a arte realmente mudou).
 */
enum class CompareMode { PROCEDURAL, FINAL, OVERLAY, DIFFERENCE }

object SourceCompare {

    fun provider(mode: CompareMode, active: SpriteProvider): SpriteProvider = when (mode) {
        CompareMode.PROCEDURAL -> ProceduralSpriteProvider
        CompareMode.FINAL -> active
        CompareMode.DIFFERENCE -> object : SpriteProvider by active {
            override val name = "difference"
            override fun supports(animation: AnimationId, facing: Facing) = true
            override fun frame(request: SpriteRequest): SpriteFrame {
                val f = active.frame(request)
                return f.copy(image = difference(f.image, ProceduralSpriteProvider.frame(request).image), source = "${f.source}≠procedural")
            }
        }
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

    const val DIFF_COLOR = 0xFFFF3B6B.toInt()
    const val SAME_COLOR = 0x40FFFFFF

    /** Pixels diferentes em [DIFF_COLOR]; iguais (não vazios) em cinza translúcido. */
    fun difference(final: PixelBuffer, procedural: PixelBuffer): PixelBuffer {
        val out = PixelBuffer(final.width, final.height)
        for (i in out.pixels.indices) {
            val a = final.pixels.getOrElse(i) { 0 }; val b = procedural.pixels.getOrElse(i) { 0 }
            out.pixels[i] = when {
                a != b -> DIFF_COLOR
                a ushr 24 != 0 -> SAME_COLOR
                else -> 0
            }
        }
        return out
    }

    /** Fração (0..1) dos pixels visíveis que mudaram. */
    fun changedRatio(final: PixelBuffer, procedural: PixelBuffer): Float {
        var visible = 0; var changed = 0
        for (i in final.pixels.indices) {
            val a = final.pixels[i]; val b = procedural.pixels.getOrElse(i) { 0 }
            if (a ushr 24 != 0 || b ushr 24 != 0) { visible++; if (a != b) changed++ }
        }
        return if (visible == 0) 0f else changed.toFloat() / visible
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

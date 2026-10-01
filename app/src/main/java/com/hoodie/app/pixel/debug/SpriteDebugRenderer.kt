package com.hoodie.app.pixel.debug

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.Point
import com.hoodie.app.pixel.sprite.SpriteFrame
import com.hoodie.app.pixel.sprite.SpriteProvider
import com.hoodie.app.pixel.sprite.SpriteRequest

data class DebugOptions(
    val onionSkin: Boolean = false,
    val anchors: Boolean = false,
    val boundingBox: Boolean = false,
    val feet: Boolean = false,
    val checker: Boolean = true,
)

/**
 * Inspetor de animação do Pixel Lab. Desenha o frame atual sobre uma moldura com
 * margem e, opcionalmente: onion-skin (frame anterior/próximo a 30%), âncoras,
 * bounding box e linha do chão — para achar tremedeira, pé deslizando e cabeça pulando.
 */
object SpriteDebugRenderer {
    const val MARGIN = 12
    val WIDTH = HoodiePainter.WIDTH + MARGIN * 2
    val HEIGHT = HoodiePainter.HEIGHT + MARGIN * 2

    private const val BG_A = 0xFF2B2E4A.toInt()
    private const val BG_B = 0xFF31355A.toInt()
    private const val ONION_PREV = 0xFFE58AAE.toInt()
    private const val ONION_NEXT = 0xFF7FE0C2.toInt()

    fun render(provider: SpriteProvider, request: SpriteRequest, options: DebugOptions): PixelBuffer {
        val out = PixelBuffer(WIDTH, HEIGHT)
        if (options.checker) for (y in 0 until HEIGHT) for (x in 0 until WIDTH) out.pixels[y * WIDTH + x] = if ((x / 4 + y / 4) % 2 == 0) BG_A else BG_B
        else out.fill(BG_A)

        val count = provider.frameCount(request.animation, request.direction).coerceAtLeast(1)
        val idx = request.frameIndex.mod(count)
        val current = provider.frame(request.copy(frameIndex = idx))

        if (options.onionSkin && count > 1) {
            tinted(out, provider.frame(request.copy(frameIndex = (idx - 1).mod(count))), ONION_PREV)
            tinted(out, provider.frame(request.copy(frameIndex = (idx + 1).mod(count))), ONION_NEXT)
        }
        val (left, top) = origin(current)
        out.blit(current.image, left, top)
        current.itemOverlay?.let { HoodiePainter.drawItemAt(out, it, Point(left + current.anchors.rightHand.x, top + current.anchors.rightHand.y)) }

        if (options.boundingBox) bbox(current)?.let { (x0, y0, x1, y1) ->
            val c = 0xFFF2CF5B.toInt()
            out.hline(left + x0, left + x1, top + y0, c); out.hline(left + x0, left + x1, top + y1, c)
            out.vline(left + x0, top + y0, top + y1, c); out.vline(left + x1, top + y0, top + y1, c)
        }
        if (options.feet) {
            val gy = top + current.anchors.feet.y
            for (x in 0 until WIDTH step 2) out.set(x, gy + 1, 0xFF7FE0C2.toInt())
            cross(out, left + current.anchors.feet.x, gy, 0xFF7FE0C2.toInt())
        }
        if (options.anchors) {
            val a = current.anchors
            cross(out, left + a.head.x, top + a.head.y, 0xFFF2CF5B.toInt())
            cross(out, left + a.rightHand.x, top + a.rightHand.y, 0xFFE05A5A.toInt())
            cross(out, left + a.leftHand.x, top + a.leftHand.y, 0xFF5AC8E0.toInt())
            cross(out, left + a.back.x, top + a.back.y, 0xFFDB7A3E.toInt())
        }
        return out
    }

    /** Alinha pelos pés, como na cena: assim o onion-skin revela pé deslizando. */
    private fun origin(f: SpriteFrame): Pair<Int, Int> =
        (MARGIN + HoodiePainter.FEET.x - f.anchors.feet.x) to (MARGIN + HoodiePainter.FEET.y - f.anchors.feet.y)

    private fun tinted(out: PixelBuffer, f: SpriteFrame, color: Int) {
        val (left, top) = origin(f)
        val c = (0x4D shl 24) or (color and 0xFFFFFF) // 30% alpha
        for (y in 0 until f.image.height) for (x in 0 until f.image.width) {
            if (f.image[x, y] ushr 24 != 0) out.set(left + x, top + y, c)
        }
    }

    private fun cross(out: PixelBuffer, x: Int, y: Int, c: Int) {
        out.set(x, y, c); out.set(x - 1, y, c); out.set(x + 1, y, c); out.set(x, y - 1, c); out.set(x, y + 1, c)
    }

    /** Retângulo dos pixels opacos do frame (para conferir altura/largura estável). */
    fun bbox(f: SpriteFrame): List<Int>? {
        var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
        for (y in 0 until f.image.height) for (x in 0 until f.image.width) if (f.image[x, y] ushr 24 != 0) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        return if (x1 < 0) null else listOf(x0, y0, x1, y1)
    }
}

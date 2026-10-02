package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.procedural.HoodiePoseRenderer
import com.hoodie.app.pixel.sprite.procedural.HoodieAccessoryPainter
import com.hoodie.app.pixel.sprite.procedural.ProceduralDrawing.recorder

/** Paleta oficial do Hoodie (baseada na concept art: gato azul de moletom, contorno escuro). 17 cores. */
object HoodiePalette {
    const val OUTLINE = 0xFF1A1C33.toInt()
    const val FUR = 0xFF86A9E8.toInt()
    const val FUR_SHADE = 0xFF6586CE.toInt()
    const val FUR_LIGHT = 0xFFB0C9F6.toInt()
    const val INNER_EAR = 0xFF4E69B0.toInt()
    const val HOOD = 0xFFB9CBEF.toInt()
    const val HOOD_SHADE = 0xFF92A9DB.toInt()
    const val HOOD_DARK = 0xFF6E84BE.toInt()
    const val HOOD_LIGHT = 0xFFDAE5FA.toInt()
    const val EYE = 0xFF0F1124.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val NOSE = 0xFF283063.toInt()
    const val TONGUE = 0xFFE07A93.toInt()
    const val BLUSH = 0xFFEFA3C8.toInt()
    const val STRING = 0xFFF1F5FF.toInt()
    const val BACKPACK = 0xFFDB7A3E.toInt()
    const val BACKPACK_DARK = 0xFFAA5329.toInt()

    val ALL = listOf(OUTLINE, FUR, FUR_SHADE, FUR_LIGHT, INNER_EAR, HOOD, HOOD_SHADE, HOOD_DARK, HOOD_LIGHT, EYE, WHITE, NOSE, TONGUE, BLUSH, STRING, BACKPACK, BACKPACK_DARK)
}

/** Sprite procedural + âncoras do frame. */
data class PaintedSprite(val image: PixelBuffer, val anchors: SpriteAnchors)

/**
 * Pintor procedural do Hoodie (fallback/debug do SpriteProvider). Gera o sprite
 * 48×72 de uma [HoodiePose] em três vistas (frente, costas, lado esquerdo). Cada
 * parte é uma forma com contorno próprio de 1px, desenhada de trás para frente.
 */
object HoodiePainter {
    const val WIDTH = 48
    const val HEIGHT = 72
    /** Os pés tocam o chão sempre neste ponto (o que evita "pé deslizando"). */
    val FEET = Point(24, 71)

    private val cache = HashMap<HoodiePose, PaintedSprite>()

    /** Partes do corpo, para separar camadas semânticas no .aseprite (arm_left, tail…). */
    enum class Part(val layer: String) {
        HEAD("head"), EARS("ears"), TORSO("torso"), ARM_LEFT("arm_left"), ARM_RIGHT("arm_right"),
        HAND_LEFT("hand_left"), HAND_RIGHT("hand_right"), LEG_LEFT("leg_left"), LEG_RIGHT("leg_right"),
        TAIL("tail"), STRINGS("strings"), BACKPACK("backpack"), ACCESSORY("accessory"),
    }


    /**
     * Pinta [pose] registrando qual parte pintou cada pixel (ordinal de [Part], -1 = vazio).
     * "esquerda/direita" são do lado da IMAGEM. Não usa o cache.
     */
    fun paintWithParts(pose: HoodiePose): Pair<PaintedSprite, IntArray> {
        val rec = IntArray(WIDTH * HEIGHT) { -1 }
        recorder.set(rec)
        try {
            val painted = paint(pose)
            if (pose.lift <= 0) return painted to rec
            val shifted = IntArray(rec.size) { -1 }
            for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
                val sy = y + pose.lift
                if (sy < HEIGHT) shifted[y * WIDTH + x] = rec[sy * WIDTH + x]
            }
            return painted to shifted
        } finally {
            recorder.set(null)
        }
    }

    fun sprite(pose: HoodiePose): PixelBuffer = painted(pose).image

    fun painted(pose: HoodiePose): PaintedSprite = synchronized(cache) { cache.getOrPut(pose) { paint(pose) } }

    fun paint(pose: HoodiePose): PaintedSprite = HoodiePoseRenderer.paint(pose)

    fun drawItemAt(b: PixelBuffer, item: Item, hand: Point) = HoodieAccessoryPainter.drawItemAt(b, item, hand)
}

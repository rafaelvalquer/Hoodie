package com.hoodie.app.pixel.art

import com.hoodie.app.pixel.SheetBaker
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.AnchorMarkers
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.HoodiePalette
import com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
import com.hoodie.app.pixel.sprite.SpriteFrame
import com.hoodie.app.pixel.sprite.SpriteRequest

/**
 * Arte final v1 do Hoodie, pixel a pixel, sobre as poses do procedural (mesma
 * proporção, mesmos pés e âncoras, mesma contagem de frames). Os retoques são os
 * de um acabamento à mão:
 *
 * 1. **Selective outline** — o contorno interno (braço sobre o corpo, pernas,
 *    dedos) troca o preto pelo tom escuro do material; só a silhueta fica preta.
 * 2. **Rim light** — a luz vem de cima/esquerda: a primeira fileira de pelo e de
 *    moletom encostada na silhueta ganha o tom claro.
 * 3. **Sombra de contato** — o moletom logo abaixo da cabeça escurece (a cabeça
 *    projeta sombra na gola), o que separa cabeça e corpo em todos os frames.
 *
 * O resultado vira um .aseprite com camadas por papel (outline, fur, hoodie…),
 * a camada `anchors` e a referência `baseline (referencia)` travada, e é exportado
 * para o APK no mesmo formato do `aseprite -b`. Um artista pode redesenhar por
 * cima no Aseprite e reexportar com assets-source/hoodie/export.sh.
 */
object FinalArtStudio {

    /** Arquivos fonte e o que cada um entrega (tags `<anim>_<vista>`). */
    val GROUPS: Map<String, List<Pair<AnimationId, Facing>>> = linkedMapOf(
        "hoodie_walk" to listOf(AnimationId.WALK, AnimationId.WALK_BACKPACK).flatMap { a -> listOf(Facing.SIDE, Facing.FRONT, Facing.BACK).map { a to it } },
        "hoodie_idle" to listOf(AnimationId.IDLE, AnimationId.IDLE_SIT, AnimationId.IDLE_LOOK, AnimationId.IDLE_EAR, AnimationId.IDLE_SCRATCH).map { it to Facing.FRONT },
        "hoodie_sleep" to listOf(AnimationId.BED_SIT, AnimationId.BED_LIE_DOWN, AnimationId.SLEEP, AnimationId.SLEEP_TURN, AnimationId.WAKE_EYES, AnimationId.BED_EXIT).map { it to Facing.FRONT },
        "hoodie_work" to listOf(
            AnimationId.SIT_DOWN, AnimationId.WORK_TYPING, AnimationId.STOP_TYPING, AnimationId.REACH_MOUSE, AnimationId.WORK_MOUSE,
            AnimationId.WORK_READ, AnimationId.STAND_UP, AnimationId.WORK_NOTES, AnimationId.WORK_TIRED,
        ).map { it to Facing.FRONT },
    )

    /** Camadas do .aseprite, de baixo para cima. */
    const val BASELINE = "baseline (referencia)"
    const val ANCHORS = "anchors"
    val LAYERS = listOf(BASELINE, "legs", "fur", "fur_shadow", "hoodie", "hoodie_shadow", "arms", "ears", "strings", "face", "accessory", "outline", ANCHORS)

    private val FUR = setOf(HoodiePalette.FUR, HoodiePalette.FUR_LIGHT)
    private val FUR_ALL = setOf(HoodiePalette.FUR, HoodiePalette.FUR_SHADE, HoodiePalette.FUR_LIGHT)
    private val HOOD_ALL = setOf(HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE, HoodiePalette.HOOD_LIGHT, HoodiePalette.HOOD_DARK)
    private val FACE = setOf(HoodiePalette.EYE, HoodiePalette.WHITE, HoodiePalette.NOSE, HoodiePalette.TONGUE, HoodiePalette.BLUSH)

    fun procedural(anim: AnimationId, facing: Facing, i: Int): SpriteFrame =
        ProceduralSpriteProvider.frame(SpriteRequest(anim, SheetBaker.directionOf(facing), i))

    fun frame(anim: AnimationId, facing: Facing, i: Int): SpriteFrame {
        val base = procedural(anim, facing, i)
        val headY = base.anchors.head.y
        val pose = ProceduralSpriteProvider.poseFor(SpriteRequest(anim, SheetBaker.directionOf(facing), i))
        return base.copy(image = retouch(base.image, faceBottom = if (pose.headOnly) Int.MAX_VALUE else headY + 26), source = "final")
    }

    /** Os três retoques, sem mudar a silhueta (pés, chão e âncoras ficam iguais). */
    fun retouch(src: PixelBuffer, faceBottom: Int): PixelBuffer {
        val out = PixelBuffer(src.width, src.height).also { src.pixels.copyInto(it.pixels) }
        fun c(x: Int, y: Int) = src[x, y]
        fun clear(x: Int, y: Int) = c(x, y) ushr 24 == 0
        val n4 = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        for (y in 0 until src.height) for (x in 0 until src.width) {
            val px = c(x, y)
            when {
                // 1. Selective outline: linha interna cercada de um único material.
                px == HoodiePalette.OUTLINE -> {
                    var touchesOutside = false
                    for (dy in -1..1) for (dx in -1..1) if (clear(x + dx, y + dy)) touchesOutside = true
                    if (touchesOutside) continue
                    val around = n4.map { (dx, dy) -> c(x + dx, y + dy) }.filter { it != HoodiePalette.OUTLINE }
                    if (around.size < 2) continue
                    if (around.all { it in HOOD_ALL }) out.set(x, y, HoodiePalette.HOOD_DARK)
                    else if (y > faceBottom && around.all { it in FUR_ALL }) out.set(x, y, HoodiePalette.INNER_EAR)
                }
                // 2. Rim light: encostado na silhueta por cima ou pela esquerda.
                px == HoodiePalette.FUR || px == HoodiePalette.HOOD -> {
                    val lit = (c(x, y - 1) == HoodiePalette.OUTLINE && clear(x, y - 2)) || (c(x - 1, y) == HoodiePalette.OUTLINE && clear(x - 2, y))
                    if (lit) out.set(x, y, if (px == HoodiePalette.FUR) HoodiePalette.FUR_LIGHT else HoodiePalette.HOOD_LIGHT)
                    // 3. Sombra de contato: moletom logo abaixo do contorno da cabeça (pelo acima).
                    else if (px == HoodiePalette.HOOD && c(x, y - 1) == HoodiePalette.OUTLINE && c(x, y - 2) in FUR) out.set(x, y, HoodiePalette.HOOD_SHADE)
                }
            }
        }
        return out
    }

    // ───────────── Documento .aseprite ─────────────

    /** Papel de cada pixel → camada. As camadas particionam a imagem: compor tudo devolve o frame. */
    fun layerOf(color: Int, y: Int, hemY: Int): String = when (color) {
        HoodiePalette.OUTLINE -> if (y > hemY) "legs" else "outline"
        HoodiePalette.FUR, HoodiePalette.FUR_LIGHT -> if (y > hemY) "legs" else "fur"
        HoodiePalette.FUR_SHADE, HoodiePalette.INNER_EAR -> if (y > hemY) "legs" else "fur_shadow"
        HoodiePalette.HOOD, HoodiePalette.HOOD_LIGHT -> "hoodie"
        HoodiePalette.HOOD_SHADE, HoodiePalette.HOOD_DARK -> "hoodie_shadow"
        HoodiePalette.STRING -> "strings"
        in FACE -> "face"
        else -> "accessory"
    }

    fun document(group: String): AsepriteFile.Document {
        val clips = GROUPS.getValue(group)
        val frames = mutableListOf<AsepriteFile.Frame>()
        val tags = mutableListOf<AsepriteFile.Tag>()
        val colors = linkedSetOf<Int>().apply { addAll(HoodiePalette.ALL) }
        clips.forEach { (anim, facing) ->
            val from = frames.size
            for (i in anim.frames.indices) {
                val art = frame(anim, facing, i)
                val base = procedural(anim, facing, i)
                val hemY = (0 until art.image.height).lastOrNull { y -> (0 until art.image.width).any { x -> art.image[x, y] in HOOD_ALL } } ?: art.image.height
                val layers = LAYERS.associateWith { PixelBuffer(HoodiePainter.WIDTH, HoodiePainter.HEIGHT) }
                for (y in 0 until art.image.height) for (x in 0 until art.image.width) {
                    val px = art.image[x, y]
                    if (px ushr 24 == 0) continue
                    colors += px
                    layers.getValue(layerOf(px, y, hemY)).pixels[y * art.image.width + x] = px
                }
                base.image.pixels.copyInto(layers.getValue(BASELINE).pixels)
                val a = art.anchors
                val markers = layers.getValue(ANCHORS)
                markers.set(a.feet.x, a.feet.y, AnchorMarkers.FEET); markers.set(a.head.x, a.head.y, AnchorMarkers.HEAD)
                markers.set(a.rightHand.x, a.rightHand.y, AnchorMarkers.RIGHT_HAND); markers.set(a.leftHand.x, a.leftHand.y, AnchorMarkers.LEFT_HAND)
                markers.set(a.back.x, a.back.y, AnchorMarkers.BACK)
                frames += AsepriteFile.Frame(art.durationMs.toInt(), LAYERS.mapIndexed { li, name -> AsepriteFile.Cel(li, 0, 0, layers.getValue(name)) })
            }
            tags += AsepriteFile.Tag("${anim.name.lowercase()}_${facing.name.lowercase()}", from, frames.size - 1)
        }
        val layers = LAYERS.map { name ->
            when (name) {
                // Referência: travada, escondida, nunca exportada.
                BASELINE -> AsepriteFile.Layer(name, AsepriteFile.LAYER_REFERENCE, 128)
                else -> AsepriteFile.Layer(name)
            }
        }
        return AsepriteFile.Document(HoodiePainter.WIDTH, HoodiePainter.HEIGHT, layers, frames, tags, colors.toList())
    }

    /** Sheet no formato `aseprite -b --format json-array --list-tags --list-slices` + camada de âncoras. */
    fun bake(group: String): SheetBaker.Baked = SheetBaker.bake(GROUPS.getValue(group)) { anim, facing, i -> frame(anim, facing, i) }
}

package com.hoodie.app.pixel.art

import com.hoodie.app.pixel.SheetBaker
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.AnchorMarkers
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.HoodiePalette
import com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
import com.hoodie.app.pixel.sprite.SpriteAnchors
import com.hoodie.app.pixel.sprite.SpriteRequest

/**
 * BOOTSTRAP da arte: cria o `.aseprite` inicial de um grupo a partir das poses do
 * procedural (mesma proporção, pés, âncoras e contagem de frames), com retoque de
 * acabamento, os passes de [ArtPasses] e camadas semânticas por parte do corpo.
 *
 * Desde a V0.2 RC o `.aseprite` é a FONTE DA VERDADE: este estúdio nunca sobrescreve
 * um arquivo existente (só com `-PartBootstrap=<grupo>` explícito). Edições feitas no
 * Aseprite são preservadas; o runtime é compilado do `.aseprite` pelo
 * [AsepriteSourceCompiler].
 */
object ArtBootstrapStudio {

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

    const val BASELINE = AsepriteSourceCompiler.BASELINE_LAYER
    const val ANCHORS = AsepriteSourceCompiler.ANCHORS_LAYER

    /** Camadas semânticas, de baixo para cima. Nem todas são usadas em todas as animações. */
    val LAYERS = listOf(
        BASELINE, "tail", "leg_left", "leg_right", "backpack", "torso", "hoodie", "hoodie_shadow", "strings",
        "arm_left", "arm_right", "hand_left", "hand_right", "head", "ears", "face", "accessory", "outline", ANCHORS,
    )

    private val FUR_ALL = setOf(HoodiePalette.FUR, HoodiePalette.FUR_SHADE, HoodiePalette.FUR_LIGHT)
    private val HOOD_ALL = setOf(HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE, HoodiePalette.HOOD_LIGHT, HoodiePalette.HOOD_DARK)
    private val HOOD_SHADOW = setOf(HoodiePalette.HOOD_SHADE, HoodiePalette.HOOD_DARK)
    val FACE = setOf(HoodiePalette.EYE, HoodiePalette.WHITE, HoodiePalette.NOSE, HoodiePalette.TONGUE, HoodiePalette.BLUSH)

    /** Um frame em edição: imagem, dono (parte) de cada pixel e âncoras. */
    class ArtFrame(val image: PixelBuffer, val parts: IntArray, var anchors: SpriteAnchors, val durationMs: Long) {
        fun set(x: Int, y: Int, color: Int, part: HoodiePainter.Part) {
            if (x !in 0 until image.width || y !in 0 until image.height) return
            image.pixels[y * image.width + x] = color
            parts[y * image.width + x] = part.ordinal
        }
        operator fun get(x: Int, y: Int) = image[x, y]
        fun partAt(x: Int, y: Int): HoodiePainter.Part? =
            if (x !in 0 until image.width || y !in 0 until image.height) null else parts[y * image.width + x].takeIf { it >= 0 }?.let { HoodiePainter.Part.entries[it] }
    }

    private fun request(anim: AnimationId, facing: Facing, i: Int) = SpriteRequest(anim, SheetBaker.directionOf(facing), i)

    /** Frame do procedural, como referência (camada baseline). */
    fun procedural(anim: AnimationId, facing: Facing, i: Int) = ProceduralSpriteProvider.frame(request(anim, facing, i))

    /** Frame final do bootstrap: procedural com partes + retoque + passes do grupo. */
    fun frame(anim: AnimationId, facing: Facing, i: Int): ArtFrame {
        val pose = ProceduralSpriteProvider.poseFor(request(anim, facing, i))
        val (painted, parts) = HoodiePainter.paintWithParts(pose)
        val duration = anim.clip.frames[i].durationMs
        val image = retouch(painted.image, faceBottom = if (pose.headOnly) Int.MAX_VALUE else painted.anchors.head.y + 26)
        val f = ArtFrame(image, parts, painted.anchors, duration)
        ArtPasses.apply(anim, facing, i, f)
        return f
    }

    /** Selective outline, rim light e sombra de contato — sem mudar a silhueta. */
    fun retouch(src: PixelBuffer, faceBottom: Int): PixelBuffer {
        val out = PixelBuffer(src.width, src.height).also { src.pixels.copyInto(it.pixels) }
        fun c(x: Int, y: Int) = src[x, y]
        fun clear(x: Int, y: Int) = c(x, y) ushr 24 == 0
        val n4 = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val px = c(x, y)
            when {
                px == HoodiePalette.OUTLINE -> {
                    var touchesOutside = false
                    for (dy in -1..1) for (dx in -1..1) if (clear(x + dx, y + dy)) touchesOutside = true
                    if (touchesOutside) continue
                    val around = n4.map { (dx, dy) -> c(x + dx, y + dy) }.filter { it != HoodiePalette.OUTLINE }
                    if (around.size < 2) continue
                    if (around.all { it in HOOD_ALL }) out.set(x, y, HoodiePalette.HOOD_DARK)
                    else if (y > faceBottom && around.all { it in FUR_ALL }) out.set(x, y, HoodiePalette.INNER_EAR)
                }
                px == HoodiePalette.FUR || px == HoodiePalette.HOOD -> {
                    val lit = (c(x, y - 1) == HoodiePalette.OUTLINE && clear(x, y - 2)) || (c(x - 1, y) == HoodiePalette.OUTLINE && clear(x - 2, y))
                    if (lit) out.set(x, y, if (px == HoodiePalette.FUR) HoodiePalette.FUR_LIGHT else HoodiePalette.HOOD_LIGHT)
                    else if (px == HoodiePalette.HOOD && c(x, y - 1) == HoodiePalette.OUTLINE && c(x, y - 2) in setOf(HoodiePalette.FUR, HoodiePalette.FUR_LIGHT)) out.set(x, y, HoodiePalette.HOOD_SHADE)
                }
            }
        }
        return out
    }

    // ───────────── Documento .aseprite ─────────────

    /** Camada de um pixel: contorno e rosto pela cor; o resto pela parte do corpo que o pintou. */
    fun layerOf(color: Int, part: HoodiePainter.Part?): String = when {
        color == HoodiePalette.OUTLINE -> "outline"
        color in FACE -> "face"
        part == null -> "accessory"
        part == HoodiePainter.Part.TORSO -> when (color) {
            in HOOD_SHADOW -> "hoodie_shadow"
            in HOOD_ALL -> "hoodie"
            else -> "torso"
        }
        else -> part.layer
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
                val layers = LAYERS.associateWith { PixelBuffer(HoodiePainter.WIDTH, HoodiePainter.HEIGHT) }
                val w = art.image.width
                for (y in 0 until art.image.height) for (x in 0 until w) {
                    val px = art.image[x, y]
                    if (px ushr 24 == 0) continue
                    colors += px
                    layers.getValue(layerOf(px, art.partAt(x, y))).pixels[y * w + x] = px
                }
                procedural(anim, facing, i).image.pixels.copyInto(layers.getValue(BASELINE).pixels)
                val a = art.anchors
                val markers = layers.getValue(ANCHORS)
                // Ordem importa quando duas âncoras caem no mesmo pixel (deitado: mãos = cabeça):
                // as mais críticas (pés, cabeça) são pintadas por último e prevalecem.
                markers.set(a.rightHand.x, a.rightHand.y, AnchorMarkers.RIGHT_HAND); markers.set(a.leftHand.x, a.leftHand.y, AnchorMarkers.LEFT_HAND)
                markers.set(a.back.x, a.back.y, AnchorMarkers.BACK)
                markers.set(a.head.x, a.head.y, AnchorMarkers.HEAD); markers.set(a.feet.x, a.feet.y, AnchorMarkers.FEET)
                frames += AsepriteFile.Frame(art.durationMs.toInt(), LAYERS.mapIndexed { li, name -> AsepriteFile.Cel(li, 0, 0, layers.getValue(name)) })
            }
            tags += AsepriteFile.Tag("${anim.name.lowercase()}_${facing.name.lowercase()}", from, frames.size - 1)
        }
        val layers = LAYERS.map { name ->
            if (name == BASELINE) AsepriteFile.Layer(name, AsepriteFile.LAYER_REFERENCE, 128) // travada, escondida, nunca exportada
            else AsepriteFile.Layer(name)
        }
        return AsepriteFile.Document(HoodiePainter.WIDTH, HoodiePainter.HEIGHT, layers, frames, tags, colors.toList())
    }
}

package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.ClipTiming
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Fonte dos frames do Hoodie. A máquina de estados pede "WALK, LEFT, frame 3"
 * e não sabe se a imagem veio de um sprite sheet desenhado à mão ou do pintor
 * procedural — o que permite migrar animação por animação.
 */
interface SpriteProvider {
    val name: String

    /** Este provider tem frames próprios para o clip nesta vista? */
    fun supports(animation: AnimationId, facing: Facing): Boolean

    fun durations(animation: AnimationId, direction: Direction): LongArray

    fun frame(request: SpriteRequest): SpriteFrame

    fun frameCount(animation: AnimationId, direction: Direction): Int = durations(animation, direction).size

    /** Frame no instante [elapsedMs] desde o início do clip. */
    fun frameAt(request: SpriteRequest, elapsedMs: Long): SpriteFrame {
        val d = durations(request.animation, request.direction)
        return frame(request.copy(frameIndex = ClipTiming.indexAt(d, elapsedMs, request.animation.loop)))
    }
}

/** Vista efetivamente usada: clips não direcionais são sempre frontais. */
fun viewFor(animation: AnimationId, direction: Direction): Direction =
    if (animation.clip.directional) direction else Direction.FRONT

/** Provider ativo do app. Procedural por padrão; o HoodieApp instala sprite sheets se existirem. */
object HoodieSprites {
    @Volatile
    var provider: SpriteProvider = ProceduralSpriteProvider
}

/**
 * Fallback/debug: gera cada frame a partir das poses do clip. Aplica postura
 * herdada, direção (RIGHT = espelho de LEFT) e overlays (piscar, olhar, orelhas).
 */
object ProceduralSpriteProvider : SpriteProvider {
    override val name = "procedural"

    private val mirrored = HashMap<HoodiePose, PaintedSprite>()

    override fun supports(animation: AnimationId, facing: Facing) = true

    override fun durations(animation: AnimationId, direction: Direction): LongArray = animation.clip.durations

    fun poseFor(request: SpriteRequest): HoodiePose {
        val clip = request.animation.clip
        val af = clip.frames[request.frameIndex.mod(clip.frames.size)]
        var pose = af.pose
        if (pose.legs == Legs.INHERIT) pose = pose.copy(legs = if (request.posture == Posture.SITTING) Legs.SIT else Legs.STAND)
        val view = viewFor(request.animation, request.direction)
        pose = pose.copy(facing = if (pose.headOnly) Facing.FRONT else view.facing)
        return request.overlay.apply(pose)
    }

    override fun frame(request: SpriteRequest): SpriteFrame {
        val clip = request.animation.clip
        val af = clip.frames[request.frameIndex.mod(clip.frames.size)]
        val pose = poseFor(request)
        val view = viewFor(request.animation, request.direction)
        val painted = if (view.flipX && pose.facing == Facing.SIDE) mirror(pose) else HoodiePainter.painted(pose)
        return SpriteFrame(painted.image, painted.anchors, af.durationMs, af.events, itemOverlay = null, source = name)
    }

    private fun mirror(pose: HoodiePose): PaintedSprite = synchronized(mirrored) {
        mirrored.getOrPut(pose) {
            val p = HoodiePainter.painted(pose)
            PaintedSprite(PixelBuffer(p.image.width, p.image.height).also { it.blit(p.image, 0, 0, flipX = true) }, p.anchors.mirror(p.image.width))
        }
    }
}

/**
 * Sprite sheet onde existir, procedural onde não existir. A escolha é feita por
 * clip e vista: dá para ter só `walk_side` desenhado à mão e o resto procedural.
 *
 * Duas regras mantêm a arte final viva como o procedural:
 * - Clip que herda a postura (Legs.INHERIT) só foi desenhado em pé; sentado, usa o procedural.
 * - Overlays (piscar, olhar, expressão, orelhas) viram um "remendo": os pixels que o
 *   overlay muda no procedural são aplicados por cima do frame final.
 */
class CompositeSpriteProvider(
    private val primary: SpriteProvider,
    private val fallback: SpriteProvider = ProceduralSpriteProvider,
) : SpriteProvider {
    override val name = "${primary.name}+${fallback.name}"

    private val patched = object : LinkedHashMap<SpriteRequest, PixelBuffer>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SpriteRequest, PixelBuffer>?) = size > 96
    }

    fun providerFor(animation: AnimationId, direction: Direction): SpriteProvider {
        val view = viewFor(animation, direction)
        return if (primary.supports(animation, view.facing)) primary else fallback
    }

    fun providerFor(request: SpriteRequest): SpriteProvider {
        val p = providerFor(request.animation, request.direction)
        val inheritsPosture = request.animation.clip.frames.any { it.pose.legs == Legs.INHERIT }
        return if (p === primary && inheritsPosture && request.posture == Posture.SITTING) fallback else p
    }

    override fun supports(animation: AnimationId, facing: Facing) = true

    override fun durations(animation: AnimationId, direction: Direction) = providerFor(animation, direction).durations(animation, direction)

    override fun frame(request: SpriteRequest): SpriteFrame {
        val p = providerFor(request)
        val f = p.frame(request)
        if (p === fallback || request.overlay == PoseOverlay.NONE) return f
        val idx = request.frameIndex.mod(request.animation.clip.frames.size)
        val key = request.copy(frameIndex = idx)
        val image = synchronized(patched) {
            patched.getOrPut(key) { overlayPatch(f.image, fallback.frame(key.copy(overlay = PoseOverlay.NONE)).image, fallback.frame(key).image) }
        }
        return if (image === f.image) f else f.copy(image = image)
    }

    companion object {
        /** Aplica em [art] só os pixels em que [withOverlay] difere de [plain]. */
        fun overlayPatch(art: PixelBuffer, plain: PixelBuffer, withOverlay: PixelBuffer): PixelBuffer {
            if (plain === withOverlay || plain.width != art.width || plain.height != art.height) return art
            var out: PixelBuffer? = null
            for (i in plain.pixels.indices) {
                if (plain.pixels[i] == withOverlay.pixels[i]) continue
                val o = out ?: PixelBuffer(art.width, art.height).also { art.pixels.copyInto(it.pixels); out = it }
                o.pixels[i] = withOverlay.pixels[i]
            }
            return out ?: art
        }
    }
}

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
 */
class CompositeSpriteProvider(
    private val primary: SpriteProvider,
    private val fallback: SpriteProvider = ProceduralSpriteProvider,
) : SpriteProvider {
    override val name = "${primary.name}+${fallback.name}"

    fun providerFor(animation: AnimationId, direction: Direction): SpriteProvider {
        val view = viewFor(animation, direction)
        return if (primary.supports(animation, view.facing)) primary else fallback
    }

    override fun supports(animation: AnimationId, facing: Facing) = true

    override fun durations(animation: AnimationId, direction: Direction) = providerFor(animation, direction).durations(animation, direction)

    override fun frame(request: SpriteRequest) = providerFor(request.animation, request.direction).frame(request)
}

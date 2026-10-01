package com.hoodie.app.pixel.animation

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.EffectKind
import com.hoodie.app.pixel.scene.EffectSpec
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.Spot
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.HoodiePose
import com.hoodie.app.pixel.sprite.Legs
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Tudo o que o renderer precisa para um frame. */
data class RenderFrame(
    val scene: PixelScene,
    val env: SceneEnv,
    val pose: HoodiePose,
    val x: Int,
    val y: Int,
    val fade: Float,
    val effects: List<Pair<EffectKind, Pair<Int, Int>>>,
)

/**
 * Máquina de estados de animação (ENTER → LOOP → EXIT). A ViewModel só diz
 * "o Hoodie está trabalhando no escritório"; aqui decidimos como ele chega lá
 * (levanta, anda até a porta, fade, entra, anda até a mesa, senta) e quais
 * microações tocam no loop, além de piscadas e olhadas aleatórias.
 */
class AnimationStateMachine(private val random: Random = Random(System.nanoTime())) {

    enum class Phase { LOOP, WALK, EXIT_WALK, FADE_OUT, FADE_IN }

    private var visual: VisualState? = null
    private var pending: VisualState? = null
    private var scene: PixelScene? = null
    var phase = Phase.LOOP
        private set

    private var x = 0f
    private var y = 0f
    private var target: Spot? = null
    private var lastTick = 0L
    private var phaseStart = 0L
    private var walkingLeft = false

    private var action: MicroAction? = null
    private var actionStart = 0L
    private var actionEnd = 0L

    private var reaction: AnimationId? = null
    private var reactionStart = 0L

    private var overlayEyes: Eyes? = null
    private var overlayEar = false
    private var overlayUntil = 0L
    private var nextOverlayAt = 0L

    /** Velocidade de caminhada em pixels lógicos por segundo. */
    private val walkSpeed = 52f

    val currentVisual: VisualState? get() = visual

    fun setVisual(v: VisualState, now: Long, greet: Boolean = false) {
        val current = visual
        if (current == null) {
            place(v, now)
            if (greet) react(if (isSittingNow()) AnimationId.WAVE_SIT else AnimationId.WAVE, now)
            return
        }
        if (v == current && pending == null) return
        if (v.scene == current.scene && pending == null) {
            visual = v
            beginTowards(v.spot, now)
            return
        }
        pending = v
        val doorSpot = scene?.spots?.get(SpotId.DOOR)
        if (doorSpot != null && scene?.walkInPlace == false && phase != Phase.FADE_OUT) {
            target = doorSpot; phase = Phase.EXIT_WALK; phaseStart = now
        } else {
            phase = Phase.FADE_OUT; phaseStart = now
        }
    }

    /** Reação curta por cima da ação atual (acenar, comemorar, dar de ombros). */
    fun react(anim: AnimationId, now: Long) {
        if (phase != Phase.LOOP) return
        reaction = anim; reactionStart = now
    }

    private fun place(v: VisualState, now: Long) {
        visual = v; pending = null
        scene = SceneRegistry[v.scene]
        val spot = scene!!.spot(v.spot)
        x = spot.x.toFloat(); y = spot.y.toFloat()
        phase = Phase.LOOP
        chooseAction(now, allowMove = false)
    }

    private fun beginTowards(spotId: SpotId, now: Long) {
        val spot = scene?.spot(spotId) ?: return
        if (scene?.walkInPlace == true || (abs(spot.x - x) < 1 && abs(spot.y - y) < 1)) {
            phase = Phase.LOOP; chooseAction(now, allowMove = false)
        } else {
            target = spot; phase = Phase.WALK; phaseStart = now
        }
    }

    private fun chooseAction(now: Long, allowMove: Boolean) {
        val v = visual ?: return
        val total = v.actions.sumOf { it.weight }
        var roll = if (total > 0) random.nextInt(total) else 0
        var chosen = v.actions.firstOrNull()
        for (a in v.actions) { if (roll < a.weight) { chosen = a; break }; roll -= a.weight }
        val a = chosen ?: return
        action = a
        actionStart = now
        val span = (a.maxMs - a.minMs).coerceAtLeast(0)
        actionEnd = now + a.minMs + if (span > 0) random.nextLong(span + 1) else 0
        val wanted = a.spot
        if (allowMove && wanted != null && scene?.spots?.containsKey(wanted) == true) {
            val spot = scene!!.spot(wanted)
            if (abs(spot.x - x) >= 1 || abs(spot.y - y) >= 1) {
                target = spot; phase = Phase.WALK; phaseStart = now
            }
        }
    }

    private fun isSittingNow(): Boolean = action?.anim?.frames?.first()?.legs == Legs.SIT

    /** Avança o tempo e devolve o frame atual. */
    fun frame(now: Long, clockMinute: Int, period: DayPeriod): RenderFrame? {
        val sc = scene ?: return null
        val v = visual ?: return null
        val dt = if (lastTick == 0L) 0f else ((now - lastTick).coerceIn(0, 100)) / 1000f
        lastTick = now
        var fade = 1f

        when (phase) {
            Phase.WALK, Phase.EXIT_WALK -> {
                val t = target
                if (t == null || step(t, dt)) {
                    if (phase == Phase.EXIT_WALK) { phase = Phase.FADE_OUT; phaseStart = now }
                    else {
                        phase = Phase.LOOP
                        // Chegou: começa a ação que motivou a caminhada (ou escolhe uma).
                        if (action == null || now >= actionEnd) chooseAction(now, allowMove = false)
                        else { val dur = actionEnd - actionStart; actionStart = now; actionEnd = now + dur }
                    }
                }
            }
            Phase.FADE_OUT -> {
                fade = 1f - (now - phaseStart) / FADE_MS.toFloat()
                if (now - phaseStart >= FADE_MS) {
                    val next = pending ?: v
                    visual = next; pending = null
                    scene = SceneRegistry[next.scene]
                    val door = scene!!.spots[SpotId.DOOR]
                    val start = if (door != null && !scene!!.walkInPlace) door else scene!!.spot(next.spot)
                    x = start.x.toFloat(); y = start.y.toFloat()
                    action = null
                    target = null
                    phase = Phase.FADE_IN; phaseStart = now
                    fade = 0f
                }
            }
            Phase.FADE_IN -> {
                fade = (now - phaseStart) / FADE_MS.toFloat()
                if (now - phaseStart >= FADE_MS) { fade = 1f; beginTowards(visual!!.spot, now) }
            }
            Phase.LOOP -> {
                reaction?.let { if (now - reactionStart >= it.durationMs + 200) reaction = null }
                if (reaction == null && now >= actionEnd) chooseAction(now, allowMove = true)
            }
        }

        val currentScene = scene ?: sc
        val currentVisual = visual ?: v
        val walking = phase == Phase.WALK || phase == Phase.EXIT_WALK ||
            (phase == Phase.FADE_IN && target != null && (abs(x - (target?.x ?: 0)) > 1)) ||
            (currentScene.walkInPlace && currentScene.id == SceneId.STREET)
        var pose = when {
            walking -> {
                val anim = if (currentVisual.backpackWalk) AnimationId.WALK_BACKPACK else AnimationId.WALK
                val p = anim.frameAt(now - phaseStart)
                if (currentScene.walkInPlace) p.copy(eyes = Eyes.OPEN) else p.copy(eyes = if (walkingLeft) Eyes.LOOK_LEFT else Eyes.LOOK_RIGHT)
            }
            reaction != null -> reaction!!.frameAt(now - reactionStart)
            else -> (action?.anim ?: AnimationId.IDLE).frameAt(now - actionStart)
        }

        // Expressão (cansado, sonolento...) só substitui olhos neutros.
        currentVisual.expression?.eyes?.let { e -> if (pose.eyes == Eyes.OPEN) pose = pose.copy(eyes = e) }
        pose = applyOverlay(pose, now)

        val effects = mutableListOf<Pair<EffectKind, Pair<Int, Int>>>()
        if (!walking && phase == Phase.LOOP) {
            (currentVisual.effects + (action?.effects ?: emptyList<EffectSpec>())).forEach { e ->
                effects += e.kind to ((x.toInt() + e.dx) to (y.toInt() + e.dy))
            }
            if (reaction == AnimationId.HAPPY || reaction == AnimationId.WAVE || reaction == AnimationId.WAVE_SIT) {
                effects += EffectKind.SPARKLE to (x.toInt() to (y.toInt() - 82))
            }
        }

        val env = SceneEnv(
            period = period,
            clockMinute = clockMinute,
            variant = currentVisual.variant,
            tvOn = currentVisual.tvOn,
            screenOn = currentVisual.screenOn,
        )
        return RenderFrame(currentScene, env, pose, x.toInt(), y.toInt(), fade.coerceIn(0f, 1f), effects)
    }

    /** Move em direção ao alvo; true quando chegou. Anda primeiro em X, depois em Y (estilo RPG). */
    private fun step(t: Spot, dt: Float): Boolean {
        val dx = t.x - x; val dy = t.y - y
        val dist = hypot(dx, dy)
        if (dist < 1f) { x = t.x.toFloat(); y = t.y.toFloat(); return true }
        val move = walkSpeed * dt
        if (abs(dx) >= 1f) {
            walkingLeft = dx < 0
            x += dx.coerceIn(-move, move)
        } else {
            y += dy.coerceIn(-move, move)
        }
        return false
    }

    private fun applyOverlay(pose: HoodiePose, now: Long): HoodiePose {
        if (pose.headOnly) return pose
        if (now >= overlayUntil) { overlayEyes = null; overlayEar = false }
        if (now >= nextOverlayAt) {
            val roll = random.nextInt(100)
            when {
                roll < 65 -> { overlayEyes = Eyes.CLOSED; overlayUntil = now + 140 }
                roll < 80 -> { overlayEyes = Eyes.LOOK_LEFT; overlayUntil = now + 700 }
                roll < 92 -> { overlayEyes = Eyes.LOOK_RIGHT; overlayUntil = now + 700 }
                else -> { overlayEar = true; overlayUntil = now + 300 }
            }
            nextOverlayAt = now + 2_000 + random.nextLong(4_000)
        }
        var p = pose
        val eyes = overlayEyes
        if (eyes != null && (p.eyes == Eyes.OPEN || (eyes == Eyes.CLOSED && p.eyes in BLINKABLE))) p = p.copy(eyes = eyes)
        if (overlayEar) p = p.copy(earTwitch = true)
        return p
    }

    companion object {
        const val FADE_MS = 420L
        private val BLINKABLE = setOf(Eyes.OPEN, Eyes.LOOK_LEFT, Eyes.LOOK_RIGHT, Eyes.LOOK_DOWN, Eyes.FOCUSED)
    }
}

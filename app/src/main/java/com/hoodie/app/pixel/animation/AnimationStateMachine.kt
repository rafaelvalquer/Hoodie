package com.hoodie.app.pixel.animation

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.EffectKind
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.Spot
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodieSprites
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Posture
import com.hoodie.app.pixel.sprite.SpriteFrame
import com.hoodie.app.pixel.sprite.SpriteProvider
import com.hoodie.app.pixel.sprite.SpriteRequest
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Tudo o que o renderer precisa para um frame. */
data class RenderFrame(
    val scene: PixelScene,
    val env: SceneEnv,
    val sprite: SpriteFrame,
    /** Posição dos pés do Hoodie na cena. */
    val x: Int,
    val y: Int,
    val fade: Float,
    val effects: List<Pair<EffectKind, Pair<Int, Int>>>,
    val animation: AnimationId,
    val direction: Direction,
    val frameIndex: Int,
)

/**
 * Máquina de estados de animação. A ViewModel só diz "o Hoodie está trabalhando
 * no escritório"; aqui o estado vira um roteiro de passos:
 *
 *     EXIT da microação → EXIT do estado (levantar) → antecipação (virar) →
 *     andar até a porta → abrir a porta → sair (fade) → trocar de cena → entrar →
 *     andar até o spot → parar → ENTER do estado (sentar) → LOOP de microações
 *
 * Mudanças de estado respeitam a [InterruptPolicy] do clip atual: nada de cortar
 * um gole de café ou a digitação no meio.
 */
class AnimationStateMachine(
    private val random: Random = Random(System.nanoTime()),
    private val sprites: () -> SpriteProvider = { HoodieSprites.provider },
) {
    enum class Phase { LOOP, SEQUENCE, WALK, DOOR, FADE_OUT, FADE_IN }

    sealed interface Step {
        data class Play(val anim: AnimationId, val direction: Direction = Direction.FRONT) : Step
        data class Walk(val target: Spot) : Step
        data object OpenDoor : Step
        data object FadeOut : Step
        data class Switch(val visual: VisualState) : Step
        data class Apply(val visual: VisualState) : Step
        data object FadeIn : Step
        data object Loop : Step
    }

    private data class Transient(val kind: EffectKind, val x: Int, val y: Int, val until: Long)

    private var visual: VisualState? = null
    private var scene: PixelScene? = null
    private var pending: VisualState? = null
    private var pendingAt = 0L
    private val reactions = ReactionResolver()
    private val transitionPlanner = TransitionPlanner()
    private val loopSelector = LoopSelector(random)

    private val steps = ArrayDeque<Step>()
    private var step: Step = Step.Loop
    private var stepStart = 0L

    private var x = 0f
    private var y = 0f
    var posture = Posture.STANDING
        private set
    private val flags = mutableSetOf<SceneFlag>()
    private var doorOpenAt = -1L
    private var doorCloseAt = -1L

    private val player = AnimationPlayer()
    val animation: AnimationId get() = player.animation
    private var direction: Direction
        get() = player.direction
        set(value) { player.direction = value }
    private val animStart: Long get() = player.startedAt
    private var lastFrameIdx: Int
        get() = player.lastFrameIndex
        set(value) { player.lastFrameIndex = value }

    private var action: MicroAction? = null
    private var actionFresh = false
    private var actionEnd = 0L
    /** Tempo restante da microação interrompida por uma reação (-1 = nenhuma). */
    private var resumeMs = -1L

    private var lastTick = 0L
    private var fade = 1f
    private val transients = mutableListOf<Transient>()
    private val idle = IdleDirector(random)

    /** Velocidade de caminhada em pixels lógicos por segundo. */
    private val walkSpeed = 52f

    val currentVisual: VisualState? get() = visual
    val sceneFlags: Set<SceneFlag> get() = flags
    val plannedSteps: List<Step> get() = listOf(step) + steps

    val phase: Phase
        get() = when (step) {
            is Step.Play, is Step.Apply -> Phase.SEQUENCE
            is Step.Walk -> Phase.WALK
            Step.OpenDoor -> Phase.DOOR
            Step.FadeOut, is Step.Switch -> Phase.FADE_OUT
            Step.FadeIn -> Phase.FADE_IN
            Step.Loop -> Phase.LOOP
        }

    // ───────────────────────── API ─────────────────────────

    /**
     * Define o estado visual alvo. Na primeira vez o gato já aparece no loop (o app
     * acabou de abrir); depois, a troca vira um roteiro de transição.
     * [greet] = o usuário acabou de abrir o app (ReactionDirector decide se ele reage).
     */
    fun setVisual(v: VisualState, now: Long, greet: Boolean = false) {
        if (visual == null) {
            place(v, now)
            if (greet) react(ReactionDirector.onAppOpened(v, action?.anim, random), now)
            return
        }
        if (v == (pending ?: visual)) return
        pending = v
        pendingAt = interruptBoundary(now)
    }

    fun react(anim: AnimationId, now: Long) = react(listOf(anim), now)

    /** Reações entram por cima do loop atual, respeitando a política de interrupção. */
    fun react(anims: List<AnimationId>, now: Long) {
        if (anims.isEmpty()) return
        reactions.request(anims, interruptBoundary(now))
    }

    /** Coloca o gato direto no loop do estado (sem transições). */
    fun place(v: VisualState, now: Long) {
        scene = SceneRegistry[v.scene]
        visual = v; pending = null
        val spot = scene!!.spot(v.spot)
        x = spot.x.toFloat(); y = spot.y.toFloat()
        flags.clear(); flags.addAll(v.steadyFlags)
        steps.clear(); step = Step.Loop; stepStart = now
        doorOpenAt = -1; doorCloseAt = -1; fade = 1f
        idle.reset(now); idle.setGaze(v.gaze, now)
        action = loopSelector.pick(v, null)
        // Microação com spot próprio (banco do passeio, esteira da academia): já aparece lá.
        action?.spot?.takeIf { it in scene!!.spots }?.let { scene!!.spot(it) }?.let { x = it.x.toFloat(); y = it.y.toFloat() }
        posture = if (action?.anim?.posture == Legs.SIT) Posture.SITTING else Posture.STANDING
        startAction(now)
    }

    // ───────────────────────── Tick ─────────────────────────

    fun frame(now: Long, clockMinute: Int, period: DayPeriod): RenderFrame? {
        if (scene == null || visual == null) return null
        val dt = if (lastTick == 0L) 0f else ((now - lastTick).coerceIn(0, 100)) / 1000f
        lastTick = now

        pending?.let { target ->
            if (now >= pendingAt && (step == Step.Loop || step is Step.Walk)) {
                pending = null
                buildPlan(target, now)
            }
        }
        val before = step
        runStep(now, dt)
        // Entrou no loop neste tick: já mostra a microação (sem um frame "velho").
        if (step == Step.Loop && before != Step.Loop) runLoop(now)
        updateDoor(now)

        val sc = scene!!
        val v = visual!!
        val provider = sprites()
        val durations = provider.durations(animation, direction)
        val idx = player.frameIndex(now, durations)
        val walking = step is Step.Walk || animation.group == AnimGroup.LOCOMOTION
        var overlay = idle.overlay(now, allowLook = step == Step.Loop && !walking).copy(expression = v.expression)
        if (walking) overlay = overlay.copy(gaze = if (direction.facing == Facing.SIDE) Eyes.LOOK_LEFT else null)
        val sprite = provider.frame(SpriteRequest(animation, direction, idx, posture, overlay))

        val left = x.toInt() - sprite.anchors.feet.x
        val top = y.toInt() - sprite.anchors.feet.y
        if (idx != lastFrameIdx) {
            lastFrameIdx = idx
            sprite.events.forEach { onEvent(it, left, top, sprite, now) }
        }

        val effects = mutableListOf<Pair<EffectKind, Pair<Int, Int>>>()
        if (step == Step.Loop && !walking) {
            (v.effects + (action?.effects ?: emptyList())).forEach { e ->
                if (e.requires != null && e.requires !in flags) return@forEach
                val p = e.anchor?.let { a -> sprite.anchors[a] }
                effects += e.kind to (if (p != null) (left + p.x + e.dx) to (top + p.y + e.dy) else (x.toInt() + e.dx) to (y.toInt() + e.dy))
            }
        }
        transients.removeAll { it.until < now }
        transients.forEach { effects += it.kind to (it.x to it.y) }

        val env = SceneEnv(period, clockMinute, v.variant, v.tvOn, v.screenOn, doorFrame(now), flags.toSet())
        return RenderFrame(sc, env, sprite, x.toInt(), y.toInt(), fade.coerceIn(0f, 1f), effects, animation, direction, idx)
    }

    // ───────────────────────── Passos ─────────────────────────

    private fun runStep(now: Long, dt: Float) {
        when (val s = step) {
            Step.Loop -> runLoop(now)
            is Step.Play -> {
                play(s.anim, s.direction, now)
                if (now - animStart >= animation.durationMs) { settlePosture(s.anim); next(now) }
            }
            is Step.Walk -> {
                if (move(s.target, dt)) next(now)
                else play(walkAnim(), direction, now)
            }
            Step.OpenDoor -> {
                play(AnimationId.TURN, Direction.BACK, now)
                if (scene?.hasAnimatedDoor != true) { next(now); return }
                if (doorOpenAt < 0) { doorOpenAt = now; doorCloseAt = -1 }
                if (now - doorOpenAt >= DOOR_FRAME_MS * SceneEnv.DOOR_OPEN) next(now)
            }
            Step.FadeOut -> {
                fade = 1f - (now - stepStart) / FADE_MS.toFloat()
                // Atravessa a porta (anda "para dentro" dela, de costas para nós).
                if (scene?.walkInPlace == false && scene?.spots?.get(SpotId.DOOR) != null) {
                    y -= walkSpeed * 0.5f * dt
                    play(walkAnim(), Direction.BACK, now)
                }
                if (now - stepStart >= FADE_MS) { fade = 0f; next(now) }
            }
            is Step.Switch -> { switchScene(s.visual, now); next(now) }
            is Step.Apply -> {
                visual = s.visual; idle.setGaze(s.visual.gaze, now); action = null
                next(now)
            }
            Step.FadeIn -> {
                fade = (now - stepStart) / FADE_MS.toFloat()
                play(AnimationId.WALK_STOP, Direction.FRONT, now)
                if (now - stepStart >= FADE_MS) { fade = 1f; next(now) }
            }
        }
    }

    private fun runLoop(now: Long) {
        val v = visual ?: return
        if (action == null) { action = loopSelector.pick(v, null); actionFresh = true }
        if (actionFresh) startAction(now)
        val a = action!!

        reactions.takeReady(now)?.let { sequence ->
            steps.clear()
            steps.addAll(sequence.steps)
            // Depois da reação, retoma a mesma microação pelo tempo que faltava.
            resumeMs = (actionEnd - now).coerceAtLeast(1_500)
            next(now)
            return
        }

        val oneShotDone = a.once && !animation.loop && now - animStart >= animation.durationMs
        if (oneShotDone || (now >= actionEnd && now >= loopBoundary(now))) {
            switchAction(now)
            return
        }
        // Alongamento esporádico durante idle.
        if (a.anim.group == AnimGroup.IDLE && a.anim.loop && idle.wantsStretch(now)) {
            steps.clear()
            steps += Step.Play(if (posture == Posture.SITTING) AnimationId.STRETCH_SIT else AnimationId.STRETCH)
            steps += Step.Loop
            next(now)
            return
        }
        play(a.anim, a.direction, now)
    }

    private fun startAction(now: Long) {
        val a = action ?: return
        actionFresh = false
        actionEnd = now + loopSelector.duration(a)
        play(a.anim, a.direction, now, restart = true)
    }

    /** Troca de microação com as transições dela (pôr a caneca, pegar o mouse, andar até o peso). */
    private fun switchAction(now: Long) {
        val v = visual ?: return
        val nextAction = loopSelector.pick(v, action)
        steps.clear()
        steps.addAll(transitionPlanner.actionPlan(scene!!, action, nextAction, x, y, posture).steps)
        action = nextAction; actionFresh = true
        next(now)
    }

    /** Sorteio ponderado evitando repetir a mesma microação (nada de typing → typing). */

    private fun next(now: Long) {
        step = steps.removeFirstOrNull() ?: Step.Loop
        stepStart = now
        if (step == Step.Loop && action != null && resumeMs >= 0) {
            // Volta de uma reação: retoma a microação.
            actionEnd = now + resumeMs
            resumeMs = -1
            play(action!!.anim, action!!.direction, now, restart = true)
        }
    }

    // ───────────────────────── Roteiro entre estados ─────────────────────────

    private fun buildPlan(target: VisualState, now: Long) {
        val sc = scene ?: return
        val current = visual ?: return
        val sequence = transitionPlanner.plan(sc, current, target, x, y, posture, action?.exit.orEmpty(), step == Step.Loop)
        steps.clear()
        steps.addAll(sequence.steps)
        resumeMs = -1
        action = null
        next(now)
    }

    /** Antecipação (vira para a direção) + caminhada + parada com follow-through. */

    private fun switchScene(target: VisualState, now: Long) {
        val sc = SceneRegistry[target.scene]
        scene = sc; visual = target; action = null
        flags.clear()
        posture = Posture.STANDING
        idle.setGaze(target.gaze, now)
        val door = sc.spots[SpotId.DOOR]
        val start = if (door != null && !sc.walkInPlace) door else sc.spot(target.spot)
        x = start.x.toFloat(); y = start.y.toFloat()
        if (door != null && sc.hasAnimatedDoor && !sc.walkInPlace) {
            doorOpenAt = now - DOOR_FRAME_MS * SceneEnv.DOOR_OPEN; doorCloseAt = -1
        } else { doorOpenAt = -1; doorCloseAt = -1 }
    }

    // ───────────────────────── Movimento e porta ─────────────────────────

    private fun walkAnim() = VisualDirector.locomotion(scene?.id, visual?.backpackWalk == true)

    /** Move em direção ao alvo; true quando chegou. Um eixo por vez (estilo RPG). */
    private fun move(t: Spot, dt: Float): Boolean {
        val dx = t.x - x; val dy = t.y - y
        if (hypot(dx, dy) < 1f) { x = t.x.toFloat(); y = t.y.toFloat(); return true }
        val stepPx = walkSpeed * dt
        val yFirst = dy > 0 && abs(dy) >= 1f
        if (yFirst || abs(dx) < 1f) {
            y += dy.coerceIn(-stepPx, stepPx); direction = Direction.of(0f, dy)
        } else {
            x += dx.coerceIn(-stepPx, stepPx); direction = Direction.of(dx, 0f)
        }
        return false
    }

    private fun updateDoor(now: Long) {
        val door = scene?.spots?.get(SpotId.DOOR) ?: return
        if (doorOpenAt >= 0 && doorCloseAt < 0 && step != Step.OpenDoor && step != Step.FadeOut && step != Step.FadeIn &&
            hypot(door.x - x, door.y - y) > 14f
        ) doorCloseAt = now
    }

    private fun doorFrame(now: Long): Int {
        if (doorOpenAt < 0) return 0
        val opening = (((now - doorOpenAt) / DOOR_FRAME_MS) + 1).toInt().coerceAtMost(SceneEnv.DOOR_OPEN)
        if (doorCloseAt < 0) return opening
        val closing = SceneEnv.DOOR_OPEN - 1 - ((now - doorCloseAt) / DOOR_FRAME_MS).toInt()
        if (closing <= 0) { doorOpenAt = -1; doorCloseAt = -1; return 0 }
        return minOf(opening, closing)
    }

    // ───────────────────────── Clips ─────────────────────────

    private fun play(anim: AnimationId, dir: Direction, now: Long, restart: Boolean = false) {
        if (!player.play(anim, dir, now, restart)) return
        // Clips com postura explícita definem a postura (INHERIT herda).
        val legs = anim.frames.first().pose
        if (!legs.headOnly) when (legs.legs) {
            Legs.SIT -> if (anim.loop) posture = Posture.SITTING
            Legs.INHERIT -> Unit
            else -> if (anim.loop) posture = Posture.STANDING
        }
    }

    /** Ao final de um clip de transição, a postura é a do último frame (sentou/levantou). */
    private fun settlePosture(anim: AnimationId) {
        val last = anim.frames.last().pose
        if (last.headOnly) return
        when (last.legs) {
            Legs.SIT -> posture = Posture.SITTING
            Legs.INHERIT -> Unit
            else -> posture = Posture.STANDING
        }
    }

    private fun interruptBoundary(now: Long): Long {
        if (step != Step.Loop) return now
        return InterruptResolver.boundary(animation, sprites().durations(animation, direction), animStart, now)
    }

    /** Microações em loop só trocam ao fim do ciclo, se o clip pedir isso. */
    private fun loopBoundary(now: Long): Long =
        if (animation.clip.interruptPolicy == InterruptPolicy.FINISH_CYCLE)
            InterruptResolver.loopBoundary(animation, sprites().durations(animation, direction), animStart, now)
        else now

    // ───────────────────────── Eventos ─────────────────────────

    private fun onEvent(e: AnimationEvent, left: Int, top: Int, sprite: SpriteFrame, now: Long) {
        when (e) {
            AnimationEvent.SIT -> if (visual?.spot == SpotId.DESK) flags += SceneFlag.CHAIR_OCCUPIED
            AnimationEvent.STAND -> flags -= SceneFlag.CHAIR_OCCUPIED
            AnimationEvent.MUG_PICKUP -> flags += SceneFlag.MUG_IN_HAND
            AnimationEvent.MUG_PUT -> flags -= SceneFlag.MUG_IN_HAND
            AnimationEvent.PHONE_PICK -> flags += SceneFlag.PHONE_IN_HAND
            AnimationEvent.PHONE_PUT -> flags -= SceneFlag.PHONE_IN_HAND
            AnimationEvent.BED_ENTER -> flags += SceneFlag.IN_BED
            AnimationEvent.BED_EXIT -> flags -= SceneFlag.IN_BED
            AnimationEvent.FOOD_SERVED -> { flags += SceneFlag.FOOD_SERVED; flags -= SceneFlag.FOOD_DONE }
            AnimationEvent.FOOD_DONE -> flags += SceneFlag.FOOD_DONE
            AnimationEvent.ITEM_PICK -> flags += SceneFlag.DUMBBELL_TAKEN
            AnimationEvent.ITEM_PUT -> flags -= SceneFlag.DUMBBELL_TAKEN
            AnimationEvent.BOOK_OPEN -> flags += SceneFlag.BOOK_OPEN
            AnimationEvent.PAGE_TURN -> if (SceneFlag.PAGE_TURNED in flags) flags -= SceneFlag.PAGE_TURNED else flags += SceneFlag.PAGE_TURNED
            AnimationEvent.ITEM_PICKED -> flags += SceneFlag.ITEM_HELD
            AnimationEvent.ITEM_IN_CART -> { flags -= SceneFlag.ITEM_HELD; flags += SceneFlag.ITEM_IN_CART }
            AnimationEvent.ITEM_AT_CHECKOUT -> { flags -= SceneFlag.ITEM_IN_CART; flags += SceneFlag.CHECKOUT_ACTIVE }
            AnimationEvent.PAYMENT_DONE -> flags -= SceneFlag.CHECKOUT_ACTIVE
            AnimationEvent.SNACK_PICKED -> flags += SceneFlag.SNACK_IN_HAND
            AnimationEvent.SNACK_FINISHED -> flags -= SceneFlag.SNACK_IN_HAND
            AnimationEvent.CAMERA_READY -> flags += SceneFlag.CAMERA_ACTIVE
            AnimationEvent.PHOTO_TAKEN -> {
                flags -= SceneFlag.CAMERA_ACTIVE
                // Flash curto na mão que segura o celular.
                transients += Transient(EffectKind.SPARKLE, left + sprite.anchors.rightHand.x, top + sprite.anchors.rightHand.y - 6, now + 400)
            }
            AnimationEvent.SPARKLE -> transients += Transient(EffectKind.SPARKLE, left + sprite.anchors.head.x, top + sprite.anchors.head.y - 6, now + 700)
            AnimationEvent.FOOTSTEP -> if (scene?.id == SceneId.STREET || scene?.id == SceneId.GENERIC_OUTDOOR || scene?.id == SceneId.LEISURE) {
                transients += Transient(EffectKind.DUST, left + sprite.anchors.feet.x, top + sprite.anchors.feet.y, now + 300)
            }
        }
    }

    companion object {
        const val FADE_MS = 420L
        const val DOOR_FRAME_MS = 100L
    }
}

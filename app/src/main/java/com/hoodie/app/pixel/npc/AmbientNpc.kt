package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterAnchors
import com.hoodie.app.pixel.character.CharacterFrame
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Point
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.Posture
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.sin
import java.util.concurrent.ConcurrentHashMap
import java.util.LinkedHashMap
import com.hoodie.app.pixel.npc.office.OfficeAmbientBrain
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcBrain
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcBrain
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcDirector
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.BuildConfig
import com.hoodie.app.pixel.performance.ScenePerformanceMonitor

/** Definição de cena seleciona identidade registrada, comportamento e fala — nunca anatomia. */
data class AmbientNpcDefinition(
    val id: String,
    val characterStyle: CharacterStyle,
    val behaviorProfile: NpcBehaviorProfile,
    val speechProfile: NpcSpeechProfile? = null,
)

/** Coordenadas usam centro e chão na resolução lógica da cena. */
data class AmbientNpcSlot(
    val definition: AmbientNpcDefinition,
    val x: Int,
    val floorY: Int,
    val baseline: Int,
    val seed: Int,
    val path: NpcPath? = null,
    /** Profundidade semântica; a política resolve escala e mínimo por espécie. */
    val depth: NpcDepth = NpcDepth.SCENE,
    /** Para NPCs parados de perfil: olhando para a direita. */
    val facingRight: Boolean = false,
    /** Comportamento procedural do escritório, consultado de forma determinística por tempo. */
    val officeBrain: OfficeAmbientBrain? = null,
    /** Comprador procedural do mercado; reconstruído por seed e tempo, sem estado compartilhado. */
    val shoppingBrain: ShoppingNpcBrain? = null,
    /** Cliente procedural do restaurante; reconstruído por seed e tempo, sem estado compartilhado. */
    val restaurantBrain: RestaurantNpcBrain? = null,
    /** Postura própria do assento (a caminhada e a orientação visual continuam separadas). */
    val seatedPosture: Posture? = null,
    /** Identificador do SeatSlot ocupado nesta cena. */
    val seatSlotId: String? = null,
    /** Alternating deterministic bump phase, set only for V2 bus passengers. */
    val busJoltPhaseMs: Long? = null,
) {
    val scale: Float get() = NpcScalePolicy.scale(definition.characterStyle, depth)
}

object NpcDirector {
    private val exec = definition(
        NpcCharacterRegistry.BULLDOG_EXEC, NpcAnimation.WALK,
        lines = listOf("Reunião em 5 min.", "Café primeiro.", "Bom dia!"),
    )
    /** Escritório: olhar → café → celular → olhar. */
    private val rabbit = definition(
        NpcCharacterRegistry.RABBIT_ANALYST, NpcAnimation.LOOK,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.LOOK, 2_600), NpcStep(NpcAnimation.STAND_COFFEE, 3_000),
            NpcStep(NpcAnimation.STAND_PHONE, 2_800), NpcStep(NpcAnimation.LOOK, 2_000),
        ),
    )
    /** Colega que anda, para, olha e volta. */
    private val cat = definition(NpcCharacterRegistry.CAT_COLLEAGUE, NpcAnimation.WALK)
    /** Ônibus: celular → janela → celular. */
    private val mouse = definition(
        NpcCharacterRegistry.MOUSE_COMMUTER, NpcAnimation.SIT_PHONE,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.SIT_PHONE, 4_000), NpcStep(NpcAnimation.LOOK_WINDOW, 2_200, seated = true),
            NpcStep(NpcAnimation.SIT_PHONE, 3_000),
        ),
    )
    /** Dorme → cabeça cai → acorda de leve → dorme. */
    private val duck = definition(
        NpcCharacterRegistry.DUCK_SLEEPY, NpcAnimation.SIT_SLEEP,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.SIT_SLEEP, 3_600), NpcStep(NpcAnimation.SIT_HEAD_DROP, 1_600),
            NpcStep(NpcAnimation.SIT_WAKE, 1_200), NpcStep(NpcAnimation.SIT_SLEEP, 3_000),
        ),
    )
    /** Ônibus: olha a janela → celular → comenta o trânsito (sentado, uma fala curta por vez). */
    private val talker = definition(
        NpcCharacterRegistry.DOG_WORKER, NpcAnimation.SIT_LOOK,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.SIT_LOOK, 3_000), NpcStep(NpcAnimation.SIT_PHONE, 2_400),
            NpcStep(NpcAnimation.TALK, 1_500, seated = true), NpcStep(NpcAnimation.SIT_LOOK, 600),
        ),
        lines = listOf("Trânsito de novo.", "Chego já.", "Quase perdi o ponto."),
    )
    private val bunny = definition(
        NpcCharacterRegistry.RABBIT_READER, NpcAnimation.SIT_PHONE,
        NpcBehaviorSequence.of(NpcStep(NpcAnimation.SIT_PHONE, 4_400), NpcStep(NpcAnimation.LOOK_WINDOW, 2_000, seated = true)),
    )

    /**
     * Passageiros sentados dos interiores em camadas (ônibus, trem, metrô): a cena coloca um por slot
     * `npc_seat_N`, escolhido por seed. O ônibus mantém a única fala curta do transporte.
     */
    fun passengers(scene: SceneId): List<AmbientNpcDefinition> = when (scene) {
        SceneId.BUS -> listOf(talker)
        SceneId.TRAIN -> listOf(bunny, mouse)
        SceneId.METRO -> listOf(duck, mouse)
        else -> emptyList()
    }
    /** Restaurante: come, comenta algo, volta a comer. */
    private val guest = definition(
        NpcCharacterRegistry.CAT_GUEST, NpcAnimation.SIT_EAT,
        NpcBehaviorSequence.of(
            NpcStep(NpcAnimation.SIT_EAT, 6_000),
            NpcStep(NpcAnimation.TALK, 1_800, seated = true, facing = Facing.SIDE),
        ),
        lines = listOf("Cheiro bom.", "Vou pedir o de sempre."),
    )
    private val walker = definition(NpcCharacterRegistry.RABBIT_WALKER, NpcAnimation.WALK)

    fun plan(scene: SceneId, env: SceneEnv): List<AmbientNpcSlot> {
        val v = env.variant and 1
        return when (scene) {
            SceneId.OFFICE -> listOf(
                if (v == 0) AmbientNpcSlot(exec, 211, 204, 204, 11 + v, officePath())
                else AmbientNpcSlot(cat, 211, 204, 204, 11 + v, colleaguePath()),
                AmbientNpcSlot(rabbit, 34, 204, 204, 31),
            )
            SceneId.RESTAURANT -> RestaurantNpcDirector.plan(env)
            SceneId.SHOPPING -> ShoppingNpcDirector.plan(env)
            SceneId.LEISURE -> listOf(
                AmbientNpcSlot(walker, 204, 252, 254, 91, walkerPath()),
                AmbientNpcSlot(cat, 38, 252, 254, 92, strollPath()),
            )
            else -> emptyList()
        }
    }

    fun plan(scene: SceneId, variant: Int): List<AmbientNpcSlot> = plan(
        scene, SceneEnv(DayPeriod.DAY, 9 * 60, variant = variant),
    )

    private fun definition(
        style: CharacterStyle,
        animation: NpcAnimation,
        sequence: NpcBehaviorSequence? = null,
        lines: List<String> = emptyList(),
    ) = AmbientNpcDefinition(
        style.id, style, NpcBehaviorProfile(animation, SpeciesMotionProfiles.forCharacter(style), sequence),
        lines.takeIf { it.isNotEmpty() }?.let(::NpcSpeechProfile),
    )

    /** Bulldog: ENTRA pela esquerda → anda → olha o Hoodie → fala → espera → anda → SAI pela direita. */
    private fun officePath() = NpcPath(
        points = listOf(
            NpcPathPoint(-24, 204),
            NpcPathPoint(64, 204, NpcAnimation.LOOK, 1_000),
            NpcPathPoint(122, 204, NpcAnimation.TALK, 1_500),
            NpcPathPoint(122, 204, NpcAnimation.IDLE, 600),
            NpcPathPoint(192, 204),
            NpcPathPoint(264, 204),
        ),
        millisPerPixel = 24,
    )

    /** Gato colega: anda → para → olha → volta (com virada). */
    private fun colleaguePath() = NpcPath(
        points = listOf(
            NpcPathPoint(214, 204, NpcAnimation.IDLE, 1_800),
            NpcPathPoint(150, 204, NpcAnimation.LOOK, 1_600),
            NpcPathPoint(214, 204),
        ),
        millisPerPixel = 30,
    )

    private fun walkerPath() = NpcPath(
        points = listOf(
            NpcPathPoint(212, 252, NpcAnimation.IDLE, 1_200),
            NpcPathPoint(150, 252, NpcAnimation.LOOK, 1_800),
            NpcPathPoint(212, 252),
        ),
        millisPerPixel = 26,
    )

    private fun strollPath() = NpcPath(
        points = listOf(
            NpcPathPoint(30, 252, NpcAnimation.IDLE, 1_600),
            NpcPathPoint(78, 252, NpcAnimation.LOOK, 1_400),
            NpcPathPoint(30, 252),
        ),
        millisPerPixel = 32,
    )
}

/** Renderer de cena: resolve movimento, pinta a pose, escala por profundidade, sombra no chão e fala. */
object NpcRenderer {
    const val AMBIENT_SCALE = AmbientScale.DEFAULT
    private const val SHADOW = 0xFF1A1C33.toInt()
    private data class TurnWidthRatios(val intoFront: Float, val outOfFront: Float)
    private val turnSideFrontRatios = ConcurrentHashMap<String, TurnWidthRatios>()
    private data class ScaledFrameKey(val frame: CharacterFrame, val scale: Float)
    private val scaledFrameCache = LinkedHashMap<ScaledFrameKey, CharacterFrame>(128, .75f, true)
    private const val SCALED_FRAME_CACHE_LIMIT = 96

    fun draw(
        b: PixelBuffer,
        slot: AmbientNpcSlot,
        timeMs: Long,
        movementOverride: NpcMovement? = null,
        officeFrameStateOverride: com.hoodie.app.pixel.npc.office.OfficeNpcFrameState? = null,
    ) {
        val measureOffice = BuildConfig.DEBUG && slot.officeBrain != null
        var stageStarted = if (measureOffice) ScenePerformanceMonitor.nowNanos() else 0L
        var poseNanos = 0L
        var paintNanos = 0L
        var turnNanos = 0L
        var scaleNanos = 0L
        var compositeNanos = 0L
        var speechNanos = 0L
        val restaurantBrain = slot.restaurantBrain
        val restaurantState = restaurantBrain?.stateAt(timeMs)
        val officeFrameState = officeFrameStateOverride ?: slot.officeBrain?.frameStateAt(timeMs)
        val movement = movementOverride ?: if (restaurantBrain != null && restaurantState != null) {
            restaurantBrain.movementAt(timeMs, restaurantState)
        } else officeFrameState?.movement ?: NpcMotionController.movement(slot, timeMs)
        if (!NpcMotionController.visible(movement)) {
            if (measureOffice) ScenePerformanceMonitor.recordNpcStages(
                ScenePerformanceMonitor.nowNanos() - stageStarted, 0L, 0L, 0L, 0L, 0L,
            )
            return
        }
        val frameData = NpcMotionController.frame(slot, timeMs, movement)
        val restaurantLine = if (restaurantBrain != null && restaurantState != null) {
            restaurantBrain.speechLineAt(timeMs, restaurantState)
        } else null
        val speechProfile = restaurantLine?.let {
            NpcSpeechProfile(listOf(it), visibleMs = RestaurantNpcBrain.SPEECH_VISIBLE_MS)
        } ?: slot.definition.speechProfile
        val officeSpeech = officeFrameState?.speech
        val speechAllowed = when {
            restaurantBrain != null -> restaurantLine != null
            slot.officeBrain != null -> officeSpeech != null
            else -> shouldSpeak(slot, timeMs)
        }
        val animation = if (
            movement.animation == NpcAnimation.TALK && speechProfile != null && !speechAllowed && slot.officeBrain == null
        ) {
            if (restaurantBrain != null) {
                NpcAnimation.IDLE
            } else {
                slot.definition.behaviorProfile.sequence?.steps?.lastOrNull { it.animation != NpcAnimation.TALK }?.animation
                    ?: slot.path?.points?.mapNotNull { it.stop }?.lastOrNull { it != NpcAnimation.TALK }
                    ?: NpcAnimation.IDLE
            }
        } else movement.animation
        val pose = (if (animation == movement.animation) frameData.pose else NpcMotionController.frame(
            slot, timeMs, movement.copy(animation = animation),
        ).pose).let { resolved ->
            val seated = slot.seatedPosture != null && movement.animation !in setOf(NpcAnimation.STAND, NpcAnimation.STAND_UP, NpcAnimation.WALK)
            val postured = if (seated) resolved.copy(facing = Facing.FRONT, posture = slot.seatedPosture!!) else resolved
            slot.busJoltPhaseMs?.let { phase ->
                val body = busJolt(timeMs + phase)
                val delayedHead = busJolt(timeMs + phase - 90L)
                postured.copy(bob = (postured.bob + body).coerceIn(-2, 2), headDy = (postured.headDy + delayedHead).coerceIn(-3, 3))
            } ?: postured
        }
        if (measureOffice) {
            poseNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        val paintedFrame = CharacterPainter.paintForNpc(
            slot.definition.characterStyle, pose, frameData.motion, movement.facingRight,
        )
        if (measureOffice) {
            paintNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        val turnedFrame = if (movement.animation == NpcAnimation.TURN_LEFT || movement.animation == NpcAnimation.TURN_RIGHT) {
            turnPerspective(paintedFrame, slot.definition.characterStyle, movement.localTimeMs)
        } else paintedFrame
        if (measureOffice) {
            turnNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        val frame = scaledFrame(turnedFrame, slot.scale)
        if (measureOffice) {
            scaleNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        val anchor = frame.anchors.seatHip ?: frame.anchors.feet
        val left = movement.x - anchor.x
        val top = movement.floorY - anchor.y
        if (frame.anchors.seatHip == null) drawGroundShadow(b, movement.x, movement.floorY, slot.definition.characterStyle, slot.scale)
        b.blitOpaqueRows(frame.image, left, top, frame.opaqueRowBounds)
        if (measureOffice) {
            compositeNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        if (slot.shoppingBrain != null && (movement.x in 0..239 || movement.phase == PathPhase.ENTER || movement.phase == PathPhase.EXIT)) {
            val shoppingState = slot.shoppingBrain.stateAt(timeMs)
            val heldItem = if (shoppingState.currentIntent == com.hoodie.app.pixel.npc.shopping.ShoppingNpcIntent.EXIT_STORE) Item.SHOPPING_BAG else Item.BASKET
            val propHand = frame.anchors.leftHand
            HoodiePainter.drawItemAt(b, heldItem, Point(left + propHand.x, top + propHand.y))
            if (heldItem == Item.BASKET) {
                val count = movement.shoppingBasketCount.coerceIn(0, 3)
                val colors = intArrayOf(0xFFC9544F.toInt(), 0xFF2F4A3C.toInt(), 0xFFF2CF5B.toInt())
                for (i in 0 until count) b.set(left + propHand.x - 3 + i * 3, top + propHand.y - 2, colors[i])
            }
        }
        slot.shoppingBrain?.stateAt(timeMs)?.let { state ->
            val line = state.speechLine ?: return@let
            NpcSpeechBubbleRenderer.draw(
                b, NpcSpeechProfile(listOf(line), visibleMs = 1_500), movement.x, movement.floorY,
                slot.seed, timeMs, state.speechOffsetMs, frame.anchors.head.y,
                (slot.definition.characterStyle.species.headHeight * slot.scale).roundToInt().coerceAtLeast(1),
            )
        }
        if (movement.animation == NpcAnimation.TALK && speechAllowed) {
            val speech = officeSpeech?.let { NpcSpeechProfile(listOf(it.line), visibleMs = it.durationMs) } ?: speechProfile
            speech?.let { activeProfile ->
            NpcSpeechBubbleRenderer.draw(
                b, activeProfile, movement.x, movement.floorY, slot.seed, timeMs,
                officeSpeech?.let { (timeMs - it.startedAt).coerceAtLeast(0) } ?: movement.localTimeMs, frame.anchors.head.y,
                (slot.definition.characterStyle.species.headHeight * slot.scale).roundToInt().coerceAtLeast(1),
            )
            }
        }
        if (measureOffice) {
            speechNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            ScenePerformanceMonitor.recordNpcStages(poseNanos, paintNanos, turnNanos, scaleNanos, compositeNanos, speechNanos)
        }
    }

    private fun scaledFrame(frame: CharacterFrame, scale: Float): CharacterFrame {
        if (scale >= 0.999f) return frame
        val key = ScaledFrameKey(frame, scale)
        synchronized(scaledFrameCache) { scaledFrameCache[key]?.let { return it } }
        val result = scaleFrameForAmbient(frame, scale)
        synchronized(scaledFrameCache) {
            scaledFrameCache[key] = result
            if (scaledFrameCache.size > SCALED_FRAME_CACHE_LIMIT) {
                val eldest = scaledFrameCache.entries.iterator()
                if (eldest.hasNext()) { eldest.next(); eldest.remove() }
            }
        }
        return result
    }

    /** Um ciclo elegível respeita o intervalo do perfil sem alterar a coreografia do NPC. */
    internal fun shouldSpeak(slot: AmbientNpcSlot, timeMs: Long): Boolean {
        slot.restaurantBrain?.let { return it.speechLineAt(timeMs) != null }
        slot.officeBrain?.let { return it.shouldSpeak(timeMs) }
        val speech = slot.definition.speechProfile ?: return false
        val behavior = slot.definition.behaviorProfile
        val cycleMs = slot.path?.let(NpcMotionController::pathCycleMs)
            ?: behavior.sequence?.cycleMs
            ?: return true
        val eligibleCycles = (speech.cycleMs.toDouble() / cycleMs).roundToInt().coerceAtLeast(1)
        val cycleIndex = if (slot.path != null) {
            Math.floorDiv(timeMs + slot.seed * 977L, cycleMs)
        } else {
            Math.floorDiv(timeMs + slot.seed * 1_291L, cycleMs)
        }
        return Math.floorMod(cycleIndex, eligibleCycles.toLong()) ==
            Math.floorMod(slot.seed.toLong(), eligibleCycles.toLong())
    }

    /**
     * Foreshortens the frontal pose during the middle of a 180° turn. At the side/front
     * boundaries its opaque width matches the authored side silhouette, avoiding a one-frame
     * body-width pop while the face changes orientation.
     */
    internal fun turnPerspective(frame: CharacterFrame, style: CharacterStyle, localTimeMs: Long): CharacterFrame {
        val start = NpcPoseLibrary.TURN_MS / 3
        val end = NpcPoseLibrary.TURN_MS * 2 / 3
        if (localTimeMs !in start..end) return frame
        val ratios = turnSideFrontRatios.computeIfAbsent(style.id) {
            fun matchingRatio(sideTime: Long, frontTime: Long): Float {
                val side = CharacterPainter.paint(style, NpcPoseLibrary.turn(sideTime))
                val front = CharacterPainter.paint(style, NpcPoseLibrary.turn(frontTime))
                return (visibleWidth(side).toFloat() / visibleWidth(front).coerceAtLeast(1)).coerceIn(0.55f, 1f)
            }
            TurnWidthRatios(
                intoFront = matchingRatio(start - 1, start),
                outOfFront = matchingRatio(end, end + 1),
            )
        }
        val ratio = if (localTimeMs <= NpcPoseLibrary.TURN_MS / 2) ratios.intoFront else ratios.outOfFront
        val progress = (localTimeMs - start).toFloat() / (end - start).coerceAtLeast(1)
        val openness = sin(PI * progress).toFloat().coerceIn(0f, 1f)
        val scaleX = ratio + (1f - ratio) * openness
        if (scaleX >= 0.999f) return frame
        val source = frame.image
        val image = PixelBuffer(source.width, source.height)
        val pivot = frame.anchors.feet.x
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val sourceX = (pivot + (x - pivot) / scaleX).roundToInt()
            if (sourceX in 0 until source.width) {
                val color = source[sourceX, y]
                if (color ushr 24 != 0) image.set(x, y, color)
            }
        }
        fun Point.turnScaled() = Point(
            (pivot + (x - pivot) * scaleX).roundToInt().coerceIn(0, image.width - 1), y,
        )
        val anchors = frame.anchors
        return CharacterFrame(
            image,
            anchors.copy(
                head = anchors.head.turnScaled(), leftHand = anchors.leftHand.turnScaled(),
                rightHand = anchors.rightHand.turnScaled(), mouth = anchors.mouth.turnScaled(),
                back = anchors.back.turnScaled(),
            ),
        )
    }

    private fun visibleWidth(frame: CharacterFrame): Int {
        var minX = frame.image.width
        var maxX = -1
        frame.image.pixels.forEachIndexed { index, color ->
            if (color ushr 24 != 0) {
                val x = index % frame.image.width
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
            }
        }
        return if (maxX < minX) 1 else maxX - minX + 1
    }

    private fun busJolt(timeMs: Long): Int = when (Math.floorMod(timeMs, 1_800L)) {
        in 0L..89L -> 1
        in 90L..189L -> 0
        in 190L..299L -> -1
        else -> 0
    }

    /** Sombra suave sob os pés: escurece o cenário (não acrescenta cor ao personagem). */
    internal fun drawGroundShadow(b: PixelBuffer, x: Int, floorY: Int, style: CharacterStyle, scale: Float) {
        val half = (((style.artProfile.proportions.hipWidth + 6) * scale / 2f).roundToInt()).coerceAtLeast(2)
        for (dy in -1..1) {
            val w = if (dy == 0) half else half - 2
            for (dx in -w..w) {
                val px = x + dx; val py = floorY + dy
                if (px in 0 until b.width && py in 0 until b.height) b.set(px, py, PixelBuffer.mix(b[px, py], SHADOW, 0.28f))
            }
        }
    }

    fun scaleFrameForAmbient(frame: CharacterFrame): CharacterFrame = scaleFrameForAmbient(frame, AMBIENT_SCALE)

    /** Reduz em torno da âncora dos pés, com pixels nítidos (nearest-neighbor). */
    fun scaleFrameForAmbient(frame: CharacterFrame, scale: Float): CharacterFrame {
        if (scale >= 0.999f) return frame
        val pivot = frame.anchors.seatHip ?: frame.anchors.feet
        val source = frame.image
        val scaled = PixelBuffer(source.width, source.height)
        for (y in 0 until scaled.height) {
            val sourceY = (pivot.y + (y - pivot.y) / scale).roundToInt()
            if (sourceY !in 0 until source.height) continue
            for (x in 0 until scaled.width) {
                val sourceX = (pivot.x + (x - pivot.x) / scale).roundToInt()
                if (sourceX in 0 until source.width) {
                    val color = source[sourceX, sourceY]
                    if (color ushr 24 != 0) scaled.set(x, y, color)
                }
            }
        }
        fun shrink(point: Point) = Point(
            (pivot.x + (point.x - pivot.x) * scale).roundToInt().coerceIn(0, scaled.width - 1),
            (pivot.y + (point.y - pivot.y) * scale).roundToInt().coerceIn(0, scaled.height - 1),
        )
        val anchors = frame.anchors
        return CharacterFrame(
            scaled,
            CharacterAnchors(
                feet = shrink(anchors.feet),
                head = shrink(anchors.head),
                leftHand = shrink(anchors.leftHand),
                rightHand = shrink(anchors.rightHand),
                mouth = shrink(anchors.mouth),
                back = shrink(anchors.back),
                seatHip = anchors.seatHip?.let(::shrink),
            ),
        )
    }
}

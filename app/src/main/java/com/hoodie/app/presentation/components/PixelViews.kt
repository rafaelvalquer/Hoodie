package com.hoodie.app.presentation.components

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.BuildConfig
import com.hoodie.app.pixel.performance.ScenePerformanceMonitor
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Expression
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.HoodieSprites
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Posture
import com.hoodie.app.pixel.sprite.SpriteRequest
import com.hoodie.app.pixel.animation.IdleDirector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import com.hoodie.app.engine.interaction.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Fixed animation time for previews and visual regression captures. Null keeps live rendering. */
data class PixelRenderFrame(
    val animationMillis: Long = 1_000L,
    val minuteOfDay: Int = 10 * 60,
    val period: DayPeriod = DayPeriod.DAY,
) {
    init {
        require(animationMillis >= 0)
        require(minuteOfDay in 0 until 24 * 60)
    }
}

val LocalPixelRenderFrame = staticCompositionLocalOf<PixelRenderFrame?> { null }

/**
 * A cena viva. Um único relógio de frames (withFrameNanos) dirige a máquina de
 * estados e o renderer; cada animação tem seu próprio FPS. Quando a tela some,
 * a composição pausa e nada roda em background — o relógio real continua e o
 * estado é recalculado ao voltar.
 */
@Composable
fun HoodieSceneView(
    visual: VisualState?,
    modifier: Modifier = Modifier,
    reactions: Flow<AnimationId>? = null,
    greet: Boolean = true,
    speed: Float = 1f,
    periodOverride: DayPeriod? = null,
    sceneOverride: PixelScene? = null,
    onScenePresented: ((SceneId) -> Unit)? = null,
    interactionsEnabled: Boolean = false,
    interactionRequests: Flow<InteractionRequest>? = null,
    onHoodieGesture: (InteractionGesture, RenderFrame) -> Unit = { _, _ -> },
    interactionAllowed: (InteractionRequest, RenderFrame) -> Boolean = { _, _ -> true },
    onInteractionStarted: (InteractionRequest) -> Unit = {},
    onInteractionFinished: (InteractionRequest) -> Unit = {},
    reactionRequestsEnabled: Boolean = interactionsEnabled,
    onVisualPresented: ((VisualState) -> Unit)? = null,
    /** Fixed seed for reproducible diagnostic runs; normal Home keeps its existing randomness. */
    randomSeed: Long? = null,
    /** Diagnostic workload clock; publication cadence is still measured in real monotonic time. */
    animationStepMs: Long? = null,
    clockMinuteOverride: Int? = null,
    /** Efeito desenhado onde a cena é tocada (inclusive no Hoodie); null = nenhum. */
    tapEffect: com.hoodie.app.pixel.effects.TapEffectStyle? = null,
) {
    val fixedFrame = LocalPixelRenderFrame.current
    val machine = remember(fixedFrame != null, randomSeed) {
        AnimationStateMachine(if (fixedFrame != null) Random(0) else Random(randomSeed ?: System.nanoTime()))
    }
    val renderer = remember { SceneRenderer() }
    val currentInteractionsEnabled by rememberUpdatedState(interactionsEnabled)
    val interaction = remember { InteractionPresentation() }
    val gestureCallback by rememberUpdatedState(onHoodieGesture)
    val allowedCallback by rememberUpdatedState(interactionAllowed)
    val startedCallback by rememberUpdatedState(onInteractionStarted)
    val finishedCallback by rememberUpdatedState(onInteractionFinished)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(machine, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) interaction.active = true
            if (event == Lifecycle.Event.ON_STOP) {
                interaction.active = false
                interaction.frame?.scene?.id?.let(com.hoodie.app.pixel.performance.SceneDiagnostics::stopPresentation)
                machine.cancelInteractiveReactions()
                interaction.request?.let { finishedCallback(it) }
                interaction.request = null
                interaction.frame = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            interaction.frame?.scene?.id?.let(com.hoodie.app.pixel.performance.SceneDiagnostics::stopPresentation)
            machine.cancelInteractiveReactions()
            interaction.request?.let { finishedCallback(it) }
            interaction.request = null
            interaction.frame = null
        }
    }
    val rendererMutex = remember(renderer) { Mutex() }
    DisposableEffect(renderer) { onDispose { renderer.dispose() } }
    val bitmap = remember { Bitmap.createBitmap(PixelScene.SCENE_W, PixelScene.SCENE_H, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
    var frames by remember { mutableIntStateOf(0) }
    val clock = remember { VirtualClock() }
    val currentSpeed by rememberUpdatedState(speed)
    val currentPeriod by rememberUpdatedState(periodOverride)
    val currentAnimationStep by rememberUpdatedState(animationStepMs)
    val currentClockMinute by rememberUpdatedState(clockMinuteOverride)
    fun animationNow() = if (currentAnimationStep != null) clock.current() else clock.now(currentSpeed)
    val currentScenePresented by rememberUpdatedState(onScenePresented)
    val visualPresentedCallback by rememberUpdatedState(onVisualPresented)

    fun presented(frame: RenderFrame, renderedRequest: InteractionRequest?) {
        interaction.frame?.scene?.id?.takeIf { it != frame.scene.id }?.let(com.hoodie.app.pixel.performance.SceneDiagnostics::stopPresentation)
        val actual = frame.copy(env = renderer.lastPresentedEnvironment ?: frame.env,
            interactionElements = renderer.lastInteractionElements, interactionHitMask = renderer.lastInteractionHitMask)
        interaction.present(actual, renderedRequest, startedCallback, finishedCallback)
        frames++
        currentScenePresented?.invoke(frame.scene.id)
        frame.interactionVisual?.let { visualPresentedCallback?.invoke(it) }
    }

    LaunchedEffect(interactionRequests, reactionRequestsEnabled) {
        if (reactionRequestsEnabled) interactionRequests?.collect { request ->
            val frame = interaction.frame
            interaction.request?.takeIf { frame != null && !allowedCallback(it, frame) }?.let {
                machine.cancelInteractiveReactions()
                finishedCallback(it)
                interaction.request = null
            }
            if (interaction.active && frame != null && allowedCallback(request, frame) &&
                machine.interact(request.response, request.scene, animationNow(),
                    request.expiresAtMs - SystemClock.uptimeMillis())) interaction.request = request
            else finishedCallback(request)
        }
        else machine.cancelInteractiveReactions()
    }

    LaunchedEffect(visual, fixedFrame) {
        // Ao abrir o app o ReactionDirector decide se ele reage (olha, acena, sorri…).
        visual?.let { machine.setVisual(it, if (fixedFrame != null) 1L else animationNow(), greet = greet) }
    }
    LaunchedEffect(reactions, fixedFrame) { if (fixedFrame == null) reactions?.collect { machine.react(it, animationNow()) } }
    LaunchedEffect(fixedFrame, visual, sceneOverride) {
        if (fixedFrame != null) {
            machine.frame(fixedFrame.animationMillis, fixedFrame.minuteOfDay, fixedFrame.period)?.let { f ->
                val rendered = if (sceneOverride == null) f else f.copy(scene = sceneOverride)
                val renderedRequest = interaction.request
                renderAndPresent(renderer, rendererMutex, bitmap, rendered, fixedFrame.animationMillis, currentInteractionsEnabled) { presented(rendered, renderedRequest) }
            }
            return@LaunchedEffect
        }
        val pacer = com.hoodie.app.pixel.performance.SceneFramePacer()
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (pacer.shouldRender(frameNanos)) {
                val t = currentAnimationStep?.let { clock.advance((it * currentSpeed).toLong().coerceAtLeast(1)) } ?: clock.now(currentSpeed)
                val time = LocalTime.now()
                val period = currentPeriod ?: DayPeriod.of(time.hour)
                interaction.request?.let { request ->
                    val frame = interaction.frame
                    if (!interaction.active || frame == null || !allowedCallback(request, frame)) {
                        machine.cancelInteractiveReactions()
                        finishedCallback(request)
                        interaction.request = null
                    }
                }
                machine.frame(t, currentClockMinute ?: (time.hour * 60 + time.minute), period)?.let { f ->
                    val rendered = if (sceneOverride == null) f else f.copy(scene = sceneOverride)
                    val renderedRequest = interaction.request
                    renderAndPresent(renderer, rendererMutex, bitmap, rendered, t, currentInteractionsEnabled) { presented(rendered, renderedRequest) }
                }
            }
        }
    }
    PixelImageContent(image, PixelScene.SCENE_W, PixelScene.SCENE_H, modifier,
        Modifier.hoodieInteractionTarget(interactionsEnabled, { interaction.frame }) { gesture, frame ->
            if (interaction.active) gestureCallback(gesture, frame)
        },
        invalidate = { frames },
        tapEffect = tapEffect,
    )
}

/** All scenes share one renderer and pixel buffer, so its lock spans both drawing and bitmap
 * presentation. Complex scene pixel work runs away from the Compose frame callback. No frame queue is
 * built: the loop awaits each frame before asking the renderer for the next one. */
private suspend fun renderAndPresent(
    renderer: SceneRenderer,
    rendererMutex: Mutex,
    bitmap: Bitmap,
    frame: RenderFrame,
    timeMs: Long,
    interactionsEnabled: Boolean,
    onPresented: () -> Unit,
) {
    fun present(buffer: PixelBuffer) {
        val bitmapStart = if (BuildConfig.DEBUG && com.hoodie.app.pixel.performance.SceneDiagnostics.enabled) ScenePerformanceMonitor.nowNanos() else 0L
        if (bitmapStart > 0L) {
            ScenePerformanceMonitor.traceBegin("Hoodie.${frame.scene.id.name}.bitmapUpdate")
        }
        bitmap.setPixels(buffer.pixels, 0, buffer.width, 0, 0, buffer.width, buffer.height)
        if (bitmapStart > 0L) {
            ScenePerformanceMonitor.traceEnd()
            ScenePerformanceMonitor.recordBitmapUpdate(ScenePerformanceMonitor.nowNanos() - bitmapStart)
            if (com.hoodie.app.pixel.performance.SceneDiagnostics.enabled) com.hoodie.app.pixel.performance.SceneDiagnostics.presented(frame.scene.id, ScenePerformanceMonitor.nowNanos() - bitmapStart)
        }
        onPresented()
    }

    rendererMutex.withLock {
        renderer.setInteractionsEnabled(interactionsEnabled)
        val buffer = when (frame.scene.id) {
            SceneId.OFFICE, SceneId.SHOPPING, SceneId.STORE, SceneId.LEISURE,
            SceneId.BAKERY, SceneId.MALL, SceneId.BEACH, SceneId.CINEMA, SceneId.PHARMACY, SceneId.HOTEL ->
                withContext(Dispatchers.Default) { renderer.render(frame, timeMs) }
            // Warm device measurements favor Main for the cached, lightweight restaurant draw.
            else -> renderer.render(frame, timeMs)
        }
        present(buffer)
    }
}

/** Tempo virtual (permite câmera lenta/rápida no Pixel Lab). */
private class VirtualClock {
    private var lastReal = -1L
    private var virtual = 1L
    fun current() = virtual
    fun advance(deltaMs: Long): Long { virtual += deltaMs; return virtual }
    fun now(speed: Float): Long {
        val real = SystemClock.uptimeMillis()
        if (lastReal >= 0) virtual += ((real - lastReal) * speed).toLong()
        lastReal = real
        return virtual
    }
}

/**
 * Desenha um bitmap lógico escalado por fator INTEIRO e sem filtro: nunca
 * interpolação bilinear em pixel art.
 */
@Composable
fun PixelImage(image: ImageBitmap, logicalW: Int, logicalH: Int, modifier: Modifier = Modifier, invalidate: () -> Int = { 0 }) {
    PixelImageContent(image, logicalW, logicalH, modifier, Modifier, invalidate)
}

/** Input belongs to the drawn canvas, giving scene and character targets distinct semantics nodes. */
@Composable
private fun PixelImageContent(image: ImageBitmap, logicalW: Int, logicalH: Int, modifier: Modifier,
    canvasModifier: Modifier, invalidate: () -> Int,
    tapEffect: com.hoodie.app.pixel.effects.TapEffectStyle? = null,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { if (maxHeight == Dp.Infinity) (logicalH * 6).toFloat() else maxHeight.toPx() }
        val scale = max(1, floor(min(maxW / logicalW, maxH / logicalH)).toInt())
        val w = with(density) { (logicalW * scale).toDp() }
        val h = with(density) { (logicalH * scale).toDp() }
        // Efeito de toque só dentro da imagem, com o mesmo tamanho de pixel da cena.
        TapEffectLayer(tapEffect, Modifier.size(w, h), pixelSize = with(density) { scale.toDp() }, snapToGrid = true) {
            Canvas(Modifier.size(w, h).then(canvasModifier)) {
                invalidate()
                drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(logicalW, logicalH), dstSize = IntSize(logicalW * scale, logicalH * scale), filterQuality = FilterQuality.None)
            }
        }
    }
}

/**
 * Sprite animado isolado (onboarding, perfil, galerias), servido pelo mesmo
 * SpriteProvider da cena. Animações curtas se repetem com uma pausa entre elas.
 */
@Composable
fun AnimatedHoodie(
    anim: AnimationId,
    modifier: Modifier = Modifier,
    size: Dp = 144.dp,
    expression: Expression? = null,
    speed: Float = 1f,
    direction: Direction = Direction.FRONT,
    posture: Posture = Posture.STANDING,
) {
    val fixedFrame = LocalPixelRenderFrame.current
    val w = HoodiePainter.WIDTH; val h = HoodiePainter.HEIGHT
    val bitmap = remember { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
    var frames by remember { mutableIntStateOf(0) }
    val currentAnim by rememberUpdatedState(anim)
    val currentExpr by rememberUpdatedState(expression)
    val currentSpeed by rememberUpdatedState(speed)
    val currentDir by rememberUpdatedState(direction)
    val currentPosture by rememberUpdatedState(posture)
    LaunchedEffect(fixedFrame, if (fixedFrame != null) anim else null, if (fixedFrame != null) expression else null, if (fixedFrame != null) direction else null, if (fixedFrame != null) posture else null) {
        if (fixedFrame != null) {
            val request = SpriteRequest(anim, direction, 0, posture,
                IdleDirector(Random(0)).overlay(fixedFrame.animationMillis, allowLook = false).copy(expression = expression))
            val elapsed = if (anim.loop) fixedFrame.animationMillis else fixedFrame.animationMillis % (anim.durationMs + 1_500)
            val frame = HoodieSprites.provider.frameAt(request, elapsed)
            val canvas = PixelBuffer(w, h)
            canvas.clear()
            canvas.blit(frame.image, HoodiePainter.FEET.x - frame.anchors.feet.x, HoodiePainter.FEET.y - frame.anchors.feet.y)
            frame.itemOverlay?.let { HoodiePainter.drawItemAt(canvas, it, frame.anchors.rightHand) }
            bitmap.setPixels(canvas.pixels, 0, w, 0, 0, w, h)
            frames++
            return@LaunchedEffect
        }
        val clock = VirtualClock()
        val idle = IdleDirector(Random)
        val canvas = PixelBuffer(w, h)
        var lastKey: Any? = null
        while (true) {
            withFrameMillis {
                val t = clock.now(currentSpeed)
                val a = currentAnim
                val elapsed = if (a.loop) t else t % (a.durationMs + 1_500)
                val req = SpriteRequest(a, currentDir, 0, currentPosture, idle.overlay(t, allowLook = false).copy(expression = currentExpr))
                val f = HoodieSprites.provider.frameAt(req, elapsed)
                val key = f.image to f.itemOverlay
                if (key != lastKey) {
                    lastKey = key
                    canvas.clear()
                    canvas.blit(f.image, HoodiePainter.FEET.x - f.anchors.feet.x, HoodiePainter.FEET.y - f.anchors.feet.y)
                    f.itemOverlay?.let { HoodiePainter.drawItemAt(canvas, it, f.anchors.rightHand) }
                    bitmap.setPixels(canvas.pixels, 0, w, 0, 0, w, h)
                    frames++
                }
            }
        }
    }
    Box(modifier.size(size * w / h, size), contentAlignment = Alignment.Center) {
        PixelImage(image, w, h) { frames }
    }
}

/** Miniatura estática de cena (Scene Gallery). */
@Composable
fun SceneThumbnail(scene: SceneId, period: DayPeriod, modifier: Modifier = Modifier) {
    val image = remember(scene, period) {
        val s = SceneRegistry[scene]
        val buf: PixelBuffer = SceneRenderer().renderEmpty(s, SceneEnv(period, 10 * 60 + 8, tvOn = true), 1_000)
        val bmp = Bitmap.createBitmap(buf.width, buf.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(buf.pixels, 0, buf.width, 0, 0, buf.width, buf.height)
        bmp.asImageBitmap()
    }
    PixelImage(image, PixelScene.SCENE_W, PixelScene.SCENE_H, modifier)
}

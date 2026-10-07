package com.hoodie.app.presentation.components

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
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
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

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
 * A cena viva. Um único relógio de frames (withFrameMillis) dirige a máquina de
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
) {
    val fixedFrame = LocalPixelRenderFrame.current
    val machine = remember(fixedFrame != null) {
        AnimationStateMachine(if (fixedFrame != null) Random(0) else Random(System.nanoTime()))
    }
    val renderer = remember { SceneRenderer() }
    val bitmap = remember { Bitmap.createBitmap(PixelScene.SCENE_W, PixelScene.SCENE_H, Bitmap.Config.ARGB_8888) }
    val image = remember { bitmap.asImageBitmap() }
    var frames by remember { mutableIntStateOf(0) }
    val clock = remember { VirtualClock() }
    val currentSpeed by rememberUpdatedState(speed)
    val currentPeriod by rememberUpdatedState(periodOverride)

    LaunchedEffect(visual, fixedFrame) {
        // Ao abrir o app o ReactionDirector decide se ele reage (olha, acena, sorri…).
        visual?.let { machine.setVisual(it, if (fixedFrame != null) 1L else clock.now(currentSpeed), greet = greet) }
    }
    LaunchedEffect(reactions, fixedFrame) { if (fixedFrame == null) reactions?.collect { machine.react(it, clock.now(currentSpeed)) } }
    LaunchedEffect(fixedFrame, visual, sceneOverride) {
        if (fixedFrame != null) {
            machine.frame(fixedFrame.animationMillis, fixedFrame.minuteOfDay, fixedFrame.period)?.let { f ->
                val buf = renderer.render(if (sceneOverride == null) f else f.copy(scene = sceneOverride), fixedFrame.animationMillis)
                bitmap.setPixels(buf.pixels, 0, buf.width, 0, 0, buf.width, buf.height)
                frames++
            }
            return@LaunchedEffect
        }
        var last = -1L
        while (true) {
            withFrameMillis {
                val t = clock.now(currentSpeed)
                if (t - last >= FRAME_MS) {
                    last = t
                    val time = LocalTime.now()
                    val period = currentPeriod ?: DayPeriod.of(time.hour)
                    machine.frame(t, time.hour * 60 + time.minute, period)?.let { f ->
                        val buf = renderer.render(if (sceneOverride == null) f else f.copy(scene = sceneOverride), t)
                        bitmap.setPixels(buf.pixels, 0, buf.width, 0, 0, buf.width, buf.height)
                        frames++
                    }
                }
            }
        }
    }
    PixelImage(image, PixelScene.SCENE_W, PixelScene.SCENE_H, modifier) { frames }
}

/** Tempo virtual (permite câmera lenta/rápida no Pixel Lab). */
private class VirtualClock {
    private var lastReal = -1L
    private var virtual = 1L
    fun now(speed: Float): Long {
        val real = SystemClock.uptimeMillis()
        if (lastReal >= 0) virtual += ((real - lastReal) * speed).toLong()
        lastReal = real
        return virtual
    }
}

private const val FRAME_MS = 33L

/**
 * Desenha um bitmap lógico escalado por fator INTEIRO e sem filtro: nunca
 * interpolação bilinear em pixel art.
 */
@Composable
fun PixelImage(image: ImageBitmap, logicalW: Int, logicalH: Int, modifier: Modifier = Modifier, invalidate: () -> Int = { 0 }) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { if (maxHeight == Dp.Infinity) (logicalH * 6).toFloat() else maxHeight.toPx() }
        val scale = max(1, floor(min(maxW / logicalW, maxH / logicalH)).toInt())
        val w = with(density) { (logicalW * scale).toDp() }
        val h = with(density) { (logicalH * scale).toDp() }
        Canvas(Modifier.size(w, h)) {
            invalidate()
            drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(logicalW, logicalH), dstSize = IntSize(logicalW * scale, logicalH * scale), filterQuality = FilterQuality.None)
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

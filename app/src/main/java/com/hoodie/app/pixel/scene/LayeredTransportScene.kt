package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.art.SceneArt
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.SpriteFrame
import com.hoodie.app.pixel.transport.TransportVibration
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Cena de transporte V3 composta de camadas de arte (docs/transport-art-bible.md §4–7):
 *
 * `bg_far` → `bg_mid` → `bg_near` (paralaxe, recortados por `masks`) → `vehicle_back` → atores →
 * `vehicle_front` → `foreground` → luz do período → `emissive` + janelas acesas do fundo.
 *
 * Balanço ([TransportMotion]/vibração) só em `vehicle_*` e nos ocupantes; paralaxe só em `bg_*`;
 * `foreground` fica fixo (rodas, sombra). O código só cuida do que é dinâmico.
 */
abstract class LayeredTransportScene(id: SceneId, protected val art: SceneArt) : PixelScene(id) {
    /** Velocidade de cada plano em px/s com o perfil a 1,0. */
    protected open val farPxPerSecond = 2f
    protected open val midPxPerSecond = 9f
    protected open val nearPxPerSecond = 40f
    protected open val fallbackSpeed = .8f

    private val covered = BooleanArray(SCENE_W * SCENE_H)
    private var pendingCharacter: Triple<SpriteFrame, Int, Int>? = null

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) = b.fill(OUTLINE)

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, t -> drawBackLayers(b, env, t) },
        Prop(FRONT_BASELINE) { b, env, t -> drawFrontLayers(b, env, t) },
    )

    /** Pés do Hoodie sentado: o slot da arte. */
    protected fun seatLeft(frame: SpriteFrame): Int? = art.slot("seat_feet")?.let { it.x - frame.anchors.feet.x }

    override fun drawCharacter(buffer: PixelBuffer, frame: SpriteFrame, x: Int, y: Int, timeMs: Long, env: SceneEnv) {
        val seat = seatLeft(frame)
        if (seat != null && kotlin.math.abs(x - seat) <= SEATED_TOLERANCE) {
            buffer.blit(frame.image, x, y + occupantSway(timeMs, env))
        } else {
            // Fora do banco (entrando/saindo): fica na frente do veículo.
            pendingCharacter = Triple(frame, x, y)
        }
    }

    protected fun offset(timeMs: Long, env: SceneEnv, pxPerSecond: Float): Int {
        val speed = TransportMotion.speed(env.transportAmbient, fallbackSpeed)
        return floor(timeMs.coerceAtLeast(0) * speed * pxPerSecond / 1000.0).toInt()
    }

    private fun drawBackLayers(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        val p = env.period
        val mask = art.mask
        listOf("bg_far" to farPxPerSecond, "bg_mid" to midPxPerSecond, "bg_near" to nearPxPerSecond).forEach { (name, speed) ->
            val layer = art.layer(name, p) ?: return@forEach
            blitScrolled(b, layer, offset(timeMs, env, speed), mask)
        }
        art.layer("vehicle_back", p)?.let { b.blit(it, 0, bodySway(timeMs, env)) }
    }

    private fun drawFrontLayers(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        val p = env.period
        val dy = bodySway(timeMs, env)
        covered.fill(false)
        art.layer("vehicle_back", p)?.let { markCovered(it, dy) }
        art.layer("vehicle_front", p)?.let { b.blit(it, 0, dy); markCovered(it, dy) }
        art.layer("foreground", p)?.let { b.blit(it, 0, 0); markCovered(it, 0) }
        drawDynamicFront(b, env, timeMs)
        pendingCharacter?.let { (frame, x, y) -> b.blit(frame.image, x, y) }
        pendingCharacter = null
    }

    /** Ganchos procedurais da cena (rodas, partículas…), desenhados sobre a frente do veículo. */
    protected open fun drawDynamicFront(b: PixelBuffer, env: SceneEnv, timeMs: Long) = Unit

    override fun drawPostLighting(buffer: PixelBuffer, env: SceneEnv, timeMs: Long) {
        val p = env.period
        if (p != DayPeriod.EVENING && p != DayPeriod.NIGHT) return
        val emissive = art.layer("emissive", p) ?: return
        val glow = emissive.pixels.filter { it ushr 24 == 0xFF }.toHashSet()
        // Janelas acesas do fundo: pixels de `bg_*` com cor emissiva, onde o veículo não cobre.
        listOf("bg_far" to farPxPerSecond, "bg_mid" to midPxPerSecond, "bg_near" to nearPxPerSecond).forEach { (name, speed) ->
            val layer = art.layer(name, p) ?: return@forEach
            val off = offset(timeMs, env, speed)
            for (y in 0 until SCENE_H) for (x in 0 until SCENE_W) {
                val i = y * SCENE_W + x
                if (covered[i] || art.mask?.get(i) == false) continue
                val c = layer.pixels[y * SCENE_W + Math.floorMod(x + off, SCENE_W)]
                if (c in glow) buffer.pixels[i] = c
            }
        }
        buffer.blit(emissive, 0, bodySway(timeMs, env))
    }

    private fun blitScrolled(b: PixelBuffer, layer: PixelBuffer, off: Int, mask: BooleanArray?) {
        val src = layer.pixels
        val dst = b.pixels
        for (y in 0 until SCENE_H) {
            val row = y * SCENE_W
            for (x in 0 until SCENE_W) {
                if (mask != null && !mask[row + x]) continue
                val c = src[row + Math.floorMod(x + off, SCENE_W)]
                if (c ushr 24 == 0xFF) dst[row + x] = c else if (c ushr 24 != 0) b.set(x, y, c)
            }
        }
    }

    private fun markCovered(layer: PixelBuffer, dy: Int) {
        for (y in 0 until SCENE_H) {
            val ty = y + dy
            if (ty !in 0 until SCENE_H) continue
            for (x in 0 until SCENE_W) if (layer.pixels[y * SCENE_W + x] ushr 24 != 0) covered[ty * SCENE_W + x] = true
        }
    }

    protected open fun bodySway(t: Long, env: SceneEnv): Int = when (env.transportAmbient?.vibration) {
        TransportVibration.MEDIUM -> SWAY_MEDIUM[((t.coerceAtLeast(0) / 210L) % SWAY_MEDIUM.size).toInt()]
        TransportVibration.NONE -> 0
        TransportVibration.LOW, null -> SWAY_LOW[((t.coerceAtLeast(0) / 320L) % SWAY_LOW.size).toInt()]
    }

    /** O ocupante acompanha a carroceria com um pequeno atraso. */
    protected fun occupantSway(t: Long, env: SceneEnv) = bodySway(t - 55L, env)

    companion object {
        const val OUTLINE = 0xFF1A1C33.toInt()
        const val FRONT_BASELINE = 10_000
        const val SEATED_TOLERANCE = 6
        val SWAY_LOW = intArrayOf(0, 0, 1, 0, 0, -1, 0, 0)
        val SWAY_MEDIUM = intArrayOf(0, 1, 1, 0, -1, 0, 1, 0)
    }
}

/** Carro V3 (enquadramento B: três quartos, frente à esquerda), arte em `car.aseprite`. */
class CarSceneV3(art: SceneArt) : LayeredTransportScene(SceneId.CAR, art) {
    private val seat = art.slot("seat_feet") ?: com.hoodie.app.pixel.sprite.Point(CarScene.SEAT_X, CarScene.SEAT_Y)
    private val door = art.slot("door_feet") ?: seat
    override val spots = mapOf(
        SpotId.SEAT to Spot(seat.x, seat.y),
        SpotId.CENTER to Spot(seat.x, seat.y),
        SpotId.DOOR to Spot(door.x, door.y),
    )
    override val defaultSpot = SpotId.SEAT

    private val wheels = art.slotsWithPrefix("wheel_").values.toList()

    /** Raios girando sobre o aro desenhado na arte (que fica fixo no chão). */
    override fun drawDynamicFront(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        val angle = offset(timeMs, env, nearPxPerSecond) / 8.0
        wheels.forEach { c ->
            for (k in 0..2) {
                val a = angle + k * 2 * Math.PI / 3
                b.line(c.x, c.y, c.x + (cos(a) * 7).roundToInt(), c.y + (sin(a) * 7).roundToInt(), OUTLINE)
            }
            b.disc(c.x, c.y, 3, OUTLINE)
        }
    }

    override fun lights(env: SceneEnv): List<Light> =
        if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) listOf(Light.Glow(11, 219, 36, .55f)) else emptyList()
}

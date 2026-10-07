package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.SpriteFrame
import com.hoodie.app.pixel.transport.TransportVibration
import kotlin.math.floor

/** Carro em perfil lateral; o relógio da cena governa toda a animação. */
class CarScene : PixelScene(SceneId.CAR) {
    override val spots = mapOf(
        SpotId.SEAT to Spot(SEAT_X, SEAT_Y),
        SpotId.CENTER to Spot(SEAT_X, SEAT_Y),
        SpotId.DOOR to Spot(DOOR_X, SEAT_Y),
    )
    override val defaultSpot = SpotId.SEAT
    override val walkInPlace = false
    override val hasAnimatedDoor = true

    private var cachedPeriod: DayPeriod? = null
    private var cachedSeed = Int.MIN_VALUE
    private var cityStrip = PixelBuffer(STRIP_W, 132)
    private var cityWindowLights = PixelBuffer(STRIP_W, 132)
    private var emissiveWindows = IntArray(0)
    private var roadStrip = PixelBuffer(STRIP_W, 116)
    private var postStrip = PixelBuffer(STRIP_W, 116)
    private var cachedLights: List<Light> = emptyList()
    private val shadow = PixelBuffer(SCENE_W, SCENE_H)
    private val interior = PixelBuffer(SCENE_W, SCENE_H)
    private val reflection = PixelBuffer(SCENE_W, SCENE_H)
    private val body = PixelBuffer(SCENE_W, SCENE_H)

    init {
        shadow.oval(31, 276, 215, 307, CarScenePalette.SHADOW)
        buildVehicle()
    }

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        ensureStrips(env)
        b.fill(CarScenePalette.SKY_TOP)
        for (y in 0 until CITY_H) {
            val c = when (y / 22) { 0 -> skyTop(env.period); 1 -> skyMid(env.period); else -> skyLow(env.period) }
            b.hline(0, 239, y, c)
        }
        if (env.period == DayPeriod.EVENING) b.disc(58, 55, 7, CarScenePalette.SUN)
        if (env.period == DayPeriod.MORNING || env.period == DayPeriod.DAY) {
            b.box(33, 28, 48, 31, CarScenePalette.CLOUD)
            b.box(36, 26, 43, 28, CarScenePalette.CLOUD)
            b.box(163, 45, 177, 48, CarScenePalette.CLOUD)
        }
    }

    override fun props(): List<Prop> = listOf(
        Prop(0) { b, env, t -> drawWorld(b, env, t) },
        Prop(160) { b, env, t -> drawInterior(b, env, t) },
        Prop(290) { b, env, t -> drawExterior(b, env, t) },
    )

    override fun lights(env: SceneEnv): List<Light> { ensureStrips(env); return cachedLights }

    /** Hoodie em perfil dentro da janela. O recorte evita pixels sobrepostos à moldura. */
    override fun drawCharacter(b: PixelBuffer, frame: SpriteFrame, x: Int, y: Int, timeMs: Long, env: SceneEnv) {
        val dy = occupantBounce(timeMs, env)
        if (x < SEATED_SPRITE_LEFT) {
            b.blit(frame.image, x, y + dy)
            return
        }
        val pixels = frame.image.pixels
        for (sy in 0 until frame.image.height) for (sx in 0 until frame.image.width) {
            val color = pixels[sy * frame.image.width + sx]
            if (color ushr 24 != 0 && inDriverWindow(x + sx, y + sy + dy)) b.set(x + sx, y + sy + dy, color)
        }
    }

    /** Camadas pós-iluminação: feixe, lanternas, painel e fumaça determinística. */
    override fun drawPostLighting(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        ensureStrips(env)
        if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) {
            val speed = TransportMotion.speed(env.transportAmbient, .8f)
            val cityOffset = offsetFor(traveledClockMs(timeMs), speed * CITY_SPEED)
            for (packed in emissiveWindows) {
                val wx = packed and 0xFFFF
                val wy = packed ushr 16
                val x = (wx - cityOffset).floorMod(PERIOD)
                val y = CITY_Y + wy
                if (x in 25..218 && y in 150..294) continue // cidade atrás da carroceria
                b.set(x, y, cityWindowLights[wx, wy])
            }
            drawTrafficSignal(b, timeMs)
        }
        if (env.period == DayPeriod.NIGHT) drawStars(b)
        if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) {
            for (row in 0..2) {
                val alpha = when (row) { 0 -> 0x18; 1 -> 0x10; else -> 0x08 }
                val color = (alpha shl 24) or 0xFFF0D080.toInt().and(0x00FFFFFF)
                b.line(32, 230 + row, 1, 216 + row * 3, color)
                b.box(1, 216 + row * 3, 32, 230 + row, color)
            }
            b.set(207, 228, 0xFFFF595C.toInt()); b.set(211, 228, 0xFFFF595C.toInt())
            b.set(105, 218, 0xFFFFD797.toInt())
        }
        drawExhaust(b, timeMs, env)
    }

    private fun drawWorld(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        ensureStrips(env)
        val speed = TransportMotion.speed(env.transportAmbient, .8f)
        val motionClock = traveledClockMs(timeMs)
        val cityOffset = offsetFor(motionClock, speed * CITY_SPEED)
        b.blitRegion(cityStrip, cityOffset, 0, SCENE_W, CITY_H, 0, CITY_Y)
        b.box(0, ROAD_Y, 239, ROAD_Y + 7, CarScenePalette.SIDEWALK)
        b.hline(0, 239, ROAD_Y + 7, CarScenePalette.OUTLINE)
        val roadOffset = offsetFor(motionClock, speed)
        b.blitRegion(roadStrip, roadOffset, 0, SCENE_W, 116, 0, ROAD_Y + 8)
        b.blitRegion(postStrip, offsetFor(motionClock, speed * POST_SPEED), 0, SCENE_W, 116, 0, ROAD_Y + 8)
        drawTrafficSignal(b, timeMs)
    }

    private fun drawInterior(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        val bodyDy = bodyBounce(timeMs, env)
        b.blit(shadow, 0, 0)
        b.blit(interior, 0, bodyDy)
    }

    private fun drawExterior(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        val motion = TransportMotion.speed(env.transportAmbient, .8f)
        val bodyDy = bodyBounce(timeMs, env)
        b.blit(reflection, 0, bodyDy)
        b.blit(body, 0, bodyDy)
        drawDoor(b, env.doorFrame, bodyDy)
        drawWheels(b, timeMs, motion)
    }

    private fun drawWheels(b: PixelBuffer, timeMs: Long, speed: Float) {
        val phase = floor(traveledClockMs(timeMs) * speed / 1000.0 / 0.30).toLong().mod(4).toInt()
        drawWheel(b, 62, phase)
        drawWheel(b, 184, phase)
    }

    private fun drawWheel(b: PixelBuffer, cx: Int, phase: Int) {
            b.disc(cx, 278, 16, CarScenePalette.OUTLINE)
            b.disc(cx, 278, 13, CarScenePalette.TIRE)
            b.disc(cx, 278, 7, CarScenePalette.WHEEL_LIGHT)
            when (phase) {
                0 -> b.hline(cx - 5, cx + 5, 278, CarScenePalette.WHEEL_DARK)
                1 -> b.line(cx - 4, 274, cx + 4, 282, CarScenePalette.WHEEL_DARK)
                2 -> b.vline(cx, 273, 283, CarScenePalette.WHEEL_DARK)
                else -> b.line(cx - 4, 282, cx + 4, 274, CarScenePalette.WHEEL_DARK)
            }
            b.disc(cx, 278, 2, CarScenePalette.OUTLINE)
    }

    /** A porta gira em quatro quadros nas transições existentes de cena. */
    private fun drawDoor(b: PixelBuffer, frame: Int, bodyDy: Int) {
        if (frame <= 0) return
        b.box(43, 226 + bodyDy, 153, 261 + bodyDy, CarScenePalette.BODY_LIGHT)
        b.box(44, 250 + bodyDy, 152, 251 + bodyDy, CarScenePalette.BODY_DARK)
        val progress = frame.coerceIn(1, SceneEnv.DOOR_OPEN)
        val topFrontX = 43 - progress * 6
        val bottomFrontX = 43 - progress * 4
        val topY = 225 + bodyDy - progress * 2
        val bottomY = 261 + bodyDy + progress * 2
        for (step in 0..36) {
            val rightX = 153
            val leftX = topFrontX + (bottomFrontX - topFrontX) * step / 36
            val y = topY + (bottomY - topY) * step / 36
            b.hline(leftX, rightX, y, if (step % 4 == 0) CarScenePalette.BODY_LIGHT else CarScenePalette.BODY)
        }
        b.line(topFrontX, topY, 153, 225 + bodyDy, CarScenePalette.OUTLINE)
        b.line(bottomFrontX, bottomY, 153, 261 + bodyDy, CarScenePalette.OUTLINE)
        b.line(topFrontX, topY, bottomFrontX, bottomY, CarScenePalette.OUTLINE)
        b.box(topFrontX + 7, 241 + bodyDy, topFrontX + 10, 243 + bodyDy, CarScenePalette.CHROME)
    }

    private fun drawExhaust(b: PixelBuffer, timeMs: Long, env: SceneEnv) {
        val epoch = timeMs.coerceAtLeast(0) / 650L
        val phase = timeMs.coerceAtLeast(0).mod(650).toInt()
        for (i in 0..1) {
            val life = (phase + i * 310) % 650
            if (life > 450) continue
            val hash = mixHash(env.daySeed, epoch.toInt() - i)
            val x = 211 + life / 90
            val y = 249 - life / 55 + (hash and 3)
            b.set(x, y, 0x665E6475)
            b.set(x + 1, y - 1, 0x445E6475)
        }
    }

    private fun drawTrafficSignal(b: PixelBuffer, timeMs: Long) {
        val phase = trafficPhase(timeMs)
        b.outlined(222, 157, 234, 190, CarScenePalette.SIGNAL_BODY, CarScenePalette.OUTLINE)
        b.disc(228, 164, 2, if (phase == TrafficPhase.STOP) CarScenePalette.SIGNAL_RED else CarScenePalette.SIGNAL_OFF)
        b.disc(228, 173, 2, if (phase == TrafficPhase.SLOW) CarScenePalette.SIGNAL_AMBER else CarScenePalette.SIGNAL_OFF)
        b.disc(228, 182, 2, if (phase == TrafficPhase.GO) CarScenePalette.SIGNAL_GREEN else CarScenePalette.SIGNAL_OFF)
    }

    internal fun traveledClockMs(timeMs: Long): Long {
        val t = timeMs.coerceAtLeast(0)
        val cycle = t / TRAFFIC_CYCLE_MS
        val p = t % TRAFFIC_CYCLE_MS
        val distance = when {
            p < TRAFFIC_GREEN_MS -> p
            p < TRAFFIC_SLOW_END_MS -> TRAFFIC_GREEN_MS + (p - TRAFFIC_GREEN_MS) / 2
            p < TRAFFIC_STOP_END_MS -> TRAFFIC_GREEN_MS + TRAFFIC_SLOW_MS / 2
            p < TRAFFIC_ACCEL_END_MS -> TRAFFIC_GREEN_MS + TRAFFIC_SLOW_MS / 2 + (p - TRAFFIC_STOP_END_MS) / 2
            else -> TRAFFIC_GREEN_MS + TRAFFIC_SLOW_MS + p - TRAFFIC_ACCEL_END_MS
        }
        return cycle * TRAFFIC_TRAVEL_MS + distance
    }

    internal fun trafficPhase(timeMs: Long): TrafficPhase {
        val p = timeMs.coerceAtLeast(0) % TRAFFIC_CYCLE_MS
        return when {
            p < TRAFFIC_GREEN_MS -> TrafficPhase.GO
            p < TRAFFIC_SLOW_END_MS -> TrafficPhase.SLOW
            p < TRAFFIC_STOP_END_MS -> TrafficPhase.STOP
            p < TRAFFIC_ACCEL_END_MS -> TrafficPhase.SLOW
            else -> TrafficPhase.GO
        }
    }

    internal fun stripPixel(layer: CarStrip, x: Int, y: Int, env: SceneEnv): Int {
        ensureStrips(env)
        val strip = when (layer) { CarStrip.CITY -> cityStrip; CarStrip.ROAD -> roadStrip; CarStrip.POSTS -> postStrip }
        return strip[x, y]
    }

    internal fun isDriverWindowPixel(x: Int, y: Int) = inDriverWindow(x, y)

    private fun buildVehicle() {
        interior.outlined(37, 153, 204, 237, CarScenePalette.BODY, CarScenePalette.OUTLINE)
        interior.box(44, 166, 153, 222, CarScenePalette.GLASS)
        interior.box(160, 166, 196, 222, CarScenePalette.GLASS)
        // Banco traseiro, apoio e mochila.
        interior.outlined(140, 188, 184, 229, CarScenePalette.SEAT, CarScenePalette.OUTLINE)
        interior.box(171, 179, 190, 195, CarScenePalette.BACKPACK)
        interior.box(175, 176, 186, 180, CarScenePalette.BACKPACK_LIGHT)
        interior.box(96, 214, 112, 220, CarScenePalette.WHEEL_DARK) // volante
        interior.disc(105, 217, 9, CarScenePalette.OUTLINE)
        interior.disc(105, 217, 6, CarScenePalette.WHEEL_LIGHT)
        interior.set(105, 217, CarScenePalette.OUTLINE)

        // Reflexo diagonal azul translúcido por pixels, evitando cores fora da paleta.
        for (i in 0..42) {
            val x = 48 + i
            val y = 174 + i / 3
            reflection.pixels[y * reflection.width + x] = CarScenePalette.GLASS_REFLECTION
            reflection.pixels[y * reflection.width + x + 1] = CarScenePalette.GLASS_REFLECTION
        }

        body.box(36, 225, 207, 263, CarScenePalette.BODY)
        body.box(43, 233, 201, 254, CarScenePalette.BODY_LIGHT)
        body.box(43, 254, 201, 261, CarScenePalette.BODY_DARK)
        body.vline(121, 226, 261, CarScenePalette.OUTLINE)
        body.box(98, 237, 110, 239, CarScenePalette.CHROME)
        body.box(171, 237, 183, 239, CarScenePalette.CHROME)
        // Moldura do teto e pilares, janela trapezoidal para a cabine e o rosto.
        body.outlined(39, 151, 205, 165, CarScenePalette.BODY, CarScenePalette.OUTLINE)
        body.line(39, 163, 48, 226, CarScenePalette.OUTLINE)
        body.line(154, 163, 154, 226, CarScenePalette.OUTLINE)
        body.line(205, 163, 197, 226, CarScenePalette.OUTLINE)
        body.box(37, 223, 207, 226, CarScenePalette.BODY_LIGHT)
        body.box(28, 245, 40, 253, CarScenePalette.BUMPER)
        body.box(202, 245, 218, 253, CarScenePalette.BUMPER)
        body.box(32, 237, 40, 243, CarScenePalette.HEADLIGHT)
        body.box(208, 237, 216, 243, CarScenePalette.REAR_LIGHT)
        body.box(25, 235, 35, 238, CarScenePalette.OUTLINE) // espelho
        body.line(30, 234, 38, 227, CarScenePalette.OUTLINE)
        body.box(44, 250, 200, 251, CarScenePalette.STRIPE)
        body.box(48, 257, 197, 259, CarScenePalette.BODY_DARK)
        // Recortes de paralama acima das rodas; o pneu fica na última camada.
        body.line(45, 269, 51, 262, CarScenePalette.BODY_DARK)
        body.hline(52, 72, 260, CarScenePalette.BODY_LIGHT)
        body.line(73, 262, 79, 269, CarScenePalette.BODY_DARK)
        body.line(167, 269, 173, 262, CarScenePalette.BODY_DARK)
        body.hline(174, 194, 260, CarScenePalette.BODY_LIGHT)
        body.line(195, 262, 201, 269, CarScenePalette.BODY_DARK)
        // Recorta a abertura no sprite do carro preservando os limites de vidro.
        for (y in 0 until body.height) for (x in 0 until body.width) {
            if (inDriverWindow(x, y)) body.pixels[y * body.width + x] = 0
        }
        // Aro externo traseiro deixa o recorte de janela com silhueta de carro.
        body.line(155, 165, 154, 224, CarScenePalette.OUTLINE)
        body.line(196, 165, 202, 225, CarScenePalette.OUTLINE)
    }

    private fun ensureStrips(env: SceneEnv) {
        if (cachedPeriod == env.period && cachedSeed == env.daySeed) return
        cachedPeriod = env.period; cachedSeed = env.daySeed
        cityStrip = PixelBuffer(STRIP_W, CITY_H)
        cityWindowLights = PixelBuffer(STRIP_W, CITY_H)
        val windows = ArrayList<Int>(96)
        roadStrip = PixelBuffer(STRIP_W, 116)
        postStrip = PixelBuffer(STRIP_W, 116)
        val sky = skyLow(env.period)
        cityStrip.fill(sky)
        var building = 0
        while (building < 10) {
            val x = building * 24
            val hash = mixHash(env.daySeed, building)
            val w = 16 + hash.ushr(3).mod(6)
            val h = 25 + hash.ushr(9).mod(63)
            val color = when ((hash ushr 20) and 3) { 0 -> CarScenePalette.BUILDING_A; 1 -> CarScenePalette.BUILDING_B; else -> CarScenePalette.BUILDING_C }
            cityStrip.box(x, CITY_H - h, x + w - 1, CITY_H - 1, color)
            val win = if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) CarScenePalette.WINDOW_LIT else CarScenePalette.WINDOW_DAY
            for (wy in CITY_H - h + 5 until CITY_H - 4 step 9) for (wx in x + 3 until x + w - 2 step 5) {
                val wh = mixHash(mixHash(env.daySeed, building), (wy shl 8) xor wx)
                val lit = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING && (wh and 1) == 0
                cityStrip.set(wx, wy, if (lit) win else CarScenePalette.WINDOW_DARK)
                if (lit) {
                    cityWindowLights.set(wx, wy, win)
                    if (wx < PERIOD) windows += (wy shl 16) or wx
                }
            }
            building++
        }
        for (y in 0 until CITY_H) for (x in 0 until PERIOD) {
            cityStrip.set(x + PERIOD, y, cityStrip[x, y])
            cityWindowLights.set(x + PERIOD, y, cityWindowLights[x, y])
        }
        emissiveWindows = windows.toIntArray()
        cityStrip.hline(0, STRIP_W - 1, CITY_H - 1, CarScenePalette.OUTLINE)
        roadStrip.fill(CarScenePalette.ROAD)
        roadStrip.box(0, 0, STRIP_W - 1, 10, CarScenePalette.SIDEWALK)
        roadStrip.hline(0, STRIP_W - 1, 10, CarScenePalette.OUTLINE)
        for (dash in 0 until PERIOD step 48) roadStrip.box(dash + 2, 83, dash + 30, 86, CarScenePalette.ROAD_MARK)
        for (post in 24 until PERIOD step 80) {
            postStrip.box(post, 7, post + 2, 100, CarScenePalette.POST)
            val lit = env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING
            postStrip.outlined(post - 6, 2, post + 8, 8, if (lit) CarScenePalette.LAMP else CarScenePalette.CLOUD, CarScenePalette.OUTLINE)
        }
        for (y in 0 until roadStrip.height) for (x in 0 until PERIOD) {
            roadStrip.set(x + PERIOD, y, roadStrip[x, y])
            postStrip.set(x + PERIOD, y, postStrip[x, y])
        }
        cachedLights = if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) {
            listOf(Light.Glow(34, 241, 40, .58f), Light.Emissive(34, 237, 38, 243))
        } else listOf(Light.Glow(34, 241, 40, .58f))
    }

    private fun bodyBounce(t: Long, env: SceneEnv): Int = when (env.transportAmbient?.vibration) {
        TransportVibration.MEDIUM -> BOUNCE_MEDIUM[((t.coerceAtLeast(0) / 210L) % BOUNCE_MEDIUM.size).toInt()]
        TransportVibration.LOW -> BOUNCE_LOW[((t.coerceAtLeast(0) / 320L) % BOUNCE_LOW.size).toInt()]
        TransportVibration.NONE -> 0
        null -> BOUNCE_LOW[((t.coerceAtLeast(0) / 320L) % BOUNCE_LOW.size).toInt()]
    }
    private fun occupantBounce(t: Long, env: SceneEnv): Int = bodyBounce(t - 55L, env)

    private fun offsetFor(timeMs: Long, speed: Float): Int {
        val active = if (cachedPeriod == null) 0f else speed
        return floor(timeMs.coerceAtLeast(0) * active / 1000.0).toInt().floorMod(PERIOD)
    }

    private fun Int.floorMod(mod: Int) = ((this % mod) + mod) % mod

    private fun inDriverWindow(x: Int, y: Int): Boolean =
        y in 166..224 && x >= 45 + (y - 166) / 7 && x <= 153 - (y - 166) / 18

    private fun drawStars(b: PixelBuffer) {
        for (i in STAR_COORDS.indices step 2) b.set(STAR_COORDS[i], STAR_COORDS[i + 1], CarScenePalette.STAR)
        b.disc(185, 22, 5, CarScenePalette.MOON)
        b.disc(188, 20, 4, CarScenePalette.SKY_TOP)
    }

    private fun skyTop(period: DayPeriod) = when (period) { DayPeriod.MORNING -> CarScenePalette.MORNING_TOP; DayPeriod.DAY -> CarScenePalette.DAY_TOP; DayPeriod.EVENING -> CarScenePalette.EVENING_TOP; DayPeriod.NIGHT -> CarScenePalette.NIGHT_TOP }
    private fun skyMid(period: DayPeriod) = when (period) { DayPeriod.MORNING -> CarScenePalette.MORNING_MID; DayPeriod.DAY -> CarScenePalette.DAY_MID; DayPeriod.EVENING -> CarScenePalette.EVENING_MID; DayPeriod.NIGHT -> CarScenePalette.NIGHT_MID }
    private fun skyLow(period: DayPeriod) = when (period) { DayPeriod.MORNING -> CarScenePalette.MORNING_LOW; DayPeriod.DAY -> CarScenePalette.DAY_LOW; DayPeriod.EVENING -> CarScenePalette.EVENING_LOW; DayPeriod.NIGHT -> CarScenePalette.NIGHT_LOW }

    private fun mixHash(a: Int, b: Int): Int {
        var x = a * -0x61c88647 + b * 0x45d9f3b
        x = (x xor (x ushr 16)) * 0x45d9f3b
        x = (x xor (x ushr 16)) * 0x45d9f3b
        return x xor (x ushr 16)
    }

    companion object {
        private val STAR_COORDS = intArrayOf(12, 9, 37, 21, 68, 7, 98, 14, 133, 4, 167, 18, 207, 8, 226, 24)
        const val SEAT_X = 120
        const val SEAT_Y = 246
        const val DOOR_X = 30
        const val SEATED_SPRITE_LEFT = 60
        const val STRIP_W = 480
        const val PERIOD = 240
        const val CITY_H = 132
        const val CITY_Y = 73
        const val ROAD_Y = 204
        const val CITY_SPEED = .28f
        const val POST_SPEED = 2.4f
        const val TRAFFIC_CYCLE_MS = 28_000L
        const val TRAFFIC_GREEN_MS = 16_000L
        const val TRAFFIC_SLOW_MS = 1_500L
        const val TRAFFIC_STOP_MS = 3_000L
        const val TRAFFIC_ACCEL_MS = 1_500L
        const val TRAFFIC_SLOW_END_MS = TRAFFIC_GREEN_MS + TRAFFIC_SLOW_MS
        const val TRAFFIC_STOP_END_MS = TRAFFIC_SLOW_END_MS + TRAFFIC_STOP_MS
        const val TRAFFIC_ACCEL_END_MS = TRAFFIC_STOP_END_MS + TRAFFIC_ACCEL_MS
        const val TRAFFIC_TRAVEL_MS = TRAFFIC_GREEN_MS + TRAFFIC_SLOW_MS / 2 + TRAFFIC_ACCEL_MS / 2 + (TRAFFIC_CYCLE_MS - TRAFFIC_ACCEL_END_MS)
        val CAR_BODY = CarScenePalette.BODY
        val BOUNCE_MEDIUM = intArrayOf(0, 1, 1, 0, -1, 0, 1, 0)
        val BOUNCE_LOW = intArrayOf(0, 0, 1, 0, 0, -1, 0, 0)
    }
}

internal enum class CarStrip { CITY, ROAD, POSTS }
internal enum class TrafficPhase { GO, SLOW, STOP }

/** Paleta fechada: sem degradês calculados durante o desenho da cena. */
object CarScenePalette {
    const val OUTLINE = 0xFF1A1C33.toInt(); const val SKY_TOP = 0xFF26304C.toInt()
    const val MORNING_TOP = 0xFFDB9E91.toInt(); const val MORNING_MID = 0xFFE8BBA4.toInt(); const val MORNING_LOW = 0xFFB7C4D1.toInt()
    const val DAY_TOP = 0xFF5B86B7.toInt(); const val DAY_MID = 0xFF87AFCC.toInt(); const val DAY_LOW = 0xFFB9C7C6.toInt()
    const val EVENING_TOP = 0xFF4B426C.toInt(); const val EVENING_MID = 0xFFD78373.toInt(); const val EVENING_LOW = 0xFF8C6174.toInt()
    const val NIGHT_TOP = 0xFF11172D.toInt(); const val NIGHT_MID = 0xFF202B4A.toInt(); const val NIGHT_LOW = 0xFF29334D.toInt()
    const val BUILDING_A = 0xFF343B58.toInt(); const val BUILDING_B = 0xFF414762.toInt(); const val BUILDING_C = 0xFF50536D.toInt()
    const val WINDOW_LIT = 0xFFFFD889.toInt(); const val WINDOW_DAY = 0xFF8FC1D9.toInt(); const val WINDOW_DARK = 0xFF282F49.toInt()
    const val SIDEWALK = 0xFF888897.toInt(); const val ROAD = 0xFF3D4150.toInt(); const val ROAD_MARK = 0xFFDAD5C4.toInt()
    const val POST = 0xFF626A7A.toInt(); const val LAMP = 0xFFFFE2A0.toInt(); const val CLOUD = 0xFFE0E8E8.toInt()
    const val SUN = 0xFFFFC56B.toInt(); const val MOON = 0xFFF4EFC8.toInt(); const val STAR = 0xFFF4F1D0.toInt()
    const val BODY = 0xFFC65F58.toInt(); const val BODY_LIGHT = 0xFFE07B68.toInt(); const val BODY_DARK = 0xFF91434B.toInt()
    const val GLASS = 0xFF79A9C2.toInt(); const val GLASS_REFLECTION = 0x8859A0C2.toInt()
    const val SEAT = 0xFF46516B.toInt(); const val BACKPACK = 0xFF31405D.toInt(); const val BACKPACK_LIGHT = 0xFF596985.toInt()
    const val WHEEL_DARK = 0xFF34394C.toInt(); const val WHEEL_LIGHT = 0xFF85899A.toInt(); const val TIRE = 0xFF262A39.toInt()
    const val CHROME = 0xFFBFC8CB.toInt(); const val BUMPER = 0xFF606777.toInt(); const val HEADLIGHT = 0xFFFFE8AB.toInt()
    const val REAR_LIGHT = 0xFFE35054.toInt(); const val STRIPE = 0xFFF2C1A0.toInt()
    const val SHADOW = 0xFF343746.toInt()
    const val SIGNAL_BODY = 0xFF333849.toInt(); const val SIGNAL_OFF = 0xFF4C4F5D.toInt()
    const val SIGNAL_RED = 0xFFFF5558.toInt(); const val SIGNAL_AMBER = 0xFFFFD064.toInt(); const val SIGNAL_GREEN = 0xFF73D28B.toInt()
}

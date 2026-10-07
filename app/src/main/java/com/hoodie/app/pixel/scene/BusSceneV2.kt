package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.character.CharacterFrame
import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.NpcStep
import com.hoodie.app.pixel.npc.NpcBehaviorSequence
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Point
import com.hoodie.app.pixel.sprite.Posture
import com.hoodie.app.pixel.sprite.SpriteFrame
import kotlin.math.floor

/** Flagged, deterministic bus interior with seat anchored passengers and clipped moving windows. */
class BusSceneV2 : PixelScene(SceneId.BUS) {
    override val spots = mapOf(SpotId.SEAT to Spot(120, 276), SpotId.CENTER to Spot(120, 276))
    override val defaultSpot = SpotId.SEAT
    override val walkInPlace = true
    override val usesTransportLightingProfile = false

    private val seatSpecs = listOf(
        Triple("single-left", 29, BusSeatType.SINGLE),
        Triple("priority", 77, BusSeatType.PREFERENTIAL),
        Triple("double", 158, BusSeatType.DOUBLE),
        Triple("wheelchair", 211, BusSeatType.WHEELCHAIR),
    )

    override fun drawBackground(b: PixelBuffer, env: SceneEnv) {
        b.box(0, 0, 239, 34, 0xFFD9E4E1.toInt())
        b.box(0, 35, 239, 157, 0xFF568174.toInt())
        b.box(0, 158, 239, 253, 0xFF466D65.toInt())
        b.box(0, 254, 239, 319, 0xFF646A76.toInt())
        b.hline(0, 239, 252, 0xFF34394A.toInt())
        b.hline(0, 239, 253, 0xFF9AA0A6.toInt())
        for (x in 0..239 step 24) b.vline(x, 158, 250, 0xFF52786D.toInt())
        b.box(0, 0, 239, 6, 0xFFB8C9C4.toInt())
    }

    private fun seatProps(env: SceneEnv): List<BusSeatProp> {
        val passengerSlots = seatSpecs.map { (id, x, type) -> BusSeatProp(id, x, SEAT_HIP_Y, type, occupied = false).slots.first() }
        val candidates = listOf(
            NpcCharacterRegistry.MOUSE_COMMUTER,
            NpcCharacterRegistry.DUCK_SLEEPY,
            NpcCharacterRegistry.DOG_WORKER,
            NpcCharacterRegistry.RABBIT_READER,
        )
        val quarter = Math.floorDiv(env.clockMinute, 15)
        val seed = env.daySeed * 31 + env.variant * 17 + quarter
        val count = 3 + (seed and 1)
        val start = Math.floorMod(seed, passengerSlots.size)
        val occupied = (0 until count).map { passengerSlots[(start + it) % passengerSlots.size].id }.toSet()
        return seatSpecs.map { (id, x, type) ->
            val theseSlots = BusSeatProp(id, x, SEAT_HIP_Y, type, occupied = false).slots
            BusSeatProp(id, x, SEAT_HIP_Y, type, theseSlots.any { it.id in occupied })
        } + BusSeatProp("hoodie-front", 120, LEAD_HIP_Y, BusSeatType.SINGLE, occupied = true)
    }

    fun seatSlots(env: SceneEnv): List<SeatSlot> = seatProps(env).flatMap { it.slots }

    override fun ambientNpcs(env: SceneEnv): List<AmbientNpcSlot> {
        val passengerSlots = seatSpecs.map { (id, x, type) -> BusSeatProp(id, x, SEAT_HIP_Y, type, false).slots.first() }
        val quarter = Math.floorDiv(env.clockMinute, 15)
        val seed = env.daySeed * 31 + env.variant * 17 + quarter
        val count = 3 + (seed and 1)
        val start = Math.floorMod(seed, passengerSlots.size)
        val candidates = listOf(
            NpcCharacterRegistry.MOUSE_COMMUTER,
            NpcCharacterRegistry.DUCK_SLEEPY,
            NpcCharacterRegistry.DOG_WORKER,
            NpcCharacterRegistry.RABBIT_READER,
        )
        return (0 until count).map { i ->
            val seat = passengerSlots[(start + i) % passengerSlots.size]
            val style = candidates[Math.floorMod(seed + i, candidates.size)]
            val sequence = if (style.id == NpcCharacterRegistry.MOUSE_COMMUTER.id) {
                NpcBehaviorSequence.of(
                    NpcStep(NpcAnimation.SIT_PHONE, 4_000, seated = true, facing = Facing.FRONT),
                    NpcStep(NpcAnimation.LOOK_WINDOW, 2_200, seated = true, facing = Facing.FRONT),
                    NpcStep(NpcAnimation.SIT_PHONE, 3_000, seated = true, facing = Facing.FRONT),
                )
            } else {
                NpcBehaviorSequence.of(
                    NpcStep(NpcAnimation.SIT_PHONE, 4_400, seated = true, facing = Facing.FRONT),
                    NpcStep(NpcAnimation.SIT_LOOK, 2_400, seated = true, facing = Facing.FRONT),
                )
            }
            val definition = AmbientNpcDefinition(
                id = style.id,
                characterStyle = style,
                behaviorProfile = NpcBehaviorProfile(
                    animation = NpcAnimation.SIT_PHONE,
                    motion = SpeciesMotionProfiles.forCharacter(style),
                    sequence = sequence,
                    reactions = false,
                ),
            )
            AmbientNpcSlot(
                definition = definition,
                x = seat.anchor.x,
                floorY = seat.anchor.y,
                baseline = seat.anchor.y,
                seed = seed + i * 23,
                depth = NpcDepth.BACKGROUND,
                seatedPosture = Posture.SIT_FRONT,
                seatSlotId = seat.id,
                busJoltPhaseMs = (i % 2) * 480L,
            )
        }
    }

    override fun props(): List<Prop> = listOf(
        Prop(32) { b, env, time -> drawWindows(b, env, time) },
        Prop(35) { b, env, time ->
            val night = env.period == DayPeriod.NIGHT
            b.outlined(84, 10, 155, 30, if (night) 0xFF18272B.toInt() else 0xFF263741.toInt(), P.OUTLINE)
            b.box(89, 15, 150, 25, if (night) 0xFF537C4C.toInt() else 0xFF356651.toInt())
            val label = if (env.clockMinute in 6 * 60..16 * 60) "TRABALHO" else "CASA"
            drawLedText(b, label, if (night) 0xFFFFE5A3.toInt() else 0xFFBEEAA0.toInt())
            for (x in 18..222 step 34) {
                b.outlined(x, 18, x + 15, 22, 0xFFFFE9A8.toInt(), P.OUTLINE)
            }
            b.hline(0, 239, 151, 0xFFCCD9D2.toInt())
            b.hline(0, 239, 154, P.OUTLINE)
            for (i in 0 until 7) {
                val phase = TransportMotion.offset(time + i * 193L, env.transportAmbient?.vibration)
                val x = 18 + i * 34 + phase
                b.vline(x, 155, 165, P.METAL_DARK)
                b.outlined(x - 4, 163, x + 4, 171, 0xFFFFD977.toInt(), P.OUTLINE)
                b.vline(x, 166, 169, 0xFF8A6232.toInt())
            }
        },
        Prop(LEAD_HIP_Y - 48) { b, env, _ -> seatProps(env).last().drawRear(b) },
        Prop(LEAD_HIP_Y + 4) { b, env, _ -> seatProps(env).last().drawFront(b) },
        Prop(252) { b, env, time -> drawSunPatches(b, env, time) },
    ) + seatSpecs.flatMap { (id, x, type) -> listOf(
        Prop(SEAT_HIP_Y - 2) { b, env, _ -> seatProps(env).first { it.id == id }.drawRear(b) },
        Prop(SEAT_HIP_Y + 8) { b, env, _ -> seatProps(env).first { it.id == id }.drawFront(b) },
    ) }

    override fun lights(env: SceneEnv): List<Light> {
        val windows = WINDOW_RECTS.map { (x0, y0, x1, y1) ->
            Light.RegionTint(x0, y0, x1, y1, 40, 60, 110)
        }
        val cabin = Light.RegionTint(0, 0, 239, 319, 224, 208, 176)
        val emissive = mutableListOf<Light>(
            Light.Emissive(17, 17, 33, 22), Light.Emissive(51, 17, 67, 22),
            Light.Emissive(85, 17, 101, 22), Light.Emissive(119, 17, 135, 22),
            Light.Emissive(153, 17, 169, 22), Light.Emissive(187, 17, 203, 22),
            Light.Emissive(84, 10, 155, 30),
        )
        seatSlots(env).filter { it.occupied && it.id != "hoodie-front" }.forEach { seat ->
            emissive += Light.Emissive(seat.anchor.x - 2, seat.anchor.y - 10, seat.anchor.x + 2, seat.anchor.y - 6)
        }
        return if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) windows + cabin + emissive else emptyList()
    }

    override fun drawCharacter(buffer: PixelBuffer, frame: SpriteFrame, x: Int, y: Int, timeMs: Long, env: SceneEnv) {
        val hip = frame.anchors.seatHip
        if (hip == null) buffer.blit(frame.image, x, y)
        else buffer.blit(frame.image, spot(SpotId.SEAT).x - hip.x, spot(SpotId.SEAT).y - hip.y)
    }

    override fun drawPostLighting(buffer: PixelBuffer, env: SceneEnv, timeMs: Long) {
        if (env.period == DayPeriod.NIGHT || env.period == DayPeriod.EVENING) {
            WINDOW_RECTS.forEachIndexed { wi, rect ->
                val (x0, y0, x1, y1) = rect
                for (i in 0..4) {
                    val x = x0 + 8 + Math.floorMod(i * 19 + wi * 7 + timeMs / 120, (x1 - x0 - 12).toLong()).toInt()
                    val y = y0 + 17 + Math.floorMod(i * 13L + wi * 11L, (y1 - y0 - 24).toLong()).toInt()
                    buffer.box(x, y, x + 1, y + 2, 0xFFFFD989.toInt())
                }
            }
            // Warm overhead strips and destination board are restored after the night tint.
            for (x in 18..222 step 34) buffer.hline(x, x + 15, 20, 0xFFFFE9B2.toInt())
            drawLedText(buffer, if (env.clockMinute in 6 * 60..16 * 60) "TRABALHO" else "CASA", 0xFFFFE5A3.toInt())
        }
    }

    private fun drawWindows(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        val speed = TransportMotion.speed(env.transportAmbient, .8f)
        WINDOW_RECTS.forEachIndexed { index, (x0, y0, x1, y1) ->
            val sky = when (env.period) {
                DayPeriod.NIGHT -> 0xFF1B2A49.toInt()
                DayPeriod.EVENING -> 0xFF705778.toInt()
                DayPeriod.MORNING -> 0xFFF4B78D.toInt()
                DayPeriod.DAY -> 0xFF76C4EC.toInt()
            }
            b.box(x0, y0, x1, y1, sky)
            for (line in 0..4) b.hline(x0 + 1, x1 - 1, y0 + line * 5, PixelBuffer.mix(sky, 0xFFF4F4EE.toInt(), .13f))
            drawCityLayer(b, x0, y0, x1, y1, timeMs, speed * .45f, index, far = true, period = env.period)
            drawCityLayer(b, x0, y0, x1, y1, timeMs, speed, index, far = false, period = env.period)
            val poleTravel = Math.floorMod((timeMs * speed / 60f).toLong() + index * 47L, 74L).toInt()
            for (x in x0 + 8 - poleTravel until x1 step 74) {
                if (x in x0 + 2..x1 - 2) {
                    b.vline(x, y0 + 1, y1 - 1, if (env.period == DayPeriod.NIGHT) 0xFF3A4862.toInt() else 0xFF9CA9A2.toInt())
                }
            }
            b.outlined(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF8C9C94.toInt(), P.OUTLINE)
            b.vline((x0 + x1) / 2, y0 - 1, y1 + 1, P.OUTLINE)
            b.hline(x0 - 1, x1 + 1, y0 + (y1 - y0) / 2, P.OUTLINE)
            b.line(x0 + 2, y0 + 8, x1 - 2, y1 - 8, 0x44FFFFFF)
        }
        // Moulding surrounds all four panes.
        b.hline(4, 235, 39, 0xFFD6E0DB.toInt()); b.hline(4, 235, 140, 0xFF233B38.toInt())
    }

    private fun drawCityLayer(
        b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int,
        timeMs: Long, speed: Float, seed: Int, far: Boolean, period: DayPeriod,
    ) {
        val span = x1 - x0 + 1
        val stride = if (far) 37 else 49
        val drift = floor(timeMs * speed / if (far) 46f else 31f).toInt()
        val base = y1 - if (far) 9 else 4
        for (i in -2..8) {
            val raw = x0 + Math.floorMod(i * stride - drift + seed * 11, span + stride) - stride
            val width = if (far) 11 + Math.floorMod(i * 7 + seed, 9) else 15 + Math.floorMod(i * 11 + seed, 14)
            val height = if (far) 24 + Math.floorMod(i * 13 + seed, 23) else 34 + Math.floorMod(i * 17 + seed, 36)
            val left = maxOf(x0 + 1, raw)
            val right = minOf(x1 - 1, raw + width)
            val top = maxOf(y0 + 2, base - height)
            if (left > right || top >= base) continue
            val color = when (period) {
                DayPeriod.NIGHT -> if (far) 0xFF354462.toInt() else 0xFF293858.toInt()
                DayPeriod.EVENING -> if (far) 0xFF8E7081.toInt() else 0xFF67566F.toInt()
                else -> if (far) 0xFF83A8B1.toInt() else 0xFF758A9B.toInt()
            }
            b.box(left, top, right, base, color)
            for (wx in raw + 3 until raw + width - 2 step 5) for (wy in top + 4 until base - 2 step 8) {
                if (wx in x0 + 1..x1 - 1 && wy in y0 + 1..y1 - 1 && (wx * 7 + wy * 11 + seed) % 3 == 0) {
                    val lit = if (period == DayPeriod.NIGHT) 0xFFB9A878.toInt() else 0xFFBDE0E8.toInt()
                    b.box(wx, wy, minOf(wx + 1, x1 - 1), minOf(wy + 2, y1 - 1), lit)
                }
            }
        }
    }

    private fun drawSunPatches(b: PixelBuffer, env: SceneEnv, timeMs: Long) {
        if (env.period !in setOf(DayPeriod.MORNING, DayPeriod.DAY, DayPeriod.EVENING)) return
        val speed = TransportMotion.speed(env.transportAmbient, .8f)
        val offset = Math.floorMod((timeMs * speed / 80f).toLong(), 112L).toInt()
        for (i in 0..2) {
            val x = i * 96 - offset
            val alpha = if (env.period == DayPeriod.EVENING) 0x28 else 0x38
            val color = (alpha shl 24) or 0xFFFFE9A8.toInt().and(0x00FFFFFF)
            for (row in 0..22) {
                val left = x + row / 2
                b.hline(left, left + 36, 260 + row, color)
            }
        }
    }

    private fun drawLedText(b: PixelBuffer, text: String, color: Int) {
        val glyphs = mapOf(
            'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"), 'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
            'C' to listOf(".##", "#..", "#..", "#..", ".##"), 'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
            'L' to listOf("#..", "#..", "#..", "#..", "###"), 'M' to listOf("#...#", "##.##", "#.#.#", "#...#", "#...#"),
            'O' to listOf("###", "#.#", "#.#", "#.#", "###"), 'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
            'S' to listOf(".##", "#..", ".#.", "..#", "##."), 'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
            'B' to listOf("##.", "#.#", "##.", "#.#", "##."), 'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"),
        )
        val total = text.length * 4 - 1
        var x = (PixelScene.SCENE_W - total) / 2
        text.forEach { ch ->
            glyphs[ch]?.forEachIndexed { y, row -> row.forEachIndexed { dx, pixel -> if (pixel == '#') b.set(x + dx, 16 + y, color) } }
            x += 4
        }
    }

    companion object {
        const val SEAT_HIP_Y = 222
        const val LEAD_HIP_Y = 276
        private val WINDOW_RECTS = listOf(
            intArrayOf(14, 44, 63, 138), intArrayOf(70, 44, 119, 138),
            intArrayOf(125, 44, 174, 138), intArrayOf(181, 44, 226, 138),
        )
    }
}

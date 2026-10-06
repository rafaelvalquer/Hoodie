package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.brain.NpcBrainState
import com.hoodie.app.pixel.npc.brain.NpcCooldowns
import com.hoodie.app.pixel.npc.brain.NpcAmbientBrain
import com.hoodie.app.pixel.npc.brain.NpcIntent
import com.hoodie.app.pixel.npc.brain.NpcPersonalityProfile
import kotlin.math.abs

/** Reconstrói a sessão a partir da seed e do tempo: frame a frame não há estado aleatório. */
class OfficeAmbientBrain(
    override val npcId: String,
    val profile: NpcPersonalityProfile,
    val startSpot: OfficeNpcSpot,
    val daySeed: Int,
) : NpcAmbientBrain {
    internal var socialSession: OfficeSocialSession? = null

    override fun stateAt(timeMs: Long): NpcBrainState = socialSession?.stateAt(this, timeMs) ?: baseStateAt(timeMs)

    internal fun baseStateAt(timeMs: Long): NpcBrainState {
        val t = timeMs.coerceAtLeast(0)
        var spot = startSpot
        var cursor = 0L
        var recent = emptyList<NpcIntent>()
        var cooldowns = NpcCooldowns()
        var index = 0L
        while (true) {
            val seedState = NpcBrainState(NpcIntent.WORK, spot, cursor, cursor, recent, decisionIndex = index, cooldowns = cooldowns)
            val intent = OfficeBehaviorPlanner.choose(npcId, daySeed, seedState, profile, cursor)
            val target = OfficeBehaviorPlanner.target(intent, spot, startSpot)
            val route = OfficeNavigationGraph.route(spot, target)
            val travelMs = route.zipWithNext().sumOf { (a, b) -> maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) * 24L }
            val started = cursor + travelMs
            val duration = OfficeBehaviorPlanner.durationMs(npcId, daySeed, index, profile, intent)
            val end = started + duration
            val state = NpcBrainState(intent, target, started, end,
                (listOf(intent) + recent).take(4), targetSpot = target, decisionIndex = index, cooldowns = cooldowns)
            if (t < started) return state.copy(currentSpot = spot)
            if (t < end) return state
            spot = target
            cursor = end
            recent = (listOf(intent) + recent).take(4)
            val nextIndex = index + 1
            cooldowns = com.hoodie.app.pixel.npc.brain.NpcCooldownManager.afterIntent(
                npcId, daySeed, nextIndex, intent, cursor, cooldowns,
            )
            index++
        }
    }

    override fun movementAt(timeMs: Long): NpcMovement {
        if (npcId == "bulldog_exec") return bulldogMovement(timeMs.coerceAtLeast(0))
        return regularMovement(timeMs)
    }

    fun isOffscreenAt(timeMs: Long): Boolean = npcId == "bulldog_exec" &&
        Math.floorMod(timeMs.coerceAtLeast(0), 240_000L) >= 66_000L

    /** Door animation follows the executive's approach and departure windows. */
    fun officeDoorFrameAt(timeMs: Long): Int {
        if (npcId != "bulldog_exec") return 0
        val cycle = Math.floorMod(timeMs.coerceAtLeast(0), 240_000L)
        val entranceOpenAt = 5_800L
        val exitOpenAt = 58_000L
        val openDuration = 300L
        val entranceCloseAt = 7_100L
        val exitCloseAt = 66_000L
        return when {
            cycle in entranceOpenAt until entranceOpenAt + openDuration -> ((cycle - entranceOpenAt) / 100L + 1).toInt()
            cycle in entranceOpenAt + openDuration until entranceCloseAt -> 3
            cycle in entranceCloseAt until entranceCloseAt + openDuration -> (3 - (cycle - entranceCloseAt) / 100L).toInt().coerceAtLeast(0)
            cycle in exitOpenAt until exitOpenAt + openDuration -> ((cycle - exitOpenAt) / 100L + 1).toInt()
            cycle in exitOpenAt + openDuration until exitCloseAt -> 3
            cycle in exitCloseAt until exitCloseAt + openDuration -> (3 - (cycle - exitCloseAt) / 100L).toInt().coerceAtLeast(0)
            else -> 0
        }
    }

    private fun bulldogMovement(timeMs: Long): NpcMovement {
        val cycle = Math.floorMod(timeMs, 240_000L)
        val door = OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DOOR)
        val enterMs = 7_000L
        val visitMs = 52_000L
        val exitMs = 7_000L
        if (isOffscreenAt(timeMs)) {
            return NpcMovement(-32, door.floorY, false, NpcAnimation.IDLE, cycle, phase = PathPhase.IDLE)
        }
        if (cycle < enterMs) {
            val f = cycle.toFloat() / enterMs
            val outsideX = 264
            val x = (outsideX + (door.x - outsideX) * f).toInt()
            return NpcMovement(x, door.floorY, false, NpcAnimation.WALK, cycle,
                walkedPx = (outsideX - door.x) * f, phase = PathPhase.ENTER)
        }
        if (cycle < enterMs + visitMs) return regularMovement(cycle - enterMs)
        val elapsed = cycle - enterMs - visitMs
        val f = elapsed.toFloat() / exitMs
        return NpcMovement((door.x + (264 - door.x) * f).toInt(), door.floorY, true,
            NpcAnimation.WALK, elapsed, walkedPx = (264 - door.x) * f, phase = PathPhase.EXIT)
    }

    private fun regularMovement(timeMs: Long): NpcMovement {
        val state = stateAt(timeMs)
        val route = OfficeNavigationGraph.route(state.currentSpot, state.targetSpot ?: state.currentSpot)
        val routeLength = route.zipWithNext().sumOf { (a, b) -> maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) * 24L }
        val travelStart = (state.intentStartedAt - routeLength).coerceAtLeast(0)
        if (timeMs < state.intentStartedAt && route.size > 1) {
            var left = timeMs - travelStart
            var walked = 0f
            for ((a, b) in route.zipWithNext()) {
                val distance = maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY))
                val segment = (distance * 24L).coerceAtLeast(1)
                if (left < segment) {
                    val f = (left.coerceAtLeast(0).toFloat() / segment).coerceIn(0f, 1f)
                    return NpcMovement((a.x + (b.x - a.x) * f).toInt(), (a.floorY + (b.floorY - a.floorY) * f).toInt(),
                        b.x >= a.x, NpcAnimation.WALK, left.coerceAtLeast(0), walked + distance * f, PathPhase.WALK)
                }
                left -= segment; walked += distance
            }
        }
        val point = OfficeNavigationGraph.spots.getValue(state.currentSpot)
        val seated = state.currentIntent == NpcIntent.WORK || state.currentIntent == NpcIntent.CHECK_PHONE
        val animation = when (state.currentIntent) {
            NpcIntent.WORK -> NpcAnimation.TYPE
            NpcIntent.GET_COFFEE -> NpcAnimation.STAND_COFFEE
            NpcIntent.CHECK_PHONE -> NpcAnimation.SIT_PHONE
            NpcIntent.LOOK_WINDOW -> NpcAnimation.LOOK_WINDOW
            NpcIntent.READ_WHITEBOARD -> NpcAnimation.LOOK
            NpcIntent.USE_PRINTER -> NpcAnimation.READ_DOCUMENT
            NpcIntent.STRETCH -> NpcAnimation.STRETCH
            NpcIntent.SOCIALIZE -> if (socialSession?.isSpeaker(npcId, timeMs) == true) NpcAnimation.TALK else NpcAnimation.REACTION
            NpcIntent.GREET_HOODIE -> NpcAnimation.TALK
            NpcIntent.WALK_AROUND, NpcIntent.IDLE, NpcIntent.ENTER_OFFICE, NpcIntent.EXIT_OFFICE -> NpcAnimation.IDLE
        }
        val local = (timeMs - state.intentStartedAt).coerceAtLeast(0)
        return NpcMovement(point.x, point.floorY, facingRight = false, animation = animation,
            localTimeMs = local, phase = PathPhase.STOP, seated = seated, facing = point.facing,
            reaction = if (state.currentIntent == NpcIntent.SOCIALIZE && animation == NpcAnimation.REACTION) com.hoodie.app.pixel.npc.NpcReaction.NOD else null)
    }

    override fun shouldSpeak(timeMs: Long): Boolean = socialSession?.shouldSpeak(npcId, timeMs) == true
}

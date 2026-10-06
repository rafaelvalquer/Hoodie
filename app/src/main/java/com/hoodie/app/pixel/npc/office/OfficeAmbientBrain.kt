package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.brain.NpcBrainState
import com.hoodie.app.pixel.npc.brain.NpcCooldowns
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.npc.brain.NpcIntent
import com.hoodie.app.pixel.npc.brain.NpcIntentPlanner
import com.hoodie.app.pixel.npc.brain.NpcPersonalityProfile
import kotlin.math.abs

/** Reconstrói a sessão a partir da seed e do tempo: frame a frame não há estado aleatório. */
class OfficeAmbientBrain(
    val npcId: String,
    val profile: NpcPersonalityProfile,
    val startSpot: OfficeNpcSpot,
    val daySeed: Int,
) {
    internal var socialSession: OfficeSocialSession? = null

    fun stateAt(timeMs: Long): NpcBrainState = socialSession?.stateAt(this, timeMs) ?: baseStateAt(timeMs)

    internal fun baseStateAt(timeMs: Long): NpcBrainState {
        val t = timeMs.coerceAtLeast(0)
        var spot = startSpot
        var cursor = 0L
        var recent = emptyList<NpcIntent>()
        var cooldowns = NpcCooldowns()
        var index = 0L
        while (true) {
            val seedState = NpcBrainState(NpcIntent.WORK, spot, cursor, cursor, recent, decisionIndex = index, cooldowns = cooldowns)
            val intent = NpcIntentPlanner.choose(npcId, daySeed, seedState, profile, cursor)
            val target = NpcIntentPlanner.target(intent, spot, startSpot)
            val route = OfficeNavigationGraph.route(spot, target)
            val travelMs = route.zipWithNext().sumOf { (a, b) -> maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) * 24L }
            val started = cursor + travelMs
            val duration = NpcIntentPlanner.durationMs(npcId, daySeed, index, profile, intent)
            val end = started + duration
            val state = NpcBrainState(intent, target, started, end,
                (listOf(intent) + recent).take(4), targetSpot = target, decisionIndex = index, cooldowns = cooldowns)
            if (t < started) return state.copy(currentSpot = spot)
            if (t < end) return state
            spot = target
            cursor = end
            recent = (listOf(intent) + recent).take(4)
            val nextIndex = index + 1
            cooldowns = cooldowns.copy(
                coffeeUntil = if (intent == NpcIntent.GET_COFFEE) cursor + 60_000 + (NpcDeterministicRandom.value(npcId, daySeed, nextIndex + 20) * 30_000).toLong() else cooldowns.coffeeUntil,
                phoneUntil = if (intent == NpcIntent.CHECK_PHONE) cursor + 20_000 + (NpcDeterministicRandom.value(npcId, daySeed, nextIndex + 21) * 30_000).toLong() else cooldowns.phoneUntil,
                socialUntil = if (intent == NpcIntent.SOCIALIZE) cursor + 45_000 + (NpcDeterministicRandom.value(npcId, daySeed, nextIndex + 22) * 75_000).toLong() else cooldowns.socialUntil,
                stretchUntil = if (intent == NpcIntent.STRETCH) cursor + 60_000 + (NpcDeterministicRandom.value(npcId, daySeed, nextIndex + 23) * 120_000).toLong() else cooldowns.stretchUntil,
            )
            index++
        }
    }

    fun movementAt(timeMs: Long): NpcMovement {
        if (npcId == "bulldog_exec") return bulldogMovement(timeMs.coerceAtLeast(0))
        return regularMovement(timeMs)
    }

    private fun bulldogMovement(timeMs: Long): NpcMovement {
        val cycle = Math.floorMod(timeMs, 240_000L)
        val door = OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DOOR)
        val enterMs = 7_000L
        val visitMs = 52_000L
        val exitMs = 7_000L
        if (cycle >= enterMs + visitMs + exitMs) {
            return NpcMovement(-32, door.floorY, false, NpcAnimation.IDLE, cycle, phase = PathPhase.IDLE)
        }
        if (cycle < enterMs) {
            val f = cycle.toFloat() / enterMs
            val x = (-24 + (door.x + 24) * f).toInt()
            return NpcMovement(x, door.floorY, true, NpcAnimation.WALK, cycle,
                walkedPx = (door.x + 24) * f, phase = PathPhase.ENTER)
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

    fun shouldSpeak(timeMs: Long): Boolean = socialSession?.shouldSpeak(npcId, timeMs) == true
}

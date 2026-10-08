package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.NpcSpeechProfile
import com.hoodie.app.pixel.npc.brain.NpcBrainState
import com.hoodie.app.pixel.npc.brain.NpcCooldowns
import com.hoodie.app.pixel.npc.brain.NpcAmbientBrain
import com.hoodie.app.pixel.npc.brain.NpcIntent
import com.hoodie.app.pixel.npc.brain.NpcPersonalityProfile
import com.hoodie.app.pixel.sprite.Facing
import kotlin.math.abs

/** Reconstrói a sessão a partir da seed e do tempo: frame a frame não há estado aleatório. */
class OfficeAmbientBrain(
    override val npcId: String,
    val profile: NpcPersonalityProfile,
    val startSpot: OfficeNpcSpot,
    val daySeed: Int,
    val speechProfile: NpcSpeechProfile? = null,
    internal val speechSeed: Int = 0,
) : NpcAmbientBrain {
    internal var socialSession: OfficeSocialSession? = null
    private data class PlannedStep(
        val action: OfficeNpcAction,
        val intentIndex: Long,
        val recentBefore: List<NpcIntent>,
        val cooldownsBefore: NpcCooldowns,
    )
    private val coordinatedTimeline = mutableListOf<PlannedStep>()

    override fun stateAt(timeMs: Long): NpcBrainState = socialSession?.stateAt(this, timeMs) ?: baseStateAt(timeMs)

    internal fun baseStateAt(timeMs: Long): NpcBrainState = plannedActionAndStateAt(timeMs, coordinated = true).second

    internal fun baseActionAt(timeMs: Long): OfficeNpcAction = plannedActionAndStateAt(timeMs, coordinated = true).first

    internal fun rawBaseStateAt(timeMs: Long): NpcBrainState = plannedActionAndStateAt(timeMs, coordinated = false).second

    internal fun rawBaseActionAt(timeMs: Long): OfficeNpcAction = plannedActionAndStateAt(timeMs, coordinated = false).first

    private fun plannedActionAndStateAt(timeMs: Long, coordinated: Boolean): Pair<OfficeNpcAction, NpcBrainState> {
        val t = timeMs.coerceAtLeast(0)
        if (coordinated && socialSession != null && !socialSession!!.isResolvingReservationAt(t)) {
            return coordinatedActionAndStateAt(t)
        }
        var spot = startSpot
        var cursor = 0L
        var recent = emptyList<NpcIntent>()
        var cooldowns = NpcCooldowns()
        var index = 0L
        while (true) {
            val seedState = NpcBrainState(NpcIntent.WORK, spot, cursor, cursor, recent, decisionIndex = index, cooldowns = cooldowns)
            val intent = OfficeBehaviorPlanner.choose(npcId, daySeed, seedState, profile, cursor)
            val preferredTarget = OfficeBehaviorPlanner.target(intent, spot, startSpot)
            val target = if (coordinated) socialSession?.reservedSpotAt(npcId, cursor, preferredTarget) ?: preferredTarget else preferredTarget
            val route = OfficeNavigationGraph.route(spot, target)
            val routeMs = route.zipWithNext().sumOf { (a, b) -> maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) * 24L }
            val leavingDesk = isDesk(spot) && spot != target
            val arrivingDesk = isDesk(target) && spot != target
            val travelMs = routeMs + when { leavingDesk -> STAND_MS + NpcPoseLibrary.TURN_MS; arrivingDesk -> NpcPoseLibrary.TURN_MS + SIT_MS; else -> 0L }
            val startedAt = cursor
            val arrivedAt = startedAt + travelMs
            val duration = OfficeBehaviorPlanner.durationMs(npcId, daySeed, index, profile, intent)
            val end = arrivedAt + duration
            val action = OfficeNpcAction(
                id = "$npcId:$index:$startedAt", npcId = npcId, intent = intent,
                origin = spot, destination = target, startedAt = startedAt, arrivedAt = arrivedAt,
                interactionEndsAt = end, finishedAt = end, route = route,
            )
            val state = NpcBrainState(intent, if (t >= arrivedAt) action.destination else action.origin, arrivedAt, end,
                (listOf(intent) + recent).take(4), targetSpot = target, decisionIndex = index, cooldowns = cooldowns)
            if (t < end) return action to state
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

    /** Builds each immutable base action once; render frames then resolve it with a short lookup. */
    private fun coordinatedActionAndStateAt(timeMs: Long): Pair<OfficeNpcAction, NpcBrainState> {
        val cached = coordinatedTimeline.firstOrNull { timeMs >= it.action.startedAt && timeMs < it.action.finishedAt }
        val step = cached ?: run {
            while (coordinatedTimeline.isEmpty() || coordinatedTimeline.last().action.finishedAt <= timeMs) {
                val previous = coordinatedTimeline.lastOrNull()
                val index = previous?.let { it.intentIndex + 1 } ?: 0L
                val cursor = previous?.action?.finishedAt ?: 0L
                val spot = previous?.action?.destination ?: startSpot
                val recent = previous?.let { (listOf(it.action.intent) + it.recentBefore).take(4) }.orEmpty()
                val cooldowns = previous?.let {
                    com.hoodie.app.pixel.npc.brain.NpcCooldownManager.afterIntent(
                        npcId, daySeed, index, it.action.intent, cursor, it.cooldownsBefore,
                    )
                } ?: NpcCooldowns()
                val seedState = NpcBrainState(NpcIntent.WORK, spot, cursor, cursor, recent, decisionIndex = index, cooldowns = cooldowns)
                val intent = OfficeBehaviorPlanner.choose(npcId, daySeed, seedState, profile, cursor)
                val preferredTarget = OfficeBehaviorPlanner.target(intent, spot, startSpot)
                val target = socialSession?.reservedSpotAt(npcId, cursor, preferredTarget) ?: preferredTarget
                val route = OfficeNavigationGraph.route(spot, target)
                val routeMs = route.zipWithNext().sumOf { (a, b) -> maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) * 24L }
                val leavingDesk = isDesk(spot) && spot != target
                val arrivingDesk = isDesk(target) && spot != target
                val travelMs = routeMs + when {
                    leavingDesk -> STAND_MS + NpcPoseLibrary.TURN_MS
                    arrivingDesk -> NpcPoseLibrary.TURN_MS + SIT_MS
                    else -> 0L
                }
                val arrivedAt = cursor + travelMs
                val finishedAt = arrivedAt + OfficeBehaviorPlanner.durationMs(npcId, daySeed, index, profile, intent)
                val action = OfficeNpcAction(
                    id = "$npcId:$index:$cursor", npcId = npcId, intent = intent,
                    origin = spot, destination = target, startedAt = cursor, arrivedAt = arrivedAt,
                    interactionEndsAt = finishedAt, finishedAt = finishedAt, route = route,
                )
                coordinatedTimeline += PlannedStep(action, index, recent, cooldowns)
            }
            coordinatedTimeline.last()
        }
        val action = step.action
        val state = NpcBrainState(
            action.intent,
            if (timeMs >= action.arrivedAt) action.destination else action.origin,
            action.arrivedAt,
            action.finishedAt,
            (listOf(action.intent) + step.recentBefore).take(4),
            targetSpot = action.destination,
            decisionIndex = step.intentIndex,
            cooldowns = step.cooldownsBefore,
        )
        return action to state
    }

    @Synchronized internal fun clearTimelineCache() { coordinatedTimeline.clear() }

    override fun movementAt(timeMs: Long): NpcMovement = movementAt(timeMs, null)

    private fun movementAt(timeMs: Long, frameState: NpcBrainState?): NpcMovement {
        if (npcId == "bulldog_exec") return bulldogMovement(timeMs.coerceAtLeast(0))
        val state = frameState ?: stateAt(timeMs)
        val socialClock = state.socialTargetId != null && state.currentIntent == NpcIntent.SOCIALIZE
        val movementTimeMs = if (socialClock) timeMs else socialSession?.baseTimeAt(npcId, timeMs) ?: timeMs
        return regularMovement(movementTimeMs, state)
    }

    /** Snapshot renderizável: posição, pose e identidade da ação são resolvidas num só objeto. */
    fun frameStateAt(timeMs: Long): OfficeNpcFrameState {
        val state = stateAt(timeMs)
        val movement = movementAt(timeMs, state)
        val speech = socialSession?.speechEventAt(npcId, timeMs) ?: executiveSpeechEventAt(timeMs)
        val phase = when {
            movement.animation == NpcAnimation.STAND_UP -> OfficeActionPhase.STANDING_UP
            movement.phase == PathPhase.TURN -> OfficeActionPhase.TURNING
            movement.animation == NpcAnimation.SIT -> OfficeActionPhase.SITTING_DOWN
            movement.phase == PathPhase.WALK || movement.phase == PathPhase.ENTER || movement.phase == PathPhase.EXIT -> OfficeActionPhase.WALKING
            state.currentIntent == NpcIntent.SOCIALIZE || movement.animation == NpcAnimation.TALK -> OfficeActionPhase.INTERACTING
            else -> OfficeActionPhase.IDLE
        }
        return OfficeNpcFrameState(
            npcId = npcId, x = movement.x, floorY = movement.floorY, phase = phase,
            animation = movement.animation, facing = movement.facing ?: OfficeNavigationGraph.spots.getValue(state.targetSpot ?: state.currentSpot).facing,
            seated = movement.seated, actionId = if (state.currentIntent == NpcIntent.SOCIALIZE) "social:${state.intentStartedAt}" else "${npcId}:${state.decisionIndex}:${state.intentStartedAt}",
            speech = speech, movement = movement, brainState = state,
        )
    }

    private fun executiveSpeechEventAt(timeMs: Long): OfficeSpeechEvent? {
        if (npcId != "bulldog_exec" || !shouldSpeak(timeMs)) return null
        val cycleStart = timeMs.coerceAtLeast(0) / OfficeExecutiveTimeline.CYCLE_MS * OfficeExecutiveTimeline.CYCLE_MS
        val cycle = timeMs - cycleStart
        val visitElapsed = cycle - OfficeExecutiveTimeline.ENTRY_MS
        val offset = if (visitElapsed in 2_500L until 4_300L) 2_500L else 27_000L
        val startedAt = cycleStart + OfficeExecutiveTimeline.ENTRY_MS + offset
        val lines = speechProfile?.lines.orEmpty()
        val lineIndex = if (lines.isEmpty()) 0 else Math.floorMod((timeMs / (speechProfile?.cycleMs ?: 1L) + speechSeed).toInt(), lines.size)
        val line = lines.getOrNull(lineIndex) ?: return null
        return OfficeSpeechEvent(npcId, line, startedAt, 1_800L)
    }

    fun isOffscreenAt(timeMs: Long): Boolean = npcId == "bulldog_exec" &&
        Math.floorMod(timeMs.coerceAtLeast(0), 240_000L) >= 66_000L

    /** Door animation follows the executive's approach and departure windows. */
    fun officeDoorFrameAt(timeMs: Long): Int {
        if (npcId != "bulldog_exec") return 0
        val cycle = Math.floorMod(timeMs.coerceAtLeast(0), OfficeExecutiveTimeline.CYCLE_MS)
        val entranceOpenAt = 5_800L
        val exitOpenAt = OfficeExecutiveTimeline.DOOR_OPEN_MS
        val openDuration = 300L
        val entranceCloseAt = 7_100L
        val exitCloseAt = OfficeExecutiveTimeline.EXIT_END_MS
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
        val cycle = Math.floorMod(timeMs, OfficeExecutiveTimeline.CYCLE_MS)
        val door = OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DOOR)
        val enterMs = OfficeExecutiveTimeline.ENTRY_MS
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
        if (cycle < OfficeExecutiveTimeline.RETURN_START_MS) {
            val movement = regularMovement(cycle - enterMs)
            val visitElapsed = cycle - enterMs
            val speaking = visitElapsed in 2_500L until 4_300L || visitElapsed in 27_000L until 28_800L
            return if (speaking) movement.copy(animation = NpcAnimation.TALK, localTimeMs = visitElapsed % 2_500L) else movement
        }
        val activityEnd = regularMovement(OfficeExecutiveTimeline.RETURN_START_MS - enterMs)
        val route = executiveReturnRoute(activityEnd.x, activityEnd.floorY)
        val returnDuration = (OfficeExecutiveTimeline.DOOR_OPEN_MS - OfficeExecutiveTimeline.RETURN_START_MS)
        val returnElapsed = (cycle - OfficeExecutiveTimeline.RETURN_START_MS).coerceAtLeast(0)
        if (cycle < OfficeExecutiveTimeline.DOOR_OPEN_MS) {
            val distance = route.zipWithNext().sumOf { (a, b) -> maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) }
            val progress = (distance.toFloat() * returnElapsed.toFloat() / returnDuration.toFloat()).coerceIn(0f, distance.toFloat())
            val (x, y) = sampleRoute(route, progress)
            return NpcMovement(x, y, facingRight = x < door.x, animation = NpcAnimation.WALK,
                localTimeMs = returnElapsed, walkedPx = progress, phase = PathPhase.WALK)
        }
        val exitElapsed = cycle - OfficeExecutiveTimeline.DOOR_OPEN_MS
        val f = (exitElapsed.toFloat() / (OfficeExecutiveTimeline.EXIT_END_MS - OfficeExecutiveTimeline.DOOR_OPEN_MS)).coerceIn(0f, 1f)
        return NpcMovement((door.x + (264 - door.x) * f).toInt(), door.floorY, true,
            NpcAnimation.WALK, exitElapsed, walkedPx = (264 - door.x) * f, phase = PathPhase.EXIT)
    }

    private fun executiveReturnRoute(x: Int, y: Int): List<OfficeSpot> {
        val nearest = OfficeNpcSpot.entries.minBy { spot ->
            val point = OfficeNavigationGraph.spots.getValue(spot)
            (point.x - x) * (point.x - x) + (point.floorY - y) * (point.floorY - y)
        }
        val path = OfficeNavigationGraph.route(nearest, OfficeNpcSpot.DOOR)
        return if (path.size == 1) listOf(OfficeSpot(x, y, Facing.SIDE), path.single())
        else listOf(OfficeSpot(x, y, Facing.SIDE)) + path.drop(1)
    }

    private fun sampleRoute(route: List<OfficeSpot>, distance: Float): Pair<Int, Int> {
        var remaining = distance
        for ((a, b) in route.zipWithNext()) {
            val length = maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)).toFloat()
            if (remaining <= length) {
                val f = if (length == 0f) 1f else remaining / length
                return ((a.x + (b.x - a.x) * f).toInt()) to ((a.floorY + (b.floorY - a.floorY) * f).toInt())
            }
            remaining -= length
        }
        return route.last().x to route.last().floorY
    }

    private fun regularMovement(timeMs: Long, state: NpcBrainState = stateAt(timeMs)): NpcMovement {
        val route = OfficeNavigationGraph.route(state.currentSpot, state.targetSpot ?: state.currentSpot)
        val routeLength = route.zipWithNext().sumOf { (a, b) -> maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) * 24L }
        val leavingDesk = isDesk(state.currentSpot) && state.currentSpot != (state.targetSpot ?: state.currentSpot)
        val arrivingDesk = isDesk(state.targetSpot ?: state.currentSpot) && state.currentSpot != (state.targetSpot ?: state.currentSpot)
        val preTransition = if (leavingDesk) STAND_MS + NpcPoseLibrary.TURN_MS else 0L
        val postTransition = if (arrivingDesk) NpcPoseLibrary.TURN_MS + SIT_MS else 0L
        val travelStart = (state.intentStartedAt - routeLength - preTransition - postTransition).coerceAtLeast(0)
        if (timeMs < state.intentStartedAt && route.size > 1) {
            var left = timeMs - travelStart
            if (leavingDesk && left < STAND_MS) {
                val point = OfficeNavigationGraph.spots.getValue(state.currentSpot)
                return NpcMovement(point.x, point.floorY, false, NpcAnimation.STAND_UP, left.coerceAtLeast(0), phase = PathPhase.STOP, facing = Facing.FRONT)
            }
            if (leavingDesk) left -= STAND_MS
            if (leavingDesk && left < NpcPoseLibrary.TURN_MS) {
                val point = OfficeNavigationGraph.spots.getValue(state.currentSpot)
                return NpcMovement(point.x, point.floorY, false, NpcAnimation.TURN_RIGHT, left.coerceAtLeast(0), phase = PathPhase.TURN, facing = Facing.FRONT)
            }
            if (leavingDesk) left -= NpcPoseLibrary.TURN_MS
            val routeStartedAt = travelStart + preTransition
            left = timeMs - routeStartedAt
            if (arrivingDesk && left >= routeLength) {
                val point = OfficeNavigationGraph.spots.getValue(state.targetSpot!!)
                val afterRoute = left - routeLength
                if (afterRoute < NpcPoseLibrary.TURN_MS) return NpcMovement(point.x, point.floorY, false, NpcAnimation.TURN_LEFT, afterRoute, phase = PathPhase.TURN, facing = Facing.FRONT)
                return NpcMovement(point.x, point.floorY, false, NpcAnimation.SIT, afterRoute - NpcPoseLibrary.TURN_MS,
                    phase = PathPhase.STOP, seated = true, facing = Facing.FRONT)
            }
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
        val seated = isDesk(state.currentSpot) && state.currentIntent in setOf(NpcIntent.WORK, NpcIntent.CHECK_PHONE, NpcIntent.IDLE, NpcIntent.STRETCH)
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

    override fun shouldSpeak(timeMs: Long): Boolean {
        if (npcId == "bulldog_exec") {
            val cycle = Math.floorMod(timeMs.coerceAtLeast(0), OfficeExecutiveTimeline.CYCLE_MS)
            val visitElapsed = cycle - OfficeExecutiveTimeline.ENTRY_MS
            return cycle in OfficeExecutiveTimeline.ENTRY_MS until OfficeExecutiveTimeline.RETURN_START_MS &&
                (visitElapsed in 2_500L until 4_300L || visitElapsed in 27_000L until 28_800L)
        }
        return socialSession?.shouldSpeak(npcId, timeMs) == true
    }

    private fun isDesk(spot: OfficeNpcSpot) = spot == OfficeNpcSpot.DESK_LEFT || spot == OfficeNpcSpot.DESK_RIGHT

    private companion object { const val STAND_MS = 640L; const val SIT_MS = 640L }
}

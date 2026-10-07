package com.hoodie.app.pixel.npc.restaurant

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth

/** Reconstrói toda a refeição a partir de seed + tempo; não guarda estado mutável por frame. */
data class RestaurantNpcBrain(
    val npcId: String,
    val profile: RestaurantNpcProfile,
    val homeSeat: RestaurantNpcSpot,
    val daySeed: Int,
    val variant: Int,
    val clockMinute: Int,
    val hoodieFoodServed: Boolean = false,
) {
    fun stateAt(timeMs: Long): RestaurantNpcState {
        val time = timeMs.coerceAtLeast(0)
        val seed = daySeed * 31 + variant
        val startSpot = RestaurantNpcSpot.DOOR
        val enterRoute = RestaurantNavigationGraph.route(startSpot, homeSeat)
        val enterTravel = routeDuration(enterRoute)
        val enterEnd = enterTravel + NpcPoseLibrary.TURN_MS + SIT_TRANSITION_MS
        if (time < enterEnd) {
            return state(
                RestaurantNpcIntent.ENTER, RestaurantMealState.WAITING, startSpot, homeSeat,
                0, enterEnd, emptyList(), 0, 0, targetEatingMs(seed), 0, false, emptyMap(),
            )
        }

        var cursor = enterEnd
        var meal = RestaurantMealState.WAITING
        var recent = listOf(RestaurantNpcIntent.ENTER)
        var index = 0L
        var eatenMs = 0L
        val targetEatingMs = targetEatingMs(seed)
        var drinks = 0
        var menuRead = false
        var hoodieReactionDone = false
        var cooldowns = emptyMap<RestaurantNpcIntent, Long>()

        while (true) {
            val intent = choose(seed, meal, cursor, recent, index, menuRead, cooldowns, hoodieReactionDone)
            if (intent == RestaurantNpcIntent.LEAVE) {
                val leaveEnd = cursor + STAND_TRANSITION_MS + NpcPoseLibrary.TURN_MS + routeDuration(
                    RestaurantNavigationGraph.route(homeSeat, RestaurantNpcSpot.DOOR),
                )
                val leaving = state(
                    intent, meal, homeSeat, RestaurantNpcSpot.DOOR, cursor, leaveEnd,
                    recent, index, eatenMs, targetEatingMs, drinks, menuRead, cooldowns,
                )
                return if (time < leaveEnd) leaving else leaving.copy(
                    currentSpot = RestaurantNpcSpot.DOOR,
                    targetSpot = RestaurantNpcSpot.DOOR,
                    intentStartedAt = leaveEnd,
                    nextDecisionAt = leaveEnd,
                )
            }

            val duration = durationMs(intent, seed, index)
            val end = cursor + duration
            val actionMeal = if (intent == RestaurantNpcIntent.EAT && meal == RestaurantMealState.SERVED) {
                RestaurantMealState.EATING
            } else meal
            val line = if (intent == RestaurantNpcIntent.TALK) {
                RestaurantSpeechLibrary.line(actionMeal, clockMinute, seed, index)
            } else null
            val active = state(
                intent, actionMeal, homeSeat, homeSeat, cursor, end,
                recent, index, eatenMs + if (intent == RestaurantNpcIntent.EAT) (time - cursor).coerceIn(0, duration) else 0,
                targetEatingMs, drinks, menuRead, cooldowns, line,
            )
            if (time < end) return active

            when (intent) {
                RestaurantNpcIntent.WAIT_FOOD -> meal = RestaurantMealState.SERVED
                RestaurantNpcIntent.EAT -> {
                    eatenMs += duration
                    meal = if (eatenMs >= targetEatingMs) RestaurantMealState.FINISHED else RestaurantMealState.EATING
                }
                RestaurantNpcIntent.DRINK -> drinks++
                RestaurantNpcIntent.READ_MENU -> menuRead = true
                RestaurantNpcIntent.REACT_TO_HOODIE -> hoodieReactionDone = true
                else -> Unit
            }
            cooldowns = cooldowns.toMutableMap().apply {
                when (intent) {
                    RestaurantNpcIntent.CHECK_PHONE -> put(intent, end + cooldownMs(profile.phoneCooldownMs, seed, index, 41))
                    RestaurantNpcIntent.DRINK -> put(intent, end + cooldownMs(profile.drinkCooldownMs, seed, index, 43))
                    RestaurantNpcIntent.TALK -> put(intent, end + cooldownMs(profile.talkCooldownMs, seed, index, 47))
                    else -> Unit
                }
            }
            recent = (listOf(intent) + recent).take(4)
            cursor = end
            index++
        }
    }

    fun movementAt(timeMs: Long, state: RestaurantNpcState = stateAt(timeMs)): NpcMovement {
        val time = timeMs.coerceAtLeast(0)
        if (state.currentIntent == RestaurantNpcIntent.ENTER) {
            val route = RestaurantNavigationGraph.route(RestaurantNpcSpot.DOOR, homeSeat)
            val travel = routeDuration(route)
            val seat = RestaurantNavigationGraph.spots.getValue(homeSeat)
            if (time < travel) return routeMovement(route, time, PathPhase.ENTER)
            val afterRoute = time - travel
            if (afterRoute < NpcPoseLibrary.TURN_MS) return NpcMovement(
                seat.x, seat.floorY, false, NpcAnimation.TURN_LEFT, afterRoute,
                phase = PathPhase.TURN, facing = seat.interactionFacing,
            )
            return NpcMovement(
                seat.x, seat.floorY, false, NpcAnimation.SIT, afterRoute - NpcPoseLibrary.TURN_MS,
                phase = PathPhase.ENTER, seated = true, facing = seat.interactionFacing,
            )
        }
        if (state.currentIntent == RestaurantNpcIntent.LEAVE) {
            if (state.currentSpot == RestaurantNpcSpot.DOOR) {
                val door = RestaurantNavigationGraph.spots.getValue(RestaurantNpcSpot.DOOR)
                return NpcMovement(door.x, door.floorY, true, NpcAnimation.IDLE, 0, phase = PathPhase.EXIT)
            }
            val elapsed = (time - state.intentStartedAt).coerceAtLeast(0)
                .coerceAtMost(STAND_TRANSITION_MS + NpcPoseLibrary.TURN_MS)
            val seat = RestaurantNavigationGraph.spots.getValue(state.currentSpot)
            if (elapsed < STAND_TRANSITION_MS) return NpcMovement(
                seat.x, seat.floorY, false, NpcAnimation.STAND_UP, elapsed,
                phase = PathPhase.STOP, seated = false, facing = seat.interactionFacing,
            )
            val afterStand = elapsed - STAND_TRANSITION_MS
            if (afterStand < NpcPoseLibrary.TURN_MS) return NpcMovement(
                seat.x, seat.floorY, false, NpcAnimation.TURN_RIGHT, afterStand,
                phase = PathPhase.TURN, seated = false, facing = Facing.FRONT,
            )
            return routeMovement(
                RestaurantNavigationGraph.route(state.currentSpot, RestaurantNpcSpot.DOOR),
                afterStand - NpcPoseLibrary.TURN_MS,
                PathPhase.EXIT,
            )
        }

        val seat = RestaurantNavigationGraph.spots.getValue(homeSeat)
        val animation = when (state.currentIntent) {
            RestaurantNpcIntent.EAT -> NpcAnimation.SIT_EAT
            RestaurantNpcIntent.DRINK -> NpcAnimation.SIT_DRINK
            RestaurantNpcIntent.CHECK_PHONE -> NpcAnimation.SIT_PHONE
            RestaurantNpcIntent.LOOK_AROUND, RestaurantNpcIntent.LOOK_WINDOW -> NpcAnimation.SIT_LOOK
            RestaurantNpcIntent.READ_MENU -> NpcAnimation.SIT_READ_MENU
            RestaurantNpcIntent.TALK -> NpcAnimation.TALK
            RestaurantNpcIntent.REACT_TO_HOODIE -> NpcAnimation.SIT_LOOK
            RestaurantNpcIntent.WAIT_FOOD, RestaurantNpcIntent.IDLE -> NpcAnimation.IDLE
            RestaurantNpcIntent.ENTER, RestaurantNpcIntent.LEAVE -> NpcAnimation.IDLE
        }
        return NpcMovement(
            seat.x, seat.floorY, facingRight = false, animation = animation,
            localTimeMs = (time - state.intentStartedAt).coerceAtLeast(0),
            phase = PathPhase.STOP, seated = true, facing = seat.facing,
        )
    }

    fun speechLineAt(timeMs: Long, state: RestaurantNpcState = stateAt(timeMs)): String? =
        state.speechLine?.takeIf {
            state.currentIntent == RestaurantNpcIntent.TALK && timeMs - state.intentStartedAt in 0 until SPEECH_VISIBLE_MS
        }

    fun tableStateAt(timeMs: Long, state: RestaurantNpcState = stateAt(timeMs)): RestaurantTableState {
        val foodAmount = when (state.mealState) {
            RestaurantMealState.WAITING -> RestaurantFoodAmount.EMPTY
            RestaurantMealState.SERVED -> RestaurantFoodAmount.FULL
            RestaurantMealState.FINISHED -> RestaurantFoodAmount.EMPTY
            RestaurantMealState.EATING -> when {
                state.eatingElapsedMs < state.eatingTargetMs * 0.30 -> RestaurantFoodAmount.FULL
                state.eatingElapsedMs < state.eatingTargetMs * 0.70 -> RestaurantFoodAmount.PARTIAL
                else -> RestaurantFoodAmount.LOW
            }
        }
        return RestaurantTableState(
            mealState = state.mealState,
            menuOpen = state.mealState == RestaurantMealState.WAITING &&
                (state.currentIntent == RestaurantNpcIntent.READ_MENU || !state.menuRead),
            foodAmount = foodAmount,
            drinkAmount = when (state.drinksTaken) {
                0 -> RestaurantDrinkAmount.FULL
                1 -> RestaurantDrinkAmount.HALF
                else -> RestaurantDrinkAmount.EMPTY
            },
        )
    }

    private fun choose(
        seed: Int,
        meal: RestaurantMealState,
        now: Long,
        recent: List<RestaurantNpcIntent>,
        index: Long,
        menuRead: Boolean,
        cooldowns: Map<RestaurantNpcIntent, Long>,
        hoodieReactionDone: Boolean,
    ): RestaurantNpcIntent {
        val weights = profile.weightsByMealState.getValue(meal).toMutableMap()
        if (hoodieFoodServed && !hoodieReactionDone && meal != RestaurantMealState.WAITING) {
            weights[RestaurantNpcIntent.REACT_TO_HOODIE] = 2
        }
        val candidates = weights.mapNotNull { (intent, base) ->
            var weight = base.coerceAtLeast(0).toFloat()
            if (now < cooldowns.getOrDefault(intent, 0)) weight = 0f
            if (intent == RestaurantNpcIntent.READ_MENU && (meal != RestaurantMealState.WAITING || menuRead)) weight = 0f
            if (intent == RestaurantNpcIntent.REACT_TO_HOODIE && (!hoodieFoodServed || hoodieReactionDone)) weight = 0f
            if (intent == RestaurantNpcIntent.TALK && now < cooldowns.getOrDefault(RestaurantNpcIntent.TALK, 0)) weight = 0f
            recent.take(4).forEachIndexed { i, previous ->
                if (previous == intent) weight *= when (i) { 0 -> 0f; 1 -> .25f; 2 -> .6f; else -> .8f }
            }
            if (weight > 0f) intent to weight else null
        }
        if (candidates.isEmpty()) return if (meal == RestaurantMealState.WAITING) RestaurantNpcIntent.WAIT_FOOD else RestaurantNpcIntent.IDLE
        val total = candidates.sumOf { it.second.toDouble() }.toFloat()
        var cursor = NpcDeterministicRandom.value(npcId, seed, index) * total
        for ((intent, weight) in candidates) {
            cursor -= weight
            if (cursor < 0f) return intent
        }
        return candidates.last().first
    }

    private fun durationMs(intent: RestaurantNpcIntent, seed: Int, index: Long): Long {
        val duration = profile.duration(intent)
        val span = duration.maximumMs - duration.minimumMs
        return duration.minimumMs +
            (NpcDeterministicRandom.value(npcId, seed, index + 0x1_0000) * (span + 1)).toLong()
    }

    private fun cooldownMs(duration: RestaurantIntentDuration, seed: Int, index: Long, offset: Long): Long {
        val span = duration.maximumMs - duration.minimumMs
        return duration.minimumMs +
            (NpcDeterministicRandom.value(npcId, seed, index + offset) * (span + 1)).toLong()
    }

    private fun targetEatingMs(seed: Int): Long = cooldownMs(profile.eatingTargetMs, seed, 0, 37)

    private fun state(
        intent: RestaurantNpcIntent,
        meal: RestaurantMealState,
        currentSpot: RestaurantNpcSpot,
        targetSpot: RestaurantNpcSpot?,
        started: Long,
        next: Long,
        recent: List<RestaurantNpcIntent>,
        index: Long,
        eaten: Long,
        targetEating: Long,
        drinks: Int,
        menuRead: Boolean,
        cooldowns: Map<RestaurantNpcIntent, Long>,
        speechLine: String? = null,
    ) = RestaurantNpcState(
        npcId, intent, meal, currentSpot, targetSpot, started, next, recent, index,
        eaten.coerceAtLeast(0), targetEating, drinks, menuRead, cooldowns, speechLine,
    )

    private fun routeDuration(route: List<RestaurantWaypoint>): Long = route.zipWithNext().sumOf { (a, b) ->
        maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * WALK_MS_PER_PIXEL
    }

    private fun routeMovement(route: List<RestaurantWaypoint>, elapsed: Long, phase: PathPhase): NpcMovement {
        var remaining = elapsed.coerceAtLeast(0)
        var walked = 0f
        for ((from, to) in route.zipWithNext()) {
            val distance = maxOf(kotlin.math.abs(to.x - from.x), kotlin.math.abs(to.floorY - from.floorY))
            val segment = (distance * WALK_MS_PER_PIXEL).coerceAtLeast(1)
            if (remaining < segment) {
                val progress = remaining.toFloat() / segment
                return NpcMovement(
                    (from.x + (to.x - from.x) * progress).toInt(),
                    (from.floorY + (to.floorY - from.floorY) * progress).toInt(),
                    to.x >= from.x, NpcAnimation.WALK, remaining, walked + distance * progress,
                    phase = phase,
                )
            }
            remaining -= segment
            walked += distance
        }
        val end = route.last()
        return NpcMovement(end.x, end.floorY, true, NpcAnimation.IDLE, remaining, walked, phase = phase)
    }

    companion object {
        const val SIT_TRANSITION_MS = 640L
        const val STAND_TRANSITION_MS = 640L
        const val WALK_MS_PER_PIXEL = 24L
        const val SPEECH_VISIBLE_MS = 1_800L
    }
}

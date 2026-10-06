package com.hoodie.app.pixel.npc.shopping

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcSpot.*

/** A história inteira pode ser reconstruída somente com identidade, seed, horário e tempo virtual. */
class ShoppingNpcBrain(
    val npcId: String,
    val daySeed: Int,
    val period: DayPeriod,
    val profile: ShoppingNpcProfile = ShoppingNpcProfiles.CALM_INDECISIVE,
) {
    private data class ActionSpec(val intent: ShoppingNpcIntent, val target: ShoppingNpcSpot)
    private data class TimedAction(
        val intent: ShoppingNpcIntent,
        val from: ShoppingNpcSpot,
        val target: ShoppingNpcSpot,
        val route: List<ShoppingNpcSpot>,
        val travelStart: Long,
        val started: Long,
        val ends: Long,
        val duration: Long,
        val basketBefore: Int,
        val basketAfter: Int,
        val speech: String?,
        val speechOffset: Long,
        val speechCooldownUntil: Long,
        val index: Long,
    )
    private data class Trip(val targetItems: Int, val actions: List<TimedAction>, val endAt: Long)

    private val trips = HashMap<Long, Trip>()
    private val cycleMs = 300_000L
    @Synchronized private fun trip(index: Long): Trip = trips.getOrPut(index) { compileTrip(index) }

    fun stateAt(timeMs: Long): ShoppingNpcState {
        val t = timeMs.coerceAtLeast(0)
        val tripIndex = t / cycleMs
        val local = t % cycleMs
        val base = tripIndex * cycleMs
        val trip = trip(tripIndex)
        val action = trip.actions.firstOrNull { local < it.ends }
        if (action == null) return ShoppingNpcState(
            ShoppingTripState.LEAVING, ShoppingNpcIntent.IDLE, OFFSCREEN, null,
            ShoppingBasketState(0, trip.targetItems), base + trip.endAt, base + cycleMs,
            trip.actions.takeLast(3).map { it.intent }.reversed(), trip.actions.size.toLong(),
            speechCooldownUntil = base + (trip.actions.lastOrNull()?.speechCooldownUntil ?: 0L),
        )
        val traveling = local < action.started
        val itemCount = when {
            action.intent != ShoppingNpcIntent.PUT_IN_BASKET -> action.basketBefore
            local < action.started + action.duration * 2 / 3 -> action.basketBefore
            else -> action.basketAfter
        }
        val intent = action.intent
        val tripState = when (intent) {
            ShoppingNpcIntent.ENTER_STORE -> ShoppingTripState.ENTERING
            ShoppingNpcIntent.WALK_TO_CHECKOUT -> ShoppingTripState.READY_TO_CHECKOUT
            ShoppingNpcIntent.WAIT_CHECKOUT, ShoppingNpcIntent.PAY -> ShoppingTripState.CHECKOUT
            ShoppingNpcIntent.EXIT_STORE -> ShoppingTripState.LEAVING
            else -> ShoppingTripState.SHOPPING
        }
        val localAction = (local - action.started).coerceAtLeast(0)
        val speechLine = action.speech?.takeIf { localAction in action.speechOffset until action.speechOffset + 1_500 }
        return ShoppingNpcState(
            tripState, intent, if (traveling) action.from else action.target,
            action.target.takeIf { traveling }, ShoppingBasketState(itemCount, trip.targetItems),
            base + if (traveling) action.travelStart else action.started,
            base + if (traveling) action.started else action.ends,
            trip.actions.take(action.index.toInt()).asReversed().map { it.intent }.take(4), action.index,
            speechLine, if (speechLine != null) localAction - action.speechOffset else 0,
            base + action.speechCooldownUntil,
        )
    }

    fun movementAt(timeMs: Long): NpcMovement {
        val t = timeMs.coerceAtLeast(0)
        val tripIndex = t / cycleMs
        val local = t % cycleMs
        val trip = trip(tripIndex)
        val action = trip.actions.firstOrNull { local < it.ends } ?: return NpcMovement(
            -30, ShoppingNavigationGraph.spots.getValue(DOOR).floorY, false,
            NpcAnimation.IDLE, local, phase = PathPhase.IDLE,
        )
        if (local < action.started && action.route.size > 1) return movementAlong(action, local)
        val spot = ShoppingNavigationGraph.spots.getValue(action.target)
        val elapsed = (local - action.started).coerceAtLeast(0)
        val animation = animation(action.intent)
        val productHeld = action.intent == ShoppingNpcIntent.PUT_IN_BASKET && action.basketAfter > action.basketBefore
        val phone = action.intent == ShoppingNpcIntent.CHECK_LIST
        return NpcMovement(
            spot.x, spot.floorY, spot.facesRight,
            animation, elapsed, phase = if (action.intent == ShoppingNpcIntent.IDLE) PathPhase.IDLE else PathPhase.STOP,
            facing = when (action.intent) {
                ShoppingNpcIntent.LOOK_PRODUCT, ShoppingNpcIntent.PICK_PRODUCT, ShoppingNpcIntent.READ_LABEL,
                ShoppingNpcIntent.COMPARE_PRODUCTS, ShoppingNpcIntent.BROWSE_AISLE, ShoppingNpcIntent.LOOK_PROMOTION,
                ShoppingNpcIntent.PUT_IN_BASKET -> com.hoodie.app.pixel.sprite.Facing.SIDE
                else -> spot.facing
            }, shoppingBasketCount = if (productHeld || phone) action.basketBefore else action.basketAfter,
            shoppingProductHeld = productHeld,
        )
    }

    fun visualStateAt(timeMs: Long): ShoppingNpcVisualState {
        val t = timeMs.coerceAtLeast(0)
        val trip = trip(t / cycleMs)
        val state = stateAt(t)
        val emptied = trip.actions.filter { it.intent == ShoppingNpcIntent.PICK_PRODUCT && it.ends <= t % cycleMs }
            .mapNotNull { aisleIndex(it.target) }.toSet()
        val selected = when {
            state.currentSpot.name.startsWith("AISLE_A") -> AISLE_A_MIDDLE
            state.currentSpot.name.startsWith("AISLE_B") -> AISLE_B_MIDDLE
            state.targetSpot?.name?.startsWith("AISLE_A") == true -> AISLE_A_MIDDLE
            state.targetSpot?.name?.startsWith("AISLE_B") == true -> AISLE_B_MIDDLE
            else -> null
        }
        val movement = movementAt(t)
        val nearDoor = (state.currentIntent == ShoppingNpcIntent.ENTER_STORE ||
            state.currentIntent == ShoppingNpcIntent.EXIT_STORE || state.targetSpot == DOOR) &&
            kotlin.math.abs(movement.x - 26) < 56 && kotlin.math.abs(movement.floorY - 160) < 48
        val active = movement.x in 0..239 || movement.phase == PathPhase.ENTER || movement.phase == PathPhase.EXIT
        return ShoppingNpcVisualState(
            active = active,
            selectedShelf = selected,
            productRemovedA = 0 in emptied,
            productRemovedB = 1 in emptied,
            checkoutActive = state.currentIntent == ShoppingNpcIntent.PAY,
            doorOpen = nearDoor,
        )
    }

    private fun compileTrip(tripIndex: Long): Trip {
        val targetItems = 1 + NpcDeterministicRandom.choose(npcId, daySeed, tripIndex + 2, 4)
        val firstA = NpcDeterministicRandom.choose(npcId, daySeed, tripIndex + 10, 2) == 0
        val aStart = if (firstA) AISLE_A_START else AISLE_B_START
        val aMiddle = if (firstA) AISLE_A_MIDDLE else AISLE_B_MIDDLE
        val bStart = if (firstA) AISLE_B_START else AISLE_A_START
        val bMiddle = if (firstA) AISLE_B_MIDDLE else AISLE_A_MIDDLE
        val specs = mutableListOf(
            ActionSpec(ShoppingNpcIntent.ENTER_STORE, DOOR),
            ActionSpec(ShoppingNpcIntent.BROWSE_AISLE, aMiddle),
            ActionSpec(ShoppingNpcIntent.LOOK_PRODUCT, aMiddle),
            ActionSpec(ShoppingNpcIntent.PICK_PRODUCT, aMiddle),
            ActionSpec(ShoppingNpcIntent.READ_LABEL, aMiddle),
            ActionSpec(ShoppingNpcIntent.PUT_IN_BASKET, aStart),
        )
        var recent = specs.asReversed().take(4).map { it.intent }
        val listIntent = weightedChoice(
            listOf(ShoppingNpcIntent.CHECK_LIST to profile.checkListWeight, ShoppingNpcIntent.CHECK_PHONE to profile.checkPhoneWeight),
            recent, tripIndex, 20,
        )
        specs += ActionSpec(listIntent, CART_AREA)
        recent = (listOf(listIntent) + recent).take(4)
        val promoOrIdle = weightedChoice(
            listOf(ShoppingNpcIntent.LOOK_PROMOTION to profile.promotionWeight, ShoppingNpcIntent.IDLE to profile.idleWeight),
            recent, tripIndex, 21,
        )
        specs += ActionSpec(promoOrIdle, if (promoOrIdle == ShoppingNpcIntent.LOOK_PROMOTION) PROMOTION_SIGN else CART_AREA)
        specs += listOf(
            ActionSpec(ShoppingNpcIntent.WALK_TO_OTHER_AISLE, bStart),
            ActionSpec(ShoppingNpcIntent.BROWSE_AISLE, bMiddle),
            ActionSpec(ShoppingNpcIntent.COMPARE_PRODUCTS, bMiddle),
        )
        repeat(targetItems - 1) { item ->
            specs += ActionSpec(ShoppingNpcIntent.PICK_PRODUCT, bMiddle)
            specs += ActionSpec(ShoppingNpcIntent.READ_LABEL, bMiddle)
            specs += ActionSpec(ShoppingNpcIntent.PUT_IN_BASKET, bStart)
            if (item < targetItems - 2) {
                val extra = weightedChoice(
                    listOf(ShoppingNpcIntent.CHECK_LIST to profile.checkListWeight, ShoppingNpcIntent.CHECK_PHONE to profile.checkPhoneWeight,
                        ShoppingNpcIntent.IDLE to profile.idleWeight),
                    specs.asReversed().take(4).map { it.intent }, tripIndex, 60 + item.toLong(),
                )
                specs += ActionSpec(extra, CART_AREA)
            }
        }
        specs += listOf(
            ActionSpec(ShoppingNpcIntent.WALK_TO_CHECKOUT, CHECKOUT_QUEUE),
            ActionSpec(ShoppingNpcIntent.WAIT_CHECKOUT, CHECKOUT_QUEUE),
            ActionSpec(ShoppingNpcIntent.PAY, CHECKOUT_COUNTER),
            ActionSpec(ShoppingNpcIntent.EXIT_STORE, OFFSCREEN),
        )

        val actions = mutableListOf<TimedAction>()
        var cursor = 0L
        var from = OFFSCREEN
        var basket = 0
        var speechCooldownUntil = 0L
        var lastPickStarted: Long? = null
        for ((index, spec) in specs.withIndex()) {
            var route = ShoppingNavigationGraph.route(from, spec.target)
            var travel = route.zipWithNext().sumOf { (a, b) -> ShoppingNavigationGraph.distance(a, b) * 25L }
            val lastPick = lastPickStarted
            if (spec.intent == ShoppingNpcIntent.PICK_PRODUCT && lastPick != null) {
                val proposedStart = cursor + travel
                val cooldownRemainder = (15_000L - (proposedStart - lastPick)).coerceAtLeast(0)
                if (cooldownRemainder > 0) {
                    val idleIndex = actions.size.toLong()
                    actions += TimedAction(ShoppingNpcIntent.IDLE, from, from, listOf(from), cursor, cursor,
                        cursor + cooldownRemainder, cooldownRemainder, basket, basket, null, 0, speechCooldownUntil, idleIndex)
                    cursor += cooldownRemainder
                    route = ShoppingNavigationGraph.route(from, spec.target)
                    travel = route.zipWithNext().sumOf { (a, b) -> ShoppingNavigationGraph.distance(a, b) * 25L }
                }
            }
            val started = cursor + travel
            val duration = durationMs(spec.intent, tripIndex, index.toLong())
            val after = if (spec.intent == ShoppingNpcIntent.PUT_IN_BASKET) (basket + 1).coerceAtMost(targetItems) else basket
            val eligible = ShoppingSpeechLibrary.lines(spec.intent, period)
            val speechChoice = NpcDeterministicRandom.value(npcId, daySeed, tripIndex * 10_000 + index + 200) < 0.56f
            val cooldown = 45_000L + (NpcDeterministicRandom.value(npcId, daySeed, tripIndex * 10_000 + index + 201) * 75_000).toLong()
            val maySpeak = eligible.isNotEmpty() && speechChoice && started >= speechCooldownUntil
            val speech = if (maySpeak) eligible[NpcDeterministicRandom.choose(npcId, daySeed, tripIndex * 10_000 + index + 202, eligible.size)] else null
            val speechOffset = if (speech != null) (700 + NpcDeterministicRandom.value(npcId, daySeed, tripIndex * 10_000 + index + 203) * (duration - 2_000).coerceAtLeast(0)).toLong() else 0L
            if (speech != null) speechCooldownUntil = started + speechOffset + cooldown
            actions += TimedAction(spec.intent, from, spec.target, route, cursor, started, started + duration, duration,
                basket, after, speech, speechOffset, speechCooldownUntil, actions.size.toLong())
            if (spec.intent == ShoppingNpcIntent.PICK_PRODUCT) lastPickStarted = started
            cursor = started + duration
            basket = after
            from = spec.target
        }
        // Assegura que mesmo uma história mais demorada deixa um período fora de cena.
        val endAt = minOf(cursor, cycleMs - 20_000L)
        return Trip(targetItems, actions.filter { it.started < endAt }.map { action ->
            if (action.ends <= endAt) action else action.copy(ends = endAt, duration = (endAt - action.started).coerceAtLeast(1))
        }, endAt)
    }

    private fun movementAlong(action: TimedAction, local: Long): NpcMovement {
        var elapsed = (local - action.travelStart).coerceAtLeast(0)
        var walked = 0f
        for ((a, b) in action.route.zipWithNext()) {
            val p = ShoppingNavigationGraph.spots.getValue(a); val q = ShoppingNavigationGraph.spots.getValue(b)
            val distance = ShoppingNavigationGraph.distance(a, b).coerceAtLeast(1)
            val segment = distance * 25L
            if (elapsed < segment) {
                val f = (elapsed.toFloat() / segment).coerceIn(0f, 1f)
                return NpcMovement(
                    (p.x + (q.x - p.x) * f).toInt(), (p.floorY + (q.floorY - p.floorY) * f).toInt(),
                    q.x >= p.x, NpcAnimation.WALK, elapsed, walked + distance * f,
                    phase = when { a == OFFSCREEN -> PathPhase.ENTER; b == OFFSCREEN -> PathPhase.EXIT; else -> PathPhase.WALK },
                    shoppingBasketCount = action.basketBefore,
                    shoppingProductHeld = action.intent == ShoppingNpcIntent.PUT_IN_BASKET && action.basketAfter > action.basketBefore,
                )
            }
            elapsed -= segment
            walked += distance
        }
        val p = ShoppingNavigationGraph.spots.getValue(action.target)
        return NpcMovement(p.x, p.floorY, p.facesRight, animation(action.intent), (local - action.started).coerceAtLeast(0), walked,
            shoppingBasketCount = action.basketAfter)
    }

    private fun animation(intent: ShoppingNpcIntent): NpcAnimation = when (intent) {
        ShoppingNpcIntent.ENTER_STORE, ShoppingNpcIntent.WALK_TO_OTHER_AISLE, ShoppingNpcIntent.WALK_TO_CHECKOUT,
        ShoppingNpcIntent.EXIT_STORE -> NpcAnimation.WALK
        ShoppingNpcIntent.BROWSE_AISLE -> NpcAnimation.STAND_BROWSE
        ShoppingNpcIntent.LOOK_PRODUCT -> NpcAnimation.LOOK
        ShoppingNpcIntent.PICK_PRODUCT -> NpcAnimation.STAND_PICK_PRODUCT
        ShoppingNpcIntent.READ_LABEL -> NpcAnimation.STAND_READ_PRODUCT
        ShoppingNpcIntent.COMPARE_PRODUCTS -> NpcAnimation.STAND_COMPARE
        ShoppingNpcIntent.CHECK_LIST -> NpcAnimation.STAND_LIST
        ShoppingNpcIntent.CHECK_PHONE -> NpcAnimation.STAND_LIST
        ShoppingNpcIntent.LOOK_PROMOTION -> NpcAnimation.LOOK_PROMOTION
        ShoppingNpcIntent.PUT_IN_BASKET -> NpcAnimation.STAND_BASKET
        ShoppingNpcIntent.WAIT_CHECKOUT -> NpcAnimation.CHECKOUT_WAIT
        ShoppingNpcIntent.PAY -> NpcAnimation.CHECKOUT_PAY
        ShoppingNpcIntent.IDLE -> NpcAnimation.IDLE
    }

    private fun durationMs(intent: ShoppingNpcIntent, trip: Long, index: Long): Long {
        val range = when (intent) {
            ShoppingNpcIntent.BROWSE_AISLE -> 5_000L..14_000L
            ShoppingNpcIntent.LOOK_PRODUCT -> 3_000L..8_000L
            ShoppingNpcIntent.READ_LABEL -> 3_000L..7_000L
            ShoppingNpcIntent.COMPARE_PRODUCTS -> 4_000L..9_000L
            ShoppingNpcIntent.CHECK_LIST -> 2_000L..6_000L
            ShoppingNpcIntent.CHECK_PHONE -> 3_000L..7_000L
            ShoppingNpcIntent.LOOK_PROMOTION -> 2_000L..5_000L
            ShoppingNpcIntent.IDLE -> 2_000L..8_000L
            ShoppingNpcIntent.WAIT_CHECKOUT -> 4_000L..12_000L
            ShoppingNpcIntent.PAY -> 3_000L..6_000L
            ShoppingNpcIntent.PICK_PRODUCT -> 1_800L..2_600L
            ShoppingNpcIntent.WALK_TO_OTHER_AISLE -> 2_000L..3_000L
            ShoppingNpcIntent.PUT_IN_BASKET -> 2_000L..4_000L
            ShoppingNpcIntent.ENTER_STORE, ShoppingNpcIntent.EXIT_STORE -> 1_000L..1_400L
            else -> profile.minPauseMs..profile.maxPauseMs
        }
        val span = range.last - range.first
        return range.first + (roll(trip, index + 1) * (span + 1)).toLong()
    }

    private fun roll(trip: Long, index: Long) = NpcDeterministicRandom.value(npcId, daySeed, trip * 10_000 + index)
    private fun weightedChoice(
        options: List<Pair<ShoppingNpcIntent, Int>>,
        recent: List<ShoppingNpcIntent>,
        trip: Long,
        index: Long,
    ): ShoppingNpcIntent {
        val weighted = options.map { (intent, base) ->
            val latest = recent.indexOf(intent)
            val factor = when (latest) { 0 -> 0f; 1 -> .25f; 2 -> .6f; 3 -> .8f; else -> 1f }
            intent to (base.coerceAtLeast(0) * factor)
        }.filter { it.second > 0 }
        if (weighted.isEmpty()) return options.first().first
        val total = weighted.sumOf { it.second.toDouble() }.toFloat()
        var cursor = roll(trip, index).toDouble() * total
        for ((intent, weight) in weighted) { cursor -= weight; if (cursor < 0.0) return intent }
        return weighted.last().first
    }
    private fun aisleIndex(spot: ShoppingNpcSpot) = when {
        spot.name.startsWith("AISLE_A") -> 0
        spot.name.startsWith("AISLE_B") -> 1
        else -> null
    }
}

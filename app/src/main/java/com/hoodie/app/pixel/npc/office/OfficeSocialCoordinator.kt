package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.brain.NpcBrainState
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.npc.brain.NpcSocialEvent
import com.hoodie.app.pixel.npc.brain.NpcSocialCoordinator as NpcSocialCoordinatorContract

typealias OfficeSocialEvent = NpcSocialEvent

/** Calendário determinístico de conversas: no máximo uma dupla e cooldown global entre eventos. */
object OfficeSocialCoordinator {
    fun activeEventAt(timeMs: Long, daySeed: Int): OfficeSocialEvent? {
        val time = timeMs.coerceAtLeast(0)
        var start = 22_000L + (NpcDeterministicRandom.value("office-social", daySeed, 0) * 20_000).toLong()
        var index = 0L
        while (start <= time) {
            val duration = 4_000L + (NpcDeterministicRandom.value("office-social", daySeed, index + 1) * 6_000).toLong()
            if (time < start + duration) {
                val speaker = if (NpcDeterministicRandom.choose("office-social", daySeed, index + 2, 2) == 0) "rabbit_analyst" else "cat_colleague"
                val speech = index % 10L < 3L
                return OfficeSocialEvent(start, duration, speaker, speech)
            }
            start += 72_000L + (NpcDeterministicRandom.value("office-social", daySeed, index + 4) * 58_000).toLong()
            index++
        }
        return null
    }

    fun currentCooldownUntil(timeMs: Long, daySeed: Int): Long {
        val time = timeMs.coerceAtLeast(0)
        var start = 22_000L + (NpcDeterministicRandom.value("office-social", daySeed, 0) * 20_000).toLong()
        var index = 0L
        var lastEnd = 0L
        while (start <= time) {
            val duration = 4_000L + (NpcDeterministicRandom.value("office-social", daySeed, index + 1) * 6_000).toLong()
            lastEnd = start + duration
            start += 72_000L + (NpcDeterministicRandom.value("office-social", daySeed, index + 4) * 58_000).toLong()
            index++
        }
        return maxOf(lastEnd, start)
    }
}

/** Os colegas recebem o mesmo encontro calculado a partir da seed; ninguém fala sozinho. */
class OfficeSocialSession(
    private val daySeed: Int,
    reservationCacheLimit: Int = RESERVATION_CACHE_LIMIT,
) : NpcSocialCoordinatorContract {
    private val cachedMeetingTravelMs: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        maxOf(
            routeDurationMs(OfficeNavigationGraph.route(OfficeNpcSpot.DESK_LEFT, OfficeNpcSpot.CENTER)),
            routeDurationMs(OfficeNavigationGraph.route(OfficeNpcSpot.DESK_RIGHT, OfficeNpcSpot.WHITEBOARD)),
        )
    }
    private var plannedThrough = -1L
    private var nextPlannedStart = 22_000L + (NpcDeterministicRandom.value("office-social", daySeed, 0) * 20_000).toLong()
    private var scheduleIndex = 0L
    private var lastActualStart = Long.MIN_VALUE
    private val scheduledEvents = mutableListOf<OfficeSocialEvent>()
    /** Packed spot assignments keep the replay window bounded without retaining per-decision maps. */
    private val reservationsByDecision = LinkedHashMap<Long, Int>(512, .75f, true)
    private val reservationCacheLimit = reservationCacheLimit.coerceAtLeast(1)
    private val resolvingReservationTimes = mutableSetOf<Long>()
    private val socialCooldownUntil = mutableMapOf<String, Long>()
    private val speechCooldownUntil = mutableMapOf<String, Long>()
    private val socialCooldownsByEvent = mutableMapOf<Long, Map<String, Long>>()
    private val speechCooldownsByEvent = mutableMapOf<Long, Pair<String, Long>>()
    private val maxDeferralMs = 90_000L
    private val minimumStartIntervalMs = 72_000L
    private val available = setOf(
        com.hoodie.app.pixel.npc.brain.NpcIntent.WORK,
        com.hoodie.app.pixel.npc.brain.NpcIntent.IDLE,
        com.hoodie.app.pixel.npc.brain.NpcIntent.READ_WHITEBOARD,
        com.hoodie.app.pixel.npc.brain.NpcIntent.LOOK_WINDOW,
    )

    @Synchronized internal fun dispose() {
        brains.values.forEach { it.socialSession = null }
        brains.clear()
        scheduledEvents.clear()
        reservationsByDecision.clear()
        resolvingReservationTimes.clear()
        socialCooldownUntil.clear()
        speechCooldownUntil.clear()
        socialCooldownsByEvent.clear()
        speechCooldownsByEvent.clear()
    }

    /** Bounded reservation memoization; old decisions can be deterministically recalculated. */
    @Synchronized internal fun cachedReservationCount(): Int = reservationsByDecision.size
    internal fun reservationCacheCapacity(): Int = reservationCacheLimit

    @Synchronized override fun activeEventAt(timeMs: Long): OfficeSocialEvent? {
        val time = timeMs.coerceAtLeast(0)
        planThrough(time + maxDeferralMs)
        var low = 0
        var high = scheduledEvents.lastIndex
        var candidate = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (scheduledEvents[middle].startAt <= time) {
                candidate = middle
                low = middle + 1
            } else high = middle - 1
        }
        return scheduledEvents.getOrNull(candidate)?.takeIf { time < it.endsAt }
    }

    private fun planThrough(horizon: Long) {
        if (horizon <= plannedThrough) return
        while (nextPlannedStart <= horizon) {
            val earliest = if (lastActualStart == Long.MIN_VALUE) nextPlannedStart
                else maxOf(nextPlannedStart, lastActualStart + minimumStartIntervalMs)
            val latest = earliest + maxDeferralMs
            var candidate = earliest
            var start: Long? = null
            while (candidate <= latest) {
                if (eligible("rabbit_analyst", candidate) && eligible("cat_colleague", candidate) &&
                    candidate >= (socialCooldownUntil["rabbit_analyst"] ?: 0L) &&
                    candidate >= (socialCooldownUntil["cat_colleague"] ?: 0L)) {
                    start = candidate
                    break
                }
                candidate += 1_000L
            }
            if (start != null) {
                val index = scheduleIndex
                val interactionMs = 4_000L + (NpcDeterministicRandom.value("office-social", daySeed, index + 1) * 6_000).toLong()
                val travelMs = meetingTravelMs()
                // O evento cobre aproximação, conversa e retorno físico. Nunca libera a rotina-base
                // enquanto os participantes ainda estão fora das mesas.
                val duration = travelMs * 2 + interactionMs + APPROACH_TRANSITION_MS + INTERACTION_PREP_MS + RETURN_TRANSITION_MS
                val speaker = if (NpcDeterministicRandom.choose("office-social", daySeed, index + 2, 2) == 0) "rabbit_analyst" else "cat_colleague"
                val shouldSpeak = scheduleIndex % 10L < 3L
                val speech = shouldSpeak && start >= (speechCooldownUntil[speaker] ?: 0L)
                val event = OfficeSocialEvent(start, duration, speaker, speech)
                scheduledEvents += event
                val eventSocialCooldowns = mutableMapOf<String, Long>()
                for (id in listOf("rabbit_analyst", "cat_colleague")) {
                    // The coordinator already enforces a 72 s global start interval. Keep
                    // participant cooldown short enough that a free pair is not needlessly
                    // blocked for another one to two minutes after a long meeting.
                    val until = event.endsAt +
                        (NpcDeterministicRandom.value(id, daySeed, index + 0x20000) * 12_000).toLong()
                    socialCooldownUntil[id] = until
                    eventSocialCooldowns[id] = until
                }
                socialCooldownsByEvent[event.startAt] = eventSocialCooldowns
                if (speech) {
                    val until = event.endsAt + 45_000L + (NpcDeterministicRandom.value(speaker, daySeed, index + 0x30000) * 75_000).toLong()
                    speechCooldownUntil[speaker] = until
                    speechCooldownsByEvent[event.startAt] = speaker to until
                }
                lastActualStart = start
            }
            val interval = 60_000L + (NpcDeterministicRandom.value("office-social", daySeed, scheduleIndex + 4) * 50_000).toLong()
            nextPlannedStart += interval
            scheduleIndex++
        }
        plannedThrough = horizon
    }

    private fun eligible(id: String, at: Long): Boolean {
        val brain = brains[id] ?: return false
        val state = brain.baseStateAt(baseTimeAt(id, at))
        val homeSpot = if (id == "rabbit_analyst") OfficeNpcSpot.DESK_LEFT else OfficeNpcSpot.DESK_RIGHT
        if (state.currentSpot != homeSpot || state.targetSpot != homeSpot || state.currentIntent !in available ||
            baseTimeAt(id, at) < state.cooldowns.socialUntil) return false
        val bulldog = brains["bulldog_exec"] ?: return true
        return bulldog.isOffscreenAt(at) && bulldog.isOffscreenAt(at + 10_000)
    }

    private val brains = mutableMapOf<String, OfficeAmbientBrain>()
    @Synchronized fun attach(brain: OfficeAmbientBrain) {
        if (brains[brain.npcId] === brain) return
        brains[brain.npcId]?.socialSession = null
        brains[brain.npcId] = brain
        brain.socialSession = this
        OfficePerformanceCounters.socialAttaches.incrementAndGet()
    }

    @Synchronized internal fun isResolvingReservationAt(timeMs: Long): Boolean = timeMs in resolvingReservationTimes

    fun stateAt(brain: OfficeAmbientBrain, timeMs: Long): com.hoodie.app.pixel.npc.brain.NpcBrainState {
        val raw = socialStateAt(brain, timeMs) ?: brain.baseStateAt(baseTimeAt(brain.npcId, timeMs))
        val visibleCooldowns = withVisibleCooldowns(brain.npcId, timeMs, raw.cooldowns)
        return if (visibleCooldowns === raw.cooldowns) raw else raw.copy(cooldowns = visibleCooldowns)
    }

    /** Destination reservation is resolved once when the action is generated, never per frame. */
    @Synchronized internal fun reservedSpotAt(npcId: String, decisionAt: Long, preferred: OfficeNpcSpot): OfficeNpcSpot {
        // A new action at the boundary currently being allocated is included as part of that
        // boundary's batch. Returning its preferred target here avoids re-entering the batch.
        if (decisionAt in resolvingReservationTimes) return preferred
        val packed = reservationsByDecision.getOrPut(decisionAt) { packReservations(assignedSpots(decisionAt)) }
        while (reservationsByDecision.size > reservationCacheLimit) {
            val eldest = reservationsByDecision.entries.iterator()
            if (eldest.hasNext()) { eldest.next(); eldest.remove() } else break
        }
        val slot = when (npcId) {
            "rabbit_analyst" -> 0
            "cat_colleague" -> 1
            "bulldog_exec" -> 2
            else -> return preferred
        }
        val spotCode = (packed ushr (slot * RESERVATION_SPOT_BITS)) and RESERVATION_SPOT_MASK
        return if (spotCode == 0) preferred else OfficeNpcSpot.entries.getOrNull(spotCode - 1) ?: preferred
    }

    /** Three spot ordinals fit in fifteen bits; no reservation maps/objects are retained per key. */
    private fun packReservations(reservations: Map<String, OfficeSpotReservation>): Int {
        var packed = 0
        for ((slot, npcId) in RESERVATION_NPC_IDS.withIndex()) {
            val spotCode = reservations[npcId]?.spot?.ordinal?.plus(1) ?: 0
            packed = packed or (spotCode shl (slot * RESERVATION_SPOT_BITS))
        }
        return packed
    }

    private fun assignedSpots(timeMs: Long): Map<String, OfficeSpotReservation> {
        resolvingReservationTimes += timeMs
        try {
        // For in-flight actions, inspect the already-coordinated timeline. Using raw timelines
        // here loses elapsed time whenever a reservation changed route length, which could let
        // a later action collide with a still-active reservation.
        val states = brains.values.map { brain ->
            val raw = brain.rawBaseActionAt(timeMs)
            brain to if (raw.startedAt < timeMs) brain.baseActionAt(timeMs) else raw
        }
        val visible = states.filter { (brain, _) -> brain.npcId != "bulldog_exec" || !brain.isOffscreenAt(timeMs) }
        val hidden = states - visible.toSet()
        val result = linkedMapOf<String, OfficeSpotReservation>()
        val reserved = mutableListOf<OfficeNpcSpot>()

        // Carry forward the reservations of already-running actions. Their timeline positions
        // are fixed at their original decision boundary and their duration reflects its route.
        val alreadyStarted = visible.filter { (_, action) -> action.startedAt < timeMs }
        for ((brain, action) in alreadyStarted) {
            val reservation = OfficeSpotReservation(
                brain.npcId, action.destination, action.startedAt, action.finishedAt,
            )
            result[brain.npcId] = reservation
            if (reservation.reservedUntil > timeMs) reserved += reservation.spot
        }

        // Resolve every action beginning at this same instant as one deterministic batch.
        val ordered = visible.filter { (_, action) -> action.startedAt == timeMs }
            .sortedWith(compareByDescending<Pair<OfficeAmbientBrain, OfficeNpcAction>> { (_, action) ->
                action.intent == com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE
            }.thenByDescending { (candidate, _) -> priority(candidate.npcId) })
        for ((candidate, action) in ordered) {
            val selected = if (hasRoom(action.destination, reserved)) action.destination else OfficeNpcSpot.entries
                .filter { hasRoom(it, reserved) }
                .minByOrNull { option -> routeDistance(action.origin, option) } ?: action.destination
            val reservation = OfficeSpotReservation(
                npcId = candidate.npcId,
                spot = selected,
                reservedFrom = timeMs,
                reservedUntil = maxOf(actionFinishedAt(action, selected), action.finishedAt, timeMs + 1L),
            )
            result[candidate.npcId] = reservation
            reserved += selected
        }
        hidden.forEach { (brain, action) ->
            result[brain.npcId] = OfficeSpotReservation(
                brain.npcId, action.destination, action.startedAt, action.finishedAt,
            )
        }
        return result
        } finally {
            resolvingReservationTimes -= timeMs
        }
    }

    private fun actionFinishedAt(action: OfficeNpcAction, destination: OfficeNpcSpot): Long {
        val route = OfficeNavigationGraph.route(action.origin, destination)
        val routeMs = route.zipWithNext().sumOf { (a, b) ->
            maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * 24L
        }
        val leavesDesk = action.origin in setOf(OfficeNpcSpot.DESK_LEFT, OfficeNpcSpot.DESK_RIGHT) && action.origin != destination
        val arrivesDesk = destination in setOf(OfficeNpcSpot.DESK_LEFT, OfficeNpcSpot.DESK_RIGHT) && action.origin != destination
        val transitions = when {
            leavesDesk -> 640L + com.hoodie.app.pixel.npc.NpcPoseLibrary.TURN_MS
            arrivesDesk -> com.hoodie.app.pixel.npc.NpcPoseLibrary.TURN_MS + 640L
            else -> 0L
        }
        val interactionMs = action.interactionEndsAt - action.arrivedAt
        return action.startedAt + routeMs + transitions + interactionMs
    }

    private fun meetingTravelMs(): Long = cachedMeetingTravelMs

    private fun routeDurationMs(route: List<OfficeSpot>): Long {
        var duration = 0L
        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            duration += maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * 24L
        }
        return duration
    }

    private companion object {
        const val RESERVATION_SPOT_BITS = 5
        const val RESERVATION_SPOT_MASK = (1 shl RESERVATION_SPOT_BITS) - 1
        val RESERVATION_NPC_IDS = listOf("rabbit_analyst", "cat_colleague", "bulldog_exec")
        const val RESERVATION_CACHE_LIMIT = 24_576
        const val APPROACH_TRANSITION_MS = 640L + com.hoodie.app.pixel.npc.NpcPoseLibrary.TURN_MS
        const val INTERACTION_PREP_MS = 300L
        const val RETURN_TRANSITION_MS = com.hoodie.app.pixel.npc.NpcPoseLibrary.TURN_MS + 640L
        const val MIN_SPOT_SEPARATION_PX = 36
    }

    private fun hasRoom(candidate: OfficeNpcSpot, reserved: List<OfficeNpcSpot>): Boolean {
        val point = OfficeNavigationGraph.spots.getValue(candidate)
        return reserved.none { existing ->
            val other = OfficeNavigationGraph.spots.getValue(existing)
            val dx = point.x - other.x
            val dy = point.floorY - other.floorY
            dx * dx + dy * dy < MIN_SPOT_SEPARATION_PX * MIN_SPOT_SEPARATION_PX
        }
    }

    private fun routeDistance(from: OfficeNpcSpot, to: OfficeNpcSpot): Int =
        OfficeNavigationGraph.route(from, to).zipWithNext().sumOf { (a, b) ->
            maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY))
        }

    /** Resolve cooldowns without allocating filtered/mapped lists on the frame path. */
    private fun withVisibleCooldowns(
        npcId: String,
        timeMs: Long,
        current: com.hoodie.app.pixel.npc.brain.NpcCooldowns,
    ): com.hoodie.app.pixel.npc.brain.NpcCooldowns {
        var social = current.socialUntil
        var speech = current.speechUntil
        for (event in scheduledEvents) {
            if (event.startAt > timeMs) continue
            social = maxOf(social, socialCooldownsByEvent[event.startAt]?.get(npcId) ?: 0L)
            val speechCooldown = speechCooldownsByEvent[event.startAt]
            if (speechCooldown?.first == npcId) speech = maxOf(speech, speechCooldown.second)
        }
        return if (social == current.socialUntil && speech == current.speechUntil) current
        else current.copy(socialUntil = social, speechUntil = speech)
    }

    private fun socialStateAt(brain: OfficeAmbientBrain, timeMs: Long): NpcBrainState? {
        if (brain.npcId != "rabbit_analyst" && brain.npcId != "cat_colleague") return null
        val event = activeEventAt(timeMs) ?: return null
        val base = brain.baseStateAt(baseTimeAt(brain.npcId, event.startAt))
        val origin = base.currentSpot
        val target = if (brain.npcId == "rabbit_analyst") OfficeNpcSpot.CENTER else OfficeNpcSpot.WHITEBOARD
        val route = OfficeNavigationGraph.route(origin, target)
        var travelMs = 0L
        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            travelMs += maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * 24L
        }
        val arrivedAt = event.startAt + travelMs + APPROACH_TRANSITION_MS
        val sharedTravelMs = cachedMeetingTravelMs
        val interactionStartsAt = event.startAt + sharedTravelMs + APPROACH_TRANSITION_MS + INTERACTION_PREP_MS
        val interactionEndsAt = event.endsAt - sharedTravelMs - RETURN_TRANSITION_MS
        val returning = timeMs >= interactionEndsAt
        val arrived = timeMs >= arrivedAt
        // Movement reaches the shared meeting point at the latest participant arrival;
        // the 300 ms preparation delay belongs after arrival and must not shorten the walk.
        val phaseStart = if (returning) interactionEndsAt else arrivedAt
        val phaseEnd = if (returning) event.endsAt else interactionEndsAt
        val visibleCooldowns = withVisibleCooldowns(brain.npcId, timeMs, base.cooldowns)
        return NpcBrainState(
            currentIntent = com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE,
            currentSpot = if (arrived) target else origin,
            intentStartedAt = if (returning) phaseEnd else phaseStart,
            nextDecisionAt = event.endsAt,
            recentIntents = (listOf(com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE) + base.recentIntents).take(4),
            targetSpot = if (returning) origin else target,
            socialTargetId = if (brain.npcId == "rabbit_analyst") "cat_colleague" else "rabbit_analyst",
            decisionIndex = base.decisionIndex,
            cooldowns = visibleCooldowns.copy(
                socialUntil = maxOf(visibleCooldowns.socialUntil, event.endsAt + 45_000),
            ),
        )
    }

    /** Pausa o relógio da rotina-base durante encontros e retoma a atividade suspensa ao voltar. */
    internal fun baseTimeAt(npcId: String, timeMs: Long): Long {
        if (npcId != "rabbit_analyst" && npcId != "cat_colleague") return timeMs.coerceAtLeast(0)
        var elapsedSocial = 0L
        for (event in scheduledEvents) {
            if (event.startAt >= timeMs) break
            elapsedSocial += (minOf(timeMs, event.endsAt) - event.startAt).coerceAtLeast(0)
        }
        return (timeMs - elapsedSocial).coerceAtLeast(0)
    }

    private fun priority(npcId: String) = when (npcId) {
        "bulldog_exec" -> 3
        "cat_colleague" -> 2
        else -> 1
    }

    private fun fallback(spot: OfficeNpcSpot, npcId: String) = when (spot) {
        OfficeNpcSpot.CENTER_LEFT -> OfficeNpcSpot.CENTER_RIGHT
        OfficeNpcSpot.CENTER_RIGHT -> OfficeNpcSpot.CENTER_LEFT
        OfficeNpcSpot.CENTER -> when (npcId) { "cat_colleague" -> OfficeNpcSpot.COFFEE; "rabbit_analyst" -> OfficeNpcSpot.PRINTER; else -> OfficeNpcSpot.DOOR }
        OfficeNpcSpot.COFFEE -> when (npcId) { "cat_colleague" -> OfficeNpcSpot.CENTER; "rabbit_analyst" -> OfficeNpcSpot.WHITEBOARD; else -> OfficeNpcSpot.DOOR }
        OfficeNpcSpot.WHITEBOARD -> when (npcId) { "cat_colleague" -> OfficeNpcSpot.CENTER; "rabbit_analyst" -> OfficeNpcSpot.COFFEE; else -> OfficeNpcSpot.PRINTER }
        OfficeNpcSpot.PRINTER -> when (npcId) { "cat_colleague" -> OfficeNpcSpot.CENTER; "rabbit_analyst" -> OfficeNpcSpot.WINDOW; else -> OfficeNpcSpot.WHITEBOARD }
        OfficeNpcSpot.WINDOW -> when (npcId) { "cat_colleague" -> OfficeNpcSpot.CENTER; "rabbit_analyst" -> OfficeNpcSpot.COFFEE; else -> OfficeNpcSpot.DOOR }
        OfficeNpcSpot.DOOR -> when (npcId) { "cat_colleague" -> OfficeNpcSpot.CENTER; "rabbit_analyst" -> OfficeNpcSpot.PRINTER; else -> OfficeNpcSpot.WHITEBOARD }
        OfficeNpcSpot.DESK_LEFT -> if (npcId == "cat_colleague") OfficeNpcSpot.CENTER else OfficeNpcSpot.WINDOW
        OfficeNpcSpot.DESK_RIGHT -> if (npcId == "rabbit_analyst") OfficeNpcSpot.CENTER else OfficeNpcSpot.PRINTER
    }

    fun isSpeaker(npcId: String, timeMs: Long): Boolean = shouldSpeak(npcId, timeMs)

    fun speechEventAt(npcId: String, timeMs: Long): OfficeSpeechEvent? {
        if (!shouldSpeak(npcId, timeMs)) return null
        val event = activeEventAt(timeMs) ?: return null
        val brain = brains[npcId] ?: return null
        val lines = brain.speechProfile?.lines.orEmpty()
        if (lines.isEmpty()) return null
        val profile = brain.speechProfile!!
        val index = Math.floorMod((timeMs / profile.cycleMs + brain.speechSeed).toInt(), lines.size)
        val partner = if (npcId == "rabbit_analyst") "cat_colleague" else "rabbit_analyst"
        return OfficeSpeechEvent(
            npcId, lines[index],
            event.startAt + meetingTravelMs() + APPROACH_TRANSITION_MS + INTERACTION_PREP_MS,
            1_700L, partner,
        )
    }

    fun shouldSpeak(npcId: String, timeMs: Long): Boolean {
        val event = activeEventAt(timeMs) ?: return false
        if (!event.withSpeechBubble || event.speakerId != npcId) return false
        val state = brains[npcId]?.let { stateAt(it, timeMs) } ?: return false
        if (state.currentIntent != com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE) return false
        val rabbit = brains["rabbit_analyst"]?.let { stateAt(it, timeMs) } ?: return false
        val cat = brains["cat_colleague"]?.let { stateAt(it, timeMs) } ?: return false
        if (rabbit.currentIntent != com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE ||
            rabbit.currentSpot != rabbit.targetSpot ||
            cat.currentIntent != com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE ||
            cat.currentSpot != cat.targetSpot
        ) return false
        val bubbleStartedAt = event.startAt + meetingTravelMs() + APPROACH_TRANSITION_MS + INTERACTION_PREP_MS
        return timeMs >= bubbleStartedAt && timeMs < bubbleStartedAt + 1_700 && timeMs < event.endsAt
    }
}

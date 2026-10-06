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
class OfficeSocialSession(private val daySeed: Int) : NpcSocialCoordinatorContract {
    private var plannedThrough = -1L
    private var nextPlannedStart = 22_000L + (NpcDeterministicRandom.value("office-social", daySeed, 0) * 20_000).toLong()
    private var scheduleIndex = 0L
    private var lastActualStart = Long.MIN_VALUE
    private val scheduledEvents = mutableListOf<OfficeSocialEvent>()
    private val socialCooldownUntil = mutableMapOf<String, Long>()
    private val speechCooldownUntil = mutableMapOf<String, Long>()
    private val socialCooldownsByEvent = mutableMapOf<Long, Map<String, Long>>()
    private val speechCooldownsByEvent = mutableMapOf<Long, Pair<String, Long>>()
    private val maxDeferralMs = 45_000L
    private val minimumStartIntervalMs = 72_000L
    private val available = setOf(
        com.hoodie.app.pixel.npc.brain.NpcIntent.WORK,
        com.hoodie.app.pixel.npc.brain.NpcIntent.IDLE,
        com.hoodie.app.pixel.npc.brain.NpcIntent.READ_WHITEBOARD,
        com.hoodie.app.pixel.npc.brain.NpcIntent.LOOK_WINDOW,
    )

    @Synchronized override fun activeEventAt(timeMs: Long): OfficeSocialEvent? {
        val time = timeMs.coerceAtLeast(0)
        planThrough(time + maxDeferralMs)
        return scheduledEvents.lastOrNull { time >= it.startAt && time < it.endsAt }
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
                val duration = 4_000L + (NpcDeterministicRandom.value("office-social", daySeed, index + 1) * 6_000).toLong()
                val speaker = if (NpcDeterministicRandom.choose("office-social", daySeed, index + 2, 2) == 0) "rabbit_analyst" else "cat_colleague"
                val shouldSpeak = scheduleIndex % 10L < 3L
                val speech = shouldSpeak && start >= (speechCooldownUntil[speaker] ?: 0L)
                val event = OfficeSocialEvent(start, duration, speaker, speech)
                scheduledEvents += event
                val eventSocialCooldowns = mutableMapOf<String, Long>()
                for (id in listOf("rabbit_analyst", "cat_colleague")) {
                    val until = event.endsAt + 45_000L +
                        (NpcDeterministicRandom.value(id, daySeed, index + 0x20000) * 75_000).toLong()
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
            val interval = 72_000L + (NpcDeterministicRandom.value("office-social", daySeed, scheduleIndex + 4) * 58_000).toLong()
            nextPlannedStart += interval
            scheduleIndex++
        }
        plannedThrough = horizon
    }

    private fun eligible(id: String, at: Long): Boolean {
        val brain = brains[id] ?: return false
        val state = brain.baseStateAt(at)
        val homeSpot = if (id == "rabbit_analyst") OfficeNpcSpot.DESK_LEFT else OfficeNpcSpot.DESK_RIGHT
        if (state.currentSpot != homeSpot || state.currentIntent !in available || at < state.cooldowns.socialUntil) return false
        val bulldog = brains["bulldog_exec"] ?: return true
        return bulldog.isOffscreenAt(at) && bulldog.isOffscreenAt(at + 10_000)
    }

    private val brains = mutableMapOf<String, OfficeAmbientBrain>()
    @Synchronized fun attach(brain: OfficeAmbientBrain) { brains[brain.npcId] = brain }

    fun stateAt(brain: OfficeAmbientBrain, timeMs: Long): com.hoodie.app.pixel.npc.brain.NpcBrainState {
        val raw = socialStateAt(brain, timeMs) ?: brain.baseStateAt(timeMs)
        val (visibleSocialUntil, visibleSpeechUntil) = cooldownsAt(brain.npcId, timeMs)
        val base = raw.copy(cooldowns = raw.cooldowns.copy(
            socialUntil = maxOf(raw.cooldowns.socialUntil, visibleSocialUntil),
            speechUntil = maxOf(raw.cooldowns.speechUntil, visibleSpeechUntil),
        ))
        val destination = base.targetSpot ?: base.currentSpot
        val assigned = assignedSpots(timeMs)[brain.npcId] ?: destination
        if (assigned == destination) return base
        val previousTravel = OfficeNavigationGraph.route(base.currentSpot, destination).zipWithNext().sumOf { (a, b) ->
            maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * 24L
        }
        val travel = OfficeNavigationGraph.route(base.currentSpot, assigned).zipWithNext().sumOf { (a, b) ->
            maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * 24L
        }
        val travelStartedAt = (base.intentStartedAt - previousTravel).coerceAtLeast(0)
        return base.copy(targetSpot = assigned, intentStartedAt = travelStartedAt + travel)
    }

    private fun assignedSpots(timeMs: Long): Map<String, OfficeNpcSpot> {
        val states = brains.values.map { it to (socialStateAt(it, timeMs) ?: it.baseStateAt(timeMs)) }
        val visible = states.filter { (brain, _) -> brain.npcId != "bulldog_exec" || brain.movementAt(timeMs).x >= 0 }
        val hidden = states - visible.toSet()
        val ordered = visible
            .sortedWith(compareByDescending<Pair<OfficeAmbientBrain, NpcBrainState>> { (_, state) ->
                state.currentIntent == com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE
            }.thenByDescending { (candidate, _) -> priority(candidate.npcId) })
        val reserved = mutableListOf<OfficeNpcSpot>()
        val result = linkedMapOf<String, OfficeNpcSpot>()
        for ((candidate, state) in ordered) {
            val preferred = state.targetSpot ?: state.currentSpot
            val selected = if (hasRoom(preferred, reserved)) preferred else OfficeNpcSpot.entries
                .filter { hasRoom(it, reserved) }
                .minByOrNull { option -> routeDistance(state.currentSpot, option) } ?: preferred
            result[candidate.npcId] = selected
            reserved += selected
        }
        hidden.forEach { (brain, state) -> result[brain.npcId] = state.targetSpot ?: state.currentSpot }
        return result
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

    private companion object { const val MIN_SPOT_SEPARATION_PX = 36 }

    private fun routeDistance(from: OfficeNpcSpot, to: OfficeNpcSpot): Int =
        OfficeNavigationGraph.route(from, to).zipWithNext().sumOf { (a, b) ->
            maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY))
        }

    private fun cooldownsAt(npcId: String, timeMs: Long): Pair<Long, Long> {
        val past = scheduledEvents.filter { it.startAt <= timeMs }
        val social = past.mapNotNull { socialCooldownsByEvent[it.startAt]?.get(npcId) }.maxOrNull() ?: 0L
        val speech = past.mapNotNull { event -> speechCooldownsByEvent[event.startAt]?.takeIf { it.first == npcId }?.second }.maxOrNull() ?: 0L
        return social to speech
    }

    private fun socialStateAt(brain: OfficeAmbientBrain, timeMs: Long): NpcBrainState? {
        if (brain.npcId !in setOf("rabbit_analyst", "cat_colleague")) return null
        val event = activeEventAt(timeMs) ?: return null
        val base = brain.baseStateAt(timeMs)
        val origin = brain.baseStateAt(event.startAt).currentSpot
        val target = if (brain.npcId == "rabbit_analyst") OfficeNpcSpot.CENTER else OfficeNpcSpot.WHITEBOARD
        val route = OfficeNavigationGraph.route(origin, target)
        val travelMs = route.zipWithNext().sumOf { (a, b) ->
            maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * 24L
        }
        val arrivedAt = event.startAt + travelMs
        val arrived = timeMs >= arrivedAt
        val (scheduledSocialUntil, scheduledSpeechUntil) = cooldownsAt(brain.npcId, timeMs)
        return NpcBrainState(
            currentIntent = com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE,
            currentSpot = if (arrived) target else origin,
            intentStartedAt = arrivedAt,
            nextDecisionAt = event.endsAt,
            recentIntents = (listOf(com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE) + base.recentIntents).take(4),
            targetSpot = target,
            socialTargetId = if (brain.npcId == "rabbit_analyst") "cat_colleague" else "rabbit_analyst",
            decisionIndex = base.decisionIndex,
            cooldowns = base.cooldowns.copy(
                socialUntil = maxOf(base.cooldowns.socialUntil, scheduledSocialUntil, event.endsAt + 45_000),
                speechUntil = maxOf(base.cooldowns.speechUntil, scheduledSpeechUntil),
            ),
        )
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

    fun isSpeaker(npcId: String, timeMs: Long): Boolean = activeEventAt(timeMs)?.speakerId == npcId

    fun shouldSpeak(npcId: String, timeMs: Long): Boolean {
        val event = activeEventAt(timeMs) ?: return false
        if (!event.withSpeechBubble || event.speakerId != npcId) return false
        val state = brains[npcId]?.let { stateAt(it, timeMs) } ?: return false
        if (state.currentIntent != com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE) return false
        val colleaguesArrived = setOf("rabbit_analyst", "cat_colleague").all { id ->
            val colleague = brains[id]?.let { stateAt(it, timeMs) } ?: return@all false
            colleague.currentIntent == com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE &&
                colleague.currentSpot == colleague.targetSpot
        }
        if (!colleaguesArrived) return false
        val bubbleStartedAt = event.startAt + 200L
        return timeMs >= bubbleStartedAt && timeMs < bubbleStartedAt + 1_700 && timeMs < event.endsAt
    }
}

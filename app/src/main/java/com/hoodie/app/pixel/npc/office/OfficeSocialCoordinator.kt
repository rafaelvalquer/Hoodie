package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.brain.NpcBrainState
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom

data class OfficeSocialEvent(
    val startAt: Long,
    val durationMs: Long,
    val speakerId: String,
    val withSpeechBubble: Boolean,
) {
    val endsAt: Long get() = startAt + durationMs
}

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
                val speech = NpcDeterministicRandom.value("office-social", daySeed, index + 3) < .30f
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
class OfficeSocialSession(private val daySeed: Int) {
    private var cachedTime = Long.MIN_VALUE
    private var cachedEvent: OfficeSocialEvent? = null
    private var cacheResolved = false
    private val available = setOf(
        com.hoodie.app.pixel.npc.brain.NpcIntent.WORK,
        com.hoodie.app.pixel.npc.brain.NpcIntent.IDLE,
        com.hoodie.app.pixel.npc.brain.NpcIntent.READ_WHITEBOARD,
        com.hoodie.app.pixel.npc.brain.NpcIntent.LOOK_WINDOW,
    )

    @Synchronized fun activeEventAt(timeMs: Long): OfficeSocialEvent? {
        if (cacheResolved && cachedTime == timeMs) return cachedEvent
        cachedTime = timeMs
        cacheResolved = true
        cachedEvent = resolveEvent(timeMs)
        return cachedEvent
    }

    private fun resolveEvent(timeMs: Long): OfficeSocialEvent? {
        val event = OfficeSocialCoordinator.activeEventAt(timeMs, daySeed) ?: return null
        if (!eligible("rabbit_analyst", event.startAt) || !eligible("cat_colleague", event.startAt)) return null
        val meetSpots = setOf(OfficeNpcSpot.CENTER, OfficeNpcSpot.WHITEBOARD)
        if (brains.values.any { it.npcId !in setOf("rabbit_analyst", "cat_colleague") &&
                (it.baseStateAt(event.startAt).targetSpot ?: it.baseStateAt(event.startAt).currentSpot) in meetSpots }) return null
        return event
    }

    private fun eligible(id: String, at: Long): Boolean {
        val brain = brains[id] ?: return false
        return brain.baseStateAt(at).currentIntent in available
    }

    private val brains = mutableMapOf<String, OfficeAmbientBrain>()
    fun attach(brain: OfficeAmbientBrain) { brains[brain.npcId] = brain }

    fun stateAt(brain: OfficeAmbientBrain, timeMs: Long): com.hoodie.app.pixel.npc.brain.NpcBrainState {
        val base = socialStateAt(brain, timeMs) ?: brain.baseStateAt(timeMs)
        val destination = base.targetSpot ?: base.currentSpot
        val capacity = OfficeNavigationGraph.spots.getValue(destination).capacity
        if (capacity > 1) return base
        val competing = brains.values.asSequence().filter { it.npcId != brain.npcId }
            .map { other -> other to (socialStateAt(other, timeMs) ?: other.baseStateAt(timeMs)) }
            .filter { (_, state) ->
                (state.targetSpot ?: state.currentSpot) == destination
            }
            .toList()
        if (competing.isEmpty()) return base
        val winner = (listOf(brain to base) + competing).maxWithOrNull(
            compareBy<Pair<OfficeAmbientBrain, NpcBrainState>> { (_, state) ->
                state.currentSpot == destination && (state.targetSpot == null || timeMs >= state.intentStartedAt)
            }.thenBy { (candidate, _) -> priority(candidate.npcId) },
        )?.first?.npcId
        if (winner == brain.npcId) return base
        val alternate = if (base.currentSpot == destination) fallback(destination, brain.npcId) else base.currentSpot
        val travel = if (alternate == base.currentSpot) 0L else OfficeNavigationGraph.route(base.currentSpot, alternate)
            .zipWithNext().sumOf { (a, b) -> maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.floorY - a.floorY)) * 24L }
        val arrivedAt = base.intentStartedAt + travel
        return base.copy(
            currentIntent = if (travel > 0) com.hoodie.app.pixel.npc.brain.NpcIntent.WALK_AROUND else com.hoodie.app.pixel.npc.brain.NpcIntent.IDLE,
            targetSpot = alternate.takeIf { travel > 0 },
            currentSpot = alternate.takeIf { timeMs >= arrivedAt } ?: base.currentSpot,
            intentStartedAt = arrivedAt,
            nextDecisionAt = competing.maxOf { it.second.nextDecisionAt },
        )
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
        return NpcBrainState(
            currentIntent = com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE,
            currentSpot = if (arrived) target else origin,
            intentStartedAt = arrivedAt,
            nextDecisionAt = event.endsAt,
            recentIntents = (listOf(com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE) + base.recentIntents).take(4),
            targetSpot = target,
            socialTargetId = if (brain.npcId == "rabbit_analyst") "cat_colleague" else "rabbit_analyst",
            decisionIndex = base.decisionIndex,
            cooldowns = base.cooldowns.copy(socialUntil = event.endsAt + 45_000),
        )
    }

    private fun priority(npcId: String) = when (npcId) {
        "bulldog_exec" -> 3
        "cat_colleague" -> 2
        else -> 1
    }

    private fun fallback(spot: OfficeNpcSpot, npcId: String) = when (spot) {
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
        return timeMs >= state.intentStartedAt && timeMs < state.intentStartedAt + 1_700
    }
}

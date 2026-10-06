package com.hoodie.app.pixel.npc.brain

import com.hoodie.app.pixel.npc.office.OfficeNpcSpot

object NpcIntentPlanner {
    fun choose(
        npcId: String,
        daySeed: Int,
        state: NpcBrainState,
        profile: NpcPersonalityProfile,
        now: Long,
    ): NpcIntent {
        val candidates = profile.intentWeights.mapNotNull { (intent, base) ->
            var weight = base.coerceAtLeast(0).toFloat()
            when (intent) {
                NpcIntent.GET_COFFEE -> if (now < state.cooldowns.coffeeUntil) weight = 0f
                NpcIntent.CHECK_PHONE -> if (now < state.cooldowns.phoneUntil) weight = 0f
                NpcIntent.SOCIALIZE, NpcIntent.GREET_HOODIE -> if (now < state.cooldowns.socialUntil) weight = 0f
                NpcIntent.STRETCH -> if (now < state.cooldowns.stretchUntil) weight = 0f
                else -> Unit
            }
            state.recentIntents.take(4).forEachIndexed { index, recent -> if (recent == intent) {
                weight *= when (index) { 0 -> 0f; 1 -> .25f; 2 -> .6f; else -> .8f }
            } }
            if (weight > 0f) intent to weight else null
        }
        if (candidates.isEmpty()) return NpcIntent.WORK
        val total = candidates.sumOf { it.second.toDouble() }.toFloat()
        var cursor = NpcDeterministicRandom.value(npcId, daySeed, state.decisionIndex) * total
        for ((intent, weight) in candidates) {
            cursor -= weight
            if (cursor < 0f) return intent
        }
        return candidates.last().first
    }

    fun target(intent: NpcIntent, current: OfficeNpcSpot, startSpot: OfficeNpcSpot): OfficeNpcSpot = when (intent) {
        NpcIntent.WORK -> startSpot
        NpcIntent.GET_COFFEE -> OfficeNpcSpot.COFFEE
        NpcIntent.CHECK_PHONE -> current
        NpcIntent.LOOK_WINDOW -> OfficeNpcSpot.WINDOW
        NpcIntent.READ_WHITEBOARD -> OfficeNpcSpot.WHITEBOARD
        NpcIntent.USE_PRINTER -> OfficeNpcSpot.PRINTER
        NpcIntent.WALK_AROUND -> if (current == OfficeNpcSpot.CENTER) OfficeNpcSpot.PRINTER else OfficeNpcSpot.CENTER
        NpcIntent.GREET_HOODIE -> OfficeNpcSpot.CENTER
        NpcIntent.SOCIALIZE -> OfficeNpcSpot.CENTER
        NpcIntent.STRETCH, NpcIntent.IDLE, NpcIntent.ENTER_OFFICE, NpcIntent.EXIT_OFFICE -> current
    }

    fun durationMs(npcId: String, daySeed: Int, index: Long, profile: NpcPersonalityProfile, intent: NpcIntent): Long {
        val range = when (intent) {
            NpcIntent.WORK -> 15_000L..40_000L
            NpcIntent.CHECK_PHONE -> 3_000L..8_000L
            NpcIntent.GET_COFFEE -> 4_000L..9_000L
            NpcIntent.LOOK_WINDOW -> 4_000L..12_000L
            NpcIntent.READ_WHITEBOARD -> 3_000L..8_000L
            NpcIntent.IDLE, NpcIntent.STRETCH -> 3_000L..15_000L
            NpcIntent.SOCIALIZE, NpcIntent.GREET_HOODIE -> 4_000L..10_000L
            NpcIntent.USE_PRINTER -> 5_000L..11_000L
            NpcIntent.WALK_AROUND -> 3_000L..7_000L
            else -> profile.minActivityMs..profile.maxActivityMs
        }
        val min = range.first; val span = (range.last - min).coerceAtLeast(0)
        return min + (NpcDeterministicRandom.value(npcId, daySeed, index + 0x10000) * (span + 1)).toLong()
    }
}

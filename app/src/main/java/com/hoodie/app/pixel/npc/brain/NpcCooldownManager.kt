package com.hoodie.app.pixel.npc.brain

/** Applies repeat-prevention windows after a planned activity finishes. */
object NpcCooldownManager {
    fun afterIntent(
        npcId: String, daySeed: Int, decisionIndex: Long, intent: NpcIntent,
        finishedAt: Long, current: NpcCooldowns,
    ): NpcCooldowns = current.copy(
        coffeeUntil = if (intent == NpcIntent.GET_COFFEE) finishedAt + 60_000 + random(npcId, daySeed, decisionIndex + 20, 30_000) else current.coffeeUntil,
        phoneUntil = if (intent == NpcIntent.CHECK_PHONE) finishedAt + 20_000 + random(npcId, daySeed, decisionIndex + 21, 30_000) else current.phoneUntil,
        socialUntil = if (intent == NpcIntent.SOCIALIZE) finishedAt + 45_000 + random(npcId, daySeed, decisionIndex + 22, 75_000) else current.socialUntil,
        stretchUntil = if (intent == NpcIntent.STRETCH) finishedAt + 60_000 + random(npcId, daySeed, decisionIndex + 23, 120_000) else current.stretchUntil,
    )

    private fun random(id: String, seed: Int, index: Long, range: Long) =
        (NpcDeterministicRandom.value(id, seed, index) * range).toLong()
}

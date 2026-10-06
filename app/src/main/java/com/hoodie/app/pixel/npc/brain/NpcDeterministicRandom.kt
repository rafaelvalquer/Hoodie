package com.hoodie.app.pixel.npc.brain

/** Hash determinístico sem estado global nem sorteios por frame. */
object NpcDeterministicRandom {
    fun value(npcId: String, daySeed: Int, decisionIndex: Long): Float {
        var x = 0x9E3779B97F4A7C15UL.toLong() xor npcId.hashCode().toLong()
        x = (x xor daySeed.toLong()) * -7046029254386353131L
        x = (x xor decisionIndex) * -4658895280553007687L
        x = x xor (x ushr 30)
        x *= -4658895280553007687L
        x = x xor (x ushr 27)
        x *= -7723592293110705685L
        x = x xor (x ushr 31)
        return ((x ushr 40).toInt() and 0xFFFFFF) / 16_777_216f
    }

    fun choose(npcId: String, daySeed: Int, decisionIndex: Long, bound: Int): Int {
        require(bound > 0)
        return (value(npcId, daySeed, decisionIndex) * bound).toInt().coerceAtMost(bound - 1)
    }
}

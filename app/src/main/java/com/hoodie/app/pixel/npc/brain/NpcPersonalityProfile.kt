package com.hoodie.app.pixel.npc.brain

data class NpcPersonalityProfile(
    val intentWeights: Map<NpcIntent, Int>,
    val minActivityMs: Long,
    val maxActivityMs: Long,
    val sociability: Float,
    val talkativeness: Float,
)

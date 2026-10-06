package com.hoodie.app.pixel.npc.brain

import com.hoodie.app.pixel.npc.office.OfficeNpcSpot

data class NpcCooldowns(
    val coffeeUntil: Long = 0,
    val phoneUntil: Long = 0,
    val speechUntil: Long = 0,
    val socialUntil: Long = 0,
    val stretchUntil: Long = 0,
)

data class NpcBrainState(
    val currentIntent: NpcIntent,
    val currentSpot: OfficeNpcSpot,
    val intentStartedAt: Long,
    val nextDecisionAt: Long,
    val recentIntents: List<NpcIntent> = emptyList(),
    val targetSpot: OfficeNpcSpot? = null,
    val socialTargetId: String? = null,
    val decisionIndex: Long = 0,
    val cooldowns: NpcCooldowns = NpcCooldowns(),
)

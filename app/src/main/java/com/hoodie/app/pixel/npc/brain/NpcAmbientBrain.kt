package com.hoodie.app.pixel.npc.brain

import com.hoodie.app.pixel.npc.NpcMovement

/** Session-local decision source used by ambient scene actors. */
interface NpcAmbientBrain {
    val npcId: String
    fun stateAt(timeMs: Long): NpcBrainState
    fun movementAt(timeMs: Long): NpcMovement
    fun shouldSpeak(timeMs: Long): Boolean
}

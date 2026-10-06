package com.hoodie.app.pixel.npc.brain

import com.hoodie.app.pixel.npc.NpcSpeechProfile
import com.hoodie.app.pixel.npc.office.OfficeSpeechLibrary

object NpcSpeechScheduler {
    fun profile(npcId: String, clockMinute: Int, daySeed: Int): NpcSpeechProfile {
        val candidates = OfficeSpeechLibrary.linesAt(clockMinute)
        val shift = NpcDeterministicRandom.choose(npcId, daySeed, clockMinute.toLong(), candidates.size)
        val ordered = candidates.drop(shift) + candidates.take(shift)
        return NpcSpeechProfile(ordered.map { it.text }, cycleMs = 90_000, visibleMs = 1_700)
    }
}

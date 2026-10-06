package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.brain.NpcBrainState
import com.hoodie.app.pixel.npc.brain.NpcIntent
import com.hoodie.app.pixel.npc.brain.NpcIntentPlanner
import com.hoodie.app.pixel.npc.brain.NpcPersonalityProfile

/** Connects the reusable intent policy to Office spots and activity rules. */
object OfficeBehaviorPlanner {
    fun choose(id: String, seed: Int, state: NpcBrainState, profile: NpcPersonalityProfile, now: Long) =
        NpcIntentPlanner.choose(id, seed, state, profile, now)

    fun target(intent: NpcIntent, current: OfficeNpcSpot, home: OfficeNpcSpot) =
        NpcIntentPlanner.target(intent, current, home)

    fun durationMs(id: String, seed: Int, index: Long, profile: NpcPersonalityProfile, intent: NpcIntent) =
        NpcIntentPlanner.durationMs(id, seed, index, profile, intent)
}

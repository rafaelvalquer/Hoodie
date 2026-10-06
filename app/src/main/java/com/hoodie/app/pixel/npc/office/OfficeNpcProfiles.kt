package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.brain.NpcIntent
import com.hoodie.app.pixel.npc.brain.NpcPersonalityProfile

object OfficeNpcProfiles {
    val rabbit = NpcPersonalityProfile(
        mapOf(NpcIntent.WORK to 40, NpcIntent.GET_COFFEE to 12, NpcIntent.CHECK_PHONE to 10,
            NpcIntent.LOOK_WINDOW to 8, NpcIntent.READ_WHITEBOARD to 10, NpcIntent.STRETCH to 7,
            NpcIntent.IDLE to 7), 15_000, 40_000, .35f, .25f,
    )
    val cat = NpcPersonalityProfile(
        mapOf(NpcIntent.WORK to 27, NpcIntent.USE_PRINTER to 12, NpcIntent.GET_COFFEE to 12,
            NpcIntent.LOOK_WINDOW to 10, NpcIntent.CHECK_PHONE to 9, NpcIntent.WALK_AROUND to 10,
            NpcIntent.IDLE to 10), 10_000, 30_000, .7f, .45f,
    )
    val bulldog = NpcPersonalityProfile(
        mapOf(NpcIntent.READ_WHITEBOARD to 30, NpcIntent.GET_COFFEE to 18, NpcIntent.CHECK_PHONE to 10,
            NpcIntent.GREET_HOODIE to 18, NpcIntent.EXIT_OFFICE to 10),
        3_000, 9_000, .6f, .55f,
    )
}

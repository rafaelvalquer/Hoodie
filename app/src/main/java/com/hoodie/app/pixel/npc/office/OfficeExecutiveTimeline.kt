package com.hoodie.app.pixel.npc.office

/** Executive visit phases and door times; the return route is scheduled before the door opens. */
object OfficeExecutiveTimeline {
    const val CYCLE_MS = 240_000L
    const val ENTRY_MS = 7_000L
    const val ACTIVITY_END_MS = 48_000L
    const val RETURN_START_MS = 48_000L
    const val DOOR_OPEN_MS = 59_000L
    const val EXIT_END_MS = 66_000L

    fun isVisibleAt(timeMs: Long): Boolean = Math.floorMod(timeMs.coerceAtLeast(0), CYCLE_MS) < EXIT_END_MS
}

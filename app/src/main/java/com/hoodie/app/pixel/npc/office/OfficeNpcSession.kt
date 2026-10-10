package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.AmbientNpcSlot
import java.util.concurrent.atomic.AtomicLong

/** Collision-safe identity for one renderer's Office simulation. */
data class OfficeSessionKey(val daySeed: Int, val variant: Int)

/** Low-cost counters exposed to debug diagnostics and deterministic regression tests. */
object OfficePerformanceCounters {
    val sessionsCreated = AtomicLong()
    val brainsCreated = AtomicLong()
    val socialAttaches = AtomicLong()
    val timelineRebuilds = AtomicLong()
    val timelinePrunes = AtomicLong()
    val timelineResets = AtomicLong()
}

/**
 * Mutable Office simulation owned by a single SceneRenderer (or an explicit preview).
 * NPC brains and their coordinator are created once; minute changes only replace speech
 * profiles and do not discard movement plans, reservations, or social cooldowns.
 */
class OfficeNpcSession internal constructor(
    val key: OfficeSessionKey,
    clockMinute: Int,
    slots: List<AmbientNpcSlot>,
    val social: OfficeSocialSession,
) {
    init { OfficePerformanceCounters.sessionsCreated.incrementAndGet() }
    private var profileMinute = clockMinute
    private val mutableSlots = slots.toMutableList()
    private var disposed = false

    @Synchronized
    fun npcSlots(clockMinute: Int): List<AmbientNpcSlot> {
        check(!disposed) { "OfficeNpcSession is disposed" }
        if (profileMinute != clockMinute) {
            profileMinute = clockMinute
            mutableSlots.indices.forEach { index ->
                val old = mutableSlots[index]
                val profile = OfficeNpcDirector.speechProfile(old.definition.id, clockMinute, key.daySeed * 31 + key.variant)
                old.officeBrain?.speechProfile = profile
                mutableSlots[index] = old.copy(definition = old.definition.copy(speechProfile = profile))
            }
        }
        return mutableSlots
    }

    @Synchronized
    internal fun dispose() {
        if (disposed) return
        disposed = true
        mutableSlots.forEach { slot ->
            slot.officeBrain?.let { brain ->
                brain.clearTimelineCache()
                brain.socialSession = null
            }
        }
        mutableSlots.clear()
        social.dispose()
    }
}

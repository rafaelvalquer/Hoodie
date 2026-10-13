package com.hoodie.app.pixel.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.VenueFrameSnapshot
import com.hoodie.app.pixel.npc.venue.VenueNpcDirector
import com.hoodie.app.pixel.npc.venue.VenueNpcFrameState
import com.hoodie.app.pixel.npc.venue.VenueNpcSession
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId

/** Cenas, sessões e trilhas comuns aos testes do Venue Activity Engine. */
object VenueTestFixtures {
    /** Cenas com roteiro implementado (cresce a cada sprint). */
    val scenes: List<SceneId> get() = SceneId.entries.filter { VenueNpcDirector.supports(it) }

    fun env(variant: Int = 0, daySeed: Int = 20_000, period: DayPeriod = DayPeriod.DAY, placeId: Long? = 7L) =
        SceneEnv(period, if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60, variant = variant, daySeed = daySeed, placeId = placeId)

    fun session(scene: SceneId, variant: Int = 0, daySeed: Int = 20_000, period: DayPeriod = DayPeriod.DAY): VenueNpcSession =
        VenueNpcDirector.createSession(scene, env(variant, daySeed, period))

    /** Estados de um NPC a cada [stepMs], até [untilMs]. */
    fun track(session: VenueNpcSession, npcId: String, untilMs: Long, stepMs: Long = 33): List<Pair<Long, VenueNpcFrameState>> =
        (0L..untilMs step stepMs).map { t -> t to session.snapshotAt(t).npcs.single { it.npcId == npcId } }

    fun snapshots(session: VenueNpcSession, untilMs: Long, stepMs: Long = 100): List<VenueFrameSnapshot> =
        (0L..untilMs step stepMs).map { session.snapshotAt(it) }

    fun ids(session: VenueNpcSession): List<String> = session.npcSlots.map { it.definition.id }
}

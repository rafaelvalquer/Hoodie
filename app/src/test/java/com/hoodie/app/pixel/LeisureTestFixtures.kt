package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.HoodieOccupancy
import com.hoodie.app.pixel.npc.leisure.LeisureNpcDirector
import com.hoodie.app.pixel.npc.leisure.LeisureNpcFrameState
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSession
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SpotId

/** Apoio comum aos testes do Passeio 2.0. */
internal object LeisureTestFixtures {
    const val RABBIT = "rabbit_walker"
    const val CAT = "cat_colleague"
    const val MOUSE = "mouse_commuter"

    fun env(variant: Int = 0, daySeed: Int = 20_000, period: DayPeriod = DayPeriod.DAY) =
        SceneEnv(period, if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60, variant = variant, daySeed = daySeed)

    fun session(variant: Int = 0, daySeed: Int = 20_000, period: DayPeriod = DayPeriod.DAY): LeisureNpcSession =
        LeisureNpcDirector.createSession(env(variant, daySeed, period))

    /** Estados de um visitante amostrados a cada [stepMs] em [0, durationMs). */
    fun track(session: LeisureNpcSession, npcId: String, durationMs: Long, stepMs: Long = 33): List<Pair<Long, LeisureNpcFrameState>> =
        (0L until durationMs step stepMs).map { t -> t to session.snapshotAt(t).npcs.single { it.npcId == npcId } }

    fun hoodieAt(x: Int, y: Int, target: SpotId?, seated: Boolean = false, walking: Boolean = false) = HoodieOccupancy(
        present = true, x = x, y = y, targetSpot = target, animation = null, seated = seated,
        transition = if (walking) HoodieOccupancy.Transition.APPROACHING else HoodieOccupancy.Transition.NONE,
    )

    /** Hoodie no caminho (PATH_A), sem querer o banco. */
    val HOODIE_ON_PATH = hoodieAt(52, 272, SpotId.PATH_A)
    /** Hoodie decidiu sentar no banco (ainda andando até lá). */
    val HOODIE_GOING_TO_BENCH = hoodieAt(52, 272, SpotId.BENCH, walking = true)
    /** Hoodie sentado no banco. */
    val HOODIE_ON_BENCH = hoodieAt(122, 228, SpotId.BENCH, seated = true)

    /** Visitante sentado no banco (ou no próprio assento): o lugar que o Hoodie usa. */
    fun onBenchSeat(s: LeisureNpcFrameState) = !s.offstage && s.x in 96..132 && s.floorY <= 240
}

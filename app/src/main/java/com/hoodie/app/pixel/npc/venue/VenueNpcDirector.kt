package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId

/**
 * Ponto de entrada dos personagens de estabelecimento. Cada cena registra o seu roteiro
 * ([VenueBlueprint]); a sessão é recriada só quando a chave muda (outro dia, lugar, variante ou
 * noite) — nunca a cada quadro.
 */
object VenueNpcDirector {
    private val blueprints: Map<SceneId, VenueBlueprint> by lazy {
        listOf(BakeryVenue, PharmacyVenue).associateBy { it.scene }
    }

    fun supports(scene: SceneId): Boolean = scene in blueprints

    fun sessionKey(scene: SceneId, env: SceneEnv): VenueSessionKey =
        VenueSessionKey(scene, env.placeId, env.daySeed, env.variant.mod(VARIANTS), env.period == DayPeriod.NIGHT)

    fun createSession(scene: SceneId, env: SceneEnv): VenueNpcSession = createSession(sessionKey(scene, env))

    fun createSession(key: VenueSessionKey): VenueNpcSession =
        VenueNpcSession(key, blueprints[key.scene] ?: error("${key.scene} não é um estabelecimento"))

    /** Slots para quem não tem um SceneRenderer (Pixel Lab, testes de catálogo). */
    fun plan(scene: SceneId, env: SceneEnv): List<AmbientNpcSlot> = createSession(scene, env).npcSlots

    /** Aparências por cena (a planta é a mesma; muda decoração e elenco). */
    const val VARIANTS = 3
}

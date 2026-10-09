package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.npc.brain.NpcSpeechScheduler

/** Elenco da cena Office Live. Cada papel mantém memória determinística para a sessão. */
object OfficeNpcDirector {
    /** Keep Office motion rollout explicitly scoped and easy to switch during homologation. */
    const val OFFICE_NPC_CONTINUOUS_TIMELINE = true

    private fun seed(env: SceneEnv) = env.daySeed * 31 + env.variant

    /** Stateless compatibility entry point for previews and deterministic tests. */
    fun plan(env: SceneEnv): List<AmbientNpcSlot> = createSession(env).npcSlots(env.clockMinute)

    /** Creates a renderer-owned session. Call once when its key changes, not once per frame. */
    fun createSession(env: SceneEnv): OfficeNpcSession {
        val daySeed = seed(env)
        val rabbit = slot(NpcCharacterRegistry.RABBIT_ANALYST, OfficeNpcSpot.DESK_LEFT, 31, daySeed, env.clockMinute, OfficeNpcProfiles.rabbit)
        val cat = slot(NpcCharacterRegistry.CAT_COLLEAGUE, OfficeNpcSpot.DESK_RIGHT, 11, daySeed, env.clockMinute, OfficeNpcProfiles.cat)
        val bulldog = slot(NpcCharacterRegistry.BULLDOG_EXEC, OfficeNpcSpot.DOOR, 71, daySeed, env.clockMinute, OfficeNpcProfiles.bulldog)
        val social = OfficeSocialSession(daySeed)
        val slots = mutableListOf(rabbit, cat, bulldog)
        slots.forEach { it.officeBrain?.let(social::attach) }
        return OfficeNpcSession(OfficeSessionKey(env.daySeed, env.variant), env.clockMinute, slots, social)
    }

    private fun slot(
        style: com.hoodie.app.pixel.character.CharacterStyle,
        spot: OfficeNpcSpot,
        seed: Int,
        daySeed: Int,
        clockMinute: Int,
        personality: com.hoodie.app.pixel.npc.brain.NpcPersonalityProfile,
    ): AmbientNpcSlot {
        val anchor = OfficeNavigationGraph.spots.getValue(spot)
        val behavior = NpcBehaviorProfile(NpcAnimation.IDLE, SpeciesMotionProfiles.forCharacter(style), reactions = false)
        val speechProfile = speechProfile(style.id, clockMinute, daySeed)
        return AmbientNpcSlot(
            definition = AmbientNpcDefinition(
                style.id, style, behavior,
                speechProfile = speechProfile,
            ),
            x = anchor.x, floorY = anchor.floorY, baseline = anchor.floorY, seed = seed,
            depth = if (spot == OfficeNpcSpot.DOOR) NpcDepth.BACKGROUND else NpcDepth.SCENE,
            officeBrain = if (OFFICE_NPC_CONTINUOUS_TIMELINE) {
                OfficePerformanceCounters.brainsCreated.incrementAndGet()
                OfficeAmbientBrain(style.id, personality, spot, daySeed, speechProfile, seed)
            } else null,
        )
    }

    internal fun speechProfile(npcId: String, clockMinute: Int, daySeed: Int) =
        if (npcId in OFFICE_NPC_IDS) NpcSpeechScheduler.profile(npcId, clockMinute, daySeed) else null

    private val OFFICE_NPC_IDS = setOf(
        NpcCharacterRegistry.RABBIT_ANALYST.id,
        NpcCharacterRegistry.CAT_COLLEAGUE.id,
        NpcCharacterRegistry.BULLDOG_EXEC.id,
    )
}

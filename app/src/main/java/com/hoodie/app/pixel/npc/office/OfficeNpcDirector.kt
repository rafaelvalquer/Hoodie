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

    private val socialSessions = object : LinkedHashMap<Int, OfficeSocialSession>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, OfficeSocialSession>?): Boolean = size > 8
    }

    private fun seed(env: SceneEnv) = env.daySeed * 31 + env.variant

    fun plan(env: SceneEnv): List<AmbientNpcSlot> {
        val daySeed = seed(env)
        val rabbit = slot(NpcCharacterRegistry.RABBIT_ANALYST, OfficeNpcSpot.DESK_LEFT, 31, daySeed, env.clockMinute, OfficeNpcProfiles.rabbit)
        val cat = slot(NpcCharacterRegistry.CAT_COLLEAGUE, OfficeNpcSpot.DESK_RIGHT, 11, daySeed, env.clockMinute, OfficeNpcProfiles.cat)
        val bulldog = slot(NpcCharacterRegistry.BULLDOG_EXEC, OfficeNpcSpot.DOOR, 71, daySeed, env.clockMinute, OfficeNpcProfiles.bulldog)
        val session = socialSession(daySeed)
        listOfNotNull(rabbit.officeBrain, cat.officeBrain, bulldog.officeBrain).forEach { brain ->
            brain.socialSession = session
            session.attach(brain)
        }
        return listOf(rabbit, cat, bulldog)
    }

    @Synchronized private fun socialSession(daySeed: Int): OfficeSocialSession =
        socialSessions.getOrPut(daySeed) { OfficeSocialSession(daySeed) }

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
        val speechProfile = if (style.id in setOf(
                NpcCharacterRegistry.RABBIT_ANALYST.id,
                NpcCharacterRegistry.CAT_COLLEAGUE.id,
                NpcCharacterRegistry.BULLDOG_EXEC.id,
            )) NpcSpeechScheduler.profile(style.id, clockMinute, daySeed) else null
        return AmbientNpcSlot(
            definition = AmbientNpcDefinition(
                style.id, style, behavior,
                speechProfile = speechProfile,
            ),
            x = anchor.x, floorY = anchor.floorY, baseline = anchor.floorY, seed = seed,
            depth = if (spot == OfficeNpcSpot.DOOR) NpcDepth.BACKGROUND else NpcDepth.SCENE,
            officeBrain = if (OFFICE_NPC_CONTINUOUS_TIMELINE) {
                OfficeAmbientBrain(style.id, personality, spot, daySeed, speechProfile, seed)
            } else null,
        )
    }
}

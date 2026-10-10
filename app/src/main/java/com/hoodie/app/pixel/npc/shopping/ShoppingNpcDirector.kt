package com.hoodie.app.pixel.npc.shopping

import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.scene.SceneEnv

object ShoppingNpcDirector {
    fun plan(env: SceneEnv): List<AmbientNpcSlot> {
        return createSession(env).npcSlots
    }

    fun createSession(env: SceneEnv): ShoppingNpcSession {
        val style = NpcCharacterRegistry.DOG_SHOPPER
        val daySeed = env.daySeed * 31 + env.variant * 17 + env.period.ordinal
        val brain = ShoppingNpcBrain(style.id, daySeed, env.period, ShoppingNpcProfiles.CALM_INDECISIVE)
        val behavior = NpcBehaviorProfile(
            animation = NpcAnimation.IDLE,
            motion = SpeciesMotionProfiles.forCharacter(style),
            reactions = false,
        )
        val definition = AmbientNpcDefinition(style.id, style, behavior)
        val slots = listOf(AmbientNpcSlot(
            definition = definition,
            x = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN).x,
            floorY = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN).floorY,
            baseline = baseline,
            seed = daySeed,
            depth = NpcDepth.SCENE,
            shoppingBrain = brain,
        ))
        return ShoppingNpcSession(daySeed, env.shoppingVenue, slots)
    }
}

/** Mutable shopper brain owned by one renderer/session; Home and previews never share it. */
class ShoppingNpcSession internal constructor(
    val seed: Int,
    val venue: com.hoodie.app.core.model.PlaceType?,
    val npcSlots: List<AmbientNpcSlot>,
)

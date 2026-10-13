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
    /**
     * Comprador ambiente de uma cena de compras. [floor]/[speech]/[baseline] trocam o
     * mercado pela loja de roupas sem mudar personagem nem comportamento.
     */
    fun plan(
        env: SceneEnv,
        floor: ShoppingFloorPlan = ShoppingNavigationGraph,
        speech: ShoppingSpeech = ShoppingSpeechLibrary,
        baseline: Int = 214,
    ): List<AmbientNpcSlot> = createSession(env, floor, speech, baseline).npcSlots

    fun createSession(
        env: SceneEnv,
        floor: ShoppingFloorPlan = ShoppingNavigationGraph,
        speech: ShoppingSpeech = ShoppingSpeechLibrary,
        baseline: Int = 214,
    ): ShoppingNpcSession {
        val style = NpcCharacterRegistry.DOG_SHOPPER
        val daySeed = env.daySeed * 31 + env.variant * 17 + env.period.ordinal
        val brain = ShoppingNpcBrain(style.id, daySeed, env.period, ShoppingNpcProfiles.CALM_INDECISIVE, floor, speech)
        val behavior = NpcBehaviorProfile(
            animation = NpcAnimation.IDLE,
            motion = SpeciesMotionProfiles.forCharacter(style),
            reactions = false,
        )
        val definition = AmbientNpcDefinition(style.id, style, behavior)
        val shopper = AmbientNpcSlot(
            definition = definition,
            x = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN).x,
            floorY = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN).floorY,
            baseline = baseline,
            seed = daySeed,
            depth = NpcDepth.SCENE,
            shoppingBrain = brain,
        )
        // Microevento (cesta que cai): o comprador ganha o roteiro e entra o cliente que ajuda.
        val slots = if (com.hoodie.app.core.config.HoodieConfig.SCENE_STORY_EVENTS) {
            val story = ShoppingStoryEvents(brain, floor)
            listOf(shopper.copy(story = story.overlay), story.helperSlot)
        } else listOf(shopper)
        return ShoppingNpcSession(daySeed, env.shoppingVenue, slots, floor)
    }
}

/** Mutable shopper brain owned by one renderer/session; Home and previews never share it. */
class ShoppingNpcSession internal constructor(
    val seed: Int,
    val venue: com.hoodie.app.core.model.PlaceType?,
    val npcSlots: List<AmbientNpcSlot>,
    /** Planta da cena (mercado ou loja): uma sessão nunca é reaproveitada na outra cena. */
    val floor: ShoppingFloorPlan = ShoppingNavigationGraph,
)

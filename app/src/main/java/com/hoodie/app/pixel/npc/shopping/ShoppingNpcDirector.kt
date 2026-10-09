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
    private data class Key(val daySeed: Int, val period: com.hoodie.app.core.time.DayPeriod, val floor: ShoppingFloorPlan)
    private val brains = LinkedHashMap<Key, ShoppingNpcBrain>()

    /**
     * Comprador ambiente de uma cena de compras. [floor]/[speech]/[baseline] trocam o
     * mercado pela loja de roupas sem mudar personagem nem comportamento.
     */
    fun plan(
        env: SceneEnv,
        floor: ShoppingFloorPlan = ShoppingNavigationGraph,
        speech: ShoppingSpeech = ShoppingSpeechLibrary,
        baseline: Int = 214,
    ): List<AmbientNpcSlot> {
        val style = NpcCharacterRegistry.DOG_SHOPPER
        val daySeed = env.daySeed * 31 + env.variant * 17 + env.period.ordinal
        val key = Key(daySeed, env.period, floor)
        val brain = synchronized(brains) {
            brains.getOrPut(key) { ShoppingNpcBrain(style.id, daySeed, env.period, ShoppingNpcProfiles.CALM_INDECISIVE, floor, speech) }
                .also { while (brains.size > 32) brains.remove(brains.keys.first()) }
        }
        val behavior = NpcBehaviorProfile(
            animation = NpcAnimation.IDLE,
            motion = SpeciesMotionProfiles.forCharacter(style),
            reactions = false,
        )
        val definition = AmbientNpcDefinition(style.id, style, behavior)
        return listOf(AmbientNpcSlot(
            definition = definition,
            x = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN).x,
            floorY = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN).floorY,
            baseline = baseline,
            seed = daySeed,
            depth = NpcDepth.SCENE,
            shoppingBrain = brain,
        ))
    }
}

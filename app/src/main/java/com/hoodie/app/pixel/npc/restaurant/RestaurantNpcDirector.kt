package com.hoodie.app.pixel.npc.restaurant

import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneFlag

/** Fachada de planejamento do restaurante; Hoodie e cliente usam o mesmo pipeline de render. */
object RestaurantNpcDirector {
    const val GUEST_ID = "cat_guest"
    val guestProfile: RestaurantNpcProfile = RestaurantNpcProfile.CALM_GUEST
    val guestSeat: RestaurantNpcSpot = RestaurantNpcSpot.TABLE_A_SEAT_RIGHT

    fun plan(env: SceneEnv): List<AmbientNpcSlot> {
        val style = NpcCharacterRegistry.CAT_GUEST
        val seat = RestaurantNavigationGraph.spots.getValue(guestSeat)
        val seed = env.daySeed * 31 + env.variant
        val brain = RestaurantNpcBrain(
            npcId = GUEST_ID,
            profile = guestProfile,
            homeSeat = guestSeat,
            daySeed = env.daySeed,
            variant = env.variant,
            clockMinute = env.clockMinute,
            hoodieFoodServed = SceneFlag.FOOD_SERVED in env.flags,
        )
        return listOf(
            AmbientNpcSlot(
                definition = AmbientNpcDefinition(
                    id = GUEST_ID,
                    characterStyle = style,
                    behaviorProfile = NpcBehaviorProfile(
                        animation = NpcAnimation.IDLE,
                        motion = SpeciesMotionProfiles.forCharacter(style),
                        reactions = false,
                    ),
                ),
                x = seat.x,
                floorY = seat.floorY,
                baseline = seat.floorY,
                seed = seed,
                depth = NpcDepth.SCENE,
                restaurantBrain = brain,
            ),
        )
    }

    fun brain(env: SceneEnv): RestaurantNpcBrain = requireNotNull(plan(env).single().restaurantBrain)

    fun tableState(env: SceneEnv, timeMs: Long): RestaurantTableState {
        val brain = brain(env)
        return brain.tableStateAt(timeMs)
    }
}

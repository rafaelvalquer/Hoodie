package com.hoodie.app.pixel.npc.shopping

import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.NpcReaction
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.npc.events.PeriodicStoryOverlay
import com.hoodie.app.pixel.npc.events.SceneStoryEvent
import com.hoodie.app.pixel.npc.events.SceneStoryEventType
import com.hoodie.app.pixel.npc.events.ScriptedStory
import com.hoodie.app.pixel.npc.events.StoryObjectHolder
import com.hoodie.app.pixel.npc.events.StoryObjectStep
import com.hoodie.app.pixel.npc.events.StoryObjectTimeline
import com.hoodie.app.pixel.npc.events.StoryTrackBuilder
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item

/**
 * SHOPPING_DROPPED_BAG: a cesta escorrega da mão do comprador parado num corredor; ele se assusta,
 * outro cliente entra, pega a cesta do chão e devolve, e os dois se agradecem. A mesma cesta
 * fica com o evento o tempo todo (dono → chão → ajudante → dono). O ajudante só existe para o
 * evento. Só acontece com o comprador parado no mesmo ponto durante o evento inteiro (as ações
 * seguidas no mesmo ponto contam: olhar, pegar, ler o rótulo); depois ele continua de onde estava.
 */
class ShoppingStoryEvents(private val shopper: ShoppingNpcBrain, private val floor: ShoppingFloorPlan) {
    val overlay = PeriodicStoryOverlay(WINDOW_MS, ::plan, setOf(shopper.npcId), setOf(HELPER_ID))

    val helperSlot: AmbientNpcSlot = NpcCharacterRegistry.RABBIT_WALKER.let { style ->
        val door = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN)
        AmbientNpcSlot(
            definition = AmbientNpcDefinition(HELPER_ID, style, NpcBehaviorProfile(NpcAnimation.IDLE, SpeciesMotionProfiles.forCharacter(style), reactions = false)),
            x = door.x, floorY = door.floorY, baseline = door.floorY, seed = 811, depth = NpcDepth.SCENE, story = overlay,
        )
    }

    private fun plan(window: Long, accepted: List<SceneStoryEvent>): ScriptedStory? {
        if (NpcDeterministicRandom.value("shopping-drop", shopper.daySeed, window) >= CHANCE) return null
        var t = window * WINDOW_MS + MIN_OFFSET_MS
        val last = (window + 1) * WINDOW_MS - OWNER_MAX_MS
        while (t <= last) {
            val state = shopper.stateAt(t)
            if (state.targetSpot == null && state.tripState == ShoppingTripState.SHOPPING && state.currentIntent in CALM) {
                val start = maxOf(t, state.intentStartedAt + SETTLE_MS)
                val ownerMs = ownerDuration(state.currentSpot)
                if (ownerMs != null && start <= last && stillThrough(state.currentSpot, start, start + ownerMs)) {
                    return script(start, window, state.currentSpot)
                }
            }
            t = maxOf(state.nextDecisionAt, t + 500)
        }
        return null
    }

    /** O comprador fica no mesmo ponto (sem andar) de [from] até [until]. */
    private fun stillThrough(spot: ShoppingNpcSpot, from: Long, until: Long): Boolean {
        var t = from
        while (true) {
            val s = shopper.stateAt(t)
            if (s.targetSpot != null || s.currentSpot != spot || s.tripState != ShoppingTripState.SHOPPING) return false
            if (s.nextDecisionAt > until) return true
            t = maxOf(s.nextDecisionAt, t + 1)
        }
    }

    private fun helperPath(spot: ShoppingNpcSpot): Pair<List<Pair<Int, Int>>, Int>? {
        val owner = floor.spots.getValue(spot)
        if (owner.x !in MIN_X..MAX_X) return null
        val route = floor.route(ShoppingNpcSpot.OFFSCREEN, spot).dropLast(1).map { floor.spots.getValue(it) }
        val approachFromLeft = (route.lastOrNull()?.x ?: owner.x - 1) <= owner.x
        val standX = if (approachFromLeft) owner.x - GAP_PX else owner.x + GAP_PX
        val points = route.drop(1).map { it.x to it.floorY } + (standX to owner.floorY)
        return points to standX
    }

    private fun ownerDuration(spot: ShoppingNpcSpot): Long? {
        val (points, _) = helperPath(spot) ?: return null
        val door = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN)
        var x = door.x; var y = door.floorY; var walk = 0L
        points.forEach { (px, py) -> walk += maxOf(kotlin.math.abs(px - x), kotlin.math.abs(py - y)) * MS_PER_PX; x = px; y = py }
        val total = HELPER_DELAY_MS + walk + points.size * NpcPoseLibrary.TURN_MS + 2 * NpcPoseLibrary.TURN_MS + PICK_MS + HAND_MS + 2 * LINE_MS
        return total.takeIf { it <= OWNER_MAX_MS }
    }

    private fun script(t0: Long, window: Long, spot: ShoppingNpcSpot): ScriptedStory {
        val owner = floor.spots.getValue(spot)
        val (points, standX) = helperPath(spot)!!
        val helperRight = standX < owner.x
        val dropX = owner.x + if (helperRight) -12 else 12
        val door = floor.spots.getValue(ShoppingNpcSpot.OFFSCREEN)
        val helper = StoryTrackBuilder(HELPER_ID, t0, door.x, door.floorY, facingRight = true)
            .hiddenUntil(t0 + HELPER_DELAY_MS)
            .walkPath(points)
        // Chega de frente para o dono, do lado de onde veio: abaixa, pega a cesta e entrega.
        helper.turnSide(helperRight)
        helper.stay(PICK_MS, NpcAnimation.STAND_PICK_PRODUCT, Facing.SIDE)
        val pickedAt = helper.time
        val handedAt = helper.serve(HAND_MS, com.hoodie.app.pixel.npc.venue.VenueScripts.SERVE_RELEASE_MS, Item.BASKET, Facing.SIDE)
        helper.stay(LINE_MS, NpcAnimation.TALK, Facing.SIDE, speech = HELPER_LINES[Math.floorMod(window.toInt(), HELPER_LINES.size)])
        val thanksAt = helper.time
        helper.stay(LINE_MS, NpcAnimation.IDLE, Facing.SIDE)
        val releaseAt = helper.time
        helper.walkPath((points.dropLast(1).asReversed()) + (door.x to door.floorY), lastPhase = PathPhase.EXIT)
        val goneAt = helper.time
        helper.hiddenUntil(goneAt + 1)

        val ownerTrack = StoryTrackBuilder(shopper.npcId, t0, owner.x, owner.floorY, facingRight = helperRight.not(), front = false)
            .stay(DROP_MS, NpcAnimation.REACTION, Facing.SIDE, reaction = NpcReaction.SURPRISED)
            .turnSide(!helperRight)
            .waitUntil(handedAt, NpcAnimation.LOOK, Facing.SIDE)
            .stay(thanksAt - handedAt, NpcAnimation.IDLE, Facing.SIDE, Item.BASKET)
            .stay(LINE_MS, NpcAnimation.TALK, Facing.SIDE, Item.BASKET, speech = OWNER_LINES[Math.floorMod(window.toInt(), OWNER_LINES.size)])
            .waitUntil(releaseAt, NpcAnimation.IDLE, Facing.SIDE, Item.BASKET)
            .build()

        val basket = StoryObjectTimeline("basket:${shopper.daySeed}:$window", Item.BASKET, listOf(
            StoryObjectStep(t0, StoryObjectHolder.HELD_BY_OWNER),
            StoryObjectStep(t0 + DROP_MS / 3, StoryObjectHolder.ON_FLOOR, dropX, owner.floorY),
            StoryObjectStep(pickedAt, StoryObjectHolder.HELD_BY_HELPER),
            StoryObjectStep(handedAt, StoryObjectHolder.HELD_BY_OWNER),
        ))
        val event = SceneStoryEvent(
            id = "shopping-drop:${shopper.daySeed}:$window", type = SceneStoryEventType.SHOPPING_DROPPED_BAG, scene = SceneId.SHOPPING,
            participants = listOf(shopper.npcId, HELPER_ID), requiredSpots = listOf(spot.name),
            startAt = t0, durationMs = goneAt - t0 + 1, priority = 2, cooldownMs = COOLDOWN_MS,
            interactAt = pickedAt - PICK_MS, resolveAt = releaseAt,
        )
        return ScriptedStory(event, listOf(ownerTrack, helper.build()), listOf(basket))
    }

    companion object {
        const val HELPER_ID = "shopping_helper"
        const val WINDOW_MS = 150_000L
        const val CHANCE = 0.55f
        const val MIN_OFFSET_MS = 12_000L
        const val SETTLE_MS = 500L
        const val DROP_MS = 1_100L
        const val HELPER_DELAY_MS = 300L
        const val PICK_MS = 700L
        const val HAND_MS = 900L
        const val LINE_MS = 1_500L
        const val GAP_PX = 32
        const val MS_PER_PX = 24L
        const val MIN_X = 64
        const val MAX_X = 200
        const val COOLDOWN_MS = 120_000L
        /** Teto para o comprador ficar parado esperando a ajuda. */
        const val OWNER_MAX_MS = 16_000L

        /** O ajudante sai daqui: o que a cesta "segura" na mão do dono durante o evento. */
        val CALM = setOf(ShoppingNpcIntent.BROWSE_AISLE, ShoppingNpcIntent.LOOK_PRODUCT, ShoppingNpcIntent.READ_LABEL,
            ShoppingNpcIntent.COMPARE_PRODUCTS, ShoppingNpcIntent.CHECK_LIST, ShoppingNpcIntent.IDLE)
        val HELPER_LINES = listOf("Caiu isso aqui!", "Opa, sua cesta!")
        val OWNER_LINES = listOf("Ah, obrigado!", "Valeu mesmo!")
    }
}

package com.hoodie.app.pixel.npc.restaurant

import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDepth
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
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item

data class RestaurantStoryKey(val daySeed: Int, val variant: Int, val hoodieFoodServed: Boolean)

/**
 * RESTAURANT_SPILL_HELP: o copo do cliente vira, ele se assusta, o garçom entra, passa o pano,
 * põe o copo de pé e os dois se agradecem; depois cada um volta ao que fazia. O garçom só existe
 * para o evento (fora dele, fora de cena). Só acontece com o cliente sentado numa ação calma
 * (olhando, celular, esperando) que dure o evento inteiro — nunca comendo, bebendo ou falando.
 */
class RestaurantStorySession(val key: RestaurantStoryKey, private val guest: RestaurantNpcBrain) {
    val overlay = PeriodicStoryOverlay(WINDOW_MS, ::plan, setOf(RestaurantNpcDirector.GUEST_ID), setOf(WAITER_ID))

    val waiterSlot: AmbientNpcSlot = NpcCharacterRegistry.DOG_WORKER.let { style ->
        AmbientNpcSlot(
            definition = AmbientNpcDefinition(WAITER_ID, style, NpcBehaviorProfile(NpcAnimation.IDLE, SpeciesMotionProfiles.forCharacter(style), reactions = false)),
            x = DOOR_X, floorY = DOOR_Y, baseline = WIPE_Y, seed = 607, depth = NpcDepth.SCENE, story = overlay,
        )
    }

    /** Os NPCs do restaurante com o roteiro do evento ligado e o garçom. */
    private var lastBase: List<AmbientNpcSlot>? = null
    private var lastSlots: List<AmbientNpcSlot>? = null
    fun slots(base: List<AmbientNpcSlot>): List<AmbientNpcSlot> {
        if (base === lastBase) lastSlots?.let { return it }
        return (base.map { it.copy(story = overlay) } + waiterSlot).also { lastBase = base; lastSlots = it }
    }

    fun dispose() { overlay.dispose(); lastBase = null; lastSlots = null }

    private fun plan(window: Long, accepted: List<SceneStoryEvent>): ScriptedStory? {
        val seed = key.daySeed * 31 + key.variant
        if (NpcDeterministicRandom.value("restaurant-spill", seed, window) >= CHANCE) return null
        val windowStart = window * WINDOW_MS + MIN_OFFSET_MS
        val windowEnd = (window + 1) * WINDOW_MS - GUEST_MS
        var t = windowStart
        while (t <= windowEnd) {
            val state = guest.stateAt(t)
            if (state.currentIntent == RestaurantNpcIntent.LEAVE) return null
            val start = maxOf(t, state.intentStartedAt + SETTLE_MS)
            if (start <= windowEnd && calmThrough(start, start + GUEST_MS)) return script(start, window)
            t = maxOf(state.nextDecisionAt, t + 500)
        }
        return null
    }

    /** Sentado no lugar dele, só em ações calmas (uma ou várias seguidas), de [from] até [until]. */
    private fun calmThrough(from: Long, until: Long): Boolean {
        var t = from
        while (true) {
            val s = guest.stateAt(t)
            if (s.currentIntent !in CALM || s.currentSpot != RestaurantNpcDirector.guestSeat) return false
            if (s.nextDecisionAt > until) return true
            t = maxOf(s.nextDecisionAt, t + 1)
        }
    }

    private fun script(t0: Long, window: Long): ScriptedStory {
        val seat = RestaurantNavigationGraph.spots.getValue(RestaurantNpcDirector.guestSeat)
        // Garçom: porta → por trás do cliente → lado esquerdo da mesa; pano, copo de pé, "de nada", volta pela porta.
        val waiter = StoryTrackBuilder(WAITER_ID, t0, DOOR_X, DOOR_Y, facingRight = false)
            .hiddenUntil(t0 + WAITER_DELAY_MS)
            .walkPath(listOf(AISLE_X to AISLE_Y, WIPE_X to WIPE_Y))
            .turnFront(Item.CLOTH)
            .stay(WIPE_MS, NpcAnimation.WIPE, Facing.FRONT, Item.CLOTH)
        val wipedAt = waiter.time
        waiter.stay(UPRIGHT_MS, NpcAnimation.STAND_PICK_PRODUCT, Facing.FRONT)
        val uprightAt = waiter.time
        val thanksAt = uprightAt
        waiter.stay(LINE_MS, NpcAnimation.IDLE, Facing.FRONT)
            .stay(LINE_MS, NpcAnimation.TALK, Facing.FRONT, speech = WAITER_LINES[Math.floorMod(window.toInt(), WAITER_LINES.size)])
        val releaseAt = waiter.time
        check(releaseAt - t0 <= GUEST_MS) { "o evento passou do tempo reservado ao cliente" }
        waiter.walkPath(listOf(AISLE_X to AISLE_Y, DOOR_X to DOOR_Y), lastPhase = PathPhase.EXIT)
        val waiterGone = waiter.time
        waiter.hiddenUntil(waiterGone + 1)

        // Cliente: susto, acompanha o garçom com o olhar, agradece e volta à rotina.
        val guestTrack = StoryTrackBuilder(RestaurantNpcDirector.GUEST_ID, t0, seat.x, seat.floorY, facingRight = false, front = true, seated = true)
            .stay(SURPRISE_MS, NpcAnimation.REACTION, Facing.FRONT, reaction = NpcReaction.SURPRISED, seated = true)
            .waitUntil(thanksAt, NpcAnimation.SIT_LOOK, Facing.FRONT)
            .stay(LINE_MS, NpcAnimation.TALK, Facing.FRONT, speech = GUEST_LINES[Math.floorMod(window.toInt(), GUEST_LINES.size)], seated = true)
            .waitUntil(releaseAt, NpcAnimation.SIT_LOOK, Facing.FRONT)
            .build()

        val glass = StoryObjectTimeline("glass:${key.daySeed}:$window", Item.GLASS, listOf(
            StoryObjectStep(t0, StoryObjectHolder.SPILLED),
            StoryObjectStep(uprightAt, StoryObjectHolder.ON_TABLE),
        ))
        val cloth = StoryObjectTimeline("cloth:${key.daySeed}:$window", Item.CLOTH, listOf(
            StoryObjectStep(t0, StoryObjectHolder.HELD_BY_HELPER),
        ))
        val event = SceneStoryEvent(
            id = "restaurant-spill:${key.daySeed}:$window", type = SceneStoryEventType.RESTAURANT_SPILL_HELP, scene = SceneId.RESTAURANT,
            participants = listOf(RestaurantNpcDirector.GUEST_ID, WAITER_ID), requiredSpots = listOf(RestaurantNpcDirector.guestSeat.name, "aisle"),
            startAt = t0, durationMs = waiterGone - t0 + 1, priority = 2, cooldownMs = COOLDOWN_MS,
            interactAt = t0 + WAITER_DELAY_MS, resolveAt = wipedAt,
        )
        return ScriptedStory(event, listOf(guestTrack, waiter.build()), listOf(glass, cloth))
    }

    companion object {
        const val WAITER_ID = "restaurant_waiter"
        const val WINDOW_MS = 150_000L
        const val CHANCE = 0.55f
        const val MIN_OFFSET_MS = 15_000L
        const val SETTLE_MS = 600L
        const val SURPRISE_MS = 1_200L
        const val WAITER_DELAY_MS = 700L
        const val WIPE_MS = 2_400L
        const val UPRIGHT_MS = 700L
        const val LINE_MS = 1_500L
        const val COOLDOWN_MS = 120_000L
        /** Quanto do evento o cliente precisa ficar calmo na cadeira (até o garçom se despedir). */
        const val GUEST_MS = 11_500L
        const val DOOR_X = 264
        const val DOOR_Y = 184
        /** Passa por trás do cliente (acima do tampo) e para do lado esquerdo livre da mesa, de frente. */
        const val AISLE_X = 182
        const val AISLE_Y = 202
        const val WIPE_X = 166
        const val WIPE_Y = 240

        val CALM = setOf(RestaurantNpcIntent.IDLE, RestaurantNpcIntent.LOOK_AROUND, RestaurantNpcIntent.LOOK_WINDOW,
            RestaurantNpcIntent.CHECK_PHONE, RestaurantNpcIntent.WAIT_FOOD)
        val GUEST_LINES = listOf("Ops! Obrigado!", "Ai, que susto! Valeu!")
        val WAITER_LINES = listOf("Sem problemas!", "Acontece!")

        fun key(env: SceneEnv) = RestaurantStoryKey(env.daySeed, env.variant, SceneFlag.FOOD_SERVED in env.flags)
    }
}

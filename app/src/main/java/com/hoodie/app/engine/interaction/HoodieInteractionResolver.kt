package com.hoodie.app.engine.interaction

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import kotlin.random.Random

/** Pure selection. The caller commits history only after the engine actually starts the sequence. */
class HoodieInteractionResolver(
    private val rules: List<InteractionRule>,
    private val random: Random = Random.Default,
    private val config: HoodieInteractionConfig = HoodieInteractionConfig(),
    private val venuesEnabled: Boolean = com.hoodie.app.core.config.HoodieConfig.HOODIE_VENUE_INTERACTIONS_V1,
) {
    private val policy = HoodieInteractionPolicy(config)

    fun stillEligible(response: InteractionResponse, context: InteractionContext): Boolean {
        if (response.ruleId == "repeat") return true
        val rule = rules.firstOrNull { it.id == response.ruleId } ?: return false
        return (venuesEnabled || rule.scene !in venueScenes) &&
            (rule.scene == null || rule.scene == context.scene || context.scene == SceneId.HOME && context.userContext == UserContextType.WORK && rule.scene == SceneId.OFFICE) &&
            (rule.activity == null || rule.activity == context.activity) && matches(rule.condition, context)
    }

    fun resolve(context: InteractionContext, gesture: InteractionGesture, history: HoodieInteractionHistory, now: Long): InteractionDecision {
        policy.evaluate(context, gesture, history, now)?.let { return it }
        if (!history.canSpeak(now)) {
            val animation = when {
                context.mood < 30 -> AnimationId.GLANCE
                context.energy < 30 -> AnimationId.EAR_FLICK
                else -> listOf(AnimationId.EAR_FLICK, AnimationId.GLANCE, AnimationId.SMILE)[random.nextInt(3)]
            }
            return InteractionDecision.Accepted(InteractionResponse("repeat", listOf(animation), null, 0, gesture = gesture))
        }
        val eligible = rules.filter { rule ->
            val workAtHome = context.scene == SceneId.HOME && context.userContext == UserContextType.WORK
            (venuesEnabled || rule.scene !in venueScenes) && rule.gesture == gesture && (rule.scene == null || rule.scene == context.scene && !(workAtHome && rule.scene == SceneId.HOME) ||
                workAtHome && rule.scene == SceneId.OFFICE) &&
                (rule.activity == null || rule.activity == context.activity) && matches(rule.condition, context)
        }
        val priority = eligible.maxOfOrNull { it.priority } ?: return InteractionDecision.Blocked("NO_ELIGIBLE_RULE")
        val candidates = eligible.filter { it.priority == priority }
        val fresh = candidates.filter { it.text !in history.recentPhrases }
        if (fresh.isEmpty()) {
            // Two-variant rules cannot supply four distinct phrases. Prefer silence to repeating history.
            return InteractionDecision.Accepted(InteractionResponse("repeat", listOf(AnimationId.GLANCE), null, 0, gesture = gesture))
        }
        var draw = random.nextInt(fresh.sumOf { it.weight })
        val selected = fresh.first { draw -= it.weight; draw < 0 }
        return InteractionDecision.Accepted(InteractionResponse(selected.id, selected.animations, selected.text, config.bubbleDurationMs,
            if (gesture == InteractionGesture.LONG_PRESS) config.maxPettingMs else 2_500, gesture))
    }

    private fun matches(condition: InteractionCondition, c: InteractionContext): Boolean {
        val a = c.animation
        fun anim(vararg ids: AnimationId) = a in ids
        return when (condition) {
            InteractionCondition.ANY -> true
            InteractionCondition.WAKING -> c.activity == HoodieActivity.WAKING_UP
            InteractionCondition.RESTING -> !c.walkingNow && (c.activity in setOf(HoodieActivity.RESTING, HoodieActivity.IDLE) || c.scene == SceneId.FAMILY && c.seated || anim(AnimationId.GYM_REST, AnimationId.LEISURE_BENCH, AnimationId.WAIT_FOOD, AnimationId.STUDY_THINK))
            InteractionCondition.BREAK -> !c.walkingNow && (c.activity in setOf(HoodieActivity.COFFEE, HoodieActivity.RESTING, HoodieActivity.IDLE) || c.animation.group == com.hoodie.app.pixel.animation.AnimGroup.COFFEE)
            InteractionCondition.TV -> anim(AnimationId.WATCH_TV)
            InteractionCondition.GAMING -> a.group == com.hoodie.app.pixel.animation.AnimGroup.GAME
            InteractionCondition.READING -> anim(AnimationId.READING)
            InteractionCondition.COOKING -> anim(AnimationId.COOK)
            InteractionCondition.CLEANING -> anim(AnimationId.CLEAN)
            InteractionCondition.FREE -> !c.seated && !c.walkingNow && (c.scene != SceneId.HOME || c.activity in setOf(HoodieActivity.IDLE, HoodieActivity.RESTING))
            InteractionCondition.OCCUPIED -> c.seated || c.activity !in setOf(HoodieActivity.IDLE, HoodieActivity.RESTING)
            InteractionCondition.TYPING -> anim(AnimationId.WORK_TYPING, AnimationId.WORK_MOUSE)
            InteractionCondition.FOCUSED -> anim(AnimationId.WORK_READ, AnimationId.WORK_NOTES)
            InteractionCondition.COFFEE -> a.group == com.hoodie.app.pixel.animation.AnimGroup.COFFEE
            InteractionCondition.TIRED -> c.energy < 30 || anim(AnimationId.WORK_TIRED)
            InteractionCondition.BULLDOG -> InteractionElement.BULLDOG in c.presentElements
            InteractionCondition.RABBIT -> InteractionElement.RABBIT in c.presentElements
            InteractionCondition.WORKING -> c.activity == HoodieActivity.WORKING
            InteractionCondition.MENU -> anim(AnimationId.LOOK_MENU)
            InteractionCondition.WAITING -> anim(AnimationId.WAIT_FOOD)
            InteractionCondition.EATING -> anim(AnimationId.EAT)
            InteractionCondition.FINISHING -> anim(AnimationId.FINISH_FOOD)
            InteractionCondition.TABLE -> c.seated
            InteractionCondition.WARMUP -> anim(AnimationId.GYM_WARMUP)
            InteractionCondition.LIFT -> anim(AnimationId.LIFT)
            InteractionCondition.WATER -> anim(AnimationId.WATER)
            InteractionCondition.WALKING -> c.walkingNow
            InteractionCondition.LANDSCAPE -> anim(AnimationId.LEISURE_LOOK, AnimationId.LEISURE_PHOTO)
            InteractionCondition.BENCH -> anim(AnimationId.LEISURE_BENCH)
            InteractionCondition.BUTTERFLY -> InteractionElement.BUTTERFLY in c.presentElements
            InteractionCondition.SHELVES -> anim(AnimationId.SHOP_LOOK)
            InteractionCondition.PICKING -> anim(AnimationId.SHOP_PICK)
            InteractionCondition.CHECKOUT -> anim(AnimationId.SHOP_PAY)
            InteractionCondition.CLOTHES -> anim(AnimationId.STORE_BROWSE_RACK)
            InteractionCondition.GARMENT -> anim(AnimationId.STORE_HOLD_GARMENT)
            InteractionCondition.LEAVING -> anim(AnimationId.STORE_BAG_EXIT)
            InteractionCondition.STUDY_READ -> anim(AnimationId.STUDY_READ)
            InteractionCondition.STUDY_WRITE -> anim(AnimationId.STUDY_WRITE)
            InteractionCondition.STUDY_THINK -> anim(AnimationId.STUDY_THINK)
            InteractionCondition.TALKING -> anim(AnimationId.VISIT_CHAT)
            InteractionCondition.LISTENING -> anim(AnimationId.VISIT_LISTEN)
            InteractionCondition.LAUGH -> anim(AnimationId.VISIT_LAUGH)
            InteractionCondition.SAFE_STOP -> c.safePassengerStop && c.vehicleMoving == false
            InteractionCondition.BAKERY_CHOOSE -> anim(AnimationId.BAKERY_CHOOSE)
            InteractionCondition.BAKERY_WAIT -> anim(AnimationId.BAKERY_WAIT_ORDER) && has(c, com.hoodie.app.pixel.scene.SceneFlag.ORDER_PENDING)
            InteractionCondition.BAKERY_COFFEE -> a.group == com.hoodie.app.pixel.animation.AnimGroup.COFFEE && has(c, com.hoodie.app.pixel.scene.SceneFlag.MUG_IN_HAND)
            InteractionCondition.BAKERY_REST -> anim(AnimationId.BAKERY_PEOPLE_WATCH, AnimationId.IDLE_SIT) && !has(c, com.hoodie.app.pixel.scene.SceneFlag.PASTRY_IN_HAND)
            InteractionCondition.MALL_WINDOW -> anim(AnimationId.MALL_WINDOW_SHOP)
            InteractionCondition.VENUE_STATIONARY -> !c.walkingNow && !c.transitioning && anim(AnimationId.IDLE, AnimationId.IDLE_LOOK, AnimationId.LOOK_AROUND, AnimationId.MALL_WINDOW_SHOP, AnimationId.LEISURE_BENCH, AnimationId.IDLE_SIT, AnimationId.SHOP_LOOK)
            InteractionCondition.MALL_REST -> anim(AnimationId.LEISURE_BENCH, AnimationId.IDLE_SIT)
            InteractionCondition.BEACH_SEA -> anim(AnimationId.BEACH_LOOK_SEA)
            InteractionCondition.BEACH_LOUNGE -> anim(AnimationId.BEACH_LOUNGE)
            InteractionCondition.BEACH_ICE_CREAM -> anim(AnimationId.BEACH_ICE_CREAM) && has(c, com.hoodie.app.pixel.scene.SceneFlag.ICE_CREAM_IN_HAND)
            InteractionCondition.BEACH_SAND -> !c.walkingNow && anim(AnimationId.IDLE, AnimationId.LOOK_AROUND)
            InteractionCondition.CINEMA_WAIT -> c.cinemaPhase in setOf(com.hoodie.app.pixel.npc.venue.CinemaPhase.LOBBY, com.hoodie.app.pixel.npc.venue.CinemaPhase.DIMMING) && anim(AnimationId.CINEMA_WATCH, AnimationId.IDLE, AnimationId.CINEMA_BUY_POPCORN)
            InteractionCondition.CINEMA_WATCH -> anim(AnimationId.CINEMA_WATCH) && c.cinemaPhase in setOf(com.hoodie.app.pixel.npc.venue.CinemaPhase.DIMMING, com.hoodie.app.pixel.npc.venue.CinemaPhase.SCREENING)
            InteractionCondition.CINEMA_REACT -> anim(AnimationId.CINEMA_REACT) && c.cinemaPhase == com.hoodie.app.pixel.npc.venue.CinemaPhase.SCREENING
            InteractionCondition.CINEMA_POPCORN -> anim(AnimationId.CINEMA_EAT_POPCORN) && has(c, com.hoodie.app.pixel.scene.SceneFlag.POPCORN_HELD)
            InteractionCondition.CINEMA_AFTER -> c.seated && anim(AnimationId.CINEMA_WATCH, AnimationId.CINEMA_REACT, AnimationId.CINEMA_EAT_POPCORN) && c.cinemaPhase == com.hoodie.app.pixel.npc.venue.CinemaPhase.EXIT_LIGHTS
            InteractionCondition.PHARMACY_SHELVES -> anim(AnimationId.SHOP_LOOK)
            InteractionCondition.PHARMACY_PRODUCT -> anim(AnimationId.PHARMACY_EXAMINE) && has(c, com.hoodie.app.pixel.scene.SceneFlag.ITEM_HELD)
            InteractionCondition.VENUE_WAIT -> anim(AnimationId.IDLE, AnimationId.IDLE_LOOK)
            InteractionCondition.HOTEL_CHECK_IN -> anim(AnimationId.HOTEL_CHECK_IN)
            InteractionCondition.HOTEL_LOBBY -> anim(AnimationId.LOOK_AROUND)
            InteractionCondition.HOTEL_WAIT -> anim(AnimationId.HOTEL_CHECK_WATCH, AnimationId.IDLE_SIT)
            InteractionCondition.HOTEL_WATCH -> anim(AnimationId.HOTEL_CHECK_WATCH)
            InteractionCondition.HOTEL_REST -> c.seated && anim(AnimationId.IDLE_SIT)
        }
    }

    private fun has(c: InteractionContext, flag: com.hoodie.app.pixel.scene.SceneFlag) = flag in c.sceneFlags

    companion object {
        val venueScenes = setOf(SceneId.BAKERY, SceneId.MALL, SceneId.BEACH, SceneId.CINEMA, SceneId.PHARMACY, SceneId.HOTEL)
    }
}

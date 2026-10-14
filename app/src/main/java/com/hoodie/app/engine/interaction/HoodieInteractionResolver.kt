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
) {
    private val policy = HoodieInteractionPolicy(config)

    fun stillEligible(response: InteractionResponse, context: InteractionContext): Boolean {
        if (response.ruleId == "repeat") return true
        val rule = rules.firstOrNull { it.id == response.ruleId } ?: return false
        return (rule.activity == null || rule.activity == context.activity) && matches(rule.condition, context)
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
            rule.gesture == gesture && (rule.scene == null || rule.scene == context.scene && !(workAtHome && rule.scene == SceneId.HOME) ||
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
        }
    }
}

package com.hoodie.app.engine.interaction

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import org.json.JSONArray

object HoodieInteractionCatalog {
    const val ASSET = "metadata/interactive_dialogues.json"

    fun parse(json: String): List<InteractionRule> {
        val array = JSONArray(json)
        val rules = (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            require(listOf("id", "scene", "gesture", "activity", "condition", "text", "animations", "priority", "weight").all(obj::has)) { "Incomplete interaction rule at $index" }
            fun optionalString(key: String) = if (obj.isNull(key)) null else obj.getString(key).takeIf { it.isNotBlank() }
            val animations = obj.getJSONArray("animations")
            InteractionRule(
                obj.getString("id"), optionalString("scene")?.let(SceneId::valueOf),
                InteractionGesture.valueOf(obj.getString("gesture")), InteractionCondition.valueOf(obj.getString("condition")),
                obj.getString("text"), (0 until animations.length()).map { AnimationId.valueOf(animations.getString(it)) },
                obj.getInt("priority"), obj.optInt("weight", 1),
                optionalString("activity")?.let(HoodieActivity::valueOf),
            )
        }
        require(rules.isNotEmpty()) { "Empty interaction catalog" }
        require(rules.map { it.id }.distinct().size == rules.size) { "Duplicate interaction rule ID" }
        rules.forEach {
            require(it.id.isNotBlank() && it.text.isNotBlank() && it.text.length <= 120)
            require(it.weight in 1..100 && it.priority in 0..100)
            require(it.animations.isNotEmpty())
            require(it.animations.none { animation -> animation !in allowedAnimations }) { "Interaction with side effects: ${it.id}" }
            if (it.gesture == InteractionGesture.LONG_PRESS) require(it.animations.sumOf { anim -> anim.durationMs } <= 3_000)
        }
        return rules
    }

    /** Catalogs cannot inject prop events, locomotion, food or payment cycles. */
    val allowedAnimations = setOf(
        AnimationId.NOTICE, AnimationId.GLANCE, AnimationId.WAVE, AnimationId.SMILE, AnimationId.HAPPY,
        AnimationId.SURPRISED, AnimationId.SHRUG, AnimationId.EAR_FLICK, AnimationId.NOD,
        AnimationId.YAWN, AnimationId.STRETCH, AnimationId.STRETCH_SIT, AnimationId.LOOK_AROUND,
        AnimationId.VISIT_LAUGH, AnimationId.STUDY_THINK, AnimationId.LEISURE_LOOK, AnimationId.THINK,
        AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE,
    )
}

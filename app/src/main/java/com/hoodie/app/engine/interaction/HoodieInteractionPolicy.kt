package com.hoodie.app.engine.interaction

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId

class HoodieInteractionPolicy(private val config: HoodieInteractionConfig = HoodieInteractionConfig()) {
    fun evaluate(context: InteractionContext, gesture: InteractionGesture, history: HoodieInteractionHistory, now: Long): InteractionDecision? {
        fun blocked(reason: String) = InteractionDecision.Blocked(reason)
        if (!context.screenActive) return blocked("HOME_INACTIVE")
        if (context.transitioning) return blocked("SCENE_TRANSITION")
        if (context.asleep || context.activity == HoodieActivity.SLEEPING || context.animation in sleeping) return blocked("SLEEPING")
        if (context.sequenceBusy) return blocked("REACTION_BUSY")
        val inTransport = context.scene in transport
        if (context.vehicleMoving == true) return blocked("VEHICLE_MOVING")
        if (inTransport) {
            if (gesture == InteractionGesture.LONG_PRESS) return blocked("TRANSPORT_PETTING")
            if (context.scene !in passengers) return blocked("DRIVING_OR_UNKNOWN_MODE")
            if (context.vehicleMoving != false || !context.safePassengerStop) return blocked("STOP_NOT_CONFIRMED")
        } else if (context.userContext == UserContextType.COMMUTING || context.activity == HoodieActivity.COMMUTING) {
            return blocked("COMMUTING")
        }
        if (gesture == InteractionGesture.LONG_PRESS && (context.walkingNow || context.animation in exercise || context.animation in venueUnsafePetting ||
                context.scene == SceneId.SCHOOL && context.animation in setOf(AnimationId.STUDY_READ, AnimationId.STUDY_WRITE, AnimationId.STUDY_PAGE_TURN))) return blocked("WAIT_FOR_REST")
        history.lastGestureAt?.let {
            val remaining = config.gestureIntervalMs - (now - it)
            if (remaining > 0) return InteractionDecision.Cooldown(remaining)
        }
        return null
    }

    companion object {
        val passengers = setOf(SceneId.BUS, SceneId.TRAIN, SceneId.METRO, SceneId.TRANSIT)
        val transport = passengers + setOf(SceneId.CAR, SceneId.BICYCLE, SceneId.GENERIC_RIDE)
        private val sleeping = setOf(AnimationId.SLEEP, AnimationId.SLEEP_TURN, AnimationId.NAP_SIT)
        private val exercise = setOf(AnimationId.LIFT, AnimationId.LIFT_PICK, AnimationId.LIFT_PUT, AnimationId.WATER, AnimationId.GYM_WARMUP)
        private val venueUnsafePetting = setOf(AnimationId.BEACH_ICE_CREAM, AnimationId.MALL_ENTER_STORE)
    }
}

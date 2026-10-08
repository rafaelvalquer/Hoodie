package com.hoodie.app.engine.daystate

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.daycycle.*
import com.hoodie.app.domain.daystate.*
import com.hoodie.app.domain.detection.ConfidenceScore

/** Wake and sleep timing arrive from DailyActivityWindowResolver, never a second detector. */
data class DayStateInput(
    val now: Long,
    val window: DailyActivityWindow,
    val context: UserContextType?,
    val contextConfidence: ConfidenceScore = ConfidenceScore.UNKNOWN,
    val movementConfidence: ConfidenceScore = ConfidenceScore.UNKNOWN,
    val movementConfirmed: Boolean = false,
    val phoneActive: Boolean = false,
    val lastActivityAt: Long? = null,
    val nearBedtime: Boolean = false,
    val sleepConfirmed: Boolean = false,
)

object DayStateEngine {
    private val transitions = mapOf(
        DayState.SLEEPING to setOf(DayState.WAKING),
        DayState.WAKING to setOf(DayState.ACTIVE),
        DayState.ACTIVE to setOf(DayState.COMMUTING, DayState.WINDING_DOWN),
        DayState.COMMUTING to setOf(DayState.ACTIVE),
        DayState.WINDING_DOWN to setOf(DayState.ACTIVE, DayState.SLEEPING),
    )

    fun canTransition(from: DayState, to: DayState) = from == to || to in transitions.getValue(from)

    fun resolve(current: DayStateSnapshot?, input: DayStateInput): DayStateSnapshot {
        val before = current ?: DayStateSnapshot(DayState.SLEEPING, input.window.civilStartAt, ConfidenceScore(.45f), DayStateReason.BEDTIME_PATTERN, true)
        if (input.now < before.updatedAt) return before
        val moving = input.movementConfidence.value >= .60f
        val knownContext = input.context != null && input.context !in setOf(UserContextType.UNKNOWN, UserContextType.COMMUTING) && input.contextConfidence.value >= .60f
        val sleep = input.window.sleepAfterEnd?.takeIf { input.now >= it.startedAt }
        val activityAfterSleep = sleep != null && (input.lastActivityAt ?: Long.MIN_VALUE) > sleep.startedAt
        val hasWake = input.window.wakeReason != WakeReason.SCHEDULE_FALLBACK && input.now >= input.window.activeStartAt &&
            (before.state != DayState.SLEEPING || input.window.activeStartAt >= before.startedAt)
        val idle = input.lastActivityAt?.let { input.now - it } ?: 0L
        val recentActivity = input.lastActivityAt?.let { it <= input.now && input.now - it <= 5 * 60_000L } == true
        val winding = input.context == UserContextType.HOME && !moving && !input.phoneActive &&
            (sleep != null && !activityAfterSleep || input.nearBedtime && idle >= 30 * 60_000L)
        val target = when (before.state) {
            DayState.SLEEPING -> if (!input.sleepConfirmed && (sleep == null || activityAfterSleep) && (hasWake || moving || input.phoneActive || knownContext && recentActivity)) DayState.WAKING else DayState.SLEEPING
            DayState.WAKING -> if (moving || input.phoneActive || knownContext) DayState.ACTIVE else DayState.WAKING
            DayState.ACTIVE -> when { moving -> DayState.COMMUTING; winding || input.sleepConfirmed -> DayState.WINDING_DOWN; else -> DayState.ACTIVE }
            DayState.COMMUTING -> if (!moving && knownContext) DayState.ACTIVE else DayState.COMMUTING
            DayState.WINDING_DOWN -> when {
                moving || input.phoneActive || activityAfterSleep || knownContext && input.context != UserContextType.HOME -> DayState.ACTIVE
                input.sleepConfirmed || sleep != null -> DayState.SLEEPING
                else -> DayState.WINDING_DOWN
            }
        }
        check(canTransition(before.state, target))
        val reason = when (target) {
            DayState.WAKING -> when (input.window.wakeReason) {
                WakeReason.HOME_EXIT -> DayStateReason.WAKE_HOME_EXIT
                WakeReason.MOBILITY_CONFIRMED -> DayStateReason.WAKE_MOBILITY
                else -> if (moving) DayStateReason.WAKE_MOBILITY else DayStateReason.WAKE_PHONE
            }
            DayState.ACTIVE -> when {
                before.state == DayState.COMMUTING -> if (input.contextConfidence.value >= .85f) DayStateReason.ARRIVAL_CONFIRMED else DayStateReason.ARRIVAL_INFERRED
                input.phoneActive -> DayStateReason.ACTIVE_PHONE
                moving -> DayStateReason.ACTIVE_MOVEMENT
                else -> DayStateReason.ACTIVE_CONTEXT
            }
            DayState.COMMUTING -> if (input.movementConfirmed) DayStateReason.COMMUTE_CONFIRMED else DayStateReason.COMMUTE_INFERRED
            DayState.WINDING_DOWN -> if (input.nearBedtime) DayStateReason.BEDTIME_PATTERN else DayStateReason.HOME_LOW_ACTIVITY
            DayState.SLEEPING -> if (input.sleepConfirmed) DayStateReason.SLEEP_CONFIRMED else if (sleep != null) DayStateReason.SLEEP_INACTIVITY else before.reason
        }
        val confidence = when (target) {
            DayState.COMMUTING -> input.movementConfidence
            DayState.SLEEPING -> if (input.sleepConfirmed) ConfidenceScore.CERTAIN else when (sleep?.confidence) {
                SleepConfidence.HIGH -> ConfidenceScore(.90f)
                SleepConfidence.MEDIUM -> ConfidenceScore(.70f)
                else -> before.confidence
            }
            DayState.WAKING -> when (input.window.wakeConfidence) {
                WakeConfidence.HIGH -> ConfidenceScore(.90f)
                WakeConfidence.MEDIUM -> ConfidenceScore(.70f)
                else -> maxOf(input.contextConfidence, input.movementConfidence, compareBy { it.value })
            }
            DayState.ACTIVE -> if (input.phoneActive) ConfidenceScore(.85f) else if (moving) input.movementConfidence else input.contextConfidence
            DayState.WINDING_DOWN -> ConfidenceScore(.70f)
        }
        return DayStateSnapshot(target, if (target == before.state) before.startedAt else input.now, confidence, reason, confidence.value < .85f, input.now)
    }
}

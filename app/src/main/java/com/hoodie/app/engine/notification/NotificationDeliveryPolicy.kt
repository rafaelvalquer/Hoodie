package com.hoodie.app.engine.notification

import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.domain.notification.NotificationMode

enum class DeliveryDecision { SHOW_NOW, DEFER, IN_APP_ONLY, EXPIRE }

data class NotificationDecisionInput(
    val mode: NotificationMode,
    val notificationsAllowed: Boolean,
    val isVehicleMoving: Boolean,
    val dayState: DayState?,
    val isProvisionalDayState: Boolean,
    val questionAgeMs: Long,
    val deliveredToday: Int,
    val isStillRelevant: Boolean,
)

/**
 * Decide COMO comunicar algo que o motor já gerou. Pura (sem Android) e determinística.
 * Não altera quantas perguntas existem: uma pergunta sem alerta continua pendente dentro do app.
 */
object NotificationDeliveryPolicy {
    /** Uma pergunta sobre algo antigo não deve virar alerta horas depois. */
    const val MAX_QUESTION_AGE_MS = 2 * HOUR_MS
    const val VEHICLE_RETRY_MS = 10 * MINUTE_MS
    const val SLEEP_RETRY_MS = 30 * MINUTE_MS
    const val PROVISIONAL_RETRY_MS = 10 * MINUTE_MS

    fun dailyLimit(mode: NotificationMode): Int = when (mode) {
        NotificationMode.SILENT -> 0
        NotificationMode.BALANCED -> 2
        NotificationMode.PARTICIPATIVE -> 4
    }

    fun decide(i: NotificationDecisionInput): DeliveryDecision = when {
        !i.isStillRelevant || i.questionAgeMs > MAX_QUESTION_AGE_MS -> DeliveryDecision.EXPIRE
        i.mode == NotificationMode.SILENT || !i.notificationsAllowed -> DeliveryDecision.IN_APP_ONLY
        i.deliveredToday >= dailyLimit(i.mode) -> DeliveryDecision.IN_APP_ONLY
        i.isVehicleMoving -> DeliveryDecision.DEFER
        // Sono (confiável ou provisório) silencia o não urgente; na dúvida, adia em vez de interromper.
        i.dayState == DayState.SLEEPING -> DeliveryDecision.DEFER
        else -> DeliveryDecision.SHOW_NOW
    }

    /** Quanto esperar antes de reavaliar um adiamento. */
    fun retryDelayMs(i: NotificationDecisionInput): Long = when {
        i.isVehicleMoving -> VEHICLE_RETRY_MS
        i.dayState == DayState.SLEEPING -> if (i.isProvisionalDayState) PROVISIONAL_RETRY_MS else SLEEP_RETRY_MS
        else -> PROVISIONAL_RETRY_MS
    }
}
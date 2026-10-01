package com.hoodie.app.engine.context

import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.inWindow
import com.hoodie.app.core.time.minuteOfDay
import com.hoodie.app.engine.routine.RoutineEngine
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlin.math.abs

/** Sinal vindo da camada de sensores. Nunca contém trajeto, só o lugar envolvido. */
sealed interface ContextSignal {
    data class Enter(val place: Place) : ContextSignal
    data class Dwell(val place: Place) : ContextSignal
    data class Exit(val place: Place, val minutesOutside: Int = 0) : ContextSignal
}

/** Confirmação passada do usuário — é assim que o app "aprende" sem IA. */
data class ConfirmationRecord(
    val type: UserContextType,
    val placeId: Long?,
    val dayOfWeek: DayOfWeek,
    val minuteOfDay: Int,
    val accepted: Boolean,
    val timestamp: Long,
)

data class ContextInput(
    val signal: ContextSignal,
    val now: ZonedDateTime,
    val routine: Routine,
    val dayOff: Boolean,
    val previous: UserContextType?,
    val confirmations: List<ConfirmationRecord>,
)

enum class ContextDecision {
    /** Confiança alta: aplica sem perguntar. */
    APPLY,

    /** Confiança média: aplica provisoriamente e pergunta (respeitando o cooldown). */
    APPLY_AND_ASK,

    /** Confiança baixa: melhor assumir UNKNOWN do que inventar. */
    UNKNOWN,
}

data class ContextCandidate(val type: UserContextType, val score: Int, val reasons: List<String>) {
    val decision: ContextDecision
        get() = when {
            score >= ContextScorer.APPLY_THRESHOLD -> ContextDecision.APPLY
            score >= ContextScorer.ASK_THRESHOLD -> ContextDecision.APPLY_AND_ASK
            else -> ContextDecision.UNKNOWN
        }
    val confidence: Float get() = score.coerceIn(0, 100) / 100f
}

/**
 * Sistema determinístico de confiança:
 *
 *     Lugar conhecido      +40
 *     Horário esperado     +30
 *     Dia esperado         +20
 *     Histórico compatível +10..30
 */
object ContextScorer {
    const val APPLY_THRESHOLD = 85
    const val ASK_THRESHOLD = 40

    /** Minutos fora do trabalho para considerar que "parou" em algum lugar. */
    const val LUNCH_MIN_OUTSIDE = 15

    fun score(input: ContextInput): ContextCandidate = when (val s = input.signal) {
        is ContextSignal.Enter -> scoreEnter(s.place, input)
        is ContextSignal.Dwell -> scoreEnter(s.place, input)
        is ContextSignal.Exit -> scoreExit(s, input)
    }

    private fun scoreEnter(place: Place, input: ContextInput): ContextCandidate {
        val reasons = mutableListOf("Lugar conhecido (+40)")
        var score = 40
        val minute = input.now.minuteOfDay()
        val type = when (place.type) {
            PlaceType.RESTAURANT ->
                if (RoutineEngine.isLunchWindow(minute, input.routine)) UserContextType.LUNCH else UserContextType.LEISURE
            else -> place.type.toContext()
        }
        if (type == UserContextType.WORK) {
            val workDay = RoutineEngine.isWorkDay(input.now.toLocalDate(), input.routine, input.dayOff)
            // Chegar até 90 min antes ou ficar até 3h depois ainda é "horário esperado".
            val expectedHour = inWindow(minute, input.routine.startMinute - 90, input.routine.endMinute + 180)
            if (workDay && expectedHour) { score += 30; reasons += "Horário esperado (+30)" }
            if (workDay) { score += 20; reasons += "Dia esperado (+20)" }
            val history = input.confirmations.any { it.accepted && it.type == UserContextType.WORK && it.placeId == place.id } ||
                input.previous == UserContextType.LUNCH || place.confirmationCount > 0
            if (history) { score += 10; reasons += "Histórico compatível (+10)" }
        } else {
            // Casa, academia etc.: o próprio usuário disse o que é o lugar.
            score += 30 + 20
            reasons += "Lugar definido pelo usuário (+50)"
            if (place.confirmationCount > 0) { score += 10; reasons += "Já visitado (+10)" }
        }
        return ContextCandidate(type, score.coerceAtMost(100), reasons)
    }

    private fun scoreExit(exit: ContextSignal.Exit, input: ContextInput): ContextCandidate {
        val minute = input.now.minuteOfDay()
        val leftWork = exit.place.type == PlaceType.WORK
        val workDay = RoutineEngine.isWorkDay(input.now.toLocalDate(), input.routine, input.dayOff)
        if (leftWork && workDay && RoutineEngine.isLunchWindow(minute, input.routine)) {
            val reasons = mutableListOf("Horário de almoço (+30)", "Saiu do trabalho (+30)")
            var score = 60
            if (exit.minutesOutside >= LUNCH_MIN_OUTSIDE) {
                score += 20; reasons += "Fora há ${exit.minutesOutside} min (+20)"
            }
            val similar = lunchConfirmations(input)
            when {
                similar >= 3 -> { score += 30; reasons += "Almoço confirmado $similar vezes (+30)" }
                similar >= 1 -> { score += 10; reasons += "Almoço já confirmado (+10)" }
            }
            return ContextCandidate(UserContextType.LUNCH, score.coerceAtMost(100), reasons)
        }
        return ContextCandidate(UserContextType.COMMUTING, APPLY_THRESHOLD, listOf("Saiu de ${exit.place.name}"))
    }

    /** Confirmações de almoço aceitas em dias úteis num horário parecido (±45 min), nas últimas 2 semanas. */
    private fun lunchConfirmations(input: ContextInput): Int {
        val minute = input.now.minuteOfDay()
        val cutoff = input.now.toInstant().toEpochMilli() - 14L * 24 * 60 * 60 * 1000
        return input.confirmations.count {
            it.accepted && it.type == UserContextType.LUNCH && it.timestamp >= cutoff &&
                abs(it.minuteOfDay - minute) <= 45
        }
    }
}

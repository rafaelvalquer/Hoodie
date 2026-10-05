package com.hoodie.app.engine.hoodie

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.HoodieActivity.*
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.inWindow
import com.hoodie.app.core.time.minuteOfDay
import com.hoodie.app.core.time.nextOccurrence
import com.hoodie.app.engine.routine.RoutineEngine
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlin.random.Random

data class DecisionInput(
    val at: ZonedDateTime,
    val context: UserContextType,
    val needs: Needs,
    val previous: HoodieActivity?,
    val routine: Routine,
    val sleep: SleepSchedule,
    val dayOff: Boolean,
)

data class HoodieDecision(val activity: HoodieActivity, val durationMs: Long)

/**
 * O gato não copia o usuário: o contexto do usuário só muda os pesos das
 * atividades. Assim ele parece ter uma rotina paralela, própria.
 */
object HoodieDecisionEngine {

    /** Contexto efetivo: home office em horário de trabalho conta como trabalho. */
    fun effectiveContext(input: DecisionInput): UserContextType {
        val minute = input.at.minuteOfDay()
        val workDay = RoutineEngine.isWorkDay(input.at.toLocalDate(), input.routine, input.dayOff)
        if (input.context == UserContextType.HOME && input.routine.workMode == WorkMode.HOME_OFFICE && workDay &&
            RoutineEngine.isWorkHours(minute, input.routine) &&
            !inWindow(minute, input.routine.lunchStartMinute, input.routine.lunchEndMinute)
        ) return UserContextType.WORK
        return input.context
    }

    fun weights(input: DecisionInput): Map<HoodieActivity, Int> {
        val minute = input.at.minuteOfDay()
        val ctx = effectiveContext(input)
        val needs = input.needs
        val sleepTime = RoutineEngine.isSleepTime(minute, input.sleep)
        val weekend = input.at.dayOfWeek == DayOfWeek.SATURDAY || input.at.dayOfWeek == DayOfWeek.SUNDAY
        val w = mutableMapOf<HoodieActivity, Int>()

        // Sequências naturais primeiro.
        when (input.previous) {
            SLEEPING -> if (!sleepTime && ctx == UserContextType.HOME) return mapOf(WAKING_UP to 100)
            WAKING_UP -> if (ctx == UserContextType.HOME) return mapOf(BREAKFAST to 100)
            COOKING -> return mapOf(EATING to 100)
            else -> Unit
        }

        when (ctx) {
            UserContextType.HOME, UserContextType.UNKNOWN -> {
                if (sleepTime) {
                    w[SLEEPING] = 85; w[WATCHING_TV] = 5; w[READING] = 5; w[PHONE] = 5
                } else if (ctx == UserContextType.UNKNOWN) {
                    w[IDLE] = 50; w[PHONE] = 30; w[WALKING] = 20
                } else if (weekend && minute in 9 * 60 until 18 * 60) {
                    // Sábado em casa: o Hoodie vive a própria vida.
                    w[GAMING] = 40; w[READING] = 20; w[COFFEE] = 20; w[CLEANING] = 10; w[WALKING] = 10
                } else if (minute < 12 * 60) {
                    w[COFFEE] = 25; w[PHONE] = 20; w[IDLE] = 20; w[READING] = 15; w[CLEANING] = 10; w[GAMING] = 10
                } else {
                    w[GAMING] = 25; w[WATCHING_TV] = 25; w[COOKING] = 15; w[READING] = 15; w[PHONE] = 10; w[RESTING] = 10
                }
                // Perto da hora de dormir o sono vai ficando mais provável.
                if (!sleepTime && inWindow(minute, input.sleep.sleepMinute - 60, input.sleep.sleepMinute)) {
                    w[SLEEPING] = (w[SLEEPING] ?: 0) + 35
                }
                if (needs.hunger > 65 && !sleepTime) {
                    w[COOKING] = (w[COOKING] ?: 0) + 40; w[EATING] = (w[EATING] ?: 0) + 20
                }
            }
            UserContextType.WORK -> {
                w[WORKING] = if (needs.energy > 30) 70 else 30
                w[COFFEE] = if (needs.energy < 40) 30 else 10
                w[RESTING] = 10; w[PHONE] = 5; w[IDLE] = 5
                if (needs.hunger > 70) w[EATING] = 15
            }
            UserContextType.LUNCH -> { w[EATING] = if (needs.hunger > 20) 70 else 20; w[COFFEE] = 15; w[PHONE] = 15 }
            UserContextType.DINING -> {
                w[EATING] = if (needs.hunger > 20) 70 else 45
                w[PHONE] = 12; w[COFFEE] = 8; w[RESTING] = 6; w[IDLE] = 4
            }
            UserContextType.COMMUTING -> w[COMMUTING] = 100
            UserContextType.GYM -> { w[TRAINING] = 75; w[RESTING] = 15; w[IDLE] = 10 }
            UserContextType.STUDY -> {
                w[STUDYING] = 65; w[READING] = 15; w[COFFEE] = 8; w[PHONE] = 5; w[RESTING] = 7
                // Foco baixo: estuda menos, distrai no celular e descansa.
                if (needs.focus < 30) { w[STUDYING] = 35; w[PHONE] = 15; w[RESTING] = 15 }
                // Energia baixa: café e descanso.
                if (needs.energy < 30) { w[COFFEE] = 20; w[RESTING] = (w[RESTING] ?: 0) + 10 }
            }
            UserContextType.SHOPPING -> {
                w[SHOPPING] = 65; w[WALKING] = 15; w[PHONE] = 10; w[EATING] = if (needs.hunger > 60) 10 else 3; w[IDLE] = 5
                if (needs.hunger > 75) w[EATING] = 25
                if (needs.energy < 25) { w[SHOPPING] = 35; w[RESTING] = 15 }
            }
            UserContextType.VISITING -> {
                w[SOCIALIZING] = 55; w[EATING] = 12; w[COFFEE] = 12; w[WATCHING_TV] = 8; w[PHONE] = 5; w[RESTING] = 8
                // Social baixo: a visita vira conversa.
                if (needs.social < 35) w[SOCIALIZING] = 75
                if (needs.hunger > 65) w[EATING] = 25
            }
            UserContextType.LEISURE -> {
                w[SIGHTSEEING] = 50; w[WALKING] = 20; w[PHONE] = 8; w[COFFEE] = 8; w[EATING] = 6; w[RESTING] = 8
                if (needs.energy < 30) { w[WALKING] = 5; w[RESTING] = 20 }
                if (needs.hunger > 65) w[EATING] = 20
            }
            UserContextType.TRAVEL -> {
                w[WALKING] = 45; w[IDLE] = 20; w[PHONE] = 15; w[COFFEE] = 10; w[EATING] = if (needs.hunger > 60) 30 else 10
            }
        }

        // Energia muito baixa: não iniciar atividade longa.
        if (needs.energy <= 10) {
            w.keys.filter { it.isLong }.forEach { w.remove(it) }
            if (ctx != UserContextType.COMMUTING) {
                w[RESTING] = (w[RESTING] ?: 0) + 60
                if (!sleepTime) w[COFFEE] = (w[COFFEE] ?: 0) + 30
            }
        }
        if (needs.energy < 25 && (ctx == UserContextType.HOME) && !sleepTime) {
            w[RESTING] = (w[RESTING] ?: 0) + 25
        }
        return w.filterValues { it > 0 }
    }

    fun decide(input: DecisionInput, random: Random): HoodieDecision {
        val weights = weights(input)
        val activity = pick(weights, random) ?: IDLE
        return HoodieDecision(activity, durationFor(activity, input, random))
    }

    fun durationFor(activity: HoodieActivity, input: DecisionInput, random: Random): Long {
        if (activity == SLEEPING) {
            val nowMs = input.at.toInstant().toEpochMilli()
            val wake = nextOccurrence(nowMs, input.sleep.wakeMinute, input.at.zone)
            // Cochilo fora do horário: no máximo 40 min.
            val minute = input.at.minuteOfDay()
            return if (RoutineEngine.isSleepTime(minute, input.sleep) ||
                inWindow(minute, input.sleep.sleepMinute - 60, input.sleep.sleepMinute)
            ) wake - nowMs else 40 * MINUTE_MS
        }
        val (min, max) = when (activity) {
            WAKING_UP -> 8 to 12
            BREAKFAST -> 15 to 25
            WORKING -> 25 to 70
            COFFEE -> 5 to 12
            RESTING -> 10 to 25
            PHONE -> 5 to 12
            IDLE -> 5 to 15
            EATING -> 15 to 30
            GAMING -> 30 to 80
            READING -> 20 to 50
            WATCHING_TV -> 25 to 60
            COOKING -> 15 to 30
            CLEANING -> 15 to 30
            WALKING -> 15 to 40
            TRAINING -> 20 to 45
            COMMUTING -> 20 to 20
            SLEEPING -> 40 to 40
            STUDYING -> 20 to 60
            SHOPPING -> 15 to 50
            SOCIALIZING -> 15 to 45
            SIGHTSEEING -> 15 to 45
        }
        var minutes = random.nextInt(min, max + 1).toLong()
        if (input.needs.energy <= 10) minutes = minutes.coerceAtMost(20)
        return minutes * MINUTE_MS
    }

    private fun pick(weights: Map<HoodieActivity, Int>, random: Random): HoodieActivity? {
        val total = weights.values.sum()
        if (total <= 0) return null
        var roll = random.nextInt(total)
        // Ordem estável (ordinal) para que a mesma seed gere sempre a mesma escolha.
        for ((activity, weight) in weights.entries.sortedBy { it.key.ordinal }) {
            if (roll < weight) return activity
            roll -= weight
        }
        return null
    }
}

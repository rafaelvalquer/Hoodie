package com.hoodie.app.engine.routine

import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.core.time.formatHm
import com.hoodie.app.core.time.inWindow
import com.hoodie.app.core.time.minuteOfDay
import java.time.LocalDate
import java.time.ZonedDateTime

/** Próximo marco da rotina, mostrado na Home ("Próximo evento"). */
data class UpcomingEvent(val emoji: String, val label: String, val minuteOfDay: Int, val tomorrow: Boolean) {
    val display: String get() = "$emoji $label ~${formatHm(minuteOfDay)}" + if (tomorrow) " (amanhã)" else ""
}

/**
 * Regras determinísticas sobre a rotina configurada. Sem estado: tudo é função do
 * horário, da rotina e das exceções de trabalho configuradas para o dia.
 */
object RoutineEngine {

    /** Folga antes do início do almoço em que uma saída do trabalho já conta como "provável almoço". */
    const val LUNCH_SLACK_MIN = 30

    fun isWorkDay(date: LocalDate, routine: Routine, dayOff: Boolean): Boolean =
        routine.hasWork && !dayOff && date.dayOfWeek in routine.days

    fun isWorkHours(minute: Int, routine: Routine): Boolean =
        inWindow(minute, routine.startMinute, routine.endMinute)

    fun isLunchWindow(minute: Int, routine: Routine): Boolean =
        inWindow(minute, routine.lunchStartMinute - LUNCH_SLACK_MIN, routine.lunchEndMinute)

    fun isSleepTime(minute: Int, sleep: SleepSchedule): Boolean =
        inWindow(minute, sleep.sleepMinute, sleep.wakeMinute)

    /** Horário passou do fim do expediente previsto (expediente longo). */
    fun isOvertime(now: ZonedDateTime, routine: Routine): Boolean =
        routine.hasWork && now.minuteOfDay() >= routine.endMinute

    /**
     * "Rotina provável": usada quando não há localização. Nunca quebra o jogo —
     * na dúvida, o Hoodie segue o que a rotina diz.
     */
    fun probableContext(now: ZonedDateTime, routine: Routine, dayOff: Boolean): UserContextType {
        val minute = now.minuteOfDay()
        if (isWorkDay(now.toLocalDate(), routine, dayOff) && isWorkHours(minute, routine)) {
            val atLunch = inWindow(minute, routine.lunchStartMinute, routine.lunchEndMinute)
            return when {
                atLunch && routine.workMode == WorkMode.OFFICE -> UserContextType.LUNCH
                routine.workMode == WorkMode.HOME_OFFICE && atLunch -> UserContextType.HOME
                else -> UserContextType.WORK
            }
        }
        return UserContextType.HOME
    }

    fun nextEvent(
        now: ZonedDateTime,
        routine: Routine,
        sleep: SleepSchedule,
        dayOff: Boolean,
        current: UserContextType,
    ): UpcomingEvent {
        val minute = now.minuteOfDay()
        val workToday = isWorkDay(now.toLocalDate(), routine, dayOff)
        if (workToday) {
            if (minute < routine.startMinute) return UpcomingEvent("🏢", "Trabalho", routine.startMinute, false)
            if (minute < routine.lunchStartMinute) return UpcomingEvent("🍜", "Almoço", routine.lunchStartMinute, false)
            if (current == UserContextType.LUNCH && minute < routine.lunchEndMinute) {
                return UpcomingEvent("🏢", "Volta ao trabalho", routine.lunchEndMinute, false)
            }
            if (minute < routine.endMinute) return UpcomingEvent("🏠", "Fim do expediente", routine.endMinute, false)
        }
        if (minute < sleep.sleepMinute && minute >= sleep.wakeMinute) {
            return UpcomingEvent("🛏", "Hora de dormir", sleep.sleepMinute, false)
        }
        val tomorrow = now.toLocalDate().plusDays(1)
        if (minute >= sleep.wakeMinute && isWorkDay(tomorrow, routine, false)) {
            return UpcomingEvent("🏢", "Trabalho", routine.startMinute, true)
        }
        return UpcomingEvent("☀️", "Acordar", sleep.wakeMinute, minute >= sleep.wakeMinute)
    }
}

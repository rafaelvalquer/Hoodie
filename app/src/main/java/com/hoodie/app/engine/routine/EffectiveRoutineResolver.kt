package com.hoodie.app.engine.routine

import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.domain.routine.EffectiveDayRoutine
import com.hoodie.app.domain.routine.LearnedRoutineSlot
import com.hoodie.app.domain.routine.RoutineEventType
import com.hoodie.app.domain.routine.RoutineValueOrigin
import com.hoodie.app.domain.routine.RoutineValueSource
import com.hoodie.app.domain.routine.routineDayGroup
import java.time.LocalDate
import kotlin.math.abs

/**
 * Manual + aprendido → rotina efetiva de um dia. Puro e determinístico.
 *
 * Regras:
 * - Só padrões confiáveis ajustam: confiança ≥ [MIN_CONFIDENCE] e desvio ≤ [MAX_DEVIATION_MIN].
 *   O padrão do dia da semana (ex.: MONDAY) tem prioridade sobre o do grupo (WEEKDAY).
 * - O aprendido ajusta o manual em no máximo ±[MAX_SHIFT_MIN] min: um mês atípico
 *   não desmonta a rotina que a pessoa configurou.
 * - A ordem do dia é preservada: entrada < almoço < volta < saída, almoço com pelo menos
 *   [MIN_LUNCH_MIN] min, sono entre 4 h e 12 h e acordar antes de entrar no trabalho.
 *   Um ajuste que quebra a ordem volta ao manual.
 * - Marcos que a pessoa não configura (sair de casa, academia, voltar para casa) entram
 *   só como informação, quando confiáveis.
 */
object EffectiveRoutineResolver {
    const val MIN_CONFIDENCE = .60f
    const val MAX_DEVIATION_MIN = 45
    const val MAX_SHIFT_MIN = 90
    const val MIN_LUNCH_MIN = 15
    private const val MIN_SLEEP_MIN = 4 * 60
    private const val MAX_SLEEP_MIN = 12 * 60

    private val WORK = listOf(RoutineEventType.WORK_START, RoutineEventType.LUNCH_START, RoutineEventType.LUNCH_END, RoutineEventType.WORK_END)
    private val SLEEP = listOf(RoutineEventType.WAKE, RoutineEventType.SLEEP)
    private val INFO_ONLY = listOf(RoutineEventType.LEAVE_HOME, RoutineEventType.GYM_START, RoutineEventType.HOME_RETURN)

    fun resolve(
        date: LocalDate,
        manual: Routine,
        manualSleep: SleepSchedule,
        slots: List<LearnedRoutineSlot>,
        enabled: Boolean,
    ): EffectiveDayRoutine {
        val group = routineDayGroup(date.dayOfWeek)
        if (!enabled) return EffectiveDayRoutine.manual(date, manual, manualSleep, learningEnabled = false)

        val origins = linkedMapOf<RoutineEventType, RoutineValueOrigin>()
        fun adjust(type: RoutineEventType, manualMinute: Int) {
            val slot = reliableSlot(date, type, slots)
            origins[type] = if (slot == null) {
                RoutineValueOrigin(type, manualMinute, null, manualMinute, RoutineValueSource.MANUAL)
            } else {
                val delta = circularDelta(manualMinute, slot.medianMinute)
                val limited = abs(delta) > MAX_SHIFT_MIN
                val applied = delta.coerceIn(-MAX_SHIFT_MIN, MAX_SHIFT_MIN)
                RoutineValueOrigin(
                    type, manualMinute, slot.medianMinute, wrap(manualMinute + applied),
                    if (limited) RoutineValueSource.LEARNED_LIMITED else RoutineValueSource.LEARNED,
                    slot.confidence.value, slot.sampleCount,
                )
            }
        }
        fun revert(type: RoutineEventType) {
            origins[type]?.takeIf { it.learned }?.let { origins[type] = it.copy(effectiveMinute = it.manualMinute!!, source = RoutineValueSource.MANUAL) }
        }
        fun eff(type: RoutineEventType) = origins.getValue(type).effectiveMinute

        // Trabalho e almoço: só para quem trabalha e quando a rotina manual tem a ordem normal
        // (turnos que cruzam a meia-noite ficam como configurados).
        val manualWork = listOf(manual.startMinute, manual.lunchStartMinute, manual.lunchEndMinute, manual.endMinute)
        val workAdjustable = manual.hasWork && manualWork.zipWithNext().all { (a, b) -> a < b }
        if (workAdjustable) {
            WORK.zip(manualWork).forEach { (type, minute) -> adjust(type, minute) }
            // Desfaz ajustes até a ordem do dia voltar a valer (no máximo um passo por campo).
            repeat(WORK.size) {
                val broken = WORK.zipWithNext().firstOrNull { (a, b) ->
                    eff(a) >= eff(b) || (a == RoutineEventType.LUNCH_START && b == RoutineEventType.LUNCH_END && eff(b) - eff(a) < MIN_LUNCH_MIN)
                } ?: return@repeat
                // Reverte primeiro o lado menos confiável do par.
                val (a, b) = broken
                val ca = origins.getValue(a).confidence ?: 2f
                val cb = origins.getValue(b).confidence ?: 2f
                if (ca <= cb) revert(a) else revert(b)
            }
        }

        SLEEP.zip(listOf(manualSleep.wakeMinute, manualSleep.sleepMinute)).forEach { (type, minute) -> adjust(type, minute) }
        fun sleepOk() = ((eff(RoutineEventType.WAKE) - eff(RoutineEventType.SLEEP) + 1440) % 1440) in MIN_SLEEP_MIN..MAX_SLEEP_MIN
        if (!sleepOk()) { revert(RoutineEventType.WAKE); if (!sleepOk()) revert(RoutineEventType.SLEEP) }
        // Acordar depois de entrar no trabalho não faz sentido: o acordar aprendido cede.
        // Só em dia de trabalho: no sábado sem expediente o acordar aprendido vale.
        if (workAdjustable && date.dayOfWeek in manual.days && eff(RoutineEventType.WAKE) >= eff(RoutineEventType.WORK_START)) revert(RoutineEventType.WAKE)

        INFO_ONLY.forEach { type ->
            reliableSlot(date, type, slots)?.let {
                origins[type] = RoutineValueOrigin(type, null, it.medianMinute, it.medianMinute, RoutineValueSource.LEARNED, it.confidence.value, it.sampleCount)
            }
        }

        val routine = if (workAdjustable) manual.copy(
            startMinute = eff(RoutineEventType.WORK_START),
            lunchStartMinute = eff(RoutineEventType.LUNCH_START),
            lunchEndMinute = eff(RoutineEventType.LUNCH_END),
            endMinute = eff(RoutineEventType.WORK_END),
        ) else manual
        val sleep = manualSleep.copy(wakeMinute = eff(RoutineEventType.WAKE), sleepMinute = eff(RoutineEventType.SLEEP))
        return EffectiveDayRoutine(date, group, routine, sleep, origins, learningEnabled = true)
    }

    /** Padrão confiável do dia: o do dia da semana primeiro, depois o do grupo. */
    fun reliableSlot(date: LocalDate, type: RoutineEventType, slots: List<LearnedRoutineSlot>): LearnedRoutineSlot? {
        fun ok(s: LearnedRoutineSlot) = s.type == type && s.confidence.value >= MIN_CONFIDENCE && s.deviationMinutes <= MAX_DEVIATION_MIN
        return slots.firstOrNull { ok(it) && it.dayGroup == date.dayOfWeek.name }
            ?: slots.firstOrNull { ok(it) && it.dayGroup == routineDayGroup(date.dayOfWeek) }
    }

    /** Diferença learned − manual pelo caminho mais curto do relógio (−719..720). */
    fun circularDelta(manual: Int, learned: Int): Int {
        val d = (learned - manual) % 1440
        return when {
            d > 720 -> d - 1440
            d <= -720 -> d + 1440
            else -> d
        }
    }

    private fun wrap(minute: Int) = ((minute % 1440) + 1440) % 1440
}

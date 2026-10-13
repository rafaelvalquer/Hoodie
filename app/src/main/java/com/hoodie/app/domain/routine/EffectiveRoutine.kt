package com.hoodie.app.domain.routine

import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import java.time.LocalDate

/** De onde veio um horário da rotina efetiva. */
enum class RoutineValueSource {
    /** Configurado pela pessoa (ou o aprendizado não era confiável). */
    MANUAL,

    /** Padrão aprendido confiável, dentro do limite de ajuste. */
    LEARNED,

    /** Padrão aprendido longe demais do manual: aplicado só até o limite de ajuste. */
    LEARNED_LIMITED,
}

/** Um horário da rotina efetiva e como ele foi decidido. */
data class RoutineValueOrigin(
    val type: RoutineEventType,
    /** Horário configurado (null = a pessoa não configura este marco, ex.: academia). */
    val manualMinute: Int?,
    /** Mediana aprendida confiável (null = sem padrão confiável). */
    val learnedMinute: Int?,
    val effectiveMinute: Int,
    val source: RoutineValueSource,
    val confidence: Float? = null,
    val sampleCount: Int = 0,
) {
    val learned: Boolean get() = source != RoutineValueSource.MANUAL
}

/**
 * Rotina efetiva de um dia: a configuração manual ajustada pelos padrões
 * aprendidos confiáveis do grupo do dia (útil, sábado, domingo). É o que as
 * engines (contexto, Hoodie, mobilidade) e a Home usam no lugar da rotina crua.
 */
data class EffectiveDayRoutine(
    val date: LocalDate,
    val dayGroup: String,
    val routine: Routine,
    val sleep: SleepSchedule,
    val origins: Map<RoutineEventType, RoutineValueOrigin>,
    /** Ajuste pelo aprendizado ligado (interruptor da tela Rotina + feature flag). */
    val learningEnabled: Boolean,
) {
    fun origin(type: RoutineEventType): RoutineValueOrigin? = origins[type]

    /** Quantos horários vieram do aprendizado. */
    val learnedCount: Int get() = origins.values.count { it.learned }

    companion object {
        fun manual(date: LocalDate, routine: Routine, sleep: SleepSchedule, learningEnabled: Boolean = false) = EffectiveDayRoutine(
            date, routineDayGroup(date.dayOfWeek), routine, sleep, emptyMap(), learningEnabled,
        )
    }
}

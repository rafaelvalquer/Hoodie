package com.hoodie.app.data.repository

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.IntelligenceDao
import com.hoodie.app.core.database.LearnedRoutineSlotEntity
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.currentDateFlow
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.routine.EffectiveDayRoutine
import com.hoodie.app.domain.routine.LearnedRoutineSlot
import com.hoodie.app.domain.routine.RoutineEventType
import com.hoodie.app.engine.routine.EffectiveRoutineResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject

/**
 * Fonte única da rotina que o app segue: a configuração manual ajustada pelos
 * padrões aprendidos confiáveis ([EffectiveRoutineResolver]). Contexto, Hoodie,
 * mobilidade e Home leem daqui em vez da rotina crua.
 *
 * Sem [intelligence] (testes antigos, aprendizado desligado) devolve a rotina manual.
 */
class EffectiveRoutineRepository @Inject constructor(
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val intelligence: IntelligenceDao?,
    private val clock: ClockProvider,
) {
    suspend fun forDate(date: LocalDate): EffectiveDayRoutine = forDates(listOf(date)).getValue(date)

    suspend fun today(): EffectiveDayRoutine = forDate(clock.today())

    /** Vários dias de uma vez (uma leitura do banco), ex.: a simulação do Hoodie. */
    suspend fun forDates(dates: Collection<LocalDate>): Map<LocalDate, EffectiveDayRoutine> {
        val manual = routines.get()
        val s = settings.current()
        val enabled = enabled(s.learnedRoutineEnabled)
        val slots = if (enabled) intelligence?.routineSlots().orEmpty().mapNotNull(::toDomain) else emptyList()
        return dates.associateWith { EffectiveRoutineResolver.resolve(it, manual, s.sleep, slots, enabled) }
    }

    /** Rotina efetiva de hoje, atualizada quando a configuração, o aprendizado ou o dia mudam. */
    fun observeToday(): Flow<EffectiveDayRoutine> = combine(
        routines.routine,
        settings.settings,
        intelligence?.observeRoutineSlots() ?: flowOf(emptyList()),
        currentDateFlow(clock),
    ) { manual, s, rows, date ->
        val enabled = enabled(s.learnedRoutineEnabled)
        EffectiveRoutineResolver.resolve(date, manual, s.sleep, if (enabled) rows.mapNotNull(::toDomain) else emptyList(), enabled)
    }

    private fun enabled(userSwitch: Boolean) = HoodieConfig.LEARNED_ROUTINE && userSwitch && intelligence != null

    companion object {
        fun toDomain(row: LearnedRoutineSlotEntity): LearnedRoutineSlot? {
            val type = RoutineEventType.entries.firstOrNull { it.name == row.type } ?: return null
            return LearnedRoutineSlot(row.dayGroup, type, row.medianMinute, row.deviationMinutes, row.sampleCount, ConfidenceScore(row.confidence))
        }
    }
}

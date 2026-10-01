package com.hoodie.app.engine.hoodie

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.HoodieState
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.atZone
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Visão somente-leitura do histórico de contexto do usuário. */
interface ContextTimeline {
    fun contextAt(time: Long): UserContextType

    /** Primeiro instante > [after] em que o contexto muda, ou null. */
    fun nextChangeAfter(after: Long): Long?
}

data class SimulationEnv(
    val zone: ZoneId,
    val routine: Routine,
    val sleep: SleepSchedule,
    val daysOff: Set<LocalDate>,
)

data class CompletedActivity(
    val activity: HoodieActivity,
    val startedAt: Long,
    val endedAt: Long,
    val userContext: UserContextType,
)

data class AdvanceResult(val state: HoodieState, val completed: List<CompletedActivity>, val started: List<HoodieState>)

/**
 * Coração do princípio "o app nunca depende de ficar aberto".
 *
 * Dado o último estado salvo e o relógio atual, reconstrói tudo o que aconteceu
 * no intervalo: cada atividade termina em expectedEndAt (ou quando o contexto do
 * usuário muda) e a próxima é escolhida com uma seed derivada do instante — a
 * mesma entrada produz sempre o mesmo resultado, então reabrir o app não "rerola"
 * a vida do gato.
 */
object HoodieSimulator {

    /** Acima disso não vale simular minuto a minuto: o gato "recomeça" o dia. */
    private const val MAX_CATCH_UP_MS = 3 * DAY_MS
    private const val MAX_STEPS = 2_000

    fun initial(now: Long, timeline: ContextTimeline, env: SimulationEnv): HoodieState {
        val ctx = timeline.contextAt(now)
        val decision = HoodieDecisionEngine.decide(input(now, ctx, Needs(), null, env), seeded(now, null))
        return HoodieState(decision.activity, now, now + decision.durationMs, Needs(), now, ctx)
    }

    fun advance(state: HoodieState, now: Long, timeline: ContextTimeline, env: SimulationEnv): AdvanceResult {
        if (now - state.needsAt > MAX_CATCH_UP_MS) {
            val fresh = initial(now, timeline, env)
            return AdvanceResult(fresh, emptyList(), listOf(fresh))
        }
        var s = state
        val completed = mutableListOf<CompletedActivity>()
        val started = mutableListOf<HoodieState>()
        var steps = 0
        while (steps++ < MAX_STEPS) {
            val change = timeline.nextChangeAfter(s.needsAt)
            val boundary = listOfNotNull(s.expectedEndAt, change).minOrNull() ?: break
            if (boundary > now) break

            val needs = NeedsEngine.apply(s.needs, s.activity, s.userContext, (boundary - s.needsAt) / MINUTE_MS)
            val ctx = timeline.contextAt(boundary)
            val decision = HoodieDecisionEngine.decide(input(boundary, ctx, needs, s.activity, env), seeded(boundary, s.activity))
            val end = boundary + decision.durationMs
            s = if (decision.activity == s.activity) {
                // Continua a mesma atividade: mantém o "desde" original.
                s.copy(expectedEndAt = end, needs = needs, needsAt = boundary, userContext = ctx)
            } else {
                completed += CompletedActivity(s.activity, s.startedAt, boundary, s.userContext)
                HoodieState(decision.activity, boundary, end, needs, boundary, ctx).also { started += it }
            }
        }
        return AdvanceResult(s, completed, started)
    }

    /** Necessidades "ao vivo" para exibição, sem alterar o estado salvo. */
    fun liveNeeds(state: HoodieState, now: Long): Needs =
        NeedsEngine.apply(state.needs, state.activity, state.userContext, (now - state.needsAt) / MINUTE_MS)

    private fun input(at: Long, ctx: UserContextType, needs: Needs, previous: HoodieActivity?, env: SimulationEnv): DecisionInput {
        val zoned = at.atZone(env.zone)
        return DecisionInput(zoned, ctx, needs, previous, env.routine, env.sleep, zoned.toLocalDate() in env.daysOff)
    }

    private fun seeded(at: Long, previous: HoodieActivity?): Random =
        Random(at / MINUTE_MS * 31 + (previous?.ordinal ?: 99))
}

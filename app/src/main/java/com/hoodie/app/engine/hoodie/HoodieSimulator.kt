package com.hoodie.app.engine.hoodie

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.HoodieState
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
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

data class AdvanceResult(
    val state: HoodieState,
    val completed: List<CompletedActivity>,
    val started: List<HoodieState>,
    val recovery: SimulationRecovery = SimulationRecovery.NORMAL_CATCH_UP,
)

enum class SimulationRecovery {
    /** Reconstrói tudo o que aconteceu desde o último estado. */
    NORMAL_CATCH_UP,

    /** Ausência longa demais: recomeça agora, sem inventar os dias que não dá para reconstruir. */
    RESET_AFTER_LONG_ABSENCE,
}

/** Até onde vale reconstruir o passado. */
object SimulationRecoveryPolicy {
    fun decide(lastNeedsAt: Long, now: Long): SimulationRecovery =
        if (now - lastNeedsAt > HoodieConfig.MAX_CATCH_UP_MS) SimulationRecovery.RESET_AFTER_LONG_ABSENCE
        else SimulationRecovery.NORMAL_CATCH_UP
}

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

    private const val MAX_STEPS = 2_000

    fun initial(now: Long, timeline: ContextTimeline, env: SimulationEnv): HoodieState {
        val ctx = timeline.contextAt(now)
        val decision = HoodieDecisionEngine.decide(input(now, ctx, Needs(), null, env), seeded(now, null))
        return HoodieState(decision.activity, now, now + decision.durationMs, Needs(), now, ctx)
    }

    fun advance(state: HoodieState, now: Long, timeline: ContextTimeline, env: SimulationEnv): AdvanceResult {
        if (SimulationRecoveryPolicy.decide(state.needsAt, now) == SimulationRecovery.RESET_AFTER_LONG_ABSENCE) {
            val fresh = initial(now, timeline, env)
            return AdvanceResult(fresh, emptyList(), listOf(fresh), SimulationRecovery.RESET_AFTER_LONG_ABSENCE)
        }
        var s = state
        val completed = mutableListOf<CompletedActivity>()
        val started = mutableListOf<HoodieState>()
        var steps = 0
        while (steps++ < MAX_STEPS) {
            // O contexto mudou "por trás" do estado salvo (evento atrasado ou correção):
            // reage já, em vez de esperar a atividade atual terminar.
            val stale = timeline.contextAt(s.needsAt) != s.userContext
            val change = if (stale) s.needsAt else timeline.nextChangeAfter(s.needsAt)
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
        // Relógio voltou (TIME_SET): nunca aplica tempo negativo.
        NeedsEngine.apply(state.needs, state.activity, state.userContext, ((now - state.needsAt) / MINUTE_MS).coerceAtLeast(0))

    private fun input(at: Long, ctx: UserContextType, needs: Needs, previous: HoodieActivity?, env: SimulationEnv): DecisionInput {
        val zoned = at.atZone(env.zone)
        return DecisionInput(zoned, ctx, needs, previous, env.routine, env.sleep, zoned.toLocalDate() in env.daysOff)
    }

    private fun seeded(at: Long, previous: HoodieActivity?): Random =
        Random(at / MINUTE_MS * 31 + (previous?.ordinal ?: 99))
}

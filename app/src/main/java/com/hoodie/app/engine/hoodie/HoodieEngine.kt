package com.hoodie.app.engine.hoodie

import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.HoodieActivityDao
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.HoodieStateDao
import com.hoodie.app.core.database.HoodieStateEntity
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.model.HoodieState
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.atZone
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.data.repository.TimelineRepository
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.engine.timeline.ContextSpan
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class HoodieSnapshot(
    val state: HoodieState,
    val liveNeeds: Needs,
    /** Atividades iniciadas durante esta reconciliação (para notificações/timeline). */
    val started: List<HoodieState>,
)

/**
 * Ponte entre o simulador puro e o banco. Chamado ao abrir o app, a cada minuto
 * com o app aberto, a cada mudança de contexto e periodicamente pelo WorkManager.
 * Um Mutex garante que UI e worker nunca simulem o mesmo intervalo duas vezes.
 */
@Singleton
class HoodieEngine @Inject constructor(
    private val stateDao: HoodieStateDao,
    private val activityDao: HoodieActivityDao,
    private val contextDao: ContextEventDao,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val timeline: TimelineRepository,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
) {
    private val mutex = Mutex()

    suspend fun resolve(): HoodieSnapshot = mutex.withLock {
        val now = clock.nowMillis()
        val zone = clock.zone()
        val s = settings.current()
        val routine = routines.get()
        val today = clock.today()
        val daysOff = routines.daysOff(today.minusDays(4), today.plusDays(1))
        val env = SimulationEnv(zone, routine, s.sleep, daysOff)

        val saved = stateDao.get()?.toDomain()
        val from = minOf(saved?.needsAt ?: now, now) - DAY_MS
        val events = contextDao.overlapping(from, now + 1).map { ContextSpan(it.type, it.startedAt, it.endedAt) }
        val contexts = EventContextTimeline(events) { t ->
            val zoned = t.atZone(zone)
            RoutineEngine.probableContext(zoned, routine, zoned.toLocalDate() in daysOff)
        }

        val result = if (saved == null) {
            val initial = HoodieSimulator.initial(now, contexts, env)
            AdvanceResult(initial, emptyList(), listOf(initial))
        } else {
            HoodieSimulator.advance(saved, now, contexts, env)
        }

        stateDao.upsert(result.state.toEntity())
        if (result.completed.isNotEmpty()) {
            activityDao.insertAll(result.completed.map { HoodieActivityEntity(activity = it.activity, startedAt = it.startedAt, endedAt = it.endedAt, userContext = it.userContext) })
        }
        if (result.recovery == SimulationRecovery.RESET_AFTER_LONG_ABSENCE) {
            timeline.recordSystem("🐱", "${s.catName} retomou a rotina.", now)
            log.log(DebugEventLogger.Category.HOODIE, "RESET_AFTER_LONG_ABSENCE")
        }
        var previous = saved?.activity
        result.started.forEach { st ->
            timeline.recordHoodieActivity(st.activity.emoji, "${s.catName} ${st.activity.pastTense}", st.startedAt)
            log.log(DebugEventLogger.Category.HOODIE, "${previous ?: "∅"} → ${st.activity} (${st.userContext})")
            previous = st.activity
        }
        HoodieSnapshot(result.state, HoodieSimulator.liveNeeds(result.state, now), result.started)
    }

    /**
     * O contexto a partir de [since] foi desfeito (oscilação de GPS): apaga o que o
     * Hoodie "viveu" baseado nele e retoma a atividade que foi interrompida.
     * O próximo [resolve] decide a partir de agora com o contexto corrigido.
     */
    suspend fun discardSince(since: Long) = mutex.withLock {
        val saved = stateDao.get()?.toDomain() ?: return@withLock
        activityDao.deleteStartedFrom(since)
        timeline.invalidateRange(TimelineSourceType.HOODIE_ACTIVITY, since, Long.MAX_VALUE)
        if (saved.startedAt < since) return@withLock
        val now = clock.nowMillis()
        val interrupted = activityDao.endedAt(since)
        val restored = if (interrupted != null) {
            activityDao.delete(interrupted.id)
            saved.copy(activity = interrupted.activity, startedAt = interrupted.startedAt, expectedEndAt = now, userContext = interrupted.userContext)
        } else {
            saved.copy(expectedEndAt = now)
        }
        stateDao.upsert(restored.toEntity())
        log.log(DebugEventLogger.Category.HOODIE, "DISCARD desde $since → retoma ${restored.activity}")
    }

    private fun HoodieStateEntity.toDomain() = HoodieState(
        activity, startedAt, expectedEndAt, Needs(energy, hunger, mood, social, focus), needsAt, userContext,
    )

    private fun HoodieState.toEntity() = HoodieStateEntity(
        activity = activity, startedAt = startedAt, expectedEndAt = expectedEndAt,
        energy = needs.energy, hunger = needs.hunger, mood = needs.mood, social = needs.social, focus = needs.focus,
        needsAt = needsAt, userContext = userContext,
    )
}

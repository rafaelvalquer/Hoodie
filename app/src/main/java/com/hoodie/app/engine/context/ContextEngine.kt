package com.hoodie.app.engine.context

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.ConfirmationDao
import com.hoodie.app.core.database.ContextConfirmationEntity
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.database.LocationEventDao
import com.hoodie.app.core.database.LocationEventEntity
import com.hoodie.app.core.database.QuestionDao
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.model.ContextQuestion
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.atZone
import com.hoodie.app.core.time.minuteOfDay
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.engine.memory.Milestone
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.worker.CheckScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

enum class GeofenceTransition { ENTER, EXIT, DWELL }

/**
 * Context Engine: sensor → candidato → score → confiança → contexto.
 *
 * Recebe eventos de geofence, checagens agendadas, respostas e o modo manual e
 * decide o contexto. Toda escrita em context_events passa pelo
 * [ContextTransitionService] (manter, ou fechar + abrir). Depois de cada
 * mudança, pede ao HoodieEngine para reagir na hora.
 */
@Singleton
class ContextEngine @Inject constructor(
    private val contextDao: ContextEventDao,
    private val transitions: ContextTransitionService,
    private val confirmationDao: ConfirmationDao,
    private val questionDao: QuestionDao,
    private val locationEventDao: LocationEventDao,
    private val places: PlaceRepository,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val memory: MemoryEngine,
    private val hoodie: HoodieEngine,
    private val notifier: Notifier,
    private val scheduler: CheckScheduler,
    private val location: LocationSource,
    private val geofences: GeofenceRegistrar,
    private val cipher: CoordinateCipher,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
) {
    private val mutex = Mutex()

    // ───────────────────────── Sensores ─────────────────────────

    suspend fun onGeofence(placeId: Long, transition: GeofenceTransition, at: Long = clock.nowMillis()) = mutex.withLock {
        val place = places.byId(placeId) ?: return@withLock
        log.log(DebugEventLogger.Category.GEOFENCE, "${transition.name} ${place.name}")
        locationEventDao.insert(LocationEventEntity(placeId = placeId, transition = transition.name, timestamp = at))
        when (transition) {
            GeofenceTransition.ENTER, GeofenceTransition.DWELL -> handleEnter(place, at)
            GeofenceTransition.EXIT -> handleExit(place, at)
        }
    }

    private suspend fun handleEnter(place: Place, at: Long) {
        val current = contextDao.current()
        // Já estamos neste lugar (ENTER repetido ou DWELL): nada muda.
        if (current != null && current.placeId == place.id) return
        if (current != null && revertFlapIfNeeded(current, place, at)) return

        val candidate = ContextScorer.score(input(ContextSignal.Enter(place), current?.type))
        scheduler.cancelChecks()
        places.markVisited(place.id, at)
        when (candidate.decision) {
            ContextDecision.APPLY ->
                switchTo(candidate.type, at, candidate.confidence, place.id, ContextSource.GEOFENCE, TransitionReason.GEOFENCE_ENTER)
            ContextDecision.APPLY_AND_ASK -> {
                val r = switchTo(candidate.type, at, candidate.confidence, place.id, ContextSource.GEOFENCE, TransitionReason.GEOFENCE_ENTER)
                ask(QuestionKind.CONFIRM_CONTEXT, candidate.type, place.id, r.event.id)
            }
            ContextDecision.UNKNOWN ->
                switchTo(UserContextType.UNKNOWN, at, candidate.confidence, place.id, ContextSource.GEOFENCE, TransitionReason.GEOFENCE_ENTER)
        }
    }

    /**
     * GPS oscilando na borda: saiu e voltou ao mesmo lugar em poucos minutos.
     * Desfaz a saída inteira — contexto, timeline e o que o Hoodie fez nesse meio-tempo.
     */
    private suspend fun revertFlapIfNeeded(current: ContextEventEntity, place: Place, at: Long): Boolean {
        val createdByExit = current.source == ContextSource.GEOFENCE && current.placeId == null
        if (!createdByExit || at - current.startedAt >= HoodieConfig.GPS_FLAP_MS) return false
        val prev = contextDao.previous() ?: return false
        if (prev.placeId != place.id || prev.endedAt != current.startedAt) return false
        transitions.revertFlap(current, prev)
        scheduler.cancelChecks()
        hoodie.discardSince(current.startedAt)
        hoodie.resolve()
        return true
    }

    private suspend fun handleExit(place: Place, at: Long) {
        val current = contextDao.current()
        // Saída atrasada de um lugar onde já não estávamos.
        if (current != null && current.placeId != place.id) return
        val candidate = ContextScorer.score(input(ContextSignal.Exit(place), current?.type))
        if (candidate.type == UserContextType.LUNCH) {
            if (candidate.decision == ContextDecision.APPLY) {
                switchTo(UserContextType.LUNCH, at, candidate.confidence, null, ContextSource.GEOFENCE, TransitionReason.GEOFENCE_EXIT)
            } else {
                switchTo(UserContextType.COMMUTING, at, LUNCH_PENDING_CONFIDENCE, null, ContextSource.GEOFENCE, TransitionReason.GEOFENCE_EXIT)
                scheduler.scheduleLunchCheck(at, place.id)
            }
        } else {
            val r = switchTo(UserContextType.COMMUTING, at, candidate.confidence, null, ContextSource.GEOFENCE, TransitionReason.GEOFENCE_EXIT)
            scheduler.scheduleCommuteCheck(r.event.id)
        }
    }

    /**
     * Fora do trabalho por um tempo mínimo no horário de almoço → provavelmente almoço.
     * Fecha o deslocamento e abre LUNCH agora (boundary) — nunca reescreve o deslocamento.
     */
    suspend fun onLunchCheck(exitAt: Long, placeId: Long) = mutex.withLock {
        val current = contextDao.current() ?: return@withLock
        if (current.type != UserContextType.COMMUTING || current.startedAt != exitAt) return@withLock
        val place = places.byId(placeId) ?: return@withLock
        val now = clock.nowMillis()
        val minutes = ((now - exitAt) / MINUTE_MS).toInt()
        val candidate = ContextScorer.score(input(ContextSignal.Exit(place, minutes), current.type))
        if (candidate.type != UserContextType.LUNCH || candidate.decision == ContextDecision.UNKNOWN) {
            scheduler.scheduleCommuteCheck(current.id)
            return@withLock
        }
        val r = switchTo(UserContextType.LUNCH, now, candidate.confidence, null, ContextSource.GEOFENCE, TransitionReason.LUNCH_CHECK)
        if (candidate.decision == ContextDecision.APPLY_AND_ASK) {
            ask(QuestionKind.CONFIRM_CONTEXT, UserContextType.LUNCH, placeId, r.event.id)
        }
    }

    /** Deslocamento longo: uma única leitura de posição para descobrir se é um lugar novo. */
    suspend fun onCommuteCheck(eventId: Long) {
        val current = contextDao.current() ?: return
        if (current.id != eventId || current.type != UserContextType.COMMUTING) return
        val (lat, lng) = location.current() ?: return
        val known = places.containing(lat, lng)
        if (known != null) {
            onGeofence(known.id, GeofenceTransition.ENTER)
            return
        }
        mutex.withLock {
            val now = clock.nowMillis()
            val r = switchTo(UserContextType.UNKNOWN, now, NEW_PLACE_CONFIDENCE, null, ContextSource.LOCATION_CHECK, TransitionReason.COMMUTE_CHECK)
            ask(QuestionKind.NEW_PLACE, null, null, r.event.id, cipher.encrypt(lat, lng))
        }
    }

    // ───────────────────────── Usuário ─────────────────────────

    /** Modo manual "O que estou fazendo?" — também treina as regras. */
    suspend fun setManual(type: UserContextType) = mutex.withLock {
        val now = clock.nowMillis()
        val placeId = when (type) {
            UserContextType.HOME -> places.firstOfType(PlaceType.HOME)?.id
            UserContextType.WORK -> places.firstOfType(PlaceType.WORK)?.id
            UserContextType.GYM -> places.firstOfType(PlaceType.GYM)?.id
            else -> null
        }
        scheduler.cancelChecks()
        switchTo(type, now, 1f, placeId, ContextSource.MANUAL, TransitionReason.MANUAL)
        recordConfirmation(type, placeId, now, accepted = true)
    }

    /** Onboarding/Lugares: "estou aqui agora" — salva o lugar e já entra nele. */
    suspend fun savePlaceHere(type: PlaceType, name: String, lat: Double, lng: Double): Place = mutex.withLock {
        val now = clock.nowMillis()
        val place = places.add(name, type, lat, lng, HoodieConfig.DEFAULT_GEOFENCE_RADIUS_M, now)
        geofences.registerAll()
        switchTo(contextFor(type, now), now, 1f, place.id, ContextSource.ONBOARDING, TransitionReason.PLACE_SAVED)
        place
    }

    suspend fun answerYesNo(questionId: Long, yes: Boolean) = mutex.withLock {
        val q = questionDao.getById(questionId) ?: return@withLock
        if (q.answeredAt != null) return@withLock
        val now = clock.nowMillis()
        questionDao.update(q.copy(answeredAt = now, answer = if (yes) "YES" else "NO"))
        notifier.cancelQuestion(questionId)
        val candidate = q.candidate ?: return@withLock
        recordConfirmation(candidate, q.placeId, q.askedAt, accepted = yes)
        val event = q.contextEventId?.let { contextDao.getById(it) }
        when {
            yes -> {
                event?.let { transitions.confirm(it.id, 1f, ContextSource.CONFIRMATION) }
                memory.onContext(candidate, null, now)
                hoodie.resolve()
            }
            event != null && event.endedAt == null -> {
                // "Não": boundary agora. O que já passou continua registrado como foi inferido.
                val fixed = if (candidate == UserContextType.WORK) UserContextType.LEISURE else UserContextType.UNKNOWN
                switchTo(fixed, now, 1f, event.placeId, ContextSource.CONFIRMATION, TransitionReason.CONFIRMATION_REJECTED, note = "${fixed.label} (corrigido)")
            }
            else -> hoodie.resolve()
        }
    }

    /** "Parece que você está em um lugar novo. O que é?" */
    suspend fun answerNewPlace(questionId: Long, type: PlaceType) = mutex.withLock {
        val q = questionDao.getById(questionId) ?: return@withLock
        if (q.answeredAt != null) return@withLock
        val now = clock.nowMillis()
        questionDao.update(q.copy(answeredAt = now, answer = type.name, chosenPlaceType = type))
        notifier.cancelQuestion(questionId)
        val ctx = contextFor(type, now)
        recordConfirmation(ctx, null, now, accepted = true)
        memory.unlock(Milestone.FIRST_NEW_PLACE)
        val asked = q.contextEventId?.let { contextDao.getById(it) }
        val eventId = if (asked != null && asked.endedAt == null) {
            switchTo(ctx, now, 1f, null, ContextSource.CONFIRMATION, TransitionReason.NEW_PLACE_ANSWER).event.id
        } else {
            // A pessoa já saiu de lá: aprende a resposta, mas não muda o presente.
            memory.onContext(ctx, null, now)
            hoodie.resolve()
            q.contextEventId
        }
        // Segunda etapa da mesma conversa: salvar o lugar? (não conta no limite diário)
        if (q.encryptedCoordinates != null) {
            questionDao.insert(
                ContextQuestionEntity(
                    kind = QuestionKind.SAVE_PLACE, candidate = ctx, placeId = null,
                    encryptedCoordinates = q.encryptedCoordinates, chosenPlaceType = type,
                    contextEventId = eventId, askedAt = now,
                ),
            )
        }
    }

    /** "Salvar este lugar como sua academia? [Sim] [Só hoje]" */
    suspend fun answerSavePlace(questionId: Long, save: Boolean) = mutex.withLock {
        val q = questionDao.getById(questionId) ?: return@withLock
        if (q.answeredAt != null) return@withLock
        val now = clock.nowMillis()
        questionDao.update(q.copy(answeredAt = now, answer = if (save) "SAVE" else "TODAY"))
        val type = q.chosenPlaceType ?: return@withLock
        val coords = q.encryptedCoordinates?.let { cipher.decrypt(it) } ?: return@withLock
        if (!save) return@withLock
        val place = places.add(type.label, type, coords.first, coords.second, HoodieConfig.DEFAULT_GEOFENCE_RADIUS_M, now)
        geofences.registerAll()
        q.contextEventId?.let { transitions.attachPlace(it, place.id) }
    }

    suspend fun dismissQuestion(questionId: Long) = mutex.withLock {
        val q = questionDao.getById(questionId) ?: return@withLock
        questionDao.update(q.copy(answeredAt = clock.nowMillis(), answer = "DISMISSED"))
        notifier.cancelQuestion(questionId)
    }

    /**
     * Sem localização (ou sem lugares), o Hoodie segue a rotina provável:
     * horário + último contexto + rotina. Nunca quebra o jogo.
     */
    suspend fun applyRoutineFallbackIfNeeded() = mutex.withLock {
        val now = clock.nowMillis()
        val usable = location.permissionState().canMonitorGeofences && places.all().isNotEmpty()
        val current = contextDao.current()
        if (usable && current != null) return@withLock
        val zoned = clock.now()
        val probable = RoutineEngine.probableContext(zoned, routines.get(), routines.isDayOff(zoned.toLocalDate()))
        val shouldSwitch = current == null ||
            (current.source == ContextSource.ROUTINE && current.type != probable) ||
            // Sem geofence nada mais vai mudar o contexto: qualquer evento antigo (manual,
            // geofence de antes da permissão sumir) cede à rotina depois do tempo de espera.
            (!usable && current.source != ContextSource.ROUTINE && now - current.startedAt > HoodieConfig.MANUAL_HOLD_MS && current.type != probable)
        if (shouldSwitch) switchTo(probable, now, ROUTINE_CONFIDENCE, null, ContextSource.ROUTINE, TransitionReason.ROUTINE_FALLBACK)
    }

    // ───────────────────────── Internos ─────────────────────────

    private suspend fun input(signal: ContextSignal, previous: UserContextType?): ContextInput {
        val now = clock.now()
        val confirmations = confirmationDao.since(clock.nowMillis() - HoodieConfig.CONFIRMATION_LOOKBACK_MS).map {
            ConfirmationRecord(it.type, it.placeId, DayOfWeek.of(it.dayOfWeek), it.minuteOfDay, it.accepted, it.timestamp)
        }
        return ContextInput(signal, now, routines.get(), routines.isDayOff(now.toLocalDate()), previous, confirmations)
    }

    private suspend fun contextFor(type: PlaceType, now: Long): UserContextType =
        if (type == PlaceType.RESTAURANT && !RoutineEngine.isLunchWindow(now.atZone(clock.zone()).minuteOfDay(), routines.get())) {
            UserContextType.LEISURE
        } else {
            type.toContext()
        }

    private suspend fun recordConfirmation(type: UserContextType, placeId: Long?, at: Long, accepted: Boolean) {
        val z = at.atZone(clock.zone())
        confirmationDao.insert(
            ContextConfirmationEntity(type = type, placeId = placeId, dayOfWeek = z.dayOfWeek.value, minuteOfDay = z.minuteOfDay(), accepted = accepted, timestamp = at),
        )
    }

    /** Mantém ou cria boundary (via serviço), reage (memórias, avisos) e faz o Hoodie reagir na hora. */
    private suspend fun switchTo(
        type: UserContextType,
        at: Long,
        confidence: Float,
        placeId: Long?,
        source: ContextSource,
        reason: TransitionReason,
        note: String? = null,
    ): TransitionResult {
        val r = transitions.transition(type, at, confidence, placeId, source, reason, note)
        if (r.changed) {
            memory.onContext(type, r.previous?.type, r.event.startedAt)
            if (source == ContextSource.GEOFENCE) notifyArrival(type, r.previous, r.event.startedAt)
        }
        hoodie.resolve()
        return r
    }

    private suspend fun notifyArrival(type: UserContextType, previous: ContextEventEntity?, at: Long) {
        val name = settings.current().catName
        when (type) {
            UserContextType.WORK -> if (previous?.type != UserContextType.LUNCH) notifier.event("🐱 $name chegou ao trabalho.")
            UserContextType.HOME -> if (previous != null && previous.type != UserContextType.HOME && at - previous.startedAt >= HOME_RETURN_MIN_MS) {
                notifier.event("🏠 Vocês estão de volta em casa.")
            }
            UserContextType.GYM -> notifier.event("🏋 $name veio treinar junto!")
            else -> Unit
        }
    }

    private suspend fun ask(kind: QuestionKind, candidate: UserContextType?, placeId: Long?, eventId: Long?, coords: String? = null): Long? {
        val now = clock.nowMillis()
        val recent = questionDao.since(now - DAY_MS).map { AskedQuestion(it.kind, it.candidate, it.askedAt) }
        if (!ConfirmationPolicy.canAsk(now, clock.zone(), kind, candidate, recent)) return null
        val q = ContextQuestionEntity(kind = kind, candidate = candidate, placeId = placeId, encryptedCoordinates = coords, contextEventId = eventId, askedAt = now)
        val id = questionDao.insert(q)
        val prompt = ContextQuestion(id, kind, candidate, placeId, null, eventId, now, null, null).prompt
        if (kind == QuestionKind.CONFIRM_CONTEXT) notifier.askYesNo(id, prompt) else notifier.askInApp(id, prompt)
        return id
    }

    companion object {
        const val FLAP_MS = HoodieConfig.GPS_FLAP_MS
        const val MANUAL_HOLD_MS = HoodieConfig.MANUAL_HOLD_MS
        private const val LUNCH_PENDING_CONFIDENCE = 0.6f
        private const val NEW_PLACE_CONFIDENCE = 0.5f
        private const val ROUTINE_CONFIDENCE = 0.3f
        private const val HOME_RETURN_MIN_MS = 30 * MINUTE_MS
    }
}

package com.hoodie.app.engine.context

import com.hoodie.app.core.database.ConfirmationDao
import com.hoodie.app.core.database.ContextConfirmationEntity
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.database.LocationEventDao
import com.hoodie.app.core.database.LocationEventEntity
import com.hoodie.app.core.database.QuestionDao
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.location.LocationProvider
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.HoodieNotifier
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.atZone
import com.hoodie.app.core.time.minuteOfDay
import com.hoodie.app.data.repository.HistoryRepository
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.engine.memory.Milestone
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.worker.WorkScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

enum class GeofenceTransition { ENTER, EXIT, DWELL }

/**
 * Context Engine: sensor → candidato → score → confiança → contexto.
 *
 * Recebe eventos de geofence, checagens agendadas, respostas e o modo manual, e
 * mantém a tabela context_events como fonte única de verdade de "onde a pessoa
 * provavelmente está". Depois de cada mudança, pede ao HoodieEngine para reagir.
 */
@Singleton
class ContextEngine @Inject constructor(
    private val contextDao: ContextEventDao,
    private val confirmationDao: ConfirmationDao,
    private val questionDao: QuestionDao,
    private val locationEventDao: LocationEventDao,
    private val places: PlaceRepository,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val memory: MemoryEngine,
    private val hoodie: HoodieEngine,
    private val notifier: HoodieNotifier,
    private val scheduler: WorkScheduler,
    private val location: LocationProvider,
    private val geofences: GeofenceManager,
    private val cipher: CoordinateCipher,
    private val clock: ClockProvider,
) {
    private val mutex = Mutex()

    // ───────────────────────── Sensores ─────────────────────────

    suspend fun onGeofence(placeId: Long, transition: GeofenceTransition, at: Long = clock.nowMillis()) = mutex.withLock {
        val place = places.byId(placeId) ?: return@withLock
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

        // GPS oscilando na borda: saiu e voltou em poucos minutos → desfaz a saída.
        if (current != null && current.type == UserContextType.COMMUTING && at - current.startedAt < FLAP_MS) {
            val prev = contextDao.previous()
            if (prev != null && prev.placeId == place.id && prev.endedAt == current.startedAt) {
                contextDao.delete(current.id)
                contextDao.update(prev.copy(endedAt = null))
                scheduler.cancelChecks()
                hoodie.resolve()
                return
            }
        }

        val candidate = ContextScorer.score(input(ContextSignal.Enter(place), current?.type))
        scheduler.cancelChecks()
        places.markVisited(place.id, at)
        when (candidate.decision) {
            ContextDecision.APPLY -> switchTo(candidate.type, at, candidate.confidence, place.id, ContextSource.GEOFENCE)
            ContextDecision.APPLY_AND_ASK -> {
                val id = switchTo(candidate.type, at, candidate.confidence, place.id, ContextSource.GEOFENCE)
                ask(QuestionKind.CONFIRM_CONTEXT, candidate.type, place.id, id)
            }
            ContextDecision.UNKNOWN -> switchTo(UserContextType.UNKNOWN, at, candidate.confidence, place.id, ContextSource.GEOFENCE)
        }
    }

    private suspend fun handleExit(place: Place, at: Long) {
        val current = contextDao.current()
        // Saída atrasada de um lugar onde já não estávamos.
        if (current != null && current.placeId != place.id) return
        val candidate = ContextScorer.score(input(ContextSignal.Exit(place), current?.type))
        if (candidate.type == UserContextType.LUNCH) {
            if (candidate.decision == ContextDecision.APPLY) {
                switchTo(UserContextType.LUNCH, at, candidate.confidence, null, ContextSource.GEOFENCE)
            } else {
                switchTo(UserContextType.COMMUTING, at, 0.6f, null, ContextSource.GEOFENCE)
                scheduler.scheduleLunchCheck(at, place.id)
            }
        } else {
            val id = switchTo(UserContextType.COMMUTING, at, candidate.confidence, null, ContextSource.GEOFENCE)
            scheduler.scheduleCommuteCheck(id)
        }
    }

    /** Fora do trabalho por um tempo mínimo no horário de almoço → provavelmente almoço. */
    suspend fun onLunchCheck(exitAt: Long, placeId: Long) = mutex.withLock {
        val current = contextDao.current() ?: return@withLock
        if (current.type != UserContextType.COMMUTING || current.startedAt != exitAt) return@withLock
        val place = places.byId(placeId) ?: return@withLock
        val minutes = ((clock.nowMillis() - exitAt) / MINUTE_MS).toInt()
        val candidate = ContextScorer.score(input(ContextSignal.Exit(place, minutes), current.type))
        if (candidate.type != UserContextType.LUNCH || candidate.decision == ContextDecision.UNKNOWN) {
            scheduler.scheduleCommuteCheck(current.id)
            return@withLock
        }
        contextDao.update(current.copy(type = UserContextType.LUNCH, confidence = candidate.confidence))
        history.record(TimelineActor.USER, UserContextType.LUNCH.emoji, "Almoço", clock.nowMillis())
        if (candidate.decision == ContextDecision.APPLY_AND_ASK) ask(QuestionKind.CONFIRM_CONTEXT, UserContextType.LUNCH, placeId, current.id)
        else memory.unlock(Milestone.FIRST_LUNCH)
        hoodie.resolve()
    }

    /** Deslocamento longo: uma única leitura de posição para descobrir se é um lugar novo. */
    suspend fun onCommuteCheck(eventId: Long) {
        val current = contextDao.current() ?: return
        if (current.id != eventId || current.type != UserContextType.COMMUTING) return
        val (lat, lng) = location.current() ?: return
        val known = places.containing(lat, lng)
        if (known != null) { onGeofence(known.id, GeofenceTransition.ENTER); return }
        mutex.withLock {
            val now = clock.nowMillis()
            val id = switchTo(UserContextType.UNKNOWN, now, 0.5f, null, ContextSource.LOCATION_CHECK)
            ask(QuestionKind.NEW_PLACE, null, null, id, cipher.encrypt(lat, lng))
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
        switchTo(type, now, 1f, placeId, ContextSource.MANUAL)
        recordConfirmation(type, placeId, now, accepted = true)
    }

    /** Onboarding/Lugares: "estou aqui agora" — salva o lugar e já entra nele. */
    suspend fun savePlaceHere(type: PlaceType, name: String, lat: Double, lng: Double): Place = mutex.withLock {
        val now = clock.nowMillis()
        val place = places.add(name, type, lat, lng, com.hoodie.app.core.geofence.GeofenceManager.DEFAULT_RADIUS, now)
        geofences.registerAll()
        switchTo(type.toContext().let { if (type == PlaceType.RESTAURANT) UserContextType.LUNCH else it }, now, 1f, place.id, ContextSource.ONBOARDING)
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
        if (yes) {
            event?.let { contextDao.update(it.copy(confidence = 1f, source = ContextSource.CONFIRMATION)) }
            memory.onContext(candidate, null, now)
        } else if (event != null && event.endedAt == null) {
            val fixed = if (candidate == UserContextType.WORK) UserContextType.LEISURE else UserContextType.UNKNOWN
            contextDao.update(event.copy(type = fixed, confidence = 1f, source = ContextSource.CONFIRMATION))
            history.record(TimelineActor.USER, fixed.emoji, "${fixed.label} (corrigido)", now)
        }
        hoodie.resolve()
    }

    /** "Parece que você está em um lugar novo. O que é?" */
    suspend fun answerNewPlace(questionId: Long, type: PlaceType) = mutex.withLock {
        val q = questionDao.getById(questionId) ?: return@withLock
        if (q.answeredAt != null) return@withLock
        val now = clock.nowMillis()
        questionDao.update(q.copy(answeredAt = now, answer = type.name, chosenPlaceType = type))
        notifier.cancelQuestion(questionId)
        val ctx = contextFor(type, now)
        q.contextEventId?.let { contextDao.getById(it) }?.takeIf { it.endedAt == null }?.let {
            contextDao.update(it.copy(type = ctx, confidence = 1f, source = ContextSource.CONFIRMATION))
        }
        history.record(TimelineActor.USER, ctx.emoji, ctx.label, now)
        recordConfirmation(ctx, null, now, accepted = true)
        memory.unlock(Milestone.FIRST_NEW_PLACE)
        memory.onContext(ctx, null, now)
        // Segunda etapa da mesma conversa: salvar o lugar? (não conta no limite diário)
        if (q.encryptedCoordinates != null) {
            questionDao.insert(
                ContextQuestionEntity(
                    kind = QuestionKind.SAVE_PLACE, candidate = ctx, placeId = null,
                    encryptedCoordinates = q.encryptedCoordinates, chosenPlaceType = type,
                    contextEventId = q.contextEventId, askedAt = now,
                ),
            )
        }
        hoodie.resolve()
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
        val place = places.add(type.label, type, coords.first, coords.second, com.hoodie.app.core.geofence.GeofenceManager.DEFAULT_RADIUS, now)
        geofences.registerAll()
        q.contextEventId?.let { contextDao.getById(it) }?.takeIf { it.endedAt == null }?.let {
            contextDao.update(it.copy(placeId = place.id))
        }
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
        val usable = location.hasBackground() && location.isEnabled() && places.all().isNotEmpty()
        val current = contextDao.current()
        if (usable && current != null) return@withLock
        val zoned = clock.now()
        val probable = RoutineEngine.probableContext(zoned, routines.get(), routines.isDayOff(zoned.toLocalDate()))
        val shouldSwitch = current == null ||
            (current.source == ContextSource.ROUTINE && current.type != probable) ||
            (!usable && current.source == ContextSource.MANUAL && now - current.startedAt > MANUAL_HOLD_MS && current.type != probable)
        if (shouldSwitch) switchTo(probable, now, 0.3f, null, ContextSource.ROUTINE)
    }

    // ───────────────────────── Internos ─────────────────────────

    private suspend fun input(signal: ContextSignal, previous: UserContextType?): ContextInput {
        val now = clock.now()
        val confirmations = confirmationDao.since(clock.nowMillis() - 30 * DAY_MS).map {
            ConfirmationRecord(it.type, it.placeId, DayOfWeek.of(it.dayOfWeek), it.minuteOfDay, it.accepted, it.timestamp)
        }
        return ContextInput(signal, now, routines.get(), routines.isDayOff(now.toLocalDate()), previous, confirmations)
    }

    private suspend fun contextFor(type: PlaceType, now: Long): UserContextType =
        if (type == PlaceType.RESTAURANT && !RoutineEngine.isLunchWindow(now.atZone(clock.zone()).minuteOfDay(), routines.get())) UserContextType.LEISURE
        else type.toContext()

    private suspend fun recordConfirmation(type: UserContextType, placeId: Long?, at: Long, accepted: Boolean) {
        val z = at.atZone(clock.zone())
        confirmationDao.insert(ContextConfirmationEntity(type = type, placeId = placeId, dayOfWeek = z.dayOfWeek.value, minuteOfDay = z.minuteOfDay(), accepted = accepted, timestamp = at))
    }

    /** Fecha o contexto aberto e abre o novo. Retorna o id do evento vigente. */
    private suspend fun switchTo(type: UserContextType, at: Long, confidence: Float, placeId: Long?, source: ContextSource): Long {
        val current = contextDao.current()
        if (current != null && current.type == type && current.placeId == placeId) return current.id
        contextDao.closeOpen(at)
        val id = contextDao.insert(ContextEventEntity(type = type, startedAt = at, endedAt = null, confidence = confidence, placeId = placeId, source = source))
        history.record(TimelineActor.USER, type.emoji, describe(type, placeId, current), at)
        memory.onContext(type, current?.type, at)
        if (source == ContextSource.GEOFENCE) notifyArrival(type, current, at)
        hoodie.resolve()
        return id
    }

    private suspend fun describe(type: UserContextType, placeId: Long?, previous: ContextEventEntity?): String = when (type) {
        UserContextType.COMMUTING -> {
            val from = previous?.placeId?.let { places.byId(it)?.name } ?: previous?.type?.label
            if (from != null) "Saiu de: $from" else "Deslocamento"
        }
        UserContextType.UNKNOWN -> "Lugar novo"
        else -> placeId?.let { places.byId(it)?.name }?.takeIf { it != type.label }?.let { "${type.label} · $it" } ?: type.label
    }

    private suspend fun notifyArrival(type: UserContextType, previous: ContextEventEntity?, at: Long) {
        val name = settings.current().catName
        when (type) {
            UserContextType.WORK -> if (previous?.type != UserContextType.LUNCH) notifier.event("🐱 $name chegou ao trabalho.")
            UserContextType.HOME -> if (previous != null && previous.type != UserContextType.HOME && at - previous.startedAt >= 30 * MINUTE_MS) {
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
        val prompt = com.hoodie.app.core.model.ContextQuestion(id, kind, candidate, placeId, null, eventId, now, null, null).prompt
        if (kind == QuestionKind.CONFIRM_CONTEXT) notifier.askYesNo(id, prompt) else notifier.askInApp(id, prompt)
        return id
    }

    companion object {
        /** Saída + reentrada no mesmo lugar dentro desta janela = oscilação de GPS. */
        const val FLAP_MS = 5 * MINUTE_MS
        const val MANUAL_HOLD_MS = 4 * HOUR_MS
    }
}

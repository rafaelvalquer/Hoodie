package com.hoodie.app.engine.context

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.LocationEventEntity
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.MINUTE_MS

/** Internal handler; synchronization belongs exclusively to ContextEngine. */
internal class GeofenceContextHandler(private val processor: ContextSignalProcessor) {
    suspend fun onGeofence(placeId: Long, transition: GeofenceTransition, at: Long): Unit = with(processor) {
        val place = places.byId(placeId) ?: return@with
        log.log(DebugEventLogger.Category.GEOFENCE, "${transition.name} ${place.name}")
        locationEventDao.insert(LocationEventEntity(placeId = placeId, transition = transition.name, timestamp = at))
        when (transition) {
            GeofenceTransition.ENTER, GeofenceTransition.DWELL -> handleEnter(place, at)
            GeofenceTransition.EXIT -> handleExit(place, at)
        }
    }

    private suspend fun handleEnter(place: Place, at: Long): Unit = with(processor) {
        val current = contextDao.current()
        // Já estamos neste lugar (ENTER repetido ou DWELL): nada muda.
        if (current != null && current.placeId == place.id) return@with
        if (current != null && revertFlapIfNeeded(current, place, at)) return@with

        val candidate = ContextScorer.score(input(ContextSignal.Enter(place), current?.type))
        scheduler.cancelChecks()
        places.markVisited(place.id, at)
        when (candidate.decision) {
            ContextDecision.ASK_USER -> {
                val r = switchTo(UserContextType.UNKNOWN, at, candidate.confidence, place.id, ContextSource.GEOFENCE, TransitionReason.GEOFENCE_ENTER)
                ask(QuestionKind.CONFIRM_CONTEXT, candidate.type, place.id, r.event.id, detection = candidate.detection)
            }
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

    private suspend fun revertFlapIfNeeded(current: ContextEventEntity, place: Place, at: Long): Boolean = with(processor) {
        val createdByExit = current.source == ContextSource.GEOFENCE && current.placeId == null
        if (!createdByExit || at - current.startedAt >= HoodieConfig.GPS_FLAP_MS) return@with false
        val prev = contextDao.previous() ?: return@with false
        if (prev.source == ContextSource.USER_CORRECTION) return@with false
        if (prev.placeId != place.id || prev.endedAt != current.startedAt) return@with false
        transitions.revertFlap(current, prev)
        scheduler.cancelChecks()
        hoodie.discardSince(current.startedAt)
        hoodie.resolve()
        return@with true
    }

    private suspend fun handleExit(place: Place, at: Long): Unit = with(processor) {
        val current = contextDao.current()
        // Saída atrasada de um lugar onde já não estávamos.
        if (current != null && current.placeId != place.id) return@with
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

    suspend fun onLunchCheck(exitAt: Long, placeId: Long): Unit = with(processor) {
        val current = contextDao.current() ?: return@with
        if (current.type != UserContextType.COMMUTING || current.startedAt != exitAt) return@with
        val place = places.byId(placeId) ?: return@with
        val now = clock.nowMillis()
        val minutes = ((now - exitAt) / MINUTE_MS).toInt()
        val candidate = ContextScorer.score(input(ContextSignal.Exit(place, minutes), current.type))
        if (candidate.type != UserContextType.LUNCH || candidate.decision == ContextDecision.UNKNOWN) {
            scheduler.scheduleCommuteCheck(current.id)
            return@with
        }
        if (candidate.decision == ContextDecision.ASK_USER) {
            ask(QuestionKind.CONFIRM_CONTEXT, UserContextType.LUNCH, placeId, current.id, detection = candidate.detection)
            return@with
        }
        val r = switchTo(UserContextType.LUNCH, now, candidate.confidence, null, ContextSource.GEOFENCE, TransitionReason.LUNCH_CHECK)
        if (candidate.decision == ContextDecision.APPLY_AND_ASK) {
            ask(QuestionKind.CONFIRM_CONTEXT, UserContextType.LUNCH, placeId, r.event.id)
        }
    }

    suspend fun onCommuteCheck(eventId: Long): Unit = with(processor) {
        val current = contextDao.current() ?: return@with
        if (current.id != eventId || current.type != UserContextType.COMMUTING) return@with
        val (lat, lng) = location.current() ?: return@with
        arriveAtPosition(lat, lng, askNewPlace = true)
    }

    // ── APIs da mobilidade: o MobilityEngine pede, o ContextEngine decide o contexto ──

    /** Deslocamento confirmado (movimento detectado): COMMUTING, se ainda não estiver. */
    suspend fun beginCommute(fromPlaceId: Long?, at: Long, confidence: Float): Boolean = with(processor) {
        val current = contextDao.current()
        if (current?.type == UserContextType.COMMUTING) return@with false
        // Evento atrasado de um lugar onde já não estamos: não mexe no presente.
        if (fromPlaceId != null && current?.placeId != null && current.placeId != fromPlaceId) return@with false
        val r = switchTo(UserContextType.COMMUTING, at, confidence, null, ContextSource.MOBILITY, TransitionReason.MOBILITY_START)
        scheduler.scheduleCommuteCheck(r.event.id)
        r.changed
    }

    /** Chegada a um lugar conhecido descoberta pela mobilidade (mesmo caminho do ENTER). */
    suspend fun arriveAt(placeId: Long, at: Long) = onGeofence(placeId, GeofenceTransition.ENTER, at)

    /**
     * Chegada por uma leitura pontual de posição: lugar conhecido → ENTER; desconhecido →
     * fluxo atual de lugar novo (UNKNOWN + pergunta, se [askNewPlace]).
     */
    suspend fun arriveAtPosition(lat: Double, lng: Double, askNewPlace: Boolean): Unit = with(processor) {
        val known = places.containing(lat, lng)
        if (known != null) {
            onGeofence(known.id, GeofenceTransition.ENTER, clock.nowMillis())
            return@with
        }
        val now = clock.nowMillis()
        val r = switchTo(UserContextType.UNKNOWN, now, NEW_PLACE_CONFIDENCE, null, ContextSource.LOCATION_CHECK, TransitionReason.COMMUTE_CHECK)
        if (askNewPlace) ask(QuestionKind.NEW_PLACE, null, null, r.event.id, cipher.encrypt(lat, lng))
    }

    /** "Não cheguei aí": volta para deslocamento (a correção vira aprendizado na mobilidade). */
    suspend fun rejectArrival(placeId: Long, at: Long): Boolean = with(processor) {
        val current = contextDao.current() ?: return@with false
        if (current.placeId != placeId) return@with false
        val r = switchTo(UserContextType.COMMUTING, at, 1f, null, ContextSource.CONFIRMATION, TransitionReason.ARRIVAL_REJECTED, note = "Ainda a caminho")
        scheduler.scheduleCommuteCheck(r.event.id)
        true
    }
    companion object {
        private const val LUNCH_PENDING_CONFIDENCE = 0.6f
        private const val NEW_PLACE_CONFIDENCE = 0.5f
    }

}

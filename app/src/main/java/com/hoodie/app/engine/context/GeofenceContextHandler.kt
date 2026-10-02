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
        val r = switchTo(UserContextType.LUNCH, now, candidate.confidence, null, ContextSource.GEOFENCE, TransitionReason.LUNCH_CHECK)
        if (candidate.decision == ContextDecision.APPLY_AND_ASK) {
            ask(QuestionKind.CONFIRM_CONTEXT, UserContextType.LUNCH, placeId, r.event.id)
        }
    }

    suspend fun onCommuteCheck(eventId: Long): Unit = with(processor) {
        val current = contextDao.current() ?: return@with
        if (current.id != eventId || current.type != UserContextType.COMMUTING) return@with
        val (lat, lng) = location.current() ?: return@with
        val known = places.containing(lat, lng)
        if (known != null) {
            onGeofence(known.id, GeofenceTransition.ENTER, clock.nowMillis())
            return@with
        }
        val now = clock.nowMillis()
        val r = switchTo(UserContextType.UNKNOWN, now, NEW_PLACE_CONFIDENCE, null, ContextSource.LOCATION_CHECK, TransitionReason.COMMUTE_CHECK)
        ask(QuestionKind.NEW_PLACE, null, null, r.event.id, cipher.encrypt(lat, lng))
    }
    companion object {
        private const val LUNCH_PENDING_CONFIDENCE = 0.6f
        private const val NEW_PLACE_CONFIDENCE = 0.5f
    }

}

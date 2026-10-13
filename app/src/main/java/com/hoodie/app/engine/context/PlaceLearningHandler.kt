package com.hoodie.app.engine.context

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind

data class NewPlaceSaveOptions(
    val questionId: Long,
    val type: PlaceType,
    val hasLocation: Boolean,
    val nearby: List<Place>,
)

/** Internal handler; synchronization belongs exclusively to ContextEngine. */
internal class PlaceLearningHandler(private val processor: ContextSignalProcessor) {
    suspend fun savePlaceHere(type: PlaceType, name: String, lat: Double, lng: Double): Place = with(processor) {
        val now = clock.nowMillis()
        val place = places.add(name, type, lat, lng, HoodieConfig.DEFAULT_GEOFENCE_RADIUS_M, now)
        geofences.registerAll()
        switchTo(inferredContextFor(type, now), now, 1f, place.id, ContextSource.ONBOARDING, TransitionReason.PLACE_SAVED)
        place
    }

    suspend fun saveOptions(questionId: Long, manualPosition: Pair<Double, Double>? = null): NewPlaceSaveOptions? = with(processor) {
        val q = questionDao.getById(questionId) ?: return@with null
        val type = q.chosenPlaceType ?: return@with null
        if (q.kind != QuestionKind.SAVE_PLACE || q.answeredAt != null) return@with null
        val recorded = q.encryptedCoordinates?.let { cipher.decrypt(it) }
        val coords = recorded ?: manualPosition
        val nearby = coords?.let { places.findNearby(it.first, it.second, HoodieConfig.PLACE_MATCH_SEARCH_METERS) }
            .orEmpty().sortedBy { it.type != type }
        NewPlaceSaveOptions(q.id, type, recorded != null, nearby)
    }

    suspend fun answerSavePlace(questionId: Long, save: Boolean, name: String? = null,
        reusePlaceId: Long? = null, manualPosition: Pair<Double, Double>? = null): Unit = with(processor) {
        val changed = transitions.atomic {
            val q = questionDao.getById(questionId) ?: return@atomic false
            if (q.answeredAt != null) return@atomic false
            require(q.kind == QuestionKind.SAVE_PLACE)
            val type = requireNotNull(q.chosenPlaceType)
            val now = clock.nowMillis()
            if (!save) {
                questionDao.update(q.copy(answeredAt = now, answer = "TODAY"))
                return@atomic true
            }
            val coords = q.encryptedCoordinates?.let { cipher.decrypt(it) } ?: manualPosition
            requireNotNull(coords) { "Informe a localização do lugar para salvar." }
            require(coords.first.isFinite() && coords.first in -90.0..90.0 &&
                coords.second.isFinite() && coords.second in -180.0..180.0)
            val place = if (reusePlaceId != null) {
                places.findNearby(coords.first, coords.second, HoodieConfig.PLACE_MATCH_SEARCH_METERS)
                    .firstOrNull { it.id == reusePlaceId && it.type == type }
                    ?: error("O lugar escolhido não corresponde a esta visita. Atualize as opções.")
            } else {
                places.add(name?.trim()?.takeIf { it.isNotEmpty() } ?: type.label, type,
                    coords.first, coords.second, HoodieConfig.DEFAULT_GEOFENCE_RADIUS_M, now, visitedAt = null)
            }
            val visitAt = q.contextEventId?.let { contextDao.getById(it)?.startedAt } ?: q.askedAt
            places.markVisited(place.id, visitAt)
            q.contextEventId?.let { transitions.attachPlace(it, place.id) }
            questionDao.update(q.copy(answeredAt = now, answer = if (reusePlaceId == null) "SAVE" else "REUSE",
                placeId = place.id))
            true
        }
        if (changed) {
            notifier.cancelQuestion(questionId)
            if (save) geofences.registerAll()
            hoodie.resolve()
        }
    }
}

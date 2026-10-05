package com.hoodie.app.engine.context

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType

/** Internal handler; synchronization belongs exclusively to ContextEngine. */
internal class PlaceLearningHandler(private val processor: ContextSignalProcessor) {
    suspend fun savePlaceHere(type: PlaceType, name: String, lat: Double, lng: Double): Place = with(processor) {
        val now = clock.nowMillis()
        val place = places.add(name, type, lat, lng, HoodieConfig.DEFAULT_GEOFENCE_RADIUS_M, now)
        geofences.registerAll()
        switchTo(inferredContextFor(type, now), now, 1f, place.id, ContextSource.ONBOARDING, TransitionReason.PLACE_SAVED)
        place
    }

    suspend fun answerSavePlace(questionId: Long, save: Boolean): Unit = with(processor) {
        val q = questionDao.getById(questionId) ?: return@with
        if (q.answeredAt != null) return@with
        val now = clock.nowMillis()
        questionDao.update(q.copy(answeredAt = now, answer = if (save) "SAVE" else "TODAY"))
        val type = q.chosenPlaceType ?: return@with
        val coords = q.encryptedCoordinates?.let { cipher.decrypt(it) } ?: return@with
        if (!save) return@with
        val place = places.add(type.label, type, coords.first, coords.second, HoodieConfig.DEFAULT_GEOFENCE_RADIUS_M, now)
        geofences.registerAll()
        q.contextEventId?.let { transitions.attachPlace(it, place.id) }
    }
}

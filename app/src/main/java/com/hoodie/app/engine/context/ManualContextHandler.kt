package com.hoodie.app.engine.context

import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType

/** Internal handler; synchronization belongs exclusively to ContextEngine. */
internal class ManualContextHandler(private val processor: ContextSignalProcessor) {
    suspend fun setManual(type: UserContextType): Unit = with(processor) {
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

    suspend fun setManualPlace(placeType: PlaceType): Unit = with(processor) {
        val now = clock.nowMillis()
        // A escolha manual é explícita: horário e rotina nunca a reinterpretam.
        val type = placeType.toContext()
        val placeId = places.firstOfType(placeType)?.id
        scheduler.cancelChecks()
        switchTo(type, now, 1f, placeId, ContextSource.MANUAL, TransitionReason.MANUAL, venueType = placeType)
        recordConfirmation(type, placeId, now, accepted = true)
    }

    suspend fun correctPlaceNow(placeType: PlaceType): Unit = with(processor) {
        val now = clock.nowMillis()
        val type = placeType.toContext()
        val placeId = places.firstOfType(placeType)?.id
        scheduler.cancelChecks()
        val current = contextDao.current()
        switchTo(type, now, 1f, placeId, ContextSource.USER_CORRECTION, TransitionReason.MANUAL, venueType = placeType)
        recordConfirmation(type, placeId, now, accepted = true)
        hoodie.resolve()
    }

    suspend fun correctCurrentPlace(expectedEventId: Long?, placeType: PlaceType): Boolean = with(processor) {
        val current = contextDao.current()
        if (current?.id != expectedEventId) return@with false
        val now = clock.nowMillis()
        val type = placeType.toContext()
        val placeId = places.firstOfType(placeType)?.id
        scheduler.cancelChecks()
        if (current == null) switchTo(type, now, 1f, placeId, ContextSource.USER_CORRECTION, TransitionReason.MANUAL, venueType = placeType)
        else switchTo(type, now, 1f, placeId, ContextSource.USER_CORRECTION, TransitionReason.MANUAL, venueType = placeType)
        recordConfirmation(type, placeId, now, accepted = true)
        hoodie.resolve()
        true
    }
}

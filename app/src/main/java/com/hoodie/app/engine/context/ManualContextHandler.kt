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
}

package com.hoodie.app.engine.context

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.engine.routine.RoutineEngine

/** Internal handler; synchronization belongs exclusively to ContextEngine. */
internal class RoutineFallbackHandler(private val processor: ContextSignalProcessor) {
    suspend fun applyRoutineFallbackIfNeeded(): Unit = with(processor) {
        val now = clock.nowMillis()
        val usable = location.permissionState().canMonitorGeofences && places.all().isNotEmpty()
        val current = contextDao.current()
        if (usable && current != null) return@with
        val zoned = clock.now()
        val probable = RoutineEngine.probableContext(zoned, routines.get(), routines.isDayOff(zoned.toLocalDate()))
        val shouldSwitch = current == null ||
            (current.source == ContextSource.ROUTINE && current.type != probable) ||
            // Sem geofence nada mais vai mudar o contexto: qualquer evento antigo (manual,
            // geofence de antes da permissão sumir) cede à rotina depois do tempo de espera.
            (!usable && current.source != ContextSource.ROUTINE && now - current.startedAt > HoodieConfig.MANUAL_HOLD_MS && current.type != probable)
        if (shouldSwitch) switchTo(probable, now, ROUTINE_CONFIDENCE, null, ContextSource.ROUTINE, TransitionReason.ROUTINE_FALLBACK)
    }
    companion object { private const val ROUTINE_CONFIDENCE = 0.3f }

}

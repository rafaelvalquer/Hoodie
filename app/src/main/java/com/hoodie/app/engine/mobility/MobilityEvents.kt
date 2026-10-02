package com.hoodie.app.engine.mobility

import com.hoodie.app.core.mobility.MovementMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eventos de mobilidade para quem quiser reagir sem conhecer o sensor
 * (HoodieEngine hoje; Diário, Memórias, Equilíbrio e Pomodoro no futuro).
 */
sealed interface MobilityEvent {
    val sessionId: Long
    val at: Long

    data class MobilityStarted(override val sessionId: Long, override val at: Long, val originPlaceId: Long?, val mode: MovementMode) : MobilityEvent
    data class MovementModeChanged(override val sessionId: Long, override val at: Long, val from: MovementMode, val to: MovementMode) : MobilityEvent
    data class KnownPlaceApproaching(override val sessionId: Long, override val at: Long, val placeId: Long) : MobilityEvent
    data class PlaceArrived(override val sessionId: Long, override val at: Long, val placeId: Long?, val autoConfirmed: Boolean) : MobilityEvent
    data class MobilityEnded(override val sessionId: Long, override val at: Long, val destinationPlaceId: Long?, val durationMs: Long) : MobilityEvent
}

/** Barramento em memória (sem replay): os eventos persistentes estão no banco. */
@Singleton
class MobilityEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<MobilityEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<MobilityEvent> = _events.asSharedFlow()

    /** Nunca suspende quem emite (receiver com tempo curto). */
    fun emit(e: MobilityEvent) { _events.tryEmit(e) }
}

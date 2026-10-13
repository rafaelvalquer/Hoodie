package com.hoodie.app.engine.notification

import com.hoodie.app.core.database.IntelligenceDao
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MobilityPhase
import com.hoodie.app.data.repository.MobilityRepository
import com.hoodie.app.domain.daystate.DayStateSnapshot
import com.hoodie.app.engine.daystate.toSnapshot
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DeliverySignals(val vehicleMoving: Boolean, val dayState: DayStateSnapshot?)

/** Estado do usuário que a política precisa. Interface para os testes não dependerem do banco. */
fun interface DeliverySignalSource {
    suspend fun signals(): DeliverySignals
}

class DefaultDeliverySignalSource @Inject constructor(
    private val mobility: MobilityRepository,
    private val intelligence: IntelligenceDao,
) : DeliverySignalSource {
    override suspend fun signals(): DeliverySignals {
        val m = mobility.visualObservation.first()
        // Mesma regra da Home: veículo em movimento de verdade, não parado num semáforo.
        val moving = m.mode?.isVehicle == true && m.phase != MobilityPhase.IDLE && m.observedMovement != DetectedMovement.STILL
        return DeliverySignals(moving, runCatching { intelligence.observeDayState().first()?.toSnapshot() }.getOrNull())
    }
}
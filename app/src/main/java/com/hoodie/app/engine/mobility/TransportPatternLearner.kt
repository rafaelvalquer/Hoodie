package com.hoodie.app.engine.mobility

import com.hoodie.app.core.database.*
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.routine.routineDayGroup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportPatternLearner @Inject constructor(private val db: HoodieDatabase, private val transactions: TransactionRunner, private val clock: ClockProvider) {
    suspend fun record(session: MobilitySessionEntity, accepted: MovementMode, rejected: MovementMode? = null) = transactions.run {
        val origin = session.originPlaceId ?: return@run
        val destination = session.destinationPlaceId ?: return@run
        val local = java.time.Instant.ofEpochMilli(session.startedAt).atZone(clock.zone())
        val group = routineDayGroup(local.dayOfWeek)
        val bucket = (local.hour * 60 + local.minute) / 30
        suspend fun change(mode: MovementMode, confirmed: Boolean) {
            if (!mode.isVehicle || mode == MovementMode.VEHICLE_UNKNOWN) return
            val old = db.intelligenceDao().transportPatterns(origin, destination, group, bucket).firstOrNull { it.mode == mode.name }
                ?: TransportPatternEntity(origin, destination, group, bucket, mode.name, 0, 0, 0f, clock.nowMillis())
            val confirmations = old.confirmations + if (confirmed) 1 else 0
            val rejections = old.rejections + if (confirmed) 0 else 1
            db.intelligenceDao().saveTransportPattern(old.copy(confirmations = confirmations, rejections = rejections,
                confidence = (confirmations + 1f) / (confirmations + rejections + 2f), updatedAt = clock.nowMillis()))
        }
        rejected?.takeIf { it != accepted }?.let { change(it, false) }
        change(accepted, true)
    }
}

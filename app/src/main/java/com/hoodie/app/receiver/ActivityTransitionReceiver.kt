package com.hoodie.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.hoodie.app.core.database.DatabaseGate
import com.hoodie.app.core.mobility.ActivityRecognitionProvider
import com.hoodie.app.core.mobility.MovementObservation
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.engine.mobility.MobilityEngine
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private val mobilityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Transições de atividade (andando, parado, em veículo…) vindas do Google Play Services,
 * mesmo com o app fechado. Só o tipo e a hora entram no app — nada de posição.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {
    @Inject lateinit var mobilityEngine: Lazy<MobilityEngine>
    @Inject lateinit var gate: DatabaseGate
    @Inject lateinit var clock: ClockProvider

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        // elapsedRealTimeNanos → relógio de parede do app.
        val nowWall = clock.nowMillis()
        val nowElapsed = SystemClock.elapsedRealtimeNanos()
        val observations = result.transitionEvents.map { e ->
            val at = nowWall - (nowElapsed - e.elapsedRealTimeNanos) / 1_000_000
            MovementObservation(
                activity = ActivityRecognitionProvider.map(e.activityType),
                confidence = 100,
                timestamp = at.coerceAtMost(nowWall),
                entering = e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER,
            )
        }.sortedBy { it.timestamp }
        val pending = goAsync()
        mobilityScope.launch {
            try {
                if (!gate.isReady()) return@launch
                observations.forEach { mobilityEngine.get().onMovement(it) }
            } finally {
                pending.finish()
            }
        }
    }
}

package com.hoodie.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.worker.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Executa trabalho suspenso dentro do tempo de vida estendido do receiver. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } finally { pending.finish() }
    }
}

/** ENTER / EXIT / DWELL vindos do sistema. Só o id do lugar entra no app. */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {
    @Inject lateinit var contextEngine: ContextEngine
    @Inject lateinit var clock: ClockProvider

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceTransition.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceTransition.EXIT
            Geofence.GEOFENCE_TRANSITION_DWELL -> GeofenceTransition.DWELL
            else -> return
        }
        val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }
        val at = clock.nowMillis()
        runAsync { ids.forEach { contextEngine.onGeofence(it, transition, at) } }
    }
}

/**
 * Após reboot/atualização o sistema remove geofences: registramos de novo.
 * Mudança de fuso/hora: tudo é recalculado pelo relógio novo (mesmo ClockProvider
 * para Context Engine, Hoodie, resumo do dia e próximo evento).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var geofences: GeofenceManager
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var hoodie: HoodieEngine
    @Inject lateinit var log: DebugEventLogger

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        runAsync {
            log.log(DebugEventLogger.Category.SYSTEM, action.substringAfterLast('.'))
            if (action in REGISTER_ACTIONS) {
                geofences.registerAll()
                scheduler.schedulePeriodic()
            }
            if (action in TIME_ACTIONS) scheduler.reconcileNow()
            hoodie.resolve()
        }
    }

    companion object {
        val REGISTER_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
        val TIME_ACTIONS = setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)
    }
}

/** Botões Sim/Não das notificações de confirmação. */
@AndroidEntryPoint
class QuestionActionReceiver : BroadcastReceiver() {
    @Inject lateinit var contextEngine: ContextEngine

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_QUESTION, -1)
        if (id < 0) return
        val yes = intent.getBooleanExtra(EXTRA_YES, false)
        runAsync { contextEngine.answerYesNo(id, yes) }
    }

    companion object {
        const val EXTRA_QUESTION = "question_id"
        const val EXTRA_YES = "yes"
    }
}

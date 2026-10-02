package com.hoodie.app.core.mobility

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.receiver.ActivityTransitionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Permissão de reconhecimento de atividade (runtime no Android 10+). */
enum class ActivityRecognitionPermissionState {
    GRANTED,
    DENIED;

    val granted: Boolean get() = this == GRANTED
}

/** O que as engines precisam saber sobre a permissão (fake nos testes). */
interface ActivityRecognitionPermissions {
    fun state(): ActivityRecognitionPermissionState
}

/**
 * Fonte do estado da permissão. Abaixo do Android 10 a permissão é de instalação
 * (com.google.android.gms.permission.ACTIVITY_RECOGNITION) e já vem concedida.
 */
@Singleton
class ActivityRecognitionPermissionManager @Inject constructor(@ApplicationContext private val context: Context) : ActivityRecognitionPermissions {
    private val _state = MutableStateFlow(compute())
    val stateFlow: StateFlow<ActivityRecognitionPermissionState> = _state.asStateFlow()

    override fun state(): ActivityRecognitionPermissionState = compute().also { _state.value = it }

    fun refresh() = state()

    /** Permissão a pedir em runtime (null abaixo do Android 10). */
    val runtimePermission: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACTIVITY_RECOGNITION else null

    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun compute(): ActivityRecognitionPermissionState {
        val perm = runtimePermission ?: return ActivityRecognitionPermissionState.GRANTED
        return if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) ActivityRecognitionPermissionState.GRANTED
        else ActivityRecognitionPermissionState.DENIED
    }
}

/** Liga/desliga o recebimento de transições de atividade (fake nos testes). */
interface ActivityRecognitionRegistrar {
    /** true quando o sistema aceitou o registro. */
    suspend fun register(): Boolean
    suspend fun unregister()
}

/**
 * Activity Transition API: o sistema avisa quando a pessoa COMEÇA ou PARA de andar,
 * correr, pedalar, ficar parada ou estar num veículo. Sem GPS contínuo, sem polling —
 * os eventos chegam no [ActivityTransitionReceiver] mesmo com o app fechado.
 */
@Singleton
class ActivityRecognitionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: ActivityRecognitionPermissionManager,
    private val log: DebugEventLogger,
) : ActivityRecognitionRegistrar {

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, ActivityTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun register(): Boolean {
        if (!permissions.state().granted) return false
        return runCatching {
            ActivityRecognition.getClient(context).requestActivityTransitionUpdates(request(), pendingIntent).await()
            log.log(DebugEventLogger.Category.SYSTEM, "Activity Recognition registrado")
            true
        }.getOrElse {
            log.log(DebugEventLogger.Category.SYSTEM, "Activity Recognition falhou: ${it.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun unregister() {
        runCatching { ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent).await() }
    }

    private fun request(): ActivityTransitionRequest {
        val types = listOf(DetectedActivity.STILL, DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_BICYCLE, DetectedActivity.IN_VEHICLE)
        val transitions = types.flatMap { type ->
            listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                ActivityTransition.Builder().setActivityType(type).setActivityTransition(it).build()
            }
        }
        return ActivityTransitionRequest(transitions)
    }

    companion object {
        private const val REQUEST_CODE = 4_201

        /** Tipo do Google Play Services → modelo do Hoodie. */
        fun map(type: Int): DetectedMovement = when (type) {
            DetectedActivity.STILL -> DetectedMovement.STILL
            DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> DetectedMovement.WALKING
            DetectedActivity.RUNNING -> DetectedMovement.RUNNING
            DetectedActivity.ON_BICYCLE -> DetectedMovement.ON_BICYCLE
            DetectedActivity.IN_VEHICLE -> DetectedMovement.IN_VEHICLE
            else -> DetectedMovement.UNKNOWN
        }
    }
}

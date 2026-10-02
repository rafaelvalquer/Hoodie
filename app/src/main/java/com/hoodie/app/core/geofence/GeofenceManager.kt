package com.hoodie.app.core.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.receiver.GeofenceReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** O que telas e engines precisam: reaplicar os geofences após mudar lugares (fake nos testes). */
interface GeofenceRegistrar {
    val lastResult: StateFlow<GeofenceRegistrationResult?>
    suspend fun registerAll(): GeofenceRegistrationResult
    suspend fun clear()
}

/**
 * Registra uma região circular por lugar conhecido. O sistema avisa ENTER/EXIT/DWELL
 * sem o app manter GPS ligado — é isso que economiza bateria e preserva privacidade.
 * Geofences somem após reboot ou limpeza de dados do Play Services, por isso
 * [registerAll] é chamado no boot e periodicamente.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val places: PlaceRepository,
    private val location: LocationSource,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
) : GeofenceRegistrar {
    private val client by lazy { LocationServices.getGeofencingClient(context) }

    private val _lastResult = MutableStateFlow<GeofenceRegistrationResult?>(null)
    override val lastResult: StateFlow<GeofenceRegistrationResult?> = _lastResult.asStateFlow()

    private val pendingIntent: PendingIntent by lazy { pendingIntent(context) }

    @SuppressLint("MissingPermission")
    override suspend fun registerAll(): GeofenceRegistrationResult {
        val now = clock.nowMillis()
        val all = places.all()
        val permission = location.permissionState()
        val result = when {
            permission == LocationPermissionState.LOCATION_DISABLED ->
                GeofenceRegistrationResult.failure(all.size, GeofenceRegistrationError.LOCATION_DISABLED, now)
            !permission.canMonitorGeofences ->
                GeofenceRegistrationResult.failure(all.size, GeofenceRegistrationError.PERMISSION_DENIED, now)
            else -> register(all, now)
        }
        _lastResult.value = result
        log.log(DebugEventLogger.Category.GEOFENCE, describe(result))
        return result
    }

    @SuppressLint("MissingPermission")
    private suspend fun register(all: List<com.hoodie.app.core.model.Place>, now: Long): GeofenceRegistrationResult {
        val selected = GeofenceSelectionPolicy.select(all)
        return try {
            runCatching { client.removeGeofences(pendingIntent).await() }
            if (selected.isEmpty()) return GeofenceRegistrationResult(all.size, 0, all.size, null, emptySet(), now)
            val fences = selected.map { p ->
                Geofence.Builder()
                    .setRequestId(p.id.toString())
                    .setCircularRegion(p.latitude, p.longitude, p.radiusMeters)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
                    .setLoiteringDelay(HoodieConfig.GEOFENCE_LOITERING_MS)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build()
            client.addGeofences(request, pendingIntent).await()
            GeofenceRegistrationResult(all.size, selected.size, all.size - selected.size, null, selected.map { it.id }.toSet(), now)
        } catch (e: ApiException) {
            GeofenceRegistrationResult.failure(all.size, GeofenceRegistrationError.fromStatusCode(e.statusCode), now)
        } catch (e: SecurityException) {
            GeofenceRegistrationResult.failure(all.size, GeofenceRegistrationError.PERMISSION_DENIED, now)
        } catch (e: IllegalStateException) {
            GeofenceRegistrationResult.failure(all.size, GeofenceRegistrationError.PLAY_SERVICES_ERROR, now)
        }
    }

    override suspend fun clear() {
        runCatching { client.removeGeofences(pendingIntent).await() }
        _lastResult.value = null
    }

    private fun describe(r: GeofenceRegistrationResult) =
        if (r.ok) "REGISTER ${r.registered}/${r.requested} (skipped ${r.skipped})" else "REGISTER FAILED ${r.error}"

    companion object {
        const val LOITERING_MS = HoodieConfig.GEOFENCE_LOITERING_MS
        const val DEFAULT_RADIUS = HoodieConfig.DEFAULT_GEOFENCE_RADIUS_M

        fun pendingIntent(context: Context): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(context, 0, Intent(context, GeofenceReceiver::class.java), flags)
        }

        /** Remove todos os geofences sem precisar do banco (recuperação / apagar tudo). */
        suspend fun removeAll(context: Context) {
            runCatching { LocationServices.getGeofencingClient(context).removeGeofences(pendingIntent(context)).await() }
        }
    }
}

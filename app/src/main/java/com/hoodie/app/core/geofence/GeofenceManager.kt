package com.hoodie.app.core.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.hoodie.app.core.location.LocationProvider
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.receiver.GeofenceReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

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
    private val location: LocationProvider,
) {
    private val client by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(context, 0, Intent(context, GeofenceReceiver::class.java), flags)
    }

    @SuppressLint("MissingPermission")
    suspend fun registerAll(): Boolean {
        if (!location.hasForeground()) return false
        return runCatching {
            runCatching { client.removeGeofences(pendingIntent).await() }
            val fences = places.all().map { p ->
                Geofence.Builder()
                    .setRequestId(p.id.toString())
                    .setCircularRegion(p.latitude, p.longitude, p.radiusMeters)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
                    .setLoiteringDelay(LOITERING_MS)
                    .build()
            }
            if (fences.isEmpty()) return@runCatching true
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build()
            client.addGeofences(request, pendingIntent).await()
            true
        }.getOrDefault(false)
    }

    suspend fun clear() {
        runCatching { client.removeGeofences(pendingIntent).await() }
    }

    companion object {
        const val LOITERING_MS = 5 * 60 * 1000
        /** Raio padrão: 100–200 m absorve a imprecisão do GPS sem pegar o quarteirão inteiro. */
        const val DEFAULT_RADIUS = 150f
    }
}

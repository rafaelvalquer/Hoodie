package com.hoodie.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class LocationStatus { OK, NO_PERMISSION, NO_BACKGROUND, DISABLED }

/**
 * Localização pontual, nunca contínua. Usada só para: cadastrar um lugar, e
 * conferir rapidamente um deslocamento longo. Não guarda histórico de posição.
 */
@Singleton
class LocationProvider @Inject constructor(@ApplicationContext private val context: Context) {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasForeground(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackground(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else hasForeground()

    fun isEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    fun status(): LocationStatus = when {
        !hasForeground() -> LocationStatus.NO_PERMISSION
        !isEnabled() -> LocationStatus.DISABLED
        !hasBackground() -> LocationStatus.NO_BACKGROUND
        else -> LocationStatus.OK
    }

    @SuppressLint("MissingPermission")
    suspend fun current(): Pair<Double, Double>? {
        if (!hasForeground() || !isEnabled()) return null
        return runCatching {
            withTimeoutOrNull(20_000) {
                val cts = CancellationTokenSource()
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}

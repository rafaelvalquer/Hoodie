package com.hoodie.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class LocationStatus { OK, NO_PERMISSION, NO_BACKGROUND, DISABLED }

/** Uma leitura pontual da posição atual (null sem permissão/sinal). */
interface CurrentPosition {
    suspend fun current(): Pair<Double, Double>?
}

/** O que as engines precisam saber da localização (fake nos testes). */
interface LocationSource : CurrentPosition {
    fun permissionState(): LocationPermissionState

    /** Explicit, speed-only sample used by transport classification. */
    suspend fun currentSpeed(): SpeedObservation? = null
}

/** Ephemeral point sample; latitude/longitude never leave LocationProvider. */
data class SpeedObservation(val metersPerSecond: Float, val observedAt: Long)

/**
 * Localização pontual, nunca contínua. Usada só para: cadastrar um lugar, e
 * conferir rapidamente um deslocamento longo. Não guarda histórico de posição.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: LocationPermissionManager,
) : LocationSource {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun permissionState(): LocationPermissionState = permissions.current()

    fun hasForeground(): Boolean = permissions.hasForeground()

    fun hasBackground(): Boolean = permissions.hasBackground()

    fun isEnabled(): Boolean = permissions.isEnabled()

    fun status(): LocationStatus = permissionState().toStatus()

    @SuppressLint("MissingPermission")
    override suspend fun current(): Pair<Double, Double>? {
        if (!permissionState().canReadPosition) return null
        return runCatching {
            withTimeoutOrNull(POSITION_TIMEOUT_MS) {
                val cts = CancellationTokenSource()
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentSpeed(): SpeedObservation? {
        if (!permissionState().canReadPosition) return null
        return runCatching {
            withTimeoutOrNull(POSITION_TIMEOUT_MS) {
                val cts = CancellationTokenSource()
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }?.takeIf { it.hasSpeed() && it.speed.isFinite() && it.speed in 0f..100f }
                ?.let { SpeedObservation(it.speed, it.time) }
        }.getOrNull()
    }

    private companion object {
        const val POSITION_TIMEOUT_MS = 20_000L
    }
}

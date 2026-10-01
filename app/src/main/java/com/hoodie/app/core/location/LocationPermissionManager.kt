package com.hoodie.app.core.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fonte única do estado de permissão de localização. Telas chamam [refresh] no
 * onResume (o usuário pode ter voltado das configurações do sistema).
 */
@Singleton
class LocationPermissionManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<LocationPermissionState> = _state.asStateFlow()

    fun current(): LocationPermissionState = compute().also { _state.value = it }

    fun refresh(): LocationPermissionState = current()

    fun hasFine(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasForeground(): Boolean = hasFine() || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackground(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else hasForeground()

    fun isEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /** Tela de detalhes do app, onde fica "Localização → Permitir o tempo todo". */
    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun locationSettingsIntent(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun compute(): LocationPermissionState = LocationPermissionState.resolve(
        fine = hasFine(),
        coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
        background = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        enabled = isEnabled(),
        sdk = Build.VERSION.SDK_INT,
    )

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}

package com.hoodie.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.location.LocationPermissionManager
import com.hoodie.app.core.location.LocationPermissionState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** O estado calculado bate com as permissões reais do aparelho (sem alterá-las). */
@RunWith(AndroidJUnit4::class)
class LocationPermissionManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    @Test
    fun estadoReflete_asPermissoesDoSistema() {
        val manager = LocationPermissionManager(context)
        val expected = LocationPermissionState.resolve(
            fine = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            background = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            enabled = manager.isEnabled(),
            sdk = Build.VERSION.SDK_INT,
        )
        assertEquals(expected, manager.refresh())
        assertEquals(expected, manager.state.value)
    }

    @Test
    fun intentDeConfiguracoesApontaParaOApp() {
        val intent = LocationPermissionManager(context).appSettingsIntent()
        assertEquals("package:${context.packageName}", intent.data.toString())
    }
}

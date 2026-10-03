package com.hoodie.app.engine

import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.location.GeofenceStatusCodes
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.geofence.GeofenceRegistrationError
import com.hoodie.app.core.geofence.GeofenceRegistrationResult
import com.hoodie.app.core.geofence.GeofenceSelectionPolicy
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.screens.settings.locationSummary
import com.hoodie.app.presentation.common.UiText
import com.hoodie.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAndGeofenceRulesTest {

    private fun p(id: Long, type: PlaceType, visits: Int = 0, last: Long? = null, fav: Boolean = false) =
        Place(id, "p$id", type, 0.0, 0.0, 150f, visits, 0, last, fav)

    @Test
    fun `prioridade Casa Trabalho Academia favoritos confirmacoes recencia`() {
        val places = listOf(
            p(1, PlaceType.OTHER, visits = 1, last = 10),
            p(2, PlaceType.OTHER, visits = 1, last = 20),
            p(3, PlaceType.OTHER, visits = 9),
            p(4, PlaceType.RESTAURANT, fav = true),
            p(5, PlaceType.GYM),
            p(6, PlaceType.WORK),
            p(7, PlaceType.HOME),
        )
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L, 2L, 1L), GeofenceSelectionPolicy.select(places).map { it.id })
    }

    @Test
    fun `limite de geofences ativos deixa margem e mantem os essenciais`() {
        val many = (1L..150L).map { p(it, PlaceType.OTHER, last = it) } + p(500, PlaceType.HOME) + p(501, PlaceType.WORK)
        val selected = GeofenceSelectionPolicy.select(many)
        assertEquals(HoodieConfig.MAX_ACTIVE_GEOFENCES, selected.size)
        assertTrue(HoodieConfig.MAX_ACTIVE_GEOFENCES < 100)
        assertEquals(listOf(500L, 501L), selected.take(2).map { it.id })
        // Entre os comuns, os visitados mais recentemente ficam.
        assertTrue(selected.any { it.id == 150L })
        assertFalse(selected.any { it.id == 1L })
    }

    @Test
    fun `codigos do Play Services viram erros do Hoodie`() {
        val map = GeofenceRegistrationError.Companion::fromStatusCode
        assertEquals(GeofenceRegistrationError.GEOFENCE_NOT_AVAILABLE, map(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE))
        assertEquals(GeofenceRegistrationError.GEOFENCE_TOO_MANY_GEOFENCES, map(GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES))
        assertEquals(GeofenceRegistrationError.GEOFENCE_TOO_MANY_PENDING_INTENTS, map(GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS))
        assertEquals(GeofenceRegistrationError.PERMISSION_DENIED, map(GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION))
        assertEquals(GeofenceRegistrationError.PLAY_SERVICES_ERROR, map(CommonStatusCodes.API_NOT_CONNECTED))
        assertEquals(GeofenceRegistrationError.UNKNOWN, map(-12345))
    }

    @Test
    fun `estado de permissao a partir das permissoes do sistema`() {
        val r = LocationPermissionState.Companion::resolve
        assertEquals(LocationPermissionState.NONE, r(false, false, false, true, 34))
        assertEquals(LocationPermissionState.LOCATION_DISABLED, r(true, true, true, false, 34))
        assertEquals(LocationPermissionState.APPROXIMATE_ONLY, r(false, true, true, true, 34))
        assertEquals(LocationPermissionState.FOREGROUND, r(true, true, false, true, 34))
        assertEquals(LocationPermissionState.BACKGROUND, r(true, true, true, true, 34))
        // Android 9: não existe permissão separada de segundo plano.
        assertEquals(LocationPermissionState.BACKGROUND, r(true, true, false, true, 28))
    }

    @Test
    fun `so BACKGROUND monitora geofences`() {
        LocationPermissionState.entries.forEach {
            assertEquals(it == LocationPermissionState.BACKGROUND, it.canMonitorGeofences)
        }
        assertTrue(LocationPermissionState.FOREGROUND.needsBackgroundStep)
        assertEquals(LocationStatus.NO_BACKGROUND, LocationPermissionState.FOREGROUND.toStatus())
        assertEquals(LocationStatus.OK, LocationPermissionState.BACKGROUND.toStatus())
    }

    @Test
    fun `Ajustes mostra locais monitorados ou o motivo da pausa`() {
        val ok = GeofenceRegistrationResult(4, 4, 0, null)
        assertEquals(UiText.Quantity(R.plurals.location_places_monitored, 4, listOf(4)), locationSummary(LocationPermissionState.BACKGROUND, ok))
        val skipped = GeofenceRegistrationResult(100, 95, 5, null)
        assertEquals(UiText.Resource(R.string.location_places_skipped, listOf(95, 5)), locationSummary(LocationPermissionState.BACKGROUND, skipped))
        assertEquals(UiText.Resource(R.string.location_foreground_summary), locationSummary(LocationPermissionState.FOREGROUND, ok))
        val failed = GeofenceRegistrationResult.failure(3, GeofenceRegistrationError.GEOFENCE_NOT_AVAILABLE, 0)
        assertEquals(UiText.Resource(R.string.location_paused_reason, listOf(UiText.Resource(R.string.location_geofence_unavailable))), locationSummary(LocationPermissionState.BACKGROUND, failed))
    }
}

package com.hoodie.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.R
import com.hoodie.app.presentation.common.UiText
import com.hoodie.app.presentation.common.resolve
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiTextResourceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun monitoredPlacesUsesQuantity() {
        assertEquals("✅ 1 local monitorado", UiText.Quantity(R.plurals.location_places_monitored, 1, listOf(1)).resolve(context))
        assertEquals("✅ 2 locais monitorados", UiText.Quantity(R.plurals.location_places_monitored, 2, listOf(2)).resolve(context))
    }
    @Test fun nestedReasonResolvesAndKeepsLineBreak() {
        assertEquals("⚠️ Geofences pausados\nServiço de geofence indisponível no aparelho", UiText.Resource(R.string.location_paused_reason, listOf(UiText.Resource(R.string.location_geofence_unavailable))).resolve(context))
    }
    @Test fun formattedLocationSummaryKeepsArguments() {
        assertEquals("✅ 95 locais monitorados · 5 fora do limite", UiText.Resource(R.string.location_places_skipped, listOf(95, 5)).resolve(context))
        assertEquals("⚠️ Geofences pausados\nLocalização em segundo plano desativada", UiText.Resource(R.string.location_foreground_summary).resolve(context))
    }
}

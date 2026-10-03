package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.R
import com.hoodie.app.presentation.common.GeofenceFeedbackHost
import com.hoodie.app.presentation.common.LocalGeofenceWarning
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeofenceFeedbackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun retrySurvivesLeavingPickerAndDoesNotSaveAgain() {
        var picker by mutableStateOf(true)
        var saves = 0
        var registrations = 0
        rule.setContent {
            HoodieTheme {
                GeofenceFeedbackHost {
                    val notify = requireNotNull(LocalGeofenceWarning.current)
                    if (picker) PixelButton("Salvar", {
                        saves++
                        notify { registrations++; true }
                        picker = false
                    }) else Text("Destino")
                }
            }
        }
        rule.onNodeWithText("SALVAR").performClick()
        rule.onNodeWithText("Destino").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.geofence_retry)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.geofences_registered)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, saves); assertEquals(1, registrations) }
    }

    @Test fun failedRegistrationCanBeRetriedAgain() {
        var registrations = 0
        rule.setContent {
            HoodieTheme {
                GeofenceFeedbackHost {
                    val notify = requireNotNull(LocalGeofenceWarning.current)
                    PixelButton("Avisar", { notify { registrations++; registrations >= 2 } })
                }
            }
        }
        rule.onNodeWithText("AVISAR").performClick()
        val retry = rule.activity.getString(R.string.geofence_retry)
        rule.onNodeWithText(retry).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.error_geofence_retry)).assertIsDisplayed()
        rule.onNodeWithText(retry).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.geofences_registered)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(2, registrations) }
    }
}

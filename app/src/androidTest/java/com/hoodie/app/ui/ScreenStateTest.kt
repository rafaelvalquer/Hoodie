package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.R
import com.hoodie.app.core.datastore.AppSettings
import com.hoodie.app.core.error.DatabaseError
import com.hoodie.app.core.deviceusage.UsagePermissionState
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.mobility.ActivityRecognitionPermissionState
import com.hoodie.app.presentation.common.appErrorText
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelRenderFrame
import com.hoodie.app.presentation.screens.home.HomeActions
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.presentation.screens.settings.SettingsActions
import com.hoodie.app.presentation.screens.settings.SettingsContent
import com.hoodie.app.presentation.screens.phoneinsights.UsagePermissionScreen
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/** Production content: recoverable read errors and optional restricted-settings help. */
@RunWith(AndroidJUnit4::class)
class ScreenStateTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(resource: Int) = context.getString(resource)

    @Test fun homeReadFailureShowsLocalizedRetry() {
        var retries = 0
        rule.setContent { HoodieTheme { HomeContent(HomeUiState(loading = false, error = DatabaseError.ReadFailed), false, ZoneId.of("UTC"), {}, actions = HomeActions(retryLoad = { retries++ })) } }
        rule.onNodeWithText(context.appErrorText(DatabaseError.ReadFailed)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.place_retry_load).uppercase()).performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }
    @Test fun settingsReadFailureShowsLocalizedRetry() {
        var retries = 0
        rule.setContent { HoodieTheme { SettingsContent(AppSettings(), error = DatabaseError.ReadFailed,
            permission = LocationPermissionState.NONE, geofenceResult = null, usagePermission = UsagePermissionState.DENIED,
            activityPermission = ActivityRecognitionPermissionState.GRANTED, onOpen = {}, actions = SettingsActions(retryLoad = { retries++ })) } }
        rule.onNodeWithText(context.appErrorText(DatabaseError.ReadFailed)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.place_retry_load).uppercase()).performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }
    @Test fun optionalRestrictedHelpExplainsStepsAndOpensAppDetails() {
        var opens = 0
        rule.setContent { HoodieTheme { CompositionLocalProvider(LocalPixelRenderFrame provides PixelRenderFrame()) {
            UsagePermissionScreen(UsagePermissionState.DENIED, {}, Modifier.verticalScroll(rememberScrollState()), onOpenAppDetails = { opens++ })
        } } }
        rule.onNodeWithText(label(R.string.usage_restricted_help).uppercase()).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.usage_restricted_title)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.usage_restricted_steps)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.usage_open_app_details).uppercase()).performClick()
        rule.runOnIdle { assertEquals(1, opens) }
    }
    @Test fun unavailablePermissionExplainsFailureWithoutActivationAction() {
        rule.setContent { HoodieTheme { CompositionLocalProvider(LocalPixelRenderFrame provides PixelRenderFrame()) {
            UsagePermissionScreen(UsagePermissionState.UNAVAILABLE, {}, Modifier.verticalScroll(rememberScrollState()))
        } } }
        rule.onNodeWithText(label(R.string.ui_usage_permission_screen_4)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(label(R.string.ui_usage_permission_screen_5).uppercase()).assertDoesNotExist()
    }
}

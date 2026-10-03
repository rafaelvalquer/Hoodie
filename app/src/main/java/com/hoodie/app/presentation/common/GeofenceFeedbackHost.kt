package com.hoodie.app.presentation.common

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.error.PlaceError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The root owns the notification, so leaving the picker does not remove its retry action. */
val LocalGeofenceWarning = staticCompositionLocalOf<((suspend () -> Boolean) -> Unit)?> { null }

@Composable
fun GeofenceFeedbackHost(bottomPadding: Dp = 0.dp, content: @Composable () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val requests = remember { Channel<suspend () -> Boolean>(Channel.BUFFERED) }
    val events = remember(requests) { requests.receiveAsFlow() }
    val scope = rememberCoroutineScope()
    val initialText by rememberUpdatedState(stringResource(R.string.error_geofence_registration))
    val failureText by rememberUpdatedState(stringResource(R.string.error_geofence_retry))
    val successText by rememberUpdatedState(stringResource(R.string.geofences_registered))
    val retryLabel by rememberUpdatedState(stringResource(R.string.geofence_retry))
    val notify = remember(requests) { { retry: suspend () -> Boolean -> requests.trySend(retry); Unit } }
    CollectUiEvents(events) { retry ->
        scope.launch {
            var message = initialText
            while (snackbar.showSnackbar(message, retryLabel, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                var registered = false
                runUiAction(PlaceError.GeofenceRegistrationFailed, { _, cause ->
                    Log.e("GeofenceFeedbackHost", "Failed to retry geofence registration", cause)
                }) { registered = retry() }
                if (registered) {
                    snackbar.showSnackbar(successText)
                    break
                }
                message = failureText
            }
        }
    }
    CompositionLocalProvider(LocalGeofenceWarning provides notify) {
        Box(Modifier.fillMaxSize()) {
            content()
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = bottomPadding))
        }
    }
}

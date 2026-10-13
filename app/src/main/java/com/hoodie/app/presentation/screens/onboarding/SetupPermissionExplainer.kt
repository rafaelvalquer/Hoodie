package com.hoodie.app.presentation.screens.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hoodie.app.R
import com.hoodie.app.domain.setup.SetupItem

/** Explica o benefício antes de qualquer pedido ao sistema; "Agora não" nunca insiste. */
@Composable
internal fun SetupPermissionExplainer(item: SetupItem, onContinue: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (item) {
        SetupItem.HOME -> R.string.setup_explain_home_title to R.string.setup_explain_home_body
        SetupItem.BACKGROUND_LOCATION -> R.string.setup_explain_bg_title to R.string.setup_explain_bg_body
        SetupItem.WORK -> R.string.setup_explain_work_title to R.string.setup_explain_work_body
        SetupItem.NOTIFICATIONS -> R.string.setup_explain_notif_title to R.string.setup_explain_notif_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.setup_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_not_now)) } },
    )
}
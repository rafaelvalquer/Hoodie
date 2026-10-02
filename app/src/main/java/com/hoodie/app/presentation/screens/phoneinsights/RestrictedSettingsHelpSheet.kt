package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.presentation.components.PixelButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestrictedSettingsHelpSheet(onDismiss: () -> Unit, onOpenAppDetails: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.usage_restricted_title))
            Text(stringResource(R.string.usage_restricted_steps))
            PixelButton(stringResource(R.string.usage_open_app_details), onOpenAppDetails, Modifier.fillMaxWidth())
        }
    }
}

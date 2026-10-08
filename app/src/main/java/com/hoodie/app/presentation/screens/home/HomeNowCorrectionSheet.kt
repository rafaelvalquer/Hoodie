package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.theme.HoodieColors

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
internal fun HomeNowCorrectionSheet(onDismiss: () -> Unit, onSave: (PlaceType, Boolean) -> Unit, saving: Boolean) {
    var selected by remember { mutableStateOf<PlaceType?>(null) }
    var historical by remember { mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HoodieColors.Panel) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.home_now_correct_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
                Text(stringResource(R.string.home_now_correct_scope))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !historical, onClick = { historical = false }, label = { Text(stringResource(R.string.home_now_from_now)) })
                    FilterChip(selected = historical, onClick = { historical = true }, label = { Text(stringResource(R.string.home_now_since_start)) })
                }
            }
            PlaceType.physicalPlaceOptions.forEach { type ->
                FilterChip(modifier = Modifier.fillMaxWidth(), selected = selected == type, onClick = { selected = type }, label = { Text(type.label) })
            }
            PixelButton(stringResource(R.string.home_now_save_correction), { selected?.let { onSave(it, historical) } }, Modifier.fillMaxWidth(), enabled = !saving && selected != null, color = HoodieColors.Gold)
        }
    }
}

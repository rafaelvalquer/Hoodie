package com.hoodie.app.presentation.screens.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.LocalDate

enum class RoutineDayExceptionError { LOAD, WORK_CHECK, SAVE }

data class RoutineDayExceptionUiState(
    val date: LocalDate,
    val isDayOff: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: RoutineDayExceptionError? = null,
    val confirmationRequired: Boolean = false,
)

@Composable
internal fun RoutineDayExceptionSection(
    state: RoutineDayExceptionUiState,
    onDayOffChanged: (Boolean) -> Unit,
    onConfirmWorkWarning: () -> Unit,
    onCancelWorkWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelPanel(modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        SectionLabel(stringResource(R.string.routine_exceptions_title))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.routine_day_off_status, stringResource(if (state.isDayOff) R.string.routine_yes else R.string.routine_no)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.routine_day_off_description), color = HoodieColors.Muted)
            }
            Switch(
                checked = state.isDayOff,
                onCheckedChange = onDayOffChanged,
                enabled = !state.loading && !state.saving && state.error != RoutineDayExceptionError.LOAD,
                modifier = Modifier.testTag(TAG_DAY_OFF_SWITCH),
            )
        }
        if (state.loading || state.saving) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text(stringResource(if (state.saving) R.string.routine_day_off_saving else R.string.routine_day_off_loading))
            }
        }
        state.error?.let { error ->
            Text(
                stringResource(when (error) {
                    RoutineDayExceptionError.LOAD -> R.string.routine_day_off_load_error
                    RoutineDayExceptionError.WORK_CHECK -> R.string.routine_day_off_work_check_error
                    RoutineDayExceptionError.SAVE -> R.string.routine_day_off_save_error
                }),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (state.confirmationRequired) {
        AlertDialog(
            onDismissRequest = onCancelWorkWarning,
            title = { Text(stringResource(R.string.routine_day_off_work_warning_title)) },
            text = { Text(stringResource(R.string.routine_day_off_work_warning_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmWorkWarning, enabled = !state.saving) {
                    Text(stringResource(R.string.routine_day_off_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelWorkWarning) {
                    Text(stringResource(R.string.routine_day_off_cancel))
                }
            },
        )
    }
}

internal const val TAG_DAY_OFF_SWITCH = "routine_day_off_switch"

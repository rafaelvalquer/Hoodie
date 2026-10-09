package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.presentation.theme.HoodieColors

/** Shared compact, single-row speed control for both diary replay surfaces. */
@Composable
internal fun ReplaySpeedSelector(
    selectedSpeed: ReplaySpeed,
    label: String = stringResource(R.string.journey_replay_speed),
    onSpeed: (ReplaySpeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = HoodieColors.Muted,
            maxLines = 1,
            softWrap = false,
        )
        ReplaySpeed.entries.forEach { speed ->
            FilterChip(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton; contentDescription = speed.label },
                selected = selectedSpeed == speed,
                onClick = { onSpeed(speed) },
                label = {
                    Text(speed.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall)
                },
            )
        }
    }
}

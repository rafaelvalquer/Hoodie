package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.diary.journey.DiaryMapMode
import com.hoodie.app.presentation.theme.HoodieColors

/** [JORNADA] [RELÓGIO]: a escolha fica no DataStore e o replay é compartilhado. */
@Composable
fun DiaryMapModeSelector(selected: DiaryMapMode, onSelect: (DiaryMapMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = DiaryMapMode.entries.filter { it != DiaryMapMode.CLOCK || HoodieConfig.DIARY_CLOCK_VIEW }
    if (modes.size < 2) return
    val label = stringResource(R.string.journey_mode_label)
    Row(modifier.fillMaxWidth().border(2.dp, HoodieColors.Outline).semantics { contentDescription = label }) {
        modes.forEach { mode ->
            val on = mode == selected
            val text = stringResource(if (mode == DiaryMapMode.JOURNEY) R.string.journey_mode_journey else R.string.journey_mode_clock)
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (on) HoodieColors.Gold else HoodieColors.Panel)
                    .semantics { this.selected = on; role = Role.Tab }
                    .clickable { onSelect(mode) }
                    .testTag("map_mode_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (on) HoodieColors.Outline else HoodieColors.Muted)
            }
        }
    }
}

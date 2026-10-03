package com.hoodie.app.presentation.screens.places.picker

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.sizeIn
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.theme.HoodieColors

/**
 *     RAIO DO LOCAL                       150 m
 *     75 m ───────────●──────────────── 400 m
 *     [100 m] [150 m] [200 m] [300 m]
 *
 * Os atalhos só mudam o raio quando tocados — nunca automaticamente.
 */
@Composable
fun PlaceRadiusControl(radius: Float, onRadiusChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.ui_place_radius_control_1), Modifier.weight(1f))
            Text("${radius.toInt()} m", style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood, modifier = Modifier.testTag(PlacePickerTags.RADIUS_VALUE))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${PlacePickerState.MIN_RADIUS.toInt()} m", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            Slider(
                radius, onRadiusChange,
                valueRange = PlacePickerState.MIN_RADIUS..PlacePickerState.MAX_RADIUS,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag(PlacePickerTags.RADIUS_SLIDER),
            )
            Text("${PlacePickerState.MAX_RADIUS.toInt()} m", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PlacePickerState.QUICK_RADII.forEach { r ->
                val selected = radius.toInt() == r.toInt()
                Text(
                    "${r.toInt()} m",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = if (selected) HoodieColors.Outline else HoodieColors.Ink,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .border(2.dp, HoodieColors.Outline)
                        .background(if (selected) HoodieColors.Gold else HoodieColors.PanelLight)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onRadiusChange(r) })
                        .semantics { role = Role.RadioButton; this.selected = selected }
                        .wrapContentHeight(Alignment.CenterVertically)
                        .testTag(PlacePickerTags.quickRadius(r.toInt())),
                )
            }
        }
    }
}

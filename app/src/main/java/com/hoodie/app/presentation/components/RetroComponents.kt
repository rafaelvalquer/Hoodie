package com.hoodie.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.formatHm
import com.hoodie.app.pixel.icons.IconLabel
import com.hoodie.app.pixel.icons.PixelIconView
import com.hoodie.app.pixel.icons.PixelSprite
import com.hoodie.app.presentation.theme.HoodieColors
import com.hoodie.app.presentation.theme.HoodieSpacing

/** Painel com contorno escuro e sombra dura deslocada — o "card" do jogo. */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    color: Color = HoodieColors.Panel,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .drawBehind {
                val s = 4.dp.toPx()
                drawRect(HoodieColors.Outline, topLeft = androidx.compose.ui.geometry.Offset(s, s), size = size)
            }
            .background(color)
            .border(2.dp, HoodieColors.Outline)
            .let { if (onClick != null) it.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onClick) else it }
            .padding(14.dp),
        content = content,
    )
}

/** Botão retrô: bloco sólido com contorno e sombra. */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = HoodieColors.Blue,
    textColor: Color = HoodieColors.Outline,
    enabled: Boolean = true,
    leadingIcon: PixelSprite? = null,
) {
    val bg = if (enabled) color else HoodieColors.PanelLight
    Box(
        modifier
            .drawBehind {
                val s = 4.dp.toPx()
                drawRect(HoodieColors.Outline, topLeft = androidx.compose.ui.geometry.Offset(s, s), size = size)
            }
            .background(bg)
            .border(2.dp, HoodieColors.Outline)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = text }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = if (enabled) textColor else HoodieColors.Muted
        if (leadingIcon == null) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = contentColor, textAlign = TextAlign.Center, maxLines = 2)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                PixelIconView(leadingIcon, size = 20.dp, tint = contentColor)
                Spacer(Modifier.width(8.dp))
                Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = contentColor, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = HoodieColors.MutedStrong,
        modifier = modifier.padding(bottom = HoodieSpacing.LabelToValue),
    )
}

/** Barra de necessidade em blocos (10 segmentos). */
@Composable
fun NeedBar(label: String, emoji: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$emoji $label", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(118.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            val filled = (value + 5) / 10
            repeat(10) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(if (i < filled) color else HoodieColors.PanelLight)
                        .border(1.dp, HoodieColors.Outline),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("$value", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
    }
}

/** Campo de horário que abre um TimePicker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, minute: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PixelPanel(modifier, color = HoodieColors.PanelLight, onClick = { open = true }) {
        SectionLabel(label)
        Text(formatHm(minute), style = MaterialTheme.typography.headlineSmall)
    }
    if (open) {
        val state = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
            title = { Text(label) },
            text = { TimePicker(state) },
        )
    }
}

/** Balão de fala do Hoodie. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color(0xFFF6F3EA))
            .border(2.dp, HoodieColors.Outline)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("“$text”", color = HoodieColors.Outline, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, icons: List<PixelSprite?>? = null) {
    androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .background(if (on) HoodieColors.Blue else HoodieColors.PanelLight)
                    .border(2.dp, HoodieColors.Outline)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .semantics { contentDescription = label }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                val contentColor = if (on) HoodieColors.Outline else HoodieColors.Ink
                val icon = icons?.getOrNull(i)
                if (icon == null) Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
                else IconLabel(icon, label, style = MaterialTheme.typography.labelLarge, color = contentColor, iconSize = 18.dp)
            }
        }
    }
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

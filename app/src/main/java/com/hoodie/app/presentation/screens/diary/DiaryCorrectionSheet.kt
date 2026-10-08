package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.correction.CorrectionTargetType
import com.hoodie.app.domain.correction.DiaryCorrection
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryCorrectionSheet(state: DiaryEditState, zone: ZoneId, onDismiss: () -> Unit, onSave: (DiaryCorrection) -> Unit) {
    ModalBottomSheet(onDismissRequest = { if (!state.saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.diary_edit_event), style = MaterialTheme.typography.titleLarge)
            val request = state.request
            if (request != null) {
                val formatter = remember { DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT) }
                val originalStartText = remember(request.startedAt, zone) { Instant.ofEpochMilli(request.startedAt).atZone(zone).format(formatter) }
                val originalEndText = remember(request.endedAt, zone) { request.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(formatter) } ?: "" }
                var context by remember(request.targetType, request.targetId) { mutableStateOf(request.context) }
                var placeId by remember(request.targetType, request.targetId) { mutableStateOf(request.placeId) }
                var mode by remember(request.targetType, request.targetId) { mutableStateOf(request.mode) }
                var start by remember(request.targetType, request.targetId) { mutableStateOf(originalStartText) }
                var end by remember(request.targetType, request.targetId) { mutableStateOf(originalEndText) }
                var inputError by remember { mutableStateOf(false) }
                if (request.targetType == CorrectionTargetType.CONTEXT) EditChoice(stringResource(R.string.diary_edit_context), context?.label ?: "", UserContextType.entries.map { it.label to it }, !state.saving) { context = it }
                EditChoice(stringResource(R.string.diary_edit_place), state.places.firstOrNull { it.id == placeId }?.name ?: stringResource(R.string.diary_edit_no_place),
                    listOf(stringResource(R.string.diary_edit_no_place) to null) + state.places.map { it.name to it.id }, !state.saving) { placeId = it }
                if (request.targetType == CorrectionTargetType.MOBILITY_SEGMENT || context == UserContextType.COMMUTING) EditChoice(stringResource(R.string.diary_edit_transport), mode?.label ?: stringResource(R.string.diary_edit_keep_transport),
                    listOf(stringResource(R.string.diary_edit_keep_transport) to null) + MovementMode.entries.filter { it != MovementMode.NONE }.map { it.label to it }, !state.saving) { mode = it }
                OutlinedTextField(start, { start = it; inputError = false }, label = { Text(stringResource(R.string.diary_edit_start)) }, enabled = !state.saving, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("diary_edit_start"))
                OutlinedTextField(end, { end = it; inputError = false }, label = { Text(stringResource(R.string.diary_edit_end)) }, supportingText = { Text(stringResource(R.string.diary_edit_time_hint)) }, enabled = !state.saving, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("diary_edit_end"))
                if (inputError) Text(stringResource(R.string.diary_edit_invalid_time), color = MaterialTheme.colorScheme.error)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, enabled = !state.saving) { Text(stringResource(R.string.diary_edit_cancel)) }
                    Button(onClick = {
                        try {
                            fun parse(text: String): Long {
                                val local = LocalDateTime.parse(text, formatter)
                                require(zone.rules.getValidOffsets(local).isNotEmpty())
                                return local.atZone(zone).toInstant().toEpochMilli()
                            }
                            onSave(request.copy(context = context, placeId = placeId,
                                mode = if (request.targetType == CorrectionTargetType.MOBILITY_SEGMENT) mode ?: request.mode else if (context == UserContextType.COMMUTING) mode else null,
                                startedAt = if (start == originalStartText) request.startedAt else parse(start),
                                endedAt = if (end == originalEndText) request.endedAt else end.takeIf { it.isNotBlank() }?.let(::parse)))
                        } catch (_: IllegalArgumentException) { inputError = true }
                        catch (_: java.time.format.DateTimeParseException) { inputError = true }
                    }, enabled = !state.saving, modifier = Modifier.testTag("diary_edit_save")) { Text(stringResource(R.string.diary_edit_save)) }
                }
            } else {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.diary_edit_cancel)) }
            }
        }
    }
}

@Composable
private fun <T> EditChoice(label: String, selected: String, options: List<Pair<String, T>>, enabled: Boolean, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label: $selected") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false }) }
        }
    }
}

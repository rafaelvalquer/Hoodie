package com.hoodie.app.presentation.screens.diary

import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DiaryVisitDetails
import com.hoodie.app.pixel.diary.DiaryMapPlaceNode
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/** Visitas de um nó, na ordem do dia (cada uma com o próprio uso do celular). */
fun visitsOfNode(node: DiaryMapPlaceNode, details: List<DiaryVisitDetails>): List<DiaryVisitDetails> =
    node.visitIndices.mapNotNull { i -> details.firstOrNull { it.index == i } }

/**
 * Detalhe de um lugar (nó do mapa): totais e uma seção por visita, com o celular
 * medido DENTRO de cada visita — não o agregado do contexto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailBottomSheet(node: DiaryMapPlaceNode, details: List<DiaryVisitDetails>, zone: ZoneId, onDismiss: () -> Unit) {
    val uiTextContext = LocalContext.current
    val mine = visitsOfNode(node, details)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = HoodieColors.Panel) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${node.type.emoji} ${node.label.uppercase()}", style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
            Text(node.type.label, color = HoodieColors.Muted)
            DetailLine(uiTextContext.getString(R.string.ui_extra_place_detail_bottom_sheet_1), formatDuration(mine.sumOf { it.visit.durationMs }))
            DetailLine(uiTextContext.getString(R.string.ui_extra_place_detail_bottom_sheet_2), mine.size.toString())
            val phoneTotal = mine.sumOf { it.phoneUsage?.foregroundMs ?: 0L }
            if (phoneTotal > 0) DetailLine(uiTextContext.getString(R.string.ui_extra_place_detail_bottom_sheet_3), formatDuration(phoneTotal))
            mine.forEachIndexed { i, d -> VisitDetailSection(i + 1, d, zone) }
            val events = mine.flatMap { it.events }.distinctBy { it.id }.sortedBy { it.timestamp }
            SectionLabel(stringResource(R.string.ui_place_detail_bottom_sheet_1))
            if (events.isEmpty()) Text(stringResource(R.string.ui_place_detail_bottom_sheet_2), color = HoodieColors.Muted)
            else events.forEach { event ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(formatClock(event.timestamp, zone), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Gold, modifier = Modifier.width(48.dp))
                    Text("${event.emoji ?: "📍"} ${event.title}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.ui_place_detail_bottom_sheet_3), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HoodieColors.Muted)
        Text(value, color = HoodieColors.Ink)
    }
}

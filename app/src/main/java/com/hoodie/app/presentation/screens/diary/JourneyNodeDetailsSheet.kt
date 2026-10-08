package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DiaryVisitDetails
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.domain.diary.model.JourneySegment
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/** Linhas do detalhe de uma parada (texto puro, testável). */
data class JourneyDetailLine(val labelRes: Int, val value: String)

object JourneyDetails {
    fun lines(node: JourneyNode, arrivedBy: JourneySegment?, zone: ZoneId, ongoing: String): List<JourneyDetailLine> = buildList {
        add(JourneyDetailLine(R.string.journey_details_arrival, formatClock(node.arrivalAt, zone)))
        add(JourneyDetailLine(R.string.journey_details_departure, node.departureAt?.let { formatClock(it, zone) } ?: ongoing))
        add(JourneyDetailLine(R.string.journey_details_duration, formatDuration(node.durationMs)))
        add(JourneyDetailLine(R.string.journey_details_appearances, node.placeOccurrences.toString()))
        if (node.placeOccurrences > 1) add(JourneyDetailLine(R.string.journey_details_place_total, formatDuration(node.placeTotalMs)))
        node.hoodieActivity?.let { add(JourneyDetailLine(R.string.journey_details_hoodie, "${it.emoji} ${it.label}")) }
        arrivedBy?.let { s ->
            val mode = s.movementMode
            add(JourneyDetailLine(R.string.journey_details_arrived_by, "${mode?.emoji ?: "🧭"} ${mode?.label ?: "—"} · ${formatDuration(s.durationMs)}"))
        }
    }
}

/**
 *     🏢 TRABALHO          Retorno #2
 *     Chegada              13:05
 *     Saída                17:40
 *     Tempo nesta visita   4h35
 *     Vezes no dia         2
 *     Total no dia …       7h48
 *     Hoodie               💻 Trabalhando
 *     Chegou de            🚶 Caminhada · 7 min
 *     Celular nesta visita 22 min  [Teams] [Spotify]
 *     [ VER DETALHES DO LUGAR ]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyNodeDetailsSheet(
    node: JourneyNode,
    arrivedBy: JourneySegment?,
    details: DiaryVisitDetails?,
    zone: ZoneId,
    onSeeAll: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = HoodieColors.Panel) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).testTag("journey_details"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            onEdit?.let { PixelButton(stringResource(R.string.diary_edit_event), it, Modifier.fillMaxWidth()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${node.placeType.emoji} ${node.placeName.uppercase()}", style = MaterialTheme.typography.headlineSmall,
                    color = HoodieColors.Hood, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (node.isReturn) Text(stringResource(R.string.journey_return_badge, node.returnNumber), style = MaterialTheme.typography.labelLarge, color = HoodieColors.Gold)
            }
            Text(node.placeType.label, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
            JourneyDetails.lines(node, arrivedBy, zone, stringResource(R.string.journey_details_ongoing)).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(line.labelRes), style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Muted)
                    Text(line.value, style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Ink)
                }
            }
            SectionLabel(stringResource(R.string.journey_details_phone))
            val phone = details?.phoneUsage
            if (phone == null || phone.isEmpty) {
                Text(stringResource(R.string.journey_details_phone_none), style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
            } else {
                Text(formatDuration(phone.foregroundMs), style = MaterialTheme.typography.titleMedium, color = HoodieColors.Ink)
                phone.apps.take(3).forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppBadge(AppIconSource.Installed(app.packageName), app.category, size = 22.dp, showCategoryDot = false)
                        Text(app.appLabel, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatDuration(app.foregroundMs), style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
                    }
                }
            }
            PixelButton(stringResource(R.string.journey_details_see_all), onSeeAll, Modifier.fillMaxWidth().testTag("journey_details_see_all"))
            Text(stringResource(R.string.journey_details_note), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        }
    }
}

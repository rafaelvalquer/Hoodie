package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.dayreport.*
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DayReportCard(report: DailyReport, zone: ZoneId, modifier: Modifier = Modifier) {
    val date = report.date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.getDefault()))
    PixelPanel(modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        SectionLabel(stringResource(R.string.day_report_title))
        Text(date.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(when (report.status) {
            DayReportStatus.LIVE -> R.string.day_report_today
            DayReportStatus.CLOSING -> R.string.day_report_closing
            DayReportStatus.PARTIAL -> R.string.day_report_partial
            DayReportStatus.READY -> R.string.day_report_ready
        }), color = HoodieColors.Muted)
        if (report.status == DayReportStatus.PARTIAL) Text(stringResource(R.string.day_report_incomplete), color = HoodieColors.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(R.string.day_report_wake, report.wakeAt?.let { formatClock(it, zone) }
                ?: if (report.wakeEstimated) stringResource(R.string.day_report_wake_unconfirmed) else null, Modifier.weight(1f))
            Metric(R.string.day_report_work, report.workMs?.let(::formatDuration), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(R.string.day_report_commute, report.commutingMs?.let(::formatDuration), Modifier.weight(1f))
            val screen = report.screenTimeMs?.let(::formatDuration)?.let { if (report.phoneEstimated) stringResource(R.string.day_report_estimated, it) else it }
            Metric(R.string.day_report_phone, screen, Modifier.weight(1f))
        }
        if (report.gymMs != null || report.lunchMs != null) {
            val gym = report.gymMs?.let(::formatDuration) ?: "—"
            val lunch = report.lunchMs?.let(::formatDuration) ?: "—"
            Text(stringResource(R.string.day_report_other_activities, gym, lunch), style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
        }
        report.highlight?.let { highlight ->
            SectionLabel(stringResource(R.string.day_report_highlight))
            Text(highlightText(highlight), style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Hood)
        }
    }
}

@Composable
private fun Metric(label: Int, value: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(stringResource(label).uppercase(), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        Text(value ?: stringResource(R.string.day_report_unavailable), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun highlightText(highlight: DayHighlight): String = when (highlight.messageKey) {
    "day_report_highlight_gym" -> stringResource(R.string.day_report_highlight_gym, highlight.arguments.firstOrNull() ?: "")
    "day_report_highlight_return_early" -> stringResource(R.string.day_report_highlight_return_early, highlight.arguments.firstOrNull() ?: "")
    "day_report_highlight_return_late" -> stringResource(R.string.day_report_highlight_return_late, highlight.arguments.firstOrNull() ?: "")
    else -> ""
}

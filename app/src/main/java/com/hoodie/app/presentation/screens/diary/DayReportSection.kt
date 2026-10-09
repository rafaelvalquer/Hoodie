package com.hoodie.app.presentation.screens.diary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hoodie.app.domain.dayreport.DailyReport
import java.time.ZoneId

@Composable
internal fun DayReportSection(report: DailyReport, zone: ZoneId, modifier: Modifier = Modifier) {
    DayReportCard(report, zone, modifier)
}

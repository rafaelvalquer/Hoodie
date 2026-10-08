package com.hoodie.app.domain.dayreport

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.detection.ConfidenceScore
import java.time.LocalDate

enum class DayReportStatus { LIVE, CLOSING, READY, PARTIAL }
enum class DayHighlightType { HOME_RETURN_EARLIER, HOME_RETURN_LATER, COMMUTE_LONGER, GYM, FIRST_VISIT }

data class DayReportStop(
    val placeId: Long?, val name: String, val type: PlaceType, val arrivedAt: Long,
    val departedAt: Long?, val transportBefore: MovementMode?,
)

data class DayHighlight(
    val type: DayHighlightType, val messageKey: String, val arguments: List<String>,
    val confidence: ConfidenceScore, val relatedEventId: String?,
)

data class DailyReport(
    val date: LocalDate, val status: DayReportStatus, val wakeAt: Long?, val wakeEstimated: Boolean,
    val workMs: Long?, val commutingMs: Long?, val screenTimeMs: Long?, val phoneEstimated: Boolean,
    val gymMs: Long?, val lunchMs: Long?, val journey: List<DayReportStop>, val highlight: DayHighlight?,
    val generatedAt: Long,
)

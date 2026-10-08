package com.hoodie.app.domain.daystate

import com.hoodie.app.domain.detection.ConfidenceScore

data class DayStateSnapshot(
    val state: DayState,
    val startedAt: Long,
    val confidence: ConfidenceScore,
    val reason: DayStateReason,
    val provisional: Boolean,
    val updatedAt: Long = startedAt,
)

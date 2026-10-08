package com.hoodie.app.domain.home

import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.daystate.DayStateSnapshot
import com.hoodie.app.domain.detection.ConfidenceScore

enum class HomeNowStatus { CONFIRMED, PROBABLE, UNKNOWN, UNAVAILABLE }

/** Presentation of canonical state only; this model never creates or persists events. */
data class HomeNowSnapshot(
    val dayState: DayStateSnapshot?,
    val context: UserContextType?,
    val contextEventId: Long?,
    val contextStartedAt: Long?,
    val placeId: Long?,
    val placeName: String?,
    val contextConfidence: ConfidenceScore?,
    val contextSource: ContextSource?,
    val wakeAt: Long?,
    val wakeConfidence: WakeConfidence?,
    val status: HomeNowStatus,
    val updatedAt: Long,
)

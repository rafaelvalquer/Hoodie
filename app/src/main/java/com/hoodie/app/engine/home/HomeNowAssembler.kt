package com.hoodie.app.engine.home

import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.daystate.DayStateSnapshot
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.home.HomeNowSnapshot
import com.hoodie.app.domain.home.HomeNowStatus

object HomeNowAssembler {
    fun assemble(
        dayState: DayStateSnapshot?, context: ContextEvent?, placeName: String?, wakeAt: Long?,
        wakeConfidence: WakeConfidence?, now: Long, locationStatus: LocationStatus = LocationStatus.OK,
    ): HomeNowSnapshot {
        val source = context?.source
        val confidence = context?.confidence?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
        val status = when {
            context == null && locationStatus != LocationStatus.OK -> HomeNowStatus.UNAVAILABLE
            context == null || context.type == UserContextType.UNKNOWN || confidence == null || confidence < .45f -> HomeNowStatus.UNKNOWN
            source in setOf(ContextSource.MANUAL, ContextSource.CONFIRMATION, ContextSource.USER_CORRECTION) -> HomeNowStatus.CONFIRMED
            confidence >= .90f -> HomeNowStatus.IDENTIFIED
            confidence >= .60f -> HomeNowStatus.PROBABLE
            else -> HomeNowStatus.UNKNOWN
        }
        return HomeNowSnapshot(dayState, context?.type?.takeUnless { it == UserContextType.UNKNOWN }, context?.id,
            context?.startedAt?.takeIf { it > 0L }, context?.placeId, placeName, confidence?.let(::ConfidenceScore), source,
            wakeAt, wakeConfidence, status, now)
    }
}

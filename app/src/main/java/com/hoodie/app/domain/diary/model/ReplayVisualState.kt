package com.hoodie.app.domain.diary.model

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.domain.phoneinsights.model.VisitPhoneUsage

/** App em primeiro plano num instante do replay. */
data class ReplayPhoneApp(
    val packageName: String,
    val appLabel: String,
    val category: HoodieAppCategory,
    val startedAt: Long,
    val endedAt: Long,
)

/** Tudo o que o mapa e o HUD precisam num instante do replay — uma fonte só. */
data class ReplayVisualState(
    val timestamp: Long?,
    val context: UserContextType?,
    val hoodieActivity: HoodieActivity?,
    val activePhoneApp: ReplayPhoneApp?,
    val activeNodeId: String?,
    val activeTripId: String?,
    val tripProgress: Float,
    val dayPeriod: DayPeriod,
) {
    companion object {
        val IDLE = ReplayVisualState(null, null, null, null, null, null, 0f, DayPeriod.DAY)
    }
}

/** Uma visita com o que aconteceu nela: celular, atividades do Hoodie e eventos. */
data class DiaryVisitDetails(
    val index: Int,
    val visit: PlaceVisit,
    val phoneUsage: VisitPhoneUsage?,
    val hoodieActivities: List<HoodieActivity>,
    val events: List<DiaryTimelineItem>,
)

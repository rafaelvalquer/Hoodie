package com.hoodie.app.engine.diary

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.domain.daycycle.DailyActivityWindow
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import java.time.ZoneId

/**
 * Camada de integração Diário ↔ Phone Insights: pendura os insights no
 * DailyDiary e intercala os usos longos de apps na linha do tempo (e no replay),
 * entre "Saiu para almoço" e "Hoodie foi almoçar".
 */
object DiaryDigitalMerger {

    fun merge(diary: DailyDiary, insights: DailyPhoneInsights?, zone: ZoneId): DailyDiary {
        if (insights == null || insights.isEmpty) return diary
        val sourceItems = timelineItems(insights, zone)
        val phoneItems = if (diary.activityWindow == DailyActivityWindow.EMPTY) sourceItems else sourceItems.mapNotNull { item ->
            val start = maxOf(item.timestamp, diary.activityWindow.activeStartAt)
            val end = minOf(item.endsAt ?: item.timestamp, diary.activityWindow.activeEndAt)
            if (end <= start) null
            else item.copy(
                timestamp = start,
                endsAt = end,
                title = "${item.title.substringBefore(" · ")} · ${formatDuration(end - start)}",
                subtitle = "${formatClock(start, zone)}–${formatClock(end, zone)} · ${item.subtitle?.substringAfter(" · ").orEmpty()}",
            )
        }
        if (phoneItems.isEmpty()) return diary.copy(phoneInsights = insights)
        val timeline = (diary.timeline + phoneItems).sortedBy { it.timestamp }
        return diary.copy(
            timeline = timeline,
            replay = diary.replay.copy(timeline = timeline),
            phoneInsights = insights,
        )
    }

    fun timelineItems(insights: DailyPhoneInsights, zone: ZoneId): List<DiaryTimelineItem> =
        insights.appTimeline
            .filter { it.durationMs >= HoodieConfig.DIARY_PHONE_ITEM_MIN_MS }
            .map { item ->
                DiaryTimelineItem(
                    id = "phone-${item.packageName}-${item.startedAt}",
                    timestamp = item.startedAt,
                    type = DiaryTimelineType.APP_USAGE,
                    actor = DiaryActor.PHONE,
                    title = "${item.appLabel} · ${formatDuration(item.durationMs)}",
                    subtitle = "${formatClock(item.startedAt, zone)}–${formatClock(item.endedAt, zone)} · ${item.category.emoji} ${item.category.label}",
                    emoji = "📱",
                    relatedContext = item.context,
                    endsAt = item.endedAt,
                )
            }
}

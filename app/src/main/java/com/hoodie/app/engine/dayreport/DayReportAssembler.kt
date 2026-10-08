package com.hoodie.app.engine.dayreport

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.dayreport.*
import com.hoodie.app.domain.routine.LearnedRoutineSlot
import java.time.ZoneId

class DayReportAssembler(private val highlights: DayHighlightEngine = DayHighlightEngine()) {
    fun assemble(diary: DailyDiary, routine: List<LearnedRoutineSlot>, status: DayReportStatus, now: Long, zone: ZoneId): DailyReport {
        val wake = diary.activityWindow.takeIf {
            it.activeStartAt > it.civilStartAt && it.wakeConfidence != com.hoodie.app.domain.daycycle.WakeConfidence.LOW &&
                it.wakeReason != com.hoodie.app.domain.daycycle.WakeReason.SCHEDULE_FALLBACK
        }
        val wakeAt = wake?.activeStartAt
        val wakeUnconfirmed = wakeAt == null && diary.activityWindow.activeStartAt > diary.activityWindow.civilStartAt &&
            (diary.activityWindow.wakeConfidence == com.hoodie.app.domain.daycycle.WakeConfidence.LOW ||
                diary.activityWindow.wakeReason == com.hoodie.app.domain.daycycle.WakeReason.SCHEDULE_FALLBACK)
        val noEssentials = diary.visits.isEmpty() && diary.timeline.isEmpty()
        val finalStatus = when {
            status == DayReportStatus.LIVE -> DayReportStatus.LIVE
            status == DayReportStatus.CLOSING -> DayReportStatus.CLOSING
            noEssentials -> DayReportStatus.PARTIAL
            else -> status
        }
        val phone = diary.phoneInsights
        return DailyReport(diary.summary.date, finalStatus, wakeAt, wakeUnconfirmed,
            diary.summary.workMs.takeIf { diary.summary.totalMs > 0 },
            diary.summary.commutingMs.takeIf { diary.movements.isNotEmpty() || it > 0 },
            phone?.summary?.screenTimeMs, phone?.summary?.isEstimated ?: false,
            diary.summary.gymMs.takeIf { it > 0 }, diary.summary.lunchMs.takeIf { it > 0 },
            DayJourneySummaryBuilder.build(diary), highlights.select(diary, routine, zone), now)
    }
}

object DayReportStatusResolver {
    fun resolve(date: java.time.LocalDate, today: java.time.LocalDate, diary: DailyDiary): DayReportStatus = when {
        date == today -> DayReportStatus.LIVE
        date == today.minusDays(1) && diary.activityWindow.sleepAfterEnd == null -> DayReportStatus.CLOSING
        diary.activityWindow.sleepAfterEnd?.provisional == true -> DayReportStatus.CLOSING
        diary.activityWindow.wakeConfidence == com.hoodie.app.domain.daycycle.WakeConfidence.LOW -> DayReportStatus.PARTIAL
        else -> DayReportStatus.READY
    }
}

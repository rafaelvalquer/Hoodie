package com.hoodie.app.domain.dayreport

import com.hoodie.app.engine.dayreport.DayReportAssembler
import com.hoodie.app.engine.dayreport.DayReportStatusResolver
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.routine.LearnedRoutineSlot
import com.hoodie.app.domain.routine.RoutineEventType
import com.hoodie.app.domain.detection.ConfidenceScore
import java.time.ZoneId
import javax.inject.Inject

class GetDayReportUseCase @Inject constructor() {
    operator fun invoke(diary: DailyDiary, routine: List<LearnedRoutineSlot>, today: java.time.LocalDate, now: Long, zone: ZoneId): DailyReport =
        DayReportAssembler().assemble(diary, routine, DayReportStatusResolver.resolve(diary.summary.date, today, diary), now, zone)
}

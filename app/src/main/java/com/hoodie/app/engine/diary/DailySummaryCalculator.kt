package com.hoodie.app.engine.diary

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.engine.timeline.ContextSpan
import java.time.LocalDate

object DailySummaryCalculator {
    fun compute(contexts: List<ContextSpan>, date: LocalDate, dayStart: Long, dayEnd: Long, now: Long): DailySummary {
        val end = minOf(dayEnd, now)
        fun duration(span: ContextSpan) = (minOf(span.endedAt ?: now, end) - maxOf(span.startedAt, dayStart)).coerceAtLeast(0)
        val totals = contexts.groupBy { it.type }.mapValues { (_, spans) -> spans.sumOf(::duration) }
        fun total(vararg types: UserContextType) = types.sumOf { totals[it] ?: 0L }
        val recognized = setOf(UserContextType.HOME, UserContextType.WORK, UserContextType.COMMUTING, UserContextType.LUNCH, UserContextType.DINING, UserContextType.GYM, UserContextType.LEISURE)
        // DINING remains part of the miscellaneous breakdown until it gets a dedicated summary row.
        val other = (totals[UserContextType.DINING] ?: 0L) + totals.filterKeys { it !in recognized }.values.sum()
        return DailySummary(
            date = date,
            homeMs = total(UserContextType.HOME),
            workMs = total(UserContextType.WORK),
            commutingMs = total(UserContextType.COMMUTING),
            lunchMs = total(UserContextType.LUNCH),
            gymMs = total(UserContextType.GYM),
            leisureMs = total(UserContextType.LEISURE),
            otherMs = other,
        )
    }
}

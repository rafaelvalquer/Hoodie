package com.hoodie.app.engine.timeline

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType

data class ContextSpan(val type: UserContextType, val startedAt: Long, val endedAt: Long?)

data class ActivitySpan(val activity: HoodieActivity, val startedAt: Long, val endedAt: Long)

data class DailySummary(
    val userTotals: List<Pair<UserContextType, Long>>,
    val hoodieTotals: List<Pair<HoodieActivity, Long>>,
    val coffees: Int,
) {
    val isEmpty: Boolean get() = userTotals.isEmpty() && hoodieTotals.isEmpty()
}

/** "SEU DIA" e "HOODIE": durações recortadas na janela do dia. */
object DailySummaryCalculator {

    fun compute(
        contexts: List<ContextSpan>,
        activities: List<ActivitySpan>,
        current: ActivitySpan?,
        dayStart: Long,
        dayEnd: Long,
        now: Long,
    ): DailySummary {
        val limit = minOf(dayEnd, now)
        fun clip(start: Long, end: Long?): Long = (minOf(end ?: now, limit) - maxOf(start, dayStart)).coerceAtLeast(0)

        val user = contexts.groupBy { it.type }
            .mapValues { (_, spans) -> spans.sumOf { clip(it.startedAt, it.endedAt) } }
            .filterValues { it >= 60_000 }
            .toList().sortedByDescending { it.second }

        val all = activities + listOfNotNull(current)
        val hoodie = all.groupBy { it.activity }
            .mapValues { (_, spans) -> spans.sumOf { clip(it.startedAt, it.endedAt) } }
            .filterValues { it >= 60_000 }
            .toList().sortedByDescending { it.second }

        val coffees = all.count { it.activity == HoodieActivity.COFFEE && clip(it.startedAt, it.endedAt) > 0 }
        return DailySummary(user, hoodie, coffees)
    }
}
